package com.atmmemopt.opt.owo;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.mixin.owo.DerivedComponentMapAccessor;
import io.wispforest.owo.ext.DerivedComponentMap;
import io.wispforest.owo.mixin.ext.ComponentMapImplAccessor;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.LongAdder;

/**
 * Helper for Module 1: detects whether an Item subclass overrides
 * OwoItem#deriveStackComponents and safely unwraps unused DerivedComponentMap
 * prototypes without replacing the outer PatchedDataComponentMap instance.
 */
public final class OwoDerivedOptHelper {
    private static final String OWO_ITEM_CLASS_NAME = "io.wispforest.owo.ext.OwoItem";
    private static final String DERIVE_METHOD_NAME = "deriveStackComponents";
    private static final String OWO_DERIVED_FIELD_NAME = "owo$derivedMap";

    private static final LongAdder UNWRAPPED_COUNT = new LongAdder();
    private static final LongAdder KEPT_CUSTOM_COUNT = new LongAdder();

    private static final ClassValue<Boolean> HAS_CUSTOM_DERIVE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method method = type.getMethod(
                        DERIVE_METHOD_NAME,
                        DataComponentMap.class,
                        DataComponentPatch.Builder.class
                );
                Class<?> declaring = method.getDeclaringClass();
                return declaring != Item.class && !OWO_ITEM_CLASS_NAME.equals(declaring.getName());
            } catch (NoSuchMethodException e) {
                return Boolean.FALSE;
            } catch (Throwable t) {
                AtmMemOpt.LOGGER.warn("Failed to inspect deriveStackComponents on {}, keeping DerivedComponentMap for safety", type.getName(), t);
                return Boolean.TRUE;
            }
        }
    };

    private static volatile boolean setterFailed = false;

    private OwoDerivedOptHelper() {
    }

    public static boolean hasCustomDerive(Class<?> itemClass) {
        return itemClass != null && HAS_CUSTOM_DERIVE.get(itemClass);
    }

    /**
     * Unwraps DerivedComponentMap from the ItemStack's outer PatchedDataComponentMap prototype
     * and clears ItemStack.owo$derivedMap to null when the item does not override deriveStackComponents.
     *
     * IMPORTANT: The outer PatchedDataComponentMap instance itself is NOT replaced, preserving
     * any per-stack fields attached by other mods (e.g. invasiveopts / Accessories).
     */
    public static void unwrapIfUnused(ItemStack stack, PatchedDataComponentMap components) {
        if (components == null || components == PatchedDataComponentMap.EMPTY) {
            return;
        }
        Item item = stack.getItem();
        if (item == null || item == Items.AIR) {
            return;
        }
        if (hasCustomDerive(item.getClass())) {
            KEPT_CUSTOM_COUNT.increment();
            return;
        }

        ComponentMapImplAccessor accessor = (ComponentMapImplAccessor) (Object) components;
        DataComponentMap currentBase = accessor.owo$getBaseComponents();
        if (currentBase instanceof DerivedComponentMap derived) {
            DataComponentMap originalBase = ((DerivedComponentMapAccessor) derived).atmmemopt$getBase();
            accessor.owo$setBaseComponents(originalBase);
            clearOwoDerivedMapField(stack);
            UNWRAPPED_COUNT.increment();
        }
    }

    private static void clearOwoDerivedMapField(ItemStack stack) {
        MethodHandle setter = FieldHolder.SETTER;
        if (setter == null || setterFailed) {
            return;
        }
        try {
            setter.invokeExact(stack, (DerivedComponentMap) null);
        } catch (Throwable t) {
            setterFailed = true;
            AtmMemOpt.LOGGER.error("Failed to clear ItemStack.owo$derivedMap field", t);
        }
    }

    /**
     * Initialization-on-demand holder so SETTER is a true static final MethodHandle
     * eligible for JIT constant folding and invokeExact inlining.
     */
    private static final class FieldHolder {
        static final MethodHandle SETTER = resolveSetter();

        private static MethodHandle resolveSetter() {
            try {
                Field field = ItemStack.class.getDeclaredField(OWO_DERIVED_FIELD_NAME);
                field.setAccessible(true);
                return MethodHandles.lookup()
                        .unreflectSetter(field)
                        .asType(MethodType.methodType(void.class, ItemStack.class, DerivedComponentMap.class));
            } catch (Throwable t) {
                AtmMemOpt.LOGGER.warn("Could not locate ItemStack.{} field; prototype will still be unwrapped.", OWO_DERIVED_FIELD_NAME, t);
                return null;
            }
        }
    }

    public static long getUnwrappedCount() {
        return UNWRAPPED_COUNT.sum();
    }

    public static long getKeptCustomCount() {
        return KEPT_CUSTOM_COUNT.sum();
    }
}
