package com.example.mymod.client.mixin;

import com.example.mymod.client.fx.FxClient;
import com.example.mymod.client.menu.RemoteMenu;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void mymod$bobView(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		if (RemoteMenu.isVisible() || com.example.mymod.client.fx.CinemaClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void mymod$shake(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		FxClient.applyShake(camera.pos, pose);
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void mymod$renderItemInHand(CameraRenderState camera, float partialTick, Matrix4fc projection, CallbackInfo ci) {
		if (RemoteMenu.isVisible() || com.example.mymod.client.fx.CinemaClient.active()) {
			ci.cancel();
		}
	}
}
