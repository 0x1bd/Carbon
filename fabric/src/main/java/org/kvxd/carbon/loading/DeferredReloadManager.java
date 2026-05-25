package org.kvxd.carbon.loading;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReload;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.SimpleResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import org.slf4j.Logger;

public final class DeferredReloadManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier GUI_ATLAS = Identifier.ofVanilla("gui");
	private static final Set<String> STARTUP_DEFER_ONLY_LISTENERS = Set.of(
		"net.minecraft.client.sound.SoundManager",
		"net.minecraft.client.render.WorldRenderer",
		"net.minecraft.client.render.block.entity.BlockEntityRenderManager",
		"net.minecraft.client.render.entity.EntityRenderManager",
		"net.minecraft.client.particle.ParticleManager",
		"net.minecraft.client.resource.GrassColormapResourceSupplier",
		"net.minecraft.client.resource.FoliageColormapResourceSupplier",
		"net.minecraft.client.resource.DryFoliageColormapResourceSupplier",
		"net.minecraft.client.resource.VideoWarningManager",
		"net.minecraft.client.resource.waypoint.WaypointStyleAssetManager",
		"net.minecraft.client.render.item.equipment.EquipmentModelLoader",
		"net.minecraft.client.render.model.BakedModelManager"
	);
	private static final Set<String> STARTUP_REPLAY_LISTENERS = Set.of(
		"net.minecraft.client.font.FontManager",
		"net.minecraft.client.texture.AtlasManager"
	);
	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("carbon.loading.deferStartupResources", "true"));
	private static final boolean DEFER_UNICODE_FONTS = Boolean.parseBoolean(System.getProperty("carbon.loading.deferUnicodeFonts", "true"));
	private static final boolean SKIP_LOADING_OVERLAY = Boolean.parseBoolean(System.getProperty("carbon.loading.skipLoadingOverlay", "true"));
	private static final int START_DELAY_TICKS = Integer.getInteger("carbon.loading.deferStartupDelayTicks", 20);
	private static final AtomicBoolean INITIAL_RELOAD_FILTERED = new AtomicBoolean();

	private static volatile DeferredBatch pendingBatch;
	private static volatile boolean startupFastReloadActive;

	private DeferredReloadManager() {
	}

	public static ResourceReload createReload(
		final ResourceManager resourceManager,
		final List<ResourceReloader> listeners,
		final Executor backgroundExecutor,
		final Executor mainThreadExecutor,
		final CompletableFuture<Unit> initialTask,
		final boolean enableProfiling
	) {
		if (!ENABLED) {
			return SimpleResourceReload.start(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
		}

		DeferredBatch previous = pendingBatch;
		if (previous != null && !previous.started) {
			previous.cancelled = true;
			pendingBatch = null;
			LOGGER.info("Cancelled deferred startup resource batch because a full reload superseded it");
		}

		if (!INITIAL_RELOAD_FILTERED.compareAndSet(false, true)) {
			return SimpleResourceReload.start(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
		}

		List<ResourceReloader> immediate = new ArrayList<>(listeners.size());
		List<ResourceReloader> deferred = new ArrayList<>();

		for (ResourceReloader listener : listeners) {
			if (isStartupReplay(listener)) {
				immediate.add(listener);
				deferred.add(listener);
			} else if (isStartupDeferred(listener)) {
				deferred.add(listener);
			} else {
				immediate.add(listener);
			}
		}

		DeferredBatch batch = null;
		if (!deferred.isEmpty()) {
			batch = new DeferredBatch(resourceManager, List.copyOf(deferred), backgroundExecutor, mainThreadExecutor);
			pendingBatch = batch;
			LOGGER.info(
				"Deferred {} startup resource listeners until after first menu ticks: {}",
				deferred.size(),
				deferred.stream().map(ResourceReloader::getName).toList()
			);
		}

		startupFastReloadActive = batch != null;

		try {
			ResourceReload initialReload = SimpleResourceReload.start(resourceManager, immediate, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
			if (batch != null) {
				batch.initialReloadDone = initialReload.whenComplete();
				initialReload.whenComplete().whenComplete((ignored, throwable) -> startupFastReloadActive = false);
			} else {
				startupFastReloadActive = false;
			}

			return initialReload;
		} catch (Throwable throwable) {
			startupFastReloadActive = false;
			throw throwable;
		}
	}

	public static void onClientTick(final MinecraftClient client) {
		DeferredBatch batch = pendingBatch;
		if (batch == null || batch.started || batch.cancelled) {
			return;
		}

		if (batch.initialReloadDone == null || !batch.initialReloadDone.isDone() || batch.initialReloadDone.isCompletedExceptionally()) {
			return;
		}

		if (client.currentScreen == null) {
			return;
		}

		if (client.world == null && ++batch.ticksAfterInitialReload < START_DELAY_TICKS) {
			return;
		}

		startDeferredReload(batch);
	}

	public static boolean shouldLoadAtlasInStartupPass(final Identifier atlasId) {
		return !startupFastReloadActive || GUI_ATLAS.equals(atlasId);
	}

	public static boolean shouldSkipUnihexFontLoadingInStartupPass() {
		return startupFastReloadActive && DEFER_UNICODE_FONTS;
	}

	public static boolean shouldSkipLoadingOverlay() {
		return ENABLED && SKIP_LOADING_OVERLAY;
	}

	private static boolean isStartupDeferred(final ResourceReloader listener) {
		return STARTUP_DEFER_ONLY_LISTENERS.contains(listener.getClass().getName());
	}

	private static boolean isStartupReplay(final ResourceReloader listener) {
		return STARTUP_REPLAY_LISTENERS.contains(listener.getClass().getName());
	}

	private static void startDeferredReload(final DeferredBatch batch) {
		if (batch.cancelled || !batch.markStarted()) {
			return;
		}

		LOGGER.info("Starting deferred startup resource reload for {} listeners", batch.listeners.size());
		ResourceReload reload = SimpleResourceReload.start(
			batch.resourceManager,
			batch.listeners,
			batch.backgroundExecutor,
			batch.mainThreadExecutor,
			CompletableFuture.completedFuture(Unit.INSTANCE),
			false
		);

		reload.whenComplete().whenComplete((ignored, throwable) -> {
			if (throwable != null) {
				LOGGER.error("Deferred startup resource reload failed", throwable);
			} else {
				LOGGER.info("Finished deferred startup resource reload");
			}

			if (pendingBatch == batch) {
				pendingBatch = null;
			}
		});
	}

	private static final class DeferredBatch {
		private final ResourceManager resourceManager;
		private final List<ResourceReloader> listeners;
		private final Executor backgroundExecutor;
		private final Executor mainThreadExecutor;
		private volatile CompletableFuture<?> initialReloadDone;
		private volatile boolean cancelled;
		private volatile boolean started;
		private int ticksAfterInitialReload;

		private DeferredBatch(
			final ResourceManager resourceManager,
			final List<ResourceReloader> listeners,
			final Executor backgroundExecutor,
			final Executor mainThreadExecutor
		) {
			this.resourceManager = resourceManager;
			this.listeners = listeners;
			this.backgroundExecutor = backgroundExecutor;
			this.mainThreadExecutor = mainThreadExecutor;
		}

		private synchronized boolean markStarted() {
			if (this.started) {
				return false;
			}

			this.started = true;
			return true;
		}
	}
}
