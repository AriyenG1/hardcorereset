package dev.ariyeng1.hardcorereset;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.CompletableFuture;

/** Vanilla's climate-biased origin and 11x11 spiral search, loading one candidate at a time. */
final class NaturalSpawn {
    static CompletableFuture<BlockPos> find(ServerLevel level) {
        var source = level.getChunkSource();
        ChunkPos origin = source.getGenerator().getOrigin(source.randomState());
        return search(level, origin, 0, 0, 0, -1, 0);
    }
    private static CompletableFuture<BlockPos> search(ServerLevel level, ChunkPos origin,
            int x, int z, int dx, int dz, int index) {
        if (index >= 121) return CompletableFuture.failedFuture(
                new IllegalStateException("Vanilla spawn search found no natural land; refusing to build an ocean platform"));
        ChunkPos chunk = new ChunkPos(origin.x() + x, origin.z() + z);
        return level.getChunkSource().addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, chunk, 0)
                .thenComposeAsync(ignored -> {
                    BlockPos pos = PlayerSpawnFinder.getSpawnPosInChunk(level, chunk);
                    if (pos != null && level.getFluidState(pos).isEmpty() && level.getBlockState(pos).isAir()
                            && level.getBlockState(pos.above()).isAir()) {
                        return CompletableFuture.completedFuture(pos);
                    }
                    int nextDx = dx, nextDz = dz;
                    if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) {
                        nextDx = -dz; nextDz = dx;
                    }
                    return search(level, origin, x + nextDx, z + nextDz, nextDx, nextDz, index + 1);
                }, level.getServer());
    }
}
