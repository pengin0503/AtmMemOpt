package com.atmmemopt.mixin.startup;

import mezz.jei.common.search.PrefixInfo;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.ElementSearchIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElementPrefixParser.class, remap = false)
public interface JeiElementPrefixParserInvoker {
    @Invoker("getPrefixInfo")
    PrefixInfo<IListElementInfo<?>, IListElement<?>> atmmemopt$getPrefixInfo(ElementSearchIndex index);
}
