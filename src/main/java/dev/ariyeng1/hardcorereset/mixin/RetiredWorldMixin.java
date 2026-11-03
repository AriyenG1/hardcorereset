package dev.ariyeng1.hardcorereset.mixin;

import dev.ariyeng1.hardcorereset.HardcoreReset;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fantasy 0.8.4 needs its retired world resources closed before directory deletion. */
@Mixin(targets = "xyz.nucleoid.fantasy.RuntimeLevelManager", remap = false)
abstract class RetiredWorldMixin {
    @Inject(method = "delete", at = @At("HEAD"), cancellable = true, remap = false)
    private void reset$closeRetiredWorld(ServerLevel level, CallbackInfo ci) {
        if (!HardcoreReset.closeBeforeDeletion(level)) ci.cancel();
    }
}
