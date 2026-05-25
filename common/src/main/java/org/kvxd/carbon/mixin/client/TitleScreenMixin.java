package org.kvxd.carbon.mixin.client;

import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jspecify.annotations.Nullable;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
	@Shadow
	private boolean fading;

	@Inject(method = "<init>(ZLnet/minecraft/client/gui/components/LogoRenderer;)V", at = @At("RETURN"))
	private void carbon$disableStartupFade(final boolean fading, @Nullable final LogoRenderer logoRenderer, final CallbackInfo ci) {
		if (DeferredReloadManager.shouldSkipLoadingOverlay()) {
			this.fading = false;
		}
	}
}
