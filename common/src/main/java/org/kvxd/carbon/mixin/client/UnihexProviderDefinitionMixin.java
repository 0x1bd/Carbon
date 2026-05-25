package org.kvxd.carbon.mixin.client;

import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UnihexProvider.Definition.class)
public abstract class UnihexProviderDefinitionMixin {
	private static final GlyphProvider CARBON_EMPTY_UNIHEX_PROVIDER = new GlyphProvider() {
		@Override
		public IntSet getSupportedGlyphs() {
			return IntSets.EMPTY_SET;
		}
	};

	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void carbon$skipStartupUnihexLoad(final ResourceManager resourceManager, final CallbackInfoReturnable<GlyphProvider> cir) {
		if (DeferredReloadManager.shouldSkipUnihexFontLoadingInStartupPass()) {
			cir.setReturnValue(CARBON_EMPTY_UNIHEX_PROVIDER);
		}
	}
}
