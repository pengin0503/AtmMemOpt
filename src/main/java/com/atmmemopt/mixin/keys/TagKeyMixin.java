package com.atmmemopt.mixin.keys;

import com.atmmemopt.opt.keys.KeyInternOptHelper;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/** Module 23: Interns TagKey instances from the static factory and both codec factories. */
@Mixin(TagKey.class)
public class TagKeyMixin {
    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private static <T> void atmmemopt$internCreated(
            ResourceKey<? extends Registry<T>> registry,
            ResourceLocation location,
            CallbackInfoReturnable<TagKey<T>> cir
    ) {
        cir.setReturnValue(KeyInternOptHelper.internTagKey(cir.getReturnValue()));
    }

    @Inject(method = "codec", at = @At("RETURN"), cancellable = true)
    private static <T> void atmmemopt$wrapCodec(
            ResourceKey<? extends Registry<T>> registry,
            CallbackInfoReturnable<Codec<TagKey<T>>> cir
    ) {
        Codec<TagKey<T>> codec = cir.getReturnValue();
        cir.setReturnValue(codec.xmap(KeyInternOptHelper::internTagKey, Function.identity()));
    }

    @Inject(method = "hashedCodec", at = @At("RETURN"), cancellable = true)
    private static <T> void atmmemopt$wrapHashedCodec(
            ResourceKey<? extends Registry<T>> registry,
            CallbackInfoReturnable<Codec<TagKey<T>>> cir
    ) {
        Codec<TagKey<T>> codec = cir.getReturnValue();
        cir.setReturnValue(codec.xmap(KeyInternOptHelper::internTagKey, Function.identity()));
    }
}
