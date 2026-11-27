package com.atmmemopt.mixin.iris;

import com.atmmemopt.opt.iris.IrisCachePurger;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PipelineManager.class, remap = false)
public class PipelineManagerMixin {
    @Inject(method = "preparePipeline", at = @At("RETURN"))
    private void atmmemopt$scheduleShaderCachePurge(
            NamespacedId currentDimension,
            CallbackInfoReturnable<WorldRenderingPipeline> cir
    ) {
        IrisCachePurger.scheduleDelayedPurge();
    }
}
