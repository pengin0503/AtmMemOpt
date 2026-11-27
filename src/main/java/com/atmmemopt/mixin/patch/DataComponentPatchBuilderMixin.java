package com.atmmemopt.mixin.patch;

import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import net.minecraft.core.component.DataComponentPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Module 10: Interns DataComponentPatch instances returned by DataComponentPatch.Builder#build().
 */
@Mixin(DataComponentPatch.Builder.class)
public class DataComponentPatchBuilderMixin {
    @Inject(method = "build", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internBuiltPatch(CallbackInfoReturnable<DataComponentPatch> cir) {
        cir.setReturnValue(DataComponentPatchOptHelper.intern(cir.getReturnValue()));
    }
}
