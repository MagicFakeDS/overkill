package com.example.mymod.client.mixin;

import com.example.mymod.client.menu.RemoteMenu;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$startAttack(CallbackInfoReturnable<Boolean> cir) {
		if (com.example.mymod.client.fx.CinemaClient.active()) {
			com.example.mymod.client.fx.CinemaClient.skip();
			cir.setReturnValue(false);
			return;
		}
		if (RemoteMenu.onAttack()) {
			cir.setReturnValue(false);
			return;
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$continueAttack(boolean down, CallbackInfo ci) {
		if (RemoteMenu.blocksContinueAttack() || com.example.mymod.client.fx.CinemaClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void mymod$startUseItem(CallbackInfo ci) {
		if (com.example.mymod.client.fx.CinemaClient.active()) {
			com.example.mymod.client.fx.CinemaClient.skip();
			ci.cancel();
			return;
		}
		if (RemoteMenu.onUse()) {
			ci.cancel();
		}
	}
}
