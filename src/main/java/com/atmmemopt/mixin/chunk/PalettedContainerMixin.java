package com.atmmemopt.mixin.chunk;

import com.atmmemopt.opt.chunk.PalettedContainerOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Module 11: Replaces ThreadingDetector allocations in all PalettedContainer constructors
 * with a shared no-op detector. ThreadingDetectorMixin short-circuits checkAndLock()/checkAndUnlock()
 * on the shared detector without cancelling PalettedContainer#acquire()/release(), preserving
 * compatibility with mods that inject into acquire()/release().
 */
@Mixin(PalettedContainer.class)
public class PalettedContainerMixin {
    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/lang/String;)Lnet/minecraft/util/ThreadingDetector;"
            )
    )
    private ThreadingDetector atmmemopt$reuseSharedThreadingDetector(
            String name,
            Operation<ThreadingDetector> original
    ) {
        return PalettedContainerOptHelper.getSharedNoOpDetector();
    }
}
