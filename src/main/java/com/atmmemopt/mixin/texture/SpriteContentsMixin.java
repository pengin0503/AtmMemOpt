package com.atmmemopt.mixin.texture;

import com.atmmemopt.opt.texture.SpriteMipmapReleaseHelper;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    /** Set by increaseMipLevel(); consumed by the next upload of the regenerated levels. */
    @Unique
    private boolean atmmemopt$mipRegenPending;

    @Inject(
            method = "increaseMipLevel",
            at = @At("RETURN")
    )
    private void atmmemopt$markRegeneratedMipmaps(int mipLevel, CallbackInfo ci) {
        this.atmmemopt$mipRegenPending = true;
    }

    /**
     * Re-applies the M14 trim after MipmapGenerator replaced byMipLevel. Runs at the end of
     * upload() (before uploadFirstFrame's own trim hook), so level 0 remains originalImage
     * and the GPU has already consumed levels 1..N.
     */
    @Inject(
            method = "upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V",
            at = @At("TAIL")
    )
    private void atmmemopt$reTrimRegeneratedMipmaps(
            int x, int y, int frameX, int frameY, NativeImage[] atlasData, CallbackInfo ci
    ) {
        if (!this.atmmemopt$mipRegenPending) {
            return;
        }
        this.atmmemopt$mipRegenPending = false;
        if (this.byMipLevel == null || this.byMipLevel.length <= 1
                || !SpriteMipmapReleaseHelper.isStaticSprite((SpriteContents) (Object) this)) {
            return;
        }
        NativeImage[] trimmed = SpriteMipmapReleaseHelper.releaseStaticMipmaps(this.originalImage, this.byMipLevel);
        if (trimmed.length < this.byMipLevel.length) {
            this.byMipLevel = trimmed;
            SpriteMipmapReleaseHelper.recordReTrim();
        }
    }

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
