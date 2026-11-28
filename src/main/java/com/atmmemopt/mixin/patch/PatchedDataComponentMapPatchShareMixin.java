package com.atmmemopt.mixin.patch;

import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Module 10: Interns patches returned by PatchedDataComponentMap#asPatch() and deduplicates
 * this.patch by switching to the shared interned backing map with copyOnWrite = true.
 *
 * Safe with invasiveopts (Accessories): never shares PatchedDataComponentMap instances themselves,
 * and preserves CoW semantics so any future mutation triggers ensureMapOwnership().
 */
@Mixin(PatchedDataComponentMap.class)
public class PatchedDataComponentMapPatchShareMixin {
    @Shadow
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;

    @Shadow
    private boolean copyOnWrite;

    @Inject(method = "asPatch", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internAsPatchAndShareMap(CallbackInfoReturnable<DataComponentPatch> cir) {
        DataComponentPatch raw = cir.getReturnValue();
        if (raw == null || raw == DataComponentPatch.EMPTY) {
            return;
        }
        DataComponentPatch interned = DataComponentPatchOptHelper.intern(raw);
        if (interned != raw) {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> sharedMap =
                    ((DataComponentPatchAccessor) (Object) interned).atmmemopt$getMap();
            if (sharedMap != null && sharedMap != this.patch) {
                this.copyOnWrite = true;
                this.patch = sharedMap;
                DataComponentPatchOptHelper.recordMapShare();
            }
            cir.setReturnValue(interned);
        }
    }
}
