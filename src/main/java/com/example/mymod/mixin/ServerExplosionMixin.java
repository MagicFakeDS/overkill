package com.example.mymod.mixin;

import java.util.List;

import com.example.mymod.world.GravityEngine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void mymod$gravity(List<BlockPos> blocks, CallbackInfo ci) {
		GravityEngine.afterExplosion(level, blocks);
	}
}
