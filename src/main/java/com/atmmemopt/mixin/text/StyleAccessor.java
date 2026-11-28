package com.atmmemopt.mixin.text;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Module 22: raw font field accessor (the public getter substitutes DEFAULT_FONT). */
@Mixin(Style.class)
public interface StyleAccessor {
    @Accessor("font")
    ResourceLocation atmmemopt$getFont();
}
