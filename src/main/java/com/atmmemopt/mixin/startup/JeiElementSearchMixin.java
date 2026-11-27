package com.atmmemopt.mixin.startup;

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
 * the TOOLTIPS index only when the user first performs a '#' tooltip search.
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

    @Unique
    private List<IListElementInfo<?>> atmmemopt$deferredTooltipElements;

    @Unique
    private PrefixInfo<IListElementInfo<?>, IListElement<?>> atmmemopt$tooltipPrefix;

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
        if (!this.atmmemopt$parallelBuilt) {
            this.atmmemopt$parallelBuilt = true;
            this.atmmemopt$deferredTooltipElements = JeiFilterOptHelper.buildAllIndexesParallel(
                    this.elementPrefixParser,
                    elements,
                    this.prefixedSearchables,
                    JeiElementSearchMixin::createSearchable
            );
            if (this.atmmemopt$deferredTooltipElements != null) {
                this.atmmemopt$tooltipPrefix = ((JeiElementPrefixParserInvoker) this.elementPrefixParser)
                        .atmmemopt$getPrefixInfo(ElementSearchIndex.TOOLTIPS);
            }
        }
    }

    @Inject(method = "getSearchResults", at = @At("HEAD"))
    private void atmmemopt$buildTooltipIndexOnDemand(
            ElementPrefixParser.TokenInfo tokenInfo,
            CallbackInfoReturnable<Set<IListElement<?>>> cir
    ) {
        List<IListElementInfo<?>> deferred = this.atmmemopt$deferredTooltipElements;
        if (deferred != null && tokenInfo != null && tokenInfo.prefixInfo() == this.atmmemopt$tooltipPrefix) {
            this.atmmemopt$deferredTooltipElements = null;
            long start = System.nanoTime();
            PrefixedSearchable<IListElementInfo<?>, IListElement<?>> built = createSearchable(this.atmmemopt$tooltipPrefix, deferred);
            this.prefixedSearchables.put(this.atmmemopt$tooltipPrefix, built);
            this.updateCombinedSearchables();
            JeiFilterOptHelper.recordLazyTooltipBuild((System.nanoTime() - start) / 1_000_000L, deferred.size());
        }
    }
}
