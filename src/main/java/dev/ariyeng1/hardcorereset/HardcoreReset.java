package dev.ariyeng1.hardcorereset;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static net.minecraft.commands.Commands.literal;

public final class HardcoreReset implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("hardcorereset");
    static final AttachmentType<String> PLAYER_RUN = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("hardcorereset", "run"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());
    private static HardcoreReset instance;
    private RoundController<RunWorlds.Run> controller;
    private RunWorlds worlds;

    @Override
    public void onInitialize() {
        instance = this;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DeathScoreboard.ensure(server.getScoreboard());
            worlds = new RunWorlds(server);
            controller = new RoundController<>(worlds, System::nanoTime);
            controller.start();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            controller = null;
            worlds = null;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && controller != null) {
                controller.playerDied(player.getGameProfile().name());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (controller == null) return;
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                if (controller.frozen() && player.isDeadOrDying()) {
                    // Use the complete vanilla path: rebind the connection, reset movement,
                    // and clear waitingForRespawn before accepting the next client-loaded packet.
                    player.connection.handleClientCommand(new ServerboundClientCommandPacket(
                            ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                }
            }
            controller.tick();
            if (!controller.frozen()) {
                try {
                    worlds.reconcilePlayers();
                } catch (Exception error) {
                    controller.pause(error);
                }
            }
        });
        ServerPlayerEvents.JOIN.register(player -> {
            var board = player.level().getServer().getScoreboard();
            board.getOrCreatePlayerScore(player, DeathScoreboard.ensure(board)).add(0);
            onJoin(player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> DeathScoreboard.register(dispatcher));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onJoin(newPlayer));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                NextSeedCommand.register(dispatcher, seed -> controller != null && controller.queueNextSeed(seed)));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(literal("hardcorereset").then(literal("status").executes(context -> {
                    if (context.getSource().getEntity() != null || worlds == null) return 0;
                    context.getSource().sendSystemMessage(Component.literal(worlds.status()));
                    return 1;
                })).then(literal("retry").executes(context -> {
                    if (context.getSource().getEntity() != null) {
                        context.getSource().sendFailure(Component.literal("Run this recovery command from the server console."));
                        return 0;
                    }
                    return controller != null && controller.retry() ? 1 : 0;
                }))));
    }

    private void onJoin(ServerPlayer player) {
        try {
            boolean frozen = controller == null || controller.frozen();
            if (worlds != null) worlds.finishLogin(player, frozen);
            if (frozen) player.setGameMode(GameType.SPECTATOR);
            else worlds.reconcilePlayer(player);
        } catch (Exception error) {
            if (controller != null) controller.pause(error);
            else LOG.error("Player login recovery failed", error);
        }
    }

    /** Portal mixins translate vanilla keys to the current run's dimension keys. */
    public static boolean shouldRespawnInPlace(ServerPlayer player) {
        return instance != null && instance.controller != null && instance.controller.frozen()
                && player.isDeadOrDying();
    }

    public static RunWorlds worldsFor(ServerLevel source) {
        return instance != null && instance.worlds != null && instance.worlds.contains(source)
                ? instance.worlds : null;
    }

    public static net.minecraft.nbt.CompoundTag prepareLogin(java.util.UUID id, net.minecraft.nbt.CompoundTag tag) {
        return instance == null || instance.worlds == null ? tag : instance.worlds.prepareLogin(id, tag);
    }

    public static boolean closeBeforeDeletion(ServerLevel level) {
        return instance == null || instance.worlds == null || instance.worlds.closeBeforeDeletion(level);
    }
}
