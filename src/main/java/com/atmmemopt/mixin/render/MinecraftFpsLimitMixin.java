package com.atmmemopt.mixin.render;

import com.atmmemopt.opt.render.UnfocusedFpsLimitHelper;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Module 9b: Caps framerate when the Minecraft window is unfocused to reduce idle GPU/CPU usage.
 */
@Mixin(Minecraft.class)
public class MinecraftFpsLimitMixin {
    @Shadow
    private boolean windowActive;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$limitUnfocusedFps(CallbackInfoReturnable<Integer> cir) {
        if (!this.windowActive) {
            int limit = UnfocusedFpsLimitHelper.getCachedLimit();
            if (limit > 0 && cir.getReturnValueI() > limit) {
                cir.setReturnValue(limit);
            }
        }
    }
}
