package dev.ariyeng1.hardcorereset;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodData;
import dev.ariyeng1.hardcorereset.mixin.PlayerAccess;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Minecraft operations stay on the server thread; chunk futures never block it. */
public final class RunWorlds implements RoundController.Worlds<RunWorlds.Run> {
    public record Run(RunState state, RuntimeLevelHandle overworld,
                      RuntimeLevelHandle nether, RuntimeLevelHandle end, BlockPos spawn) {
        List<RuntimeLevelHandle> levels() { return List.of(overworld, nether, end); }
    }

    private final MinecraftServer server;
    private final Path stateFile;
    private final List<Run> retired = new ArrayList<>();
    private final LoginRecovery login = new LoginRecovery();
    private final Set<ServerLevel> closing = new HashSet<>();
    private Run active;
    private BlockPos spawn;

    RunWorlds(MinecraftServer server) {
        this.server = server;
        stateFile = server.getWorldPath(LevelResource.ROOT).resolve("hardcorereset/run.properties");
    }

    @Override
    public CompletableFuture<Run> prepare(boolean resume) {
        try {
            // Invalid state is an error; never silently replace a saved run.
            RunState state = resume ? RunState.readOrCreate(stateFile) : RunState.fresh();
            var overworld = open(state, "overworld", server.overworld());
            var nether = open(state, "nether", server.getLevel(Level.NETHER));
            var end = open(state, "end", server.getLevel(Level.END));
            if (end.asLevel().getDragonFight() == null) {
                throw new IllegalStateException("The run's End dimension did not initialize its dragon fight");
            }
            end.asLevel().getDragonFight().init(end.asLevel(), state.seed(), BlockPos.ZERO);
            for (var handle : List.of(overworld, nether, end)) {
                long actualSeed = handle.asLevel().getChunkSource().randomState().seed();
                if (actualSeed != state.seed()) throw new IllegalStateException("World generation seed does not match run seed");
            }
            return NaturalSpawn.find(overworld.asLevel()).thenApplyAsync(position -> {
                overworld.asLevel().setChunkForced(position.getX() >> 4, position.getZ() >> 4, true);
                return new Run(state, overworld, nether, end, position);
            }, server);
        } catch (Exception error) {
            // Preserve partial/resumed dimensions for diagnosis instead of deleting saved data.
            return CompletableFuture.failedFuture(error);
        }
    }

    private RuntimeLevelHandle open(RunState state, String name, ServerLevel template) {
        if (template == null) throw new IllegalStateException("Enable vanilla Nether and End dimensions first");
        var config = new RuntimeLevelConfig()
                .setDimensionType(template.dimensionTypeRegistration())
                .setGenerator(copyGenerator(template))
                .setSeed(state.seed()).setShouldTickTime(true)
                .setDifficulty(template.getDifficulty()).setMirrorOverworldGameRules(true);
        var id = Identifier.fromNamespaceAndPath("hardcorereset", "run_" + state.id() + "/" + name);
        var handle = Fantasy.get(server).getOrOpenPersistentLevel(id, config);
        handle.setTickWhenEmpty(true);
        return handle;
    }

