package com.example.mymod;

import com.example.mymod.block.ModBlocks;
import com.example.mymod.fx.ModParticles;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.item.ModItems;
import com.example.mymod.network.ModNetworking;
import com.example.mymod.skill.SkillScheduler;
import com.example.mymod.world.WorldCarver;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyMod implements ModInitializer {
	public static final String MOD_ID = "mymod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModBlocks.initialize();
		com.example.mymod.world.GravityEngine.initialize();
		ModParticles.initialize();
		ModSounds.initialize();
		ModNetworking.initialize();
		SkillScheduler.initialize();
		WorldCarver.initialize();

		LOGGER.info("Мод {} загружен!", MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
