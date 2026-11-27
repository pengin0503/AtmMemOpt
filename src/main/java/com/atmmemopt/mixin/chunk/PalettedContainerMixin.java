package com.atmmemopt.mixin.chunk;

import com.atmmemopt.opt.chunk.PalettedContainerOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 11: Replaces ThreadingDetector allocations in all PalettedContainer constructors
 * with a shared no-op detector and skips acquire()/release() lock overhead.
 */
@Mixin(PalettedContainer.class)
public class PalettedContainerMixin {
    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/lang/String;)Lnet/minecraft/util/ThreadingDetector;"
            )
    )
    private ThreadingDetector atmmemopt$reuseSharedThreadingDetector(
            String name,
            Operation<ThreadingDetector> original
    ) {
        return PalettedContainerOptHelper.getSharedNoOpDetector();
    }

    @Inject(method = "acquire", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$skipAcquire(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$skipRelease(CallbackInfo ci) {
        ci.cancel();
    }
}
