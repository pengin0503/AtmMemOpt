package com.atmmemopt.mixin.climate;

import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Module 13/18: Interns Climate.Parameter[] parameterSpace arrays in Climate.RTree.Node.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree$Node")
public class ClimateRTreeNodeMixin {
    @Shadow
    @Final
    @Mutable
    protected Climate.Parameter[] parameterSpace;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void atmmemopt$internParameterSpace(List<Climate.Parameter> parameterSpace, CallbackInfo ci) {
        this.parameterSpace = ClimateParameterOptHelper.internArray(this.parameterSpace);
    }
}
