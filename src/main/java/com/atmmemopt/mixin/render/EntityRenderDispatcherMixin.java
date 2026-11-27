package com.atmmemopt.mixin.render;

import com.atmmemopt.opt.render.EntityCullingHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Module 9d: Distance-culls distant entities (and tightly culls ItemEntity / ExperienceOrb)
 * in EntityRenderDispatcher#shouldRender before frustum/renderer work.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void atmmemopt$cullDistantEntity(
            E entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (EntityCullingHelper.shouldCull(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
