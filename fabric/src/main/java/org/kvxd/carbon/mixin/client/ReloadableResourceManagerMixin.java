package org.kvxd.carbon.mixin.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReload;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SimpleResourceReload;
import net.minecraft.util.Unit;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ReloadableResourceManagerImpl.class)
public abstract class ReloadableResourceManagerMixin {
	@Shadow
	@Final
	private ResourceType type;

	@Redirect(
		method = "reload",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/resource/SimpleResourceReload;start(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/resource/ResourceReload;"
		)
	)
	private ResourceReload carbon$createStartupDeferredReload(
		final ResourceManager resourceManager,
		final List<ResourceReloader> listeners,
		final Executor backgroundExecutor,
		final Executor mainThreadExecutor,
		final CompletableFuture<Unit> initialTask,
		final boolean enableProfiling
	) {
		if (this.type != ResourceType.CLIENT_RESOURCES) {
			return SimpleResourceReload.start(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
		}

		return DeferredReloadManager.createReload(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
	}
}
