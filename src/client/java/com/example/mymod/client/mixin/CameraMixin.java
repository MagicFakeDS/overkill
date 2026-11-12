package com.example.mymod.client.mixin;

import com.example.mymod.client.fx.CinemaClient;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void mymod$cinema(float partialTick, CallbackInfo ci) {
		CinemaClient.Shot shot = CinemaClient.shot();
		if (shot != null) {
			setRotation(shot.yaw(), shot.pitch());
			setPosition(shot.pos());
			detached = true;
		}
	}
}
