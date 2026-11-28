package com.atmmemopt.mixin.tick;

import com.atmmemopt.opt.tick.EntityMergeOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Module 21: Expands ItemEntity#mergeWithNeighbours search AABB from 0.5 blocks to configurable itemMergeRadius.
 */
@Mixin(ItemEntity.class)
public class ItemEntityMergeMixin {
    @WrapOperation(
            method = "mergeWithNeighbours",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"
            )
    )
    private AABB atmmemopt$expandItemMergeBox(
            AABB box,
            double x,
            double y,
            double z,
            Operation<AABB> original
    ) {
        double r = EntityMergeOptHelper.getItemMergeRadius();
        if (r > x) {
            EntityMergeOptHelper.recordItemScanExpanded();
            return original.call(box, r, y, r);
        }
        return original.call(box, x, y, z);
    }
}
