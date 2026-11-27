package com.atmmemopt.mixin.chunk;

import com.atmmemopt.opt.chunk.PalettedContainerOptHelper;
import net.minecraft.util.ThreadingDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 11 safety net: ensures direct calls to checkAndLock()/checkAndUnlock() on the
 * shared no-op ThreadingDetector never contend or throw.
 */
@Mixin(ThreadingDetector.class)
public class ThreadingDetectorMixin {
    @Inject(method = "checkAndLock", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$noopLock(CallbackInfo ci) {
        if (PalettedContainerOptHelper.isSharedNoOp((ThreadingDetector) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "checkAndUnlock", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$noopUnlock(CallbackInfo ci) {
        if (PalettedContainerOptHelper.isSharedNoOp((ThreadingDetector) (Object) this)) {
            ci.cancel();
        }
    }
}
