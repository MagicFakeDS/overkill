package com.example.mymod.client.mixin;

import com.example.mymod.client.menu.RemoteMenu;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void mymod$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (com.example.mymod.client.fx.CinemaClient.active() && event.key() == 32) {
			if (action == 1) {
				com.example.mymod.client.fx.CinemaClient.skip();
			}
			ci.cancel();
			return;
		}
		if (RemoteMenu.onKey(event.key(), action)) {
			ci.cancel();
		}
	}
}
