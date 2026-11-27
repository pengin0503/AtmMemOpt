package com.atmmemopt.mixin.texture;

import com.atmmemopt.opt.texture.SpriteMipmapReleaseHelper;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 14: After uploading a static SpriteContents to the GPU atlas, closes and drops
 * mipmap NativeImages (levels 1..N) while keeping originalImage (level 0) intact.
 *
 * Hooks right after SpriteContents#upload(...) inside uploadFirstFrame(), which is only
 * executed on the non-animated (animatedTexture == null) branch.
 */
@Mixin(SpriteContents.class)
public class SpriteContentsMixin {
    @Shadow
    @Final
    private NativeImage originalImage;

    @Shadow
    public NativeImage[] byMipLevel;

    @Inject(
            method = "uploadFirstFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/SpriteContents;upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void atmmemopt$releaseStaticMipmaps(int x, int y, CallbackInfo ci) {
        if (this.byMipLevel != null && this.byMipLevel.length > 1) {
            this.byMipLevel = SpriteMipmapReleaseHelper.releaseStaticMipmaps(this.originalImage, this.byMipLevel);
        }
    }
}
