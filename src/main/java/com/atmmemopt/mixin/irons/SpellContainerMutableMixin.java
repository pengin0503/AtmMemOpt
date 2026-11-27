package com.atmmemopt.mixin.irons;

import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellContainer.Mutable.class, remap = false)
public class SpellContainerMutableMixin {
    @Inject(method = "toImmutable", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internToImmutable(CallbackInfoReturnable<ISpellContainer> cir) {
        cir.setReturnValue(SpellContainerOptHelper.internContainerFromMutable(cir.getReturnValue()));
    }
}
