package com.atmmemopt.mixin.owo;

import com.atmmemopt.opt.owo.OwoDerivedOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wispforest.owo.ext.DerivedComponentMap;
import io.wispforest.owo.ext.OwoItem;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Module 1: Lazily allocates DerivedComponentMap#delegate (PatchedDataComponentMap)
 * only when Item#deriveStackComponents actually produces a non-empty patch.
 */
@Mixin(value = DerivedComponentMap.class, remap = false)
public class DerivedComponentMapMixin {
    @Shadow
    @Final
    private DataComponentMap base;

    @Shadow
    @Final
    @Mutable
    private PatchedDataComponentMap delegate;

    @WrapOperation(
            method = "<init>(Lnet/minecraft/core/component/DataComponentMap;)V",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/core/component/DataComponentMap;)Lnet/minecraft/core/component/PatchedDataComponentMap;"
            )
    )
    private PatchedDataComponentMap atmmemopt$lazyDelegateInit(DataComponentMap baseMap, Operation<PatchedDataComponentMap> original) {
        return null;
    }

    @Inject(method = "derive", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$lazyDerive(ItemStack stack, CallbackInfo ci) {
        Item item = stack.getItem();
        if (item == null || !OwoDerivedOptHelper.hasCustomDerive(item.getClass())) {
            this.delegate = null;
            ci.cancel();
            return;
        }

        // Reset any previous derived patch before re-deriving so stack.getComponents() sees clean state
        if (this.delegate != null) {
            this.delegate.restorePatch(DataComponentPatch.EMPTY);
        }

        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        ((OwoItem) (Object) item).deriveStackComponents(stack.getComponents(), builder);
        DataComponentPatch patch = builder.build();

        if (patch.isEmpty()) {
            this.delegate = null;
        } else {
            if (this.delegate == null) {
                this.delegate = new PatchedDataComponentMap(this.base);
            }
            this.delegate.restorePatch(patch);
        }
        ci.cancel();
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void atmmemopt$getFallback(DataComponentType<? extends T> type, CallbackInfoReturnable<T> cir) {
        if (this.delegate == null) {
            cir.setReturnValue(this.base.get(type));
        }
    }

    @Inject(method = "keySet", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$keySetFallback(CallbackInfoReturnable<Set<DataComponentType<?>>> cir) {
        if (this.delegate == null) {
            cir.setReturnValue(this.base.keySet());
        }
    }
}
