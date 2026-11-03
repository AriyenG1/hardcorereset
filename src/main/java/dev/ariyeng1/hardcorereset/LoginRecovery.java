package dev.ariyeng1.hardcorereset;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Configuration always loads the permanent hub, never a level that can retire mid-login. */
final class LoginRecovery {
    record Destination(ServerPlayer.SavedPosition position, boolean recover) {}
    private final Map<UUID, Destination> destinations = new HashMap<>();

    CompoundTag prepare(UUID id, CompoundTag source, MinecraftServer server) {
        var original = source.read(ServerPlayer.SavedPosition.MAP_CODEC).orElse(ServerPlayer.SavedPosition.EMPTY);
        boolean retired = original.dimension().filter(d -> d.identifier().getNamespace().equals("hardcorereset"))
                .filter(d -> server.getLevel(d) == null).isPresent();
        boolean recover = retired || source.getFloatOr("Health", 20) <= 0
                || source.getIntOr("playerGameType", 0) == 3;
        destinations.put(id, new Destination(original, recover));
        CompoundTag safe = source.copy();
        var hub = server.getRespawnData();
        safe.store(ServerPlayer.SavedPosition.MAP_CODEC, new ServerPlayer.SavedPosition(
                Optional.of(Level.OVERWORLD), Optional.of(Vec3.atBottomCenterOf(hub.pos())), Optional.of(Vec2.ZERO)));
        if (recover) {
            safe.putFloat("Health", 20);
            safe.putInt("playerGameType", 0);
            safe.putShort("DeathTime", (short) 0);
            safe.putShort("HurtTime", (short) 0);
            safe.putFloat("FallDistance", 0);
            safe.remove("RootVehicle");
            safe.remove(ServerPlayer.ENDER_PEARLS_TAG);
        }
        return safe;
    }
    Destination take(UUID id) { return destinations.remove(id); }
}
