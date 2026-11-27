package com.atmmemopt.mixin.model;

import com.atmmemopt.opt.model.BakedQuadOptHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 15: Deduplicates BakedQuad vertex arrays in the canonical 6-arg NeoForge constructor.
 */
@Mixin(BakedQuad.class)
public class BakedQuadMixin {
    @Shadow
    @Final
    @Mutable
    protected int[] vertices;

    @Inject(
            method = "<init>([IILnet/minecraft/core/Direction;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZ)V",
            at = @At("TAIL")
    )
    private void atmmemopt$dedupVertices(
            int[] vertices,
            int tintIndex,
            Direction direction,
            TextureAtlasSprite sprite,
            boolean shade,
            boolean hasAmbientOcclusion,
            CallbackInfo ci
    ) {
        this.vertices = BakedQuadOptHelper.internVertices(this.vertices);
    }
}
