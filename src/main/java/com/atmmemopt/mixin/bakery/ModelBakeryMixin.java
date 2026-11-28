package com.atmmemopt.mixin.bakery;

import com.atmmemopt.opt.model.ModelBakeryAuditHelper;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Module 26: Audit-only hooks. Tracks the latest bakery instance with a weak reference and
 * times bakeModels(); the actual staging-map counting happens in the existing client reload
 * listener via reflection. No ModelBakery state is touched.
 */
@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @Unique
    private long atmmemopt$bakeStartNanos;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void atmmemopt$trackBakery(
            BlockColors blockColors,
            ProfilerFiller profiler,
            Map<ResourceLocation, BlockModel> modelResources,
            Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources,
            CallbackInfo ci
    ) {
        ModelBakeryAuditHelper.trackBakery((ModelBakery) (Object) this);
    }

    @Inject(method = "bakeModels", at = @At("HEAD"))
    private void atmmemopt$startBakeTimer(ModelBakery.TextureGetter textureGetter, CallbackInfo ci) {
        this.atmmemopt$bakeStartNanos = System.nanoTime();
    }

    @Inject(method = "bakeModels", at = @At("TAIL"))
    private void atmmemopt$recordBakeTime(ModelBakery.TextureGetter textureGetter, CallbackInfo ci) {
        if (this.atmmemopt$bakeStartNanos != 0L) {
            ModelBakeryAuditHelper.recordBakeComplete(System.nanoTime() - this.atmmemopt$bakeStartNanos);
            this.atmmemopt$bakeStartNanos = 0L;
        }
    }
}
