package com.atmmemopt.mixin.model;

import com.atmmemopt.opt.model.BakedQuadOptHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Module 15/17: Interns BakedQuad instances (and their vertex int[] arrays) at the RETURN of
 * FaceBakery#bakeQuad. NeoForge mutates the freshly constructed quad in place with
 * QuadTransformers.processInPlace *inside* bakeQuad, so the canonical array must not be
 * published from the BakedQuad constructor; by RETURN all baking mutations are complete.
 */
@Mixin(FaceBakery.class)
public class FaceBakeryMixin {
    @Inject(method = "bakeQuad", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internBakedQuad(
            Vector3f posFrom,
            Vector3f posTo,
            BlockElementFace face,
            TextureAtlasSprite sprite,
            Direction facing,
            ModelState transform,
            BlockElementRotation partRotation,
            boolean shade,
            CallbackInfoReturnable<BakedQuad> cir
    ) {
        cir.setReturnValue(BakedQuadOptHelper.internPostBake(cir.getReturnValue()));
    }
}
