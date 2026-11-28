package com.atmmemopt.mixin.text;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Module 22: raw field accessors. The public isBold()/isItalic()/... helpers collapse a
 * nullable Boolean to primitive false, so they cannot distinguish "inherit" (null) from an
 * explicit false; these accessors expose the actual nullable field values. The font accessor
 * is needed because the public getter substitutes DEFAULT_FONT.
 */
@Mixin(Style.class)
public interface StyleAccessor {
    @Accessor("font")
    ResourceLocation atmmemopt$getFont();

    @Accessor("bold")
    Boolean atmmemopt$getBold();

    @Accessor("italic")
    Boolean atmmemopt$getItalic();

    @Accessor("underlined")
    Boolean atmmemopt$getUnderlined();

    @Accessor("strikethrough")
    Boolean atmmemopt$getStrikethrough();

    @Accessor("obfuscated")
    Boolean atmmemopt$getObfuscated();
}
