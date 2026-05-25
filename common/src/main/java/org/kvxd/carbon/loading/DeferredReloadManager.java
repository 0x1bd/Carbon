package org.kvxd.carbon.loading;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.slf4j.Logger;

public final class DeferredReloadManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation GUI_ATLAS = ResourceLocation.withDefaultNamespace("gui");
	private static final Set<String> STARTUP_DEFER_ONLY_LISTENERS = Set.of(
		"net.minecraft.client.sounds.SoundManager",
		"net.minecraft.client.PeriodicNotificationManager",
		"net.minecraft.client.renderer.CloudRenderer",
		"net.minecraft.client.renderer.GpuWarnlistManager",
		"net.minecraft.client.renderer.LevelRenderer",
		"net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher",
		"net.minecraft.client.renderer.entity.EntityRenderDispatcher",
		"net.minecraft.client.particle.ParticleResources",
		"net.minecraft.client.resources.DryFoliageColorReloadListener",
		"net.minecraft.client.resources.FoliageColorReloadListener",
		"net.minecraft.client.resources.GrassColorReloadListener",
		"net.minecraft.client.resources.WaypointStyleManager",
		"net.minecraft.client.resources.model.EquipmentAssetManager",
		"net.minecraft.client.resources.model.ModelManager"
	);
	private static final Set<String> STARTUP_REPLAY_LISTENERS = Set.of(
		"net.minecraft.client.gui.font.FontManager",
		"net.minecraft.client.resources.model.sprite.AtlasManager"
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

	public static ReloadInstance createReload(
		final ResourceManager resourceManager,
		final List<PreparableReloadListener> listeners,
		final Executor backgroundExecutor,
		final Executor mainThreadExecutor,
		final CompletableFuture<Unit> initialTask,
		final boolean enableProfiling
	) {
		if (!ENABLED) {
			return SimpleReloadInstance.create(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
		}

		DeferredBatch previous = pendingBatch;
		if (previous != null && !previous.started) {
			previous.cancelled = true;
			pendingBatch = null;
			LOGGER.info("Cancelled deferred startup resource batch because a full reload superseded it");
		}

		if (!INITIAL_RELOAD_FILTERED.compareAndSet(false, true)) {
			return SimpleReloadInstance.create(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
		}

		List<PreparableReloadListener> immediate = new ArrayList<>(listeners.size());
		List<PreparableReloadListener> deferred = new ArrayList<>();

		for (PreparableReloadListener listener : listeners) {
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
				deferred.stream().map(PreparableReloadListener::getName).toList()
			);
		}

		startupFastReloadActive = batch != null;

		try {
			ReloadInstance initialReload = SimpleReloadInstance.create(resourceManager, immediate, backgroundExecutor, mainThreadExecutor, initialTask, enableProfiling);
			if (batch != null) {
				batch.initialReloadDone = initialReload.done();
				initialReload.done().whenComplete((ignored, throwable) -> startupFastReloadActive = false);
			} else {
				startupFastReloadActive = false;
			}

			return initialReload;
		} catch (Throwable throwable) {
			startupFastReloadActive = false;
			throw throwable;
		}
	}

	public static void onClientTick(final Minecraft client) {
		DeferredBatch batch = pendingBatch;
		if (batch == null || batch.started || batch.cancelled) {
			return;
		}

		if (batch.initialReloadDone == null || !batch.initialReloadDone.isDone() || batch.initialReloadDone.isCompletedExceptionally()) {
			return;
		}

		if (client.screen == null) {
			return;
		}

		if (client.level == null && ++batch.ticksAfterInitialReload < START_DELAY_TICKS) {
			return;
		}

		startDeferredReload(batch);
	}

	public static boolean shouldLoadAtlasInStartupPass(final ResourceLocation atlasId) {
		return !startupFastReloadActive || GUI_ATLAS.equals(atlasId);
	}

	public static boolean shouldSkipUnihexFontLoadingInStartupPass() {
		return startupFastReloadActive && DEFER_UNICODE_FONTS;
	}

	public static boolean shouldSkipLoadingOverlay() {
		return ENABLED && SKIP_LOADING_OVERLAY;
	}

	private static boolean isStartupDeferred(final PreparableReloadListener listener) {
		return STARTUP_DEFER_ONLY_LISTENERS.contains(listener.getClass().getName());
	}

	private static boolean isStartupReplay(final PreparableReloadListener listener) {
		return STARTUP_REPLAY_LISTENERS.contains(listener.getClass().getName());
	}

	private static void startDeferredReload(final DeferredBatch batch) {
		if (batch.cancelled || !batch.markStarted()) {
			return;
		}

		LOGGER.info("Starting deferred startup resource reload for {} listeners", batch.listeners.size());
		ReloadInstance reload = SimpleReloadInstance.create(
			batch.resourceManager,
			batch.listeners,
			batch.backgroundExecutor,
			batch.mainThreadExecutor,
			CompletableFuture.completedFuture(Unit.INSTANCE),
			false
		);

		reload.done().whenComplete((ignored, throwable) -> {
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
		private final List<PreparableReloadListener> listeners;
		private final Executor backgroundExecutor;
		private final Executor mainThreadExecutor;
		private volatile CompletableFuture<?> initialReloadDone;
		private volatile boolean cancelled;
		private volatile boolean started;
		private int ticksAfterInitialReload;

		private DeferredBatch(
			final ResourceManager resourceManager,
			final List<PreparableReloadListener> listeners,
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
