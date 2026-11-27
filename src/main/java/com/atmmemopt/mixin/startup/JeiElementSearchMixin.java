package com.atmmemopt.mixin.startup;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.opt.startup.JeiFilterOptHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 8a: Parallelizes ElementSearch constructor index building and lazily builds
 * the TOOLTIPS index only when the user first performs a '$' tooltip search.
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
                }
            } catch (Throwable t) {
                this.atmmemopt$deferredTooltipElements = null;
                this.atmmemopt$vanillaRebuild = true;
                AtmMemOpt.LOGGER.error("[AtmMemOpt] Parallel JEI index building failed; using vanilla rebuilding.", t);
                original.call(instance, index, elements);
            }
        }
    }

    @Inject(method = "getSearchResults", at = @At("HEAD"))
    private void atmmemopt$buildTooltipIndexOnDemand(
            ElementPrefixParser.TokenInfo tokenInfo,
            CallbackInfoReturnable<Set<IListElement<?>>> cir
    ) {
        if (tokenInfo == null) {
            return;
        }
        PrefixInfo<IListElementInfo<?>, IListElement<?>> tooltipPrefix = this.atmmemopt$tooltipPrefix;
        if (tooltipPrefix == null || tokenInfo.prefixInfo() != tooltipPrefix) {
            return;
        }
        if (this.atmmemopt$deferredTooltipElements == null) {
            return;
        }
        // Atomic test-and-set: only one caller may build, other concurrent searches
        // simply observe the (still empty) placeholder and return an empty result this time.
        synchronized (this) {
            List<IListElementInfo<?>> deferred = this.atmmemopt$deferredTooltipElements;
            if (deferred == null) {
                return;
            }
            long start = System.nanoTime();
            try {
                // Build into a local first: only publish a fully built index, and only
                // then clear the deferred snapshot so a failure can be retried later.
                PrefixedSearchable<IListElementInfo<?>, IListElement<?>> built = createSearchable(tooltipPrefix, deferred);
                this.prefixedSearchables.put(tooltipPrefix, built);
                this.updateCombinedSearchables();
                this.atmmemopt$deferredTooltipElements = null;
                JeiFilterOptHelper.recordLazyTooltipBuild((System.nanoTime() - start) / 1_000_000L, deferred.size());
            } catch (Throwable t) {
                // Never propagate into JEI's ingredient-filter path. Keep the deferred
                // list so the next '$' search retries the build.
                AtmMemOpt.LOGGER.error("[AtmMemOpt] Failed to lazily build JEI TOOLTIPS index; will retry on next search.", t);
            }
        }
        /*
         * LIMITATION (A3): the deferred build uses the construction-time snapshot. Entries
         * added later via ElementSearch.add() are written into the empty placeholder
         * storage and are lost when it is replaced here. JEI 1.21.1's PrefixedSearchable
         * only exposes getSearchStorage()/getStrings(); ISearchStorage has no "copy all
         * string->element entries" API, and IListElement cannot be converted back into
         * the IListElementInfo that getStrings() requires, so there is no clean storage
         * merge. Rebuilding from a live info collection would need extra invasive
         * injectors; deliberately not attempted here.
         */
    }
}
