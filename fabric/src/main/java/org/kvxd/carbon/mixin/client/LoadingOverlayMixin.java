package org.kvxd.carbon.mixin.client;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	@Final
	private ResourceReload reload;

	@Shadow
	@Final
	private Consumer<Optional<Throwable>> exceptionHandler;

	@Unique
	private boolean carbon$finished;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void carbon$skipLoadingOverlayRender(final DrawContext context, final int mouseX, final int mouseY, final float delta, final CallbackInfo ci) {
		if (!DeferredReloadManager.shouldSkipLoadingOverlay()) {
			return;
		}

		if (this.client.currentScreen != null) {
			this.client.currentScreen.render(context, mouseX, mouseY, delta);
		}

		ci.cancel();
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void carbon$finishImmediatelyWhenReloadCompletes(final CallbackInfo ci) {
		if (!DeferredReloadManager.shouldSkipLoadingOverlay()) {
			return;
		}

		if (!this.carbon$finished && this.reload.isComplete()) {
			this.carbon$finished = true;

			try {
				this.reload.throwException();
				this.exceptionHandler.accept(Optional.empty());
			} catch (Throwable throwable) {
				this.exceptionHandler.accept(Optional.of(throwable));
			}

			this.client.setOverlay(null);
		}

		ci.cancel();
	}
}
