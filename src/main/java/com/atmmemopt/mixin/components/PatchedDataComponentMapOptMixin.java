package com.atmmemopt.mixin.components;

import com.atmmemopt.opt.components.PatchedMapOptStats;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Module 2: Safe empty-patch normalization on PatchedDataComponentMap paths not covered
 * by FerriteCore's TAIL hooks.
 *
 * Compatibility guarantees:
 * - Never shares PatchedDataComponentMap instances across ItemStacks.
 * - Never cancels applyPatch(DataComponentPatch) at HEAD, preserving invasiveopts'
 *   applyPatchHead / applyPatchTail lock pairing for Accessories.
 * - Only fast-returns restorePatch(EMPTY) when this.patch is ALREADY empty (no-op transition),
 *   so non-empty -> empty transitions still trigger invasiveopts' Accessories mutation events.
 */
@Mixin(PatchedDataComponentMap.class)
public class PatchedDataComponentMapOptMixin {
    @Shadow
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;

    @Shadow
    private boolean copyOnWrite;

    @Inject(method = "fromPatch", at = @At("HEAD"), cancellable = true)
    private static void atmmemopt$fastFromEmptyPatch(
            DataComponentMap prototype,
            DataComponentPatch patch,
            CallbackInfoReturnable<PatchedDataComponentMap> cir
    ) {
        if (patch.isEmpty()) {
            PatchedMapOptStats.recordFastFromEmptyPatch();
            cir.setReturnValue(new PatchedDataComponentMap(prototype));
        }
    }

    @WrapOperation(
            method = "applyPatch(Lnet/minecraft/core/component/DataComponentPatch;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/component/PatchedDataComponentMap;ensureMapOwnership()V"
            )
    )
    private void atmmemopt$skipOwnershipOnEmptyApply(
            PatchedDataComponentMap instance,
            Operation<Void> original,
            DataComponentPatch incomingPatch
    ) {
        if (incomingPatch.isEmpty()) {
            PatchedMapOptStats.recordSkippedEmptyApply();
            return;
        }
        original.call(instance);
    }

    @Inject(method = "restorePatch", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$fastEmptyNoOpRestorePatch(DataComponentPatch patch, CallbackInfo ci) {
        if (patch.isEmpty() && this.patch.isEmpty()) {
            if (this.patch != Reference2ObjectMaps.<DataComponentType<?>, Optional<?>>emptyMap()) {
                this.patch = Reference2ObjectMaps.emptyMap();
                this.copyOnWrite = true;
            }
            PatchedMapOptStats.recordNormalizedEmptyRestore();
            ci.cancel();
        }
    }

    @Inject(method = "copy", at = @At("HEAD"))
    private void atmmemopt$normalizeBeforeCopy(CallbackInfoReturnable<PatchedDataComponentMap> cir) {
        if (this.patch.isEmpty() && this.patch != Reference2ObjectMaps.<DataComponentType<?>, Optional<?>>emptyMap()) {
            this.patch = Reference2ObjectMaps.emptyMap();
            this.copyOnWrite = true;
            PatchedMapOptStats.recordNormalizedEmptyCopy();
        }
    }
}
