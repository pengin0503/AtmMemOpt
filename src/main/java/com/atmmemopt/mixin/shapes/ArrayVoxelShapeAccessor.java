package com.atmmemopt.mixin.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArrayVoxelShape.class)
public interface ArrayVoxelShapeAccessor {
    @Accessor("xs")
    DoubleList atmmemopt$getXs();

    @Accessor("ys")
    DoubleList atmmemopt$getYs();

    @Accessor("zs")
    DoubleList atmmemopt$getZs();
}
