package dev.ariyeng1.hardcorereset.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ariyeng1.hardcorereset.HardcoreReset;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve vanilla portal placement/scaling while selecting the run's paired worlds. */
@Mixin(NetherPortalBlock.class)
abstract class NetherPortalMixin {
    @ModifyExpressionValue(method = "getPortalDestination", at = {
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;NETHER:Lnet/minecraft/resources/ResourceKey;"),
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;OVERWORLD:Lnet/minecraft/resources/ResourceKey;")})
    private ResourceKey<Level> reset$selectRunWorld(ResourceKey<Level> key,
                                                   @Local(argsOnly = true) ServerLevel source) {
        var worlds = HardcoreReset.worldsFor(source);
        return worlds == null ? key : worlds.remap(key);
    }
}
