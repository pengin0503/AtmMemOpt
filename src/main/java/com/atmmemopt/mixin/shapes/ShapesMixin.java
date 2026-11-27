package com.atmmemopt.mixin.shapes;

import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public class ShapesMixin {
    @Inject(method = "create(DDDDDD)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internCreate(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        cir.setReturnValue(VoxelShapeOptHelper.internShape(cir.getReturnValue()));
    }

    @Inject(method = "joinUnoptimized", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internJoinUnoptimized(
            VoxelShape shape1,
            VoxelShape shape2,
            BooleanOp function,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        cir.setReturnValue(VoxelShapeOptHelper.internShape(cir.getReturnValue()));
    }
}
