package com.atmmemopt.mixin.irons;

import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellSlot.class, remap = false)
public class SpellSlotMixin {
    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void atmmemopt$internOf(SpellData spellData, int index, CallbackInfoReturnable<SpellSlot> cir) {
        cir.setReturnValue(SpellContainerOptHelper.internSpellSlot(spellData, index));
    }
}
