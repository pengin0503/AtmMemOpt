package com.atmmemopt.mixin.irons;

import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SpellContainer.class, remap = false)
public interface SpellContainerAccessor {
    @Accessor("slots")
    SpellSlot[] atmmemopt$getSlots();

    @Accessor("maxSpells")
    int atmmemopt$getMaxSpells();

    @Accessor("spellWheel")
    boolean atmmemopt$isSpellWheel();

    @Accessor("mustEquip")
    boolean atmmemopt$isMustEquip();

    @Accessor("improved")
    boolean atmmemopt$isImproved();
}
