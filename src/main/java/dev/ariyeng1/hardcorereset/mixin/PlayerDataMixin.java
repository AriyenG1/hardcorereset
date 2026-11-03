package dev.ariyeng1.hardcorereset.mixin;

import dev.ariyeng1.hardcorereset.HardcoreReset;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
abstract class PlayerDataMixin {
    @Inject(method = "loadPlayerData", at = @At("RETURN"), cancellable = true)
    private void reset$prepareSafeLogin(NameAndId profile, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        cir.setReturnValue(cir.getReturnValue().map(tag -> HardcoreReset.prepareLogin(profile.id(), tag)));
    }
}
