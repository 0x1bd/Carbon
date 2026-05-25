package org.kvxd.carbon.mixin.client;

import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.util.Identifier;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AtlasManager.class)
public abstract class AtlasManagerMixin {
	@Redirect(
		method = "prepareSharedState",
		at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V")
	)
	private void carbon$loadOnlyMenuAtlasesDuringStartup(final Map<Identifier, Object> atlases, final BiConsumer<Identifier, Object> action) {
		this.carbon$forEachStartupAtlas(atlases, action);
	}

	@Redirect(
		method = "acceptAtlasTextures",
		at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V")
	)
	private void carbon$exposeOnlyMenuAtlasesDuringStartup(final Map<Identifier, Object> atlases, final BiConsumer<Identifier, Object> action) {
		this.carbon$forEachStartupAtlas(atlases, action);
	}

	private void carbon$forEachStartupAtlas(final Map<Identifier, Object> atlases, final BiConsumer<Identifier, Object> action) {
		atlases.forEach((atlasId, atlasEntry) -> {
			if (DeferredReloadManager.shouldLoadAtlasInStartupPass(atlasId)) {
				action.accept(atlasId, atlasEntry);
			}
		});
	}
}
