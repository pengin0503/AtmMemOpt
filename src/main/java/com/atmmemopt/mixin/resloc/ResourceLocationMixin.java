package com.atmmemopt.mixin.resloc;

import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Module 12: Interns namespace strings in ResourceLocation's constructor and deduplicates
 * ResourceLocation instances across all factory methods.
 */
@Mixin(ResourceLocation.class)
public class ResourceLocationMixin {
    @Shadow
    @Final
    @Mutable
    private String namespace;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;)V", at = @At("TAIL"))
    private void atmmemopt$internNamespace(String namespace, String path, CallbackInfo ci) {
        this.namespace = ResourceLocationOptHelper.internNamespace(this.namespace);
    }

    @Inject(method = "createUntrusted", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internCreateUntrusted(
            String namespace,
            String path,
            CallbackInfoReturnable<ResourceLocation> cir
    ) {
        cir.setReturnValue(ResourceLocationOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(method = "withDefaultNamespace", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internWithDefaultNamespace(
            String path,
            CallbackInfoReturnable<ResourceLocation> cir
    ) {
        cir.setReturnValue(ResourceLocationOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(method = "tryBuild", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internTryBuild(
            String namespace,
            String path,
            CallbackInfoReturnable<ResourceLocation> cir
    ) {
        ResourceLocation res = cir.getReturnValue();
        if (res != null) {
            cir.setReturnValue(ResourceLocationOptHelper.intern(res));
        }
    }

    @Inject(method = "tryBySeparator", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internTryBySeparator(
            String location,
            char separator,
            CallbackInfoReturnable<ResourceLocation> cir
    ) {
        ResourceLocation res = cir.getReturnValue();
        if (res != null) {
            cir.setReturnValue(ResourceLocationOptHelper.intern(res));
        }
    }

    @Inject(method = "withPath(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internWithPath(String path, CallbackInfoReturnable<ResourceLocation> cir) {
        cir.setReturnValue(ResourceLocationOptHelper.intern(cir.getReturnValue()));
    }
}
