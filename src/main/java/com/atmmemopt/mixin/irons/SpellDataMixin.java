package com.atmmemopt.mixin.irons;

import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellData.class, remap = false)
public class SpellDataMixin {
    @Inject(method = "readFromBuffer", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internReadFromBuffer(FriendlyByteBuf buffer, CallbackInfoReturnable<SpellData> cir) {
        cir.setReturnValue(SpellContainerOptHelper.internSpellData(cir.getReturnValue()));
    }
}
