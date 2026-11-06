package dev.ariyeng1.hardcorereset.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ariyeng1.hardcorereset.HardcoreReset;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The shared fire-placement/ignition gate must recognize the run's paired dimensions too. */
@Mixin(BaseFireBlock.class)
abstract class PortalIgnitionMixin {
    @ModifyExpressionValue(method = "inPortalDimension", at = {
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;OVERWORLD:Lnet/minecraft/resources/ResourceKey;"),
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;NETHER:Lnet/minecraft/resources/ResourceKey;")})
    private static ResourceKey<Level> reset$allowRunPortals(ResourceKey<Level> key,
                                                          @Local(argsOnly = true) Level level) {
        var worlds = level instanceof ServerLevel source ? HardcoreReset.worldsFor(source) : null;
        return worlds == null ? key : worlds.remap(key);
    }
}
