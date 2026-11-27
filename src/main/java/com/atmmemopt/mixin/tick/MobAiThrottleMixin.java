package com.atmmemopt.mixin.tick;

import com.atmmemopt.opt.tick.MobAiThrottleHelper;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 20: Throttles serverAiStep() for distant non-critical mobs.
 */
@Mixin(Mob.class)
public class MobAiThrottleMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void atmmemopt$throttleDistantMobAi(CallbackInfo ci) {
        if (MobAiThrottleHelper.shouldThrottle((Mob) (Object) this)) {
            ci.cancel();
        }
    }
}