    @Override
    public void spectateEveryone() {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
        }
    }

    @Override
    public void activate(Run next) throws Exception {
        BlockPos destination = next.spawn();
        // Persist before marking any player as belonging to the new run.
        next.state().save(stateFile);
        if (active != null && active != next) retired.add(active);
        active = next;
        spawn = destination;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) enterRun(player, true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.setGameMode(GameType.SURVIVAL);
        server.getPlayerList().saveAll();
    }

    private net.minecraft.world.level.chunk.ChunkGenerator copyGenerator(ServerLevel template) {
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        var codec = net.minecraft.world.level.chunk.ChunkGenerator.CODEC;
        return codec.parse(ops, codec.encodeStart(ops, template.getChunkSource().getGenerator()).getOrThrow()).getOrThrow();
    }

    public void reconcilePlayers() {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) reconcilePlayer(player);
    }

    public void reconcilePlayer(ServerPlayer player) {
        if (active == null || spawn == null) return;
        if (player.connection.player != player || player.isRemoved() || player.isDeadOrDying()) {
            throw new IllegalStateException("Player/connection identity or health invariant failed for " + player.getUUID());
        }
        boolean stale = !active.state().id().toString().equals(player.getAttached(HardcoreReset.PLAYER_RUN));
        if (stale || !contains(player.level())) enterRun(player, false);
    }

    private void enterRun(ServerPlayer player, boolean spectatorUntilAllTransferred) {
        String run = active.state().id().toString();
        if (!run.equals(player.getAttached(HardcoreReset.PLAYER_RUN))) {
            resetPlayer(player);
            player.setAttached(HardcoreReset.PLAYER_RUN, run);
        }
        player.stopRiding();
        player.closeContainer();
        if (player.teleport(new TeleportTransition(active.overworld().asLevel(),
                Vec3.atBottomCenterOf(spawn), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == null) {
            throw new IllegalStateException("Could not transfer player " + player.getUUID());
        }
        player.setRespawnPosition(null, false);
        player.setGameMode(spectatorUntilAllTransferred ? GameType.SPECTATOR : GameType.SURVIVAL);
    }

    private void resetPlayer(ServerPlayer player) {
        player.closeContainer();
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        ((PlayerAccess) player).reset$setFoodData(new FoodData());
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.totalExperience = 0;
        player.clearFire();
        player.setTicksFrozen(0);
        player.setAirSupply(player.getMaxAirSupply());
        player.fallDistance = 0;
        player.setAbsorptionAmount(0);
        player.resetRecipes(server.getRecipeManager().getRecipes());
        for (var advancement : server.getAdvancements().getAllAdvancements()) {
            List<String> completed = new ArrayList<>();
            player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria().forEach(completed::add);
            for (String criterion : completed) player.getAdvancements().revoke(advancement, criterion);
        }
        for (StatType<?> type : BuiltInRegistries.STAT_TYPE) {
            for (Stat<?> stat : type) player.resetStat(stat);
        }
    }

    @Override
    public void retirePrevious() {
        for (Run old : List.copyOf(retired)) {
            for (RuntimeLevelHandle handle : old.levels()) {
                if (!handle.asLevel().players().isEmpty()) throw new IllegalStateException("Old world still has players; retaining it");
            }
            for (RuntimeLevelHandle handle : old.levels()) {
                closing.add(handle.asLevel());
                handle.delete();
            }
            retired.remove(old);
        }
    }

    public boolean contains(ServerLevel level) {
        return active != null && active.levels().stream().anyMatch(handle -> handle.asLevel() == level);
    }

    public ResourceKey<Level> remap(ResourceKey<Level> vanilla) {
        if (active == null) return vanilla;
        if (vanilla == Level.OVERWORLD) return active.overworld().getRegistryKey();
        if (vanilla == Level.NETHER) return active.nether().getRegistryKey();
        if (vanilla == Level.END) return active.end().getRegistryKey();
        return vanilla;
    }

    public net.minecraft.nbt.CompoundTag prepareLogin(java.util.UUID id, net.minecraft.nbt.CompoundTag tag) {
        return login.prepare(id, tag, server);
    }

    public void finishLogin(ServerPlayer player, boolean frozen) {
        var destination = login.take(player.getUUID());
        if (destination == null) return;
        if (destination.recover()) player.removeAttached(HardcoreReset.PLAYER_RUN);
        if (!frozen && !destination.recover() && destination.position().dimension().isPresent()) {
            var target = server.getLevel(destination.position().dimension().get());
            if (target != null && contains(target) && active.state().id().toString().equals(player.getAttached(HardcoreReset.PLAYER_RUN))) {
                var pos = destination.position().position().orElse(Vec3.atBottomCenterOf(spawn));
                var rotation = destination.position().rotation().orElse(net.minecraft.world.phys.Vec2.ZERO);
                player.teleport(new TeleportTransition(target, pos, Vec3.ZERO, rotation.x, rotation.y, TeleportTransition.DO_NOTHING));
            }
        }
    }

    public String status() {
        if (active == null) return "Hardcore Reset is preparing its first run";
        var level = overworld();
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        long terrain = 1;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            terrain = 31 * terrain + generator.getBaseHeight(x * 127, z * 127,
                    Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        }
        String players = server.getPlayerList().getPlayers().stream().map(p -> p.getPlainTextName()
                + ":bound=" + (p.connection.player == p) + ",health=" + p.getHealth()
                + ",removed=" + p.isRemoved() + ",loaded=" + p.connection.hasClientLoaded() + ",changing=" + p.isChangingDimension()).collect(java.util.stream.Collectors.joining(";"));
        return "run=" + active.state().id() + " seed=" + active.state().seed() + " noiseSeed=" + random.seed()
                + " terrain=" + terrain + " spawn=" + spawn + " players=" + players;
    }

    public ServerLevel overworld() { return active.overworld().asLevel(); }

    public TeleportTransition exitEnd(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            TeleportTransition bed = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
            if (contains(bed.newLevel())) return bed;
        }
        return new TeleportTransition(overworld(), Vec3.atBottomCenterOf(spawn), Vec3.ZERO,
                0, 0, TeleportTransition.DO_NOTHING);
    }

    public boolean closeBeforeDeletion(ServerLevel level) {
        if (!closing.contains(level)) return true;
        try {
            level.close();
            closing.remove(level);
            return true;
        } catch (IOException | RuntimeException error) {
            reportFailure(error);
            return false; // Retain the level and files instead of crashing a tick or deleting open files.
        }
    }

    @Override
    public void announce(String text) {
        announce(text, "");
    }

    @Override
    public void announce(String text, String died) {
        String title = text;
        boolean countdown = text.startsWith("New world in ") || text.contains(" died! New world in ");
        if (text.contains(" died! New world in ")) title = "Reset in 10";
        else if (text.startsWith("New world in ")) title = text.replace("New world in ", "Reset in ").replace("...", "");
        else if (text.startsWith("Moving to the new world")) title = "Resetting world...";
        else if (text.equals("A new run has begun!")) title = "New world ready!";
        else if (text.startsWith("World reset paused")) title = "Reset paused — check server logs";
        var heading = Component.literal(title).withStyle(net.minecraft.ChatFormatting.GOLD);
        var subtitle = Component.literal(died.isBlank() ? "" : died + " died")
                .withStyle(net.minecraft.ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                    0, countdown ? 30 : 80, countdown ? 0 : 10));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(heading));
        }
    }

    @Override
    public void reportFailure(Throwable error) {
        HardcoreReset.LOG.error("Live reset error; server and connections remain running", error);
    }
}
