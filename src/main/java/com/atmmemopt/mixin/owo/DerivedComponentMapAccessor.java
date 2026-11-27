package com.atmmemopt.mixin.owo;

import io.wispforest.owo.ext.DerivedComponentMap;
import net.minecraft.core.component.DataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DerivedComponentMap.class, remap = false)
public interface DerivedComponentMapAccessor {
    @Accessor("base")
    DataComponentMap atmmemopt$getBase();
}
