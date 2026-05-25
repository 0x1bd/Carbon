package org.kvxd.carbon.mixin.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@Shadow
	@Final
	private PackType type;

	@Redirect(
		method = "createReload",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"
		)
	)
	private ReloadInstance carbon$createStartupDeferredReload(
		final ResourceManager resourceManager,
		final List<PreparableReloadListener> listeners,
		final Executor backgroundExecutor,
		final Executor mainThreadExecutor,
		final CompletableFuture<Unit> initialTask,
		final boolean enableProfiling
	) {
		if (this.type != PackType.CLIENT_RESOURCES) {
			return SimpleReloadInstance.create(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
		}

		return DeferredReloadManager.createReload(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
	}
}
