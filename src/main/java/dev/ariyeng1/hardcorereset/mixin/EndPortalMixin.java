package dev.ariyeng1.hardcorereset.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ariyeng1.hardcorereset.HardcoreReset;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

@Mixin(EndPortalBlock.class)
abstract class EndPortalMixin {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void reset$returnToRun(ServerLevel source, Entity entity, BlockPos pos,
                                   CallbackInfoReturnable<TeleportTransition> cir) {
        var worlds = HardcoreReset.worldsFor(source);
        if (worlds != null && source.dimension() == worlds.remap(Level.END)) {
            cir.setReturnValue(worlds.exitEnd(entity));
        }
    }

    @ModifyExpressionValue(method = "getPortalDestination", at = {
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;"),
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;OVERWORLD:Lnet/minecraft/resources/ResourceKey;")})
    private ResourceKey<Level> reset$selectRunWorld(ResourceKey<Level> key,
                                                   @Local(argsOnly = true) ServerLevel source) {
        var worlds = HardcoreReset.worldsFor(source);
        return worlds == null ? key : worlds.remap(key);
    }
}
