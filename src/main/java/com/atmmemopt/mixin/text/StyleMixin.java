package com.atmmemopt.mixin.text;

import com.atmmemopt.opt.text.StyleOptHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Module 22: Interns the immutable Style instances produced by the central private factory
 * (used by the Builder, codecs and helpers). Deliberately does NOT hook
 * MutableComponent#setStyle, which is a much hotter path.
 */
@Mixin(Style.class)
public class StyleMixin {
    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internCreatedStyle(
            Optional<TextColor> color,
            Optional<Boolean> bold,
            Optional<Boolean> italic,
            Optional<Boolean> underlined,
            Optional<Boolean> strikethrough,
            Optional<Boolean> obfuscated,
            Optional<ClickEvent> clickEvent,
            Optional<HoverEvent> hoverEvent,
            Optional<String> insertion,
            Optional<ResourceLocation> font,
            CallbackInfoReturnable<Style> cir
    ) {
        cir.setReturnValue(StyleOptHelper.intern(cir.getReturnValue()));
    }
}
