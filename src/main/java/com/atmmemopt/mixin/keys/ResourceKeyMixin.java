package com.atmmemopt.mixin.keys;

import com.atmmemopt.opt.keys.KeyInternOptHelper;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/** Module 23: Interns ResourceKey instances from the factories and codecs. */
@Mixin(ResourceKey.class)
public class ResourceKeyMixin {
    @Inject(
            method = "create(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceKey;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static <T> void atmmemopt$internCreated(
            ResourceKey<? extends Registry<T>> registry,
            ResourceLocation location,
            CallbackInfoReturnable<ResourceKey<T>> cir
    ) {
        cir.setReturnValue(KeyInternOptHelper.internResourceKey(cir.getReturnValue()));
    }

    @Inject(method = "createRegistryKey", at = @At("RETURN"), cancellable = true)
    private static <T> void atmmemopt$internRegistryKey(
            ResourceLocation location,
            CallbackInfoReturnable<ResourceKey<Registry<T>>> cir
    ) {
        cir.setReturnValue(KeyInternOptHelper.internResourceKey(cir.getReturnValue()));
    }

    @Inject(method = "codec", at = @At("RETURN"), cancellable = true)
    private static <T> void atmmemopt$wrapCodec(
            ResourceKey<? extends Registry<T>> registry,
            CallbackInfoReturnable<Codec<ResourceKey<T>>> cir
    ) {
        Codec<ResourceKey<T>> codec = cir.getReturnValue();
        cir.setReturnValue(codec.xmap(KeyInternOptHelper::internResourceKey, Function.identity()));
    }

    @Inject(method = "streamCodec", at = @At("RETURN"), cancellable = true)
    private static <T> void atmmemopt$wrapStreamCodec(
            ResourceKey<? extends Registry<T>> registry,
            CallbackInfoReturnable<StreamCodec<ByteBuf, ResourceKey<T>>> cir
    ) {
        StreamCodec<ByteBuf, ResourceKey<T>> codec = cir.getReturnValue();
        cir.setReturnValue(codec.map(KeyInternOptHelper::internResourceKey, Function.identity()));
    }
}
