package org.kvxd.carbon.mixin.client;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private ReloadInstance reload;

	@Shadow
	@Final
	private Consumer<Optional<Throwable>> onFinish;

	@Unique
	private boolean carbon$finished;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void carbon$skipLoadingOverlayRender(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo ci
	) {
		if (!DeferredReloadManager.shouldSkipLoadingOverlay()) {
			return;
		}

		if (!this.reload.isDone()) {
			ci.cancel();
			return;
		}

		if (this.minecraft.screen != null) {
			this.minecraft.screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, partialTick);
		} else {
			this.minecraft.gui.extractDeferredSubtitles();
		}

		ci.cancel();
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void carbon$finishImmediatelyWhenReloadCompletes(final CallbackInfo ci) {
		if (!DeferredReloadManager.shouldSkipLoadingOverlay()) {
			return;
		}

		if (!this.carbon$finished && this.reload.isDone()) {
			this.carbon$finished = true;

			try {
				this.reload.checkExceptions();
				this.onFinish.accept(Optional.empty());
			} catch (Throwable throwable) {
				this.onFinish.accept(Optional.of(throwable));
			}

			this.minecraft.setOverlay(null);
		}

		ci.cancel();
	}
}
