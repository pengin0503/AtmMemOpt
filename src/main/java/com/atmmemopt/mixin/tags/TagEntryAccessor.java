package com.atmmemopt.mixin.tags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagEntry.class)
public interface TagEntryAccessor {
    @Accessor("id")
    ResourceLocation atmmemopt$getId();

    @Accessor("tag")
    boolean atmmemopt$isTag();

    @Accessor("required")
    boolean atmmemopt$isRequired();
}
