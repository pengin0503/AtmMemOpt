package com.atmmemopt.mixin.tick;

import com.atmmemopt.opt.tick.EntityMergeOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Module 21: Expands ExperienceOrb merge search AABBs from 0.5 blocks to configurable xpMergeRadius.
 * Covers both the per-tick scanForEntities path and the ExperienceOrb.award() spawn path
 * (tryMergeToExisting); the vanilla %40 id bucketing is left untouched.
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
            EntityMergeOptHelper.recordXpScanExpanded();
            return original.call(box, r);
        }
        return original.call(box, value);
    }

    /**
     * ExperienceOrb.award() -> tryMergeToExisting uses AABB.ofSize(pos, 1.0, 1.0, 1.0),
     * i.e. the vanilla 0.5-block radius. Expand it with the same configured radius and
     * counter as scanForEntities, preserving the (orbId - bucket) % 40 merge filter.
     */
    @WrapOperation(
            method = "tryMergeToExisting",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;ofSize(Lnet/minecraft/world/phys/Vec3;DDD)Lnet/minecraft/world/phys/AABB;"
            )
    )
    private static AABB atmmemopt$expandXpSpawnMergeBox(
            Vec3 pos,
            double xSize,
            double ySize,
            double zSize,
            Operation<AABB> original
    ) {
        double r = EntityMergeOptHelper.getXpMergeRadius();
        if (r > 0.5D && r * 2.0D > xSize) {
            EntityMergeOptHelper.recordXpScanExpanded();
            return original.call(pos, r * 2.0D, r * 2.0D, r * 2.0D);
        }
        return original.call(pos, xSize, ySize, zSize);
    }
}
