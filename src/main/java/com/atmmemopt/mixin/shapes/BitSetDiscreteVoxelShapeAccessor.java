package com.atmmemopt.mixin.shapes;

import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.BitSet;

@Mixin(BitSetDiscreteVoxelShape.class)
public interface BitSetDiscreteVoxelShapeAccessor {
    @Accessor("storage")
    BitSet atmmemopt$getStorage();

    @Accessor("xMin")
    int atmmemopt$getXMin();

    @Accessor("yMin")
    int atmmemopt$getYMin();

    @Accessor("zMin")
    int atmmemopt$getZMin();

    @Accessor("xMax")
    int atmmemopt$getXMax();

    @Accessor("yMax")
    int atmmemopt$getYMax();

    @Accessor("zMax")
    int atmmemopt$getZMax();
}
