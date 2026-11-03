package dev.ariyeng1.hardcorereset;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
                      RuntimeLevelHandle nether, RuntimeLevelHandle end) {
        List<RuntimeLevelHandle> levels() { return List.of(overworld, nether, end); }
    }

    private final MinecraftServer server;
    private final Path stateFile;
    private final List<Run> retired = new ArrayList<>();
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
            Run candidate = new Run(state, overworld, nether, end);
            List<CompletableFuture<?>> chunks = new ArrayList<>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    overworld.asLevel().setChunkForced(x, z, true);
                    chunks.add(overworld.asLevel().getChunkSource().getChunkFuture(x, z, ChunkStatus.FULL, true)
                            .thenAccept(result -> {
                                boolean[] loaded = {false};
                                result.ifSuccess(chunk -> loaded[0] = true);
                                if (!loaded[0]) throw new IllegalStateException("Spawn chunk generation failed");
                            }));
                }
            }
            return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> candidate);
        } catch (Exception error) {
            // Preserve partial/resumed dimensions for diagnosis instead of deleting saved data.
            return CompletableFuture.failedFuture(error);
        }
    }

    private RuntimeLevelHandle open(RunState state, String name, ServerLevel template) {
        if (template == null) throw new IllegalStateException("Enable vanilla Nether and End dimensions first");
        var config = new RuntimeLevelConfig()
                .setDimensionType(template.dimensionTypeRegistration())
                .setGenerator(template.getChunkSource().getGenerator())
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
        BlockPos destination = findSpawn(next.overworld().asLevel());
        // Persist before marking any player as belonging to the new run.
        next.state().save(stateFile);
        if (active != null && active != next) retired.add(active);
        active = next;
        spawn = destination;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) enterRun(player, true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.setGameMode(GameType.SURVIVAL);
        server.getPlayerList().saveAll();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != (spawn.getX() >> 4) || z != (spawn.getZ() >> 4)) {
                    next.overworld().asLevel().setChunkForced(x, z, false);
                }
            }
        }
    }

    private BlockPos findSpawn(ServerLevel level) {
        for (int x = -12; x <= 20; x += 4) {
            for (int z = -12; z <= 20; z += 4) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                var below = level.getBlockState(feet.below());
                if (below.isFaceSturdy(level, feet.below(), Direction.UP) && below.getFluidState().isEmpty()
                        && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                        && !below.is(Blocks.MAGMA_BLOCK) && !below.is(Blocks.CAMPFIRE)) return feet;
            }
        }
        // Only ocean/unsafe spawns receive a small starter platform.
        int y = Math.max(64, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(new BlockPos(x, y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState());
            }
        }
        return new BlockPos(0, y, 0);
    }

    public void reconcilePlayers() {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) reconcilePlayer(player);
    }

    public void reconcilePlayer(ServerPlayer player) {
        if (active == null || spawn == null) return;
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
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Hardcore Reset] " + text), false);
    }

    @Override
    public void reportFailure(Throwable error) {
        HardcoreReset.LOG.error("Live reset error; server and connections remain running", error);
    }
}
