package com.atmmemopt.mixin.patch;

import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Module 10: Interns DataComponentPatch instances created via CODEC, STREAM_CODEC, and forget().
 */
@Mixin(DataComponentPatch.class)
public class DataComponentPatchMixin {
    @Shadow
    @Final
    @Mutable
    public static Codec<DataComponentPatch> CODEC;

    @Shadow
    @Final
    @Mutable
    public static StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void atmmemopt$wrapCodecs(CallbackInfo ci) {
        CODEC = CODEC.xmap(DataComponentPatchOptHelper::intern, Function.identity());
        STREAM_CODEC = STREAM_CODEC.map(DataComponentPatchOptHelper::intern, Function.identity());
    }

    @Inject(method = "forget", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internForget(Predicate<DataComponentType<?>> predicate, CallbackInfoReturnable<DataComponentPatch> cir) {
        cir.setReturnValue(DataComponentPatchOptHelper.intern(cir.getReturnValue()));
    }
}
