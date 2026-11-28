package com.atmmemopt.opt.text;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.text.StyleAccessor;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 22: Interns plain (event/font/insertion-free) immutable Style instances.
 *
 * Only the exact {@code net.minecraft.network.chat.Style} class is interned; subclasses and
 * styles carrying a click event, hover event, font or insertion are returned untouched.
 * Values are weakly referenced so modded tooltip trees do not pin cache entries forever.
 */
public final class StyleOptHelper {
    private static final ConcurrentHashMap<PlainStyleKey, KeyedStyleRef> CACHE = new ConcurrentHashMap<>(4096);
    private static final ReferenceQueue<Style> QUEUE = new ReferenceQueue<>();

    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder EMPTY_UNIFIED = new LongAdder();
    private static final LongAdder EVICTIONS = new LongAdder();

    private static volatile int cachedMaxEntries = 65536;
    private static volatile boolean configLoaded = false;

    private StyleOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.PLAIN_STYLE_MAX_CACHE_ENTRIES.get();
            configLoaded = true;
            drainQueue();
            if (CACHE.size() > cachedMaxEntries) {
                EVICTIONS.add(CACHE.size());
                CACHE.clear();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private static int getMaxEntries() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedMaxEntries;
    }

    /**
     * @return a canonical instance for plain styles, the original style otherwise
     */
    public static Style intern(Style style) {
        if (style == null || style == Style.EMPTY) {
            return style;
        }
        // Subclasses from mods are never shared, even when they happen to be plain.
        if (style.getClass() != Style.class) {
            return style;
        }
        if (style.getClickEvent() != null || style.getHoverEvent() != null || style.getInsertion() != null) {
            return style;
        }
        ResourceLocation font = ((StyleAccessor) style).atmmemopt$getFont();
        if (font != null) {
            return style;
        }

        int colorValue = 0;
        TextColor color = style.getColor();
        if (color != null) {
            colorValue = color.getValue();
        }
        boolean bold = style.isBold();
        boolean italic = style.isItalic();
        boolean underlined = style.isUnderlined();
        boolean strikethrough = style.isStrikethrough();
        boolean obfuscated = style.isObfuscated();

        if (color == null && !bold && !italic && !underlined && !strikethrough && !obfuscated) {
            EMPTY_UNIFIED.increment();
            return Style.EMPTY;
        }

        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return style;
        }
        drainQueue();

        PlainStyleKey key = new PlainStyleKey(colorValue, color != null, bold, italic, underlined, strikethrough, obfuscated);
        KeyedStyleRef fastRef = CACHE.get(key);
        if (fastRef != null) {
            Style cached = fastRef.get();
            if (cached != null) {
                if (cached != style) {
                    HITS.increment();
                }
                return cached;
            }
        }
        if (CACHE.size() >= maxEntries) {
            return style;
        }

        final Style[] resultHolder = new Style[1];
        CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                Style cached = existingRef.get();
                if (cached != null) {
                    if (cached != style) {
                        HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (CACHE.size() >= maxEntries) {
                resultHolder[0] = style;
                return null;
            }
            resultHolder[0] = style;
            return new KeyedStyleRef(k, style, QUEUE);
        });
        return resultHolder[0];
    }

    public static int clearCache() {
        int removed = CACHE.size();
        CACHE.clear();
        while (QUEUE.poll() != null) {
            // drain
        }
        return removed;
    }

    private static void drainQueue() {
        Reference<? extends Style> ref;
        while ((ref = QUEUE.poll()) != null) {
            KeyedStyleRef keyed = (KeyedStyleRef) ref;
            CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getHits() {
        return HITS.sum();
    }

    public static long getEmptyUnified() {
        return EMPTY_UNIFIED.sum();
    }

    public static long getEvictions() {
        return EVICTIONS.sum();
    }

    public static int getCacheSize() {
        drainQueue();
        return CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    private record PlainStyleKey(int colorValue, boolean hasColor, boolean bold, boolean italic,
                                 boolean underlined, boolean strikethrough, boolean obfuscated) {
    }

    private static final class KeyedStyleRef extends WeakReference<Style> {
        final PlainStyleKey key;

        KeyedStyleRef(PlainStyleKey key, Style referent, ReferenceQueue<Style> queue) {
            super(referent, queue);
            this.key = key;
        }
    }
}
