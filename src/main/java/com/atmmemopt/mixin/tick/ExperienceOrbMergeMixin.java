package com.atmmemopt.mixin.tick;

import com.atmmemopt.opt.tick.EntityMergeOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Module 21: Expands ExperienceOrb#scanForEntities merge search AABB from 0.5 blocks to configurable xpMergeRadius.
 */
@Mixin(ExperienceOrb.class)
public class ExperienceOrbMergeMixin {
    @WrapOperation(
            method = "scanForEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"
            )
    )
    private AABB atmmemopt$expandXpMergeBox(
            AABB box,
            double value,
            Operation<AABB> original
    ) {
        double r = EntityMergeOptHelper.getXpMergeRadius();
        if (r > value) {
            return original.call(box, r);
        }
        return original.call(box, value);
    }
}
