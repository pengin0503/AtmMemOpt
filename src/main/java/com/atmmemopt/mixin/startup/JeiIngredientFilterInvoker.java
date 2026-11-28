package com.atmmemopt.mixin.startup;

import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.ingredients.IngredientFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

/**
 * Module 8a: exposes JEI's live element-info view so a TTL-discarded deferred TOOLTIPS
 * snapshot can be rebuilt from current elements instead of being lost permanently.
 */
@Mixin(value = IngredientFilter.class, remap = false)
public interface JeiIngredientFilterInvoker {
    @Invoker("getAllElementInfos")
    Collection<IListElementInfo<?>> atmmemopt$getAllElementInfos();
}
