package com.atmmemopt.mixin.startup;

import com.atmmemopt.opt.startup.ReloadProfiler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Module 8c: Wraps SimpleReloadInstance construction inside SimpleReloadInstance#of
 * without using ThreadLocal state, ensuring exception safety and re-entrancy safety.
 */
@Mixin(SimpleReloadInstance.class)
public class SimpleReloadInstanceMixin {
    @WrapOperation(
            method = "of",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;Ljava/util/concurrent/CompletableFuture;)Lnet/minecraft/server/packs/resources/SimpleReloadInstance;"
            )
    )
    private static SimpleReloadInstance<Void> atmmemopt$profileReloadInstance(
            Executor backgroundExecutor,
            Executor gameExecutor,
            ResourceManager resourceManager,
            List<PreparableReloadListener> listeners,
            @Coerce Object stateFactory,
            CompletableFuture<Unit> initialTask,
            Operation<SimpleReloadInstance<Void>> original
    ) {
        if (listeners == null || listeners.isEmpty()) {
            return original.call(backgroundExecutor, gameExecutor, resourceManager, listeners, stateFactory, initialTask);
        }
        ReloadProfiler.Session session = ReloadProfiler.createSession(listeners);
        SimpleReloadInstance<Void> instance = original.call(
                backgroundExecutor,
                gameExecutor,
                resourceManager,
                session.wrappedListeners,
                stateFactory,
                initialTask
        );
        if (instance != null) {
            instance.done().whenComplete((res, err) -> {
                if (err != null) {
                    session.logFailure(err);
                } else {
                    session.logTop10();
                }
            });
        }
        return instance;
    }
}
