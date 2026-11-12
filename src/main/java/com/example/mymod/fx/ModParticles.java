package com.example.mymod.fx;

import com.example.mymod.MyMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	public static final SimpleParticleType NONE =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("none"), FabricParticleTypes.simple(true));

	private ModParticles() {
	}

	public static void initialize() {
	}
}
