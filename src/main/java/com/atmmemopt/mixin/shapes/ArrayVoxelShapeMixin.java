package com.atmmemopt.mixin.shapes;

import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArrayVoxelShape.class)
public class ArrayVoxelShapeMixin {
    @Shadow
    @Final
    @Mutable
    private DoubleList xs;

    @Shadow
    @Final
    @Mutable
    private DoubleList ys;

    @Shadow
    @Final
    @Mutable
    private DoubleList zs;

    @Inject(
            method = "<init>(Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;Lit/unimi/dsi/fastutil/doubles/DoubleList;Lit/unimi/dsi/fastutil/doubles/DoubleList;Lit/unimi/dsi/fastutil/doubles/DoubleList;)V",
            at = @At("TAIL")
    )
    private void atmmemopt$internCoordinateLists(
            DiscreteVoxelShape shape,
            DoubleList xs,
            DoubleList ys,
            DoubleList zs,
            CallbackInfo ci
    ) {
        this.xs = VoxelShapeOptHelper.internDoubleList(this.xs);
        this.ys = VoxelShapeOptHelper.internDoubleList(this.ys);
        this.zs = VoxelShapeOptHelper.internDoubleList(this.zs);
        VoxelShapeAccessor shapeAcc = (VoxelShapeAccessor) this;
        DiscreteVoxelShape cur = shapeAcc.atmmemopt$getShape();
        DiscreteVoxelShape interned = VoxelShapeOptHelper.internDiscreteShape(cur);
        if (interned != cur) {
            shapeAcc.atmmemopt$setShape(interned);
        }
    }
}
