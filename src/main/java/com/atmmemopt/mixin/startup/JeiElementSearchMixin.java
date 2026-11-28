package com.atmmemopt.mixin.startup;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.opt.startup.JeiFilterOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.SearchMode;
import mezz.jei.common.search.PrefixInfo;
import mezz.jei.common.search.PrefixedSearchable;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.ElementSearch;
import mezz.jei.gui.search.ElementSearchIndex;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 8a: Parallelizes ElementSearch constructor index building and lazily builds
 * the TOOLTIPS index only when the user first performs a '$' tooltip search
 * (or a plain search when JEI tooltip search mode is ENABLED).
 */
@Mixin(value = ElementSearch.class, remap = false)
public abstract class JeiElementSearchMixin {
    @Shadow
    @Final
    private Map<PrefixInfo<IListElementInfo<?>, IListElement<?>>, PrefixedSearchable<IListElementInfo<?>, IListElement<?>>> prefixedSearchables;

    @Shadow
    @Final
    private ElementPrefixParser elementPrefixParser;

    @Shadow
    @Final
    private PrefixInfo<IListElementInfo<?>, IListElement<?>> noPrefix;

    @Shadow
    private void updateCombinedSearchables() {
        throw new AssertionError();
    }

    @Shadow
    private static PrefixedSearchable<IListElementInfo<?>, IListElement<?>> createSearchable(
            PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo,
            Collection<IListElementInfo<?>> elements
    ) {
        throw new AssertionError();
    }

    @Unique
    private boolean atmmemopt$parallelBuilt;

    /** Set when the invoker mixin is unavailable; every rebuild must then keep vanilla behaviour. */
    @Unique
    private boolean atmmemopt$vanillaRebuild;

    /* Volatile + guarded by synchronized(this) so concurrent first searches cannot double-build. */
    @Unique
    private volatile List<IListElementInfo<?>> atmmemopt$deferredTooltipElements;

    @Unique
    private volatile PrefixInfo<IListElementInfo<?>, IListElement<?>> atmmemopt$tooltipPrefix;

    @Unique
    private volatile long atmmemopt$deferredSnapshotNanos;

    /** True after the TTL discarded the snapshot; the next '$' search rebuilds from live elements. */
    @Unique
    private volatile boolean atmmemopt$ttlDiscarded;

    /** Live (non-copied) view of JEI's element infos captured at TTL discard time. */
    @Unique
    private Collection<IListElementInfo<?>> atmmemopt$liveElementInfos;

