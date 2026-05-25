package org.kvxd.carbon.mixin.client;

import net.minecraft.client.Minecraft;
import org.kvxd.carbon.loading.DeferredReloadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void carbon$onClientTick(final CallbackInfo ci) {
		DeferredReloadManager.onClientTick((Minecraft)(Object)this);
	}
}
