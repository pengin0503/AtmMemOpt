package com.atmmemopt.mixin.render;

import com.atmmemopt.opt.render.BerCullingHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 9a: Culls distant BlockEntityRenderer calls safely without conflicting with
 * Sodium / Iris / Flywheel / Colorwheel.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Camera camera;

    @Shadow
    public abstract <E extends BlockEntity> BlockEntityRenderer<E> getRenderer(E blockEntity);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void atmmemopt$cullDistantBlockEntity(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        if (this.camera == null) {
            return;
        }
        BlockEntityRenderer<E> renderer = this.getRenderer(blockEntity);
        if (renderer == null || BerCullingHelper.shouldCull(blockEntity, renderer, this.camera)) {
            ci.cancel();
        }
    }
}
