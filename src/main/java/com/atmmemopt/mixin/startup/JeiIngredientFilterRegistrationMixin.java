package com.atmmemopt.mixin.startup;

import com.atmmemopt.opt.startup.JeiFilterOptHelper;
import mezz.jei.gui.ingredients.IngredientFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 8a: registers a weak link to JEI's filter so the deferred TOOLTIPS index can be
 * rebuilt from live elements if its snapshot expires before the first '$' search.
 */
@Mixin(value = IngredientFilter.class, remap = false)
public class JeiIngredientFilterRegistrationMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void atmmemopt$registerFilter(CallbackInfo ci) {
        if ((Object) this instanceof JeiIngredientFilterInvoker invoker) {
            JeiFilterOptHelper.registerIngredientFilter(invoker);
        }
    }
}
