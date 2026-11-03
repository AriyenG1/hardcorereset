package dev.ariyeng1.hardcorereset.mixin;

import dev.ariyeng1.hardcorereset.HardcoreReset;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla's complete respawn lifecycle, changing only its spectator destination. */
@Mixin(ServerPlayer.class)
abstract class SpectatorRespawnMixin {
    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void reset$spectateAtDeathLocation(boolean consumeSpawnBlock,
            TeleportTransition.PostTeleportTransition postTransition,
            CallbackInfoReturnable<TeleportTransition> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (HardcoreReset.shouldRespawnInPlace(player)) {
            cir.setReturnValue(new TeleportTransition(player.level(), player.position(), Vec3.ZERO,
                    player.getYRot(), player.getXRot(), postTransition));
        }
    }
}
