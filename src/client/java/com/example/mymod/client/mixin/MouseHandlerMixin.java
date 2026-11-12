package com.example.mymod.client.mixin;

import com.example.mymod.client.menu.RemoteMenu;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void mymod$turnPlayer(double movementTime, CallbackInfo ci) {
		if (com.example.mymod.client.fx.CinemaClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void mymod$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (RemoteMenu.onScroll(yOffset)) {
			ci.cancel();
		}
	}
}
