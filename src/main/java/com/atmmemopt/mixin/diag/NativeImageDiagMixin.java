package com.atmmemopt.mixin.diag;

import com.atmmemopt.diag.NativeImageTracker;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 5: Tracks NativeImage allocations and close() calls when enabled via config.
 * Uses standard @Inject + @Unique fields to remain compatible with AllTheLeaks' NativeImageMixin.
 */
@Mixin(NativeImage.class)
public class NativeImageDiagMixin {
    @Unique
    private String atmmemopt$allocSite;

    @Unique
    private boolean atmmemopt$closed;

    @Inject(
            method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V",
            at = @At("RETURN")
    )
    private void atmmemopt$onAlloc4(NativeImage.Format format, int width, int height, boolean useCalloc, CallbackInfo ci) {
        if (this.atmmemopt$allocSite == null) {
            this.atmmemopt$allocSite = NativeImageTracker.recordAllocation();
        }
    }

    @Inject(
            method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZJ)V",
            at = @At("RETURN")
    )
    private void atmmemopt$onAlloc5(NativeImage.Format format, int width, int height, boolean useStbFree, long pixels, CallbackInfo ci) {
        if (this.atmmemopt$allocSite == null) {
            this.atmmemopt$allocSite = NativeImageTracker.recordAllocation();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void atmmemopt$onClose(CallbackInfo ci) {
        if (!this.atmmemopt$closed && this.atmmemopt$allocSite != null) {
            this.atmmemopt$closed = true;
            NativeImageTracker.recordClose(this.atmmemopt$allocSite);
        }
    }
}
