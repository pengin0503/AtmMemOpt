package com.atmmemopt.mixin.irons;

import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Interns containers produced by Iron's Spellbooks' Codec / StreamCodec decode paths.
 *
 * NOTE (E3): the decode logic lives in compiler-generated lambda methods
 * (lambda$static$4 / lambda$static$7). There is no stable, non-synthetic call in the
 * Codec/StreamCodec *construction* that exposes the decoded ISpellContainer, so a
 * @WrapOperation on construction cannot replace these injectors. No public upstream
 * source was available to pin an alternative name either. These injectors are therefore
 * best-effort: require = 0 lets a mod version that renames/reorders the lambdas load
 * without a crash (interning for that decode path is simply skipped). AtmMemOptMixinPlugin
 * logs a one-shot warning when the expected lambda names are absent from the target class.
 */
@Mixin(value = SpellContainer.class, remap = false)
public class SpellContainerMixin {
    /**
     * Codec decode lambda (SpellContainer.CODEC).
     */
    @Inject(method = "lambda$static$4", at = @At("RETURN"), cancellable = true, require = 0)
    private static void atmmemopt$internCodecDecode(
            Integer maxSpells,
            Boolean spellWheel,
            Boolean mustEquip,
            Boolean improved,
            List<SpellSlot> slots,
            CallbackInfoReturnable<ISpellContainer> cir
    ) {
        cir.setReturnValue(SpellContainerOptHelper.internContainer(cir.getReturnValue()));
    }

    /**
     * StreamCodec decode lambda (SpellContainer.STREAM_CODEC).
     */
    @Inject(method = "lambda$static$7", at = @At("RETURN"), cancellable = true, require = 0)
    private static void atmmemopt$internStreamDecode(
            FriendlyByteBuf buf,
            CallbackInfoReturnable<ISpellContainer> cir
    ) {
        cir.setReturnValue(SpellContainerOptHelper.internContainer(cir.getReturnValue()));
    }
}
