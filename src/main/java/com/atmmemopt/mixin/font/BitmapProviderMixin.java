package com.atmmemopt.mixin.font;

import com.atmmemopt.opt.font.FontNativeImageHelper;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 24: Observes font bitmap images and their normal vanilla release only.
 * No image is closed here: BitmapProvider retains it for later bakes (see helper Javadoc).
 */
@Mixin(BitmapProvider.class)
public class BitmapProviderMixin {
    @Shadow
    private NativeImage image;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void atmmemopt$observeImage(NativeImage image, CodepointMap<?> glyphs, CallbackInfo ci) {
        FontNativeImageHelper.recordObserved(image);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void atmmemopt$observeVanillaClose(CallbackInfo ci) {
        FontNativeImageHelper.recordProviderClose(this.image);
    }
}
