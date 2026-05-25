package org.kvxd.carbon.mixin.client;

import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.TitleScreen;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
	@Shadow
	private boolean doBackgroundFade;

	@Inject(method = "<init>(ZLnet/minecraft/client/gui/LogoDrawer;)V", at = @At("RETURN"))
	private void carbon$disableStartupFade(final boolean doBackgroundFade, final LogoDrawer logoDrawer, final CallbackInfo ci) {
		if (DeferredReloadManager.shouldSkipLoadingOverlay()) {
			this.doBackgroundFade = false;
		}
	}
}