    @WrapOperation(
            method = "<init>(Lmezz/jei/gui/search/ElementPrefixParser;Ljava/util/Collection;Lmezz/jei/api/runtime/IIngredientManager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/search/ElementSearch;rebuildSearchIndex(Lmezz/jei/gui/search/ElementSearchIndex;Ljava/util/Collection;)V"
            )
    )
    private void atmmemopt$parallelRebuildIndexes(
            ElementSearch instance,
            ElementSearchIndex index,
            Collection<IListElementInfo<?>> elements,
            Operation<Void> original
    ) {
        if (this.atmmemopt$vanillaRebuild) {
            original.call(instance, index, elements);
            return;
        }
        if (!this.atmmemopt$parallelBuilt) {
            this.atmmemopt$parallelBuilt = true;
            // instanceof guard: on JEI version drift the invoker mixin may be absent.
            // Fall back to vanilla index building for *every* rebuild call (the wrapper
            // otherwise suppresses all seven constructor-time rebuilds).
            if (!(this.elementPrefixParser instanceof JeiElementPrefixParserInvoker invoker)) {
                this.atmmemopt$vanillaRebuild = true;
                AtmMemOpt.LOGGER.warn("[AtmMemOpt] JEI ElementPrefixParser invoker unavailable; using vanilla search index building.");
                original.call(instance, index, elements);
                return;
            }
            try {
                this.atmmemopt$deferredTooltipElements = JeiFilterOptHelper.buildAllIndexesParallel(
                        invoker,
                        elements,
                        this.prefixedSearchables,
                        JeiElementSearchMixin::createSearchable
                );
                if (this.atmmemopt$deferredTooltipElements != null) {
                    this.atmmemopt$tooltipPrefix = invoker.atmmemopt$getPrefixInfo(ElementSearchIndex.TOOLTIPS);
                    this.atmmemopt$deferredSnapshotNanos = System.nanoTime();
                    JeiFilterOptHelper.recordDeferredSnapshot(this.atmmemopt$deferredTooltipElements.size());
                }
            } catch (Throwable t) {
                this.atmmemopt$deferredTooltipElements = null;
                JeiFilterOptHelper.recordDeferredConsumed();
                this.atmmemopt$vanillaRebuild = true;
                AtmMemOpt.LOGGER.error("[AtmMemOpt] Parallel JEI index building failed; using vanilla rebuilding.", t);
                original.call(instance, index, elements);
            }
        }
    }

    /** Lazy, main-thread-only TTL check (also called from add() so a stale snapshot stops growing). */
    @Unique
    private void atmmemopt$expireDeferredSnapshotIfTimedOut() {
        if (this.atmmemopt$deferredTooltipElements == null || this.atmmemopt$ttlDiscarded) {
            return;
        }
        if (!JeiFilterOptHelper.isDeferredSnapshotExpired(this.atmmemopt$deferredSnapshotNanos)) {
            return;
        }
        synchronized (this) {
            List<IListElementInfo<?>> deferred = this.atmmemopt$deferredTooltipElements;
            if (deferred == null
                    || !JeiFilterOptHelper.isDeferredSnapshotExpired(this.atmmemopt$deferredSnapshotNanos)) {
                return;
            }
            // Do not discard unless a live rebuild source exists, otherwise '$' search would
            // permanently lose tooltip search functionality.
            Collection<IListElementInfo<?>> live = JeiFilterOptHelper.getLiveElementInfos();
            if (live == null) {
                return;
            }
            this.atmmemopt$deferredTooltipElements = null;
            this.atmmemopt$deferredSnapshotNanos = 0L;
            this.atmmemopt$ttlDiscarded = true;
            this.atmmemopt$liveElementInfos = live;
            JeiFilterOptHelper.recordDeferredDiscarded(deferred.size());
        }
    }

    @Inject(method = "getSearchResults", at = @At("HEAD"))
    private void atmmemopt$buildTooltipIndexOnDemand(
            ElementPrefixParser.TokenInfo tokenInfo,
            CallbackInfoReturnable<Set<IListElement<?>>> cir
    ) {
        atmmemopt$expireDeferredSnapshotIfTimedOut();
        if ((this.atmmemopt$deferredTooltipElements == null && !this.atmmemopt$ttlDiscarded)
                || tokenInfo == null || this.atmmemopt$tooltipPrefix == null) {
            return;
        }
        PrefixInfo<IListElementInfo<?>, IListElement<?>> tooltipPrefix = this.atmmemopt$tooltipPrefix;
        PrefixInfo<IListElementInfo<?>, IListElement<?>> prefix = tokenInfo.prefixInfo();
        boolean needsTooltips = prefix == tooltipPrefix
                || (prefix == this.noPrefix && tooltipPrefix.getMode() == SearchMode.ENABLED);
        if (!needsTooltips) {
            return;
        }
        // Atomic test-and-set: only one caller may build; concurrent searches wait here
        // and then observe the cleared deferred snapshot once the winner published the index.
        synchronized (this) {
            List<IListElementInfo<?>> snapshot = this.atmmemopt$deferredTooltipElements;
            Collection<IListElementInfo<?>> source;
            if (snapshot != null) {
                source = snapshot;
            } else if (this.atmmemopt$ttlDiscarded && this.atmmemopt$liveElementInfos != null) {
                // TTL path: rebuild from JEI's live view, i.e. vanilla-equivalent behaviour.
                source = this.atmmemopt$liveElementInfos;
            } else {
                return;
            }
            long start = System.nanoTime();
            try {
                // Build into a local first: only publish a fully built index, and only
                // then clear the source so a failure can be retried later.
                PrefixedSearchable<IListElementInfo<?>, IListElement<?>> built = createSearchable(tooltipPrefix, source);
                this.prefixedSearchables.put(tooltipPrefix, built);
                this.updateCombinedSearchables();
                JeiFilterOptHelper.recordLazyTooltipBuild((System.nanoTime() - start) / 1_000_000L, source.size());
                JeiFilterOptHelper.recordDeferredConsumed();
                if (snapshot != null) {
                    this.atmmemopt$deferredTooltipElements = null;
                    this.atmmemopt$deferredSnapshotNanos = 0L;
                } else {
                    this.atmmemopt$ttlDiscarded = false;
                    this.atmmemopt$liveElementInfos = null;
                }
            } catch (Throwable t) {
                // Never propagate into JEI's ingredient-filter path. Keep the source
                // so the next '$' search retries the build.
                AtmMemOpt.LOGGER.warn("[AtmMemOpt] Failed to lazily build JEI TOOLTIPS index; will retry on next search.", t);
            }
        }
        /*
         * LIMITATION (A3): the non-TTL deferred build uses the construction-time snapshot. Entries
         * added later via ElementSearch.add() are written into the empty placeholder
         * storage and are lost when it is replaced here. JEI 1.21.1's PrefixedSearchable
         * only exposes getSearchStorage()/getStrings(); ISearchStorage has no "copy all
         * string->element entries" API, and IListElement cannot be converted back into
         * the IListElementInfo that getStrings() requires, so there is no clean storage
         * merge. Rebuilding from a live info collection would need extra invasive
         * injectors; deliberately not attempted here.
         */
    }

    @Inject(method = "add", at = @At("TAIL"), require = 0)
    private <T> void atmmemopt$syncDeferredAdd(
            IListElementInfo<T> info,
            IIngredientManager ingredientManager,
            CallbackInfo ci
    ) {
        atmmemopt$expireDeferredSnapshotIfTimedOut();
        synchronized (this) {
            List<IListElementInfo<?>> deferred = this.atmmemopt$deferredTooltipElements;
            if (deferred != null && info != null) {
                deferred.add(info);
                JeiFilterOptHelper.recordDeferredSnapshotAdd(deferred.size());
            }
        }
    }

    @Inject(method = "rebuildSearchIndexes", at = @At("TAIL"), require = 0)
    private void atmmemopt$clearDeferredOnExplicitRebuild(
            Set<ElementSearchIndex> indexes,
            Collection<IListElementInfo<?>> elements,
            CallbackInfo ci
    ) {
        if (indexes != null && indexes.contains(ElementSearchIndex.TOOLTIPS)) {
            this.atmmemopt$deferredTooltipElements = null;
            this.atmmemopt$deferredSnapshotNanos = 0L;
            this.atmmemopt$ttlDiscarded = false;
            this.atmmemopt$liveElementInfos = null;
            JeiFilterOptHelper.recordDeferredConsumed();
        }
    }
}
