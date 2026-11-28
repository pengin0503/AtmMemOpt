package com.atmmemopt.mixin.keys;

import com.atmmemopt.opt.keys.KeyInternOptHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Module 23: Interns ResourceKey instances from the static factory. */
@Mixin(ResourceKey.class)
public class ResourceKeyMixin {
    @Inject(
            method = "create(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceKey;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static <T> void atmmemopt$internCreated(
            ResourceKey<? extends Registry<T>> registry,
            ResourceLocation location,
            CallbackInfoReturnable<ResourceKey<T>> cir
    ) {
        cir.setReturnValue(KeyInternOptHelper.internResourceKey(cir.getReturnValue()));
    }
}
