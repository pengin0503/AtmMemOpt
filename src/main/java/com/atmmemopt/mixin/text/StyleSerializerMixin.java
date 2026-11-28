package com.atmmemopt.mixin.text;

import com.atmmemopt.opt.text.StyleOptHelper;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Module 22: Intercepts Style.CODEC / STREAM_CODEC decode results so decoded plain styles
 * share the same canonical instances as styles created through Style#create.
 */
@Mixin(Style.Serializer.class)
public class StyleSerializerMixin {
    @Shadow
    @Final
    @Mutable
    public static Codec<Style> CODEC;

    @Shadow
    @Final
    @Mutable
    public static StreamCodec<RegistryFriendlyByteBuf, Style> TRUSTED_STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void atmmemopt$wrapCodecs(CallbackInfo ci) {
        CODEC = CODEC.xmap(StyleOptHelper::intern, Function.identity());
        TRUSTED_STREAM_CODEC = TRUSTED_STREAM_CODEC.map(StyleOptHelper::intern, Function.identity());
    }
}
