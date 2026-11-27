package com.atmmemopt.mixin.climate;

import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * Module 13: Interns Biome Climate.Parameter instances created via factories and CODEC.
 */
@Mixin(Climate.Parameter.class)
public class ClimateParameterMixin {
    @Shadow
    @Final
    @Mutable
    public static Codec<Climate.Parameter> CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void atmmemopt$wrapCodec(CallbackInfo ci) {
        CODEC = CODEC.xmap(ClimateParameterOptHelper::intern, Function.identity());
    }

    @Inject(method = "point", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internPoint(float value, CallbackInfoReturnable<Climate.Parameter> cir) {
        cir.setReturnValue(ClimateParameterOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(method = "span(FF)Lnet/minecraft/world/level/biome/Climate$Parameter;", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internSpanFloats(float min, float max, CallbackInfoReturnable<Climate.Parameter> cir) {
        cir.setReturnValue(ClimateParameterOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(
            method = "span(Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;)Lnet/minecraft/world/level/biome/Climate$Parameter;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void atmmemopt$internSpanPair(
            Climate.Parameter min,
            Climate.Parameter max,
            CallbackInfoReturnable<Climate.Parameter> cir
    ) {
        cir.setReturnValue(ClimateParameterOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(
            method = "span(Lnet/minecraft/world/level/biome/Climate$Parameter;)Lnet/minecraft/world/level/biome/Climate$Parameter;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void atmmemopt$internSpanInstance(
            Climate.Parameter other,
            CallbackInfoReturnable<Climate.Parameter> cir
    ) {
        cir.setReturnValue(ClimateParameterOptHelper.intern(cir.getReturnValue()));
    }
}
