package com.example.mymod.client;

import com.example.mymod.client.fx.FxClient;
import com.example.mymod.client.menu.MenuRenderer;
import com.example.mymod.client.menu.RemoteMenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class MyModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LevelRenderEvents.COLLECT_SUBMITS.register(MenuRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(RemoteMenu::tick);

		FxClient.initialize();

		com.example.mymod.client.fx.CinemaClient.initialize();

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
					com.example.mymod.network.GravityPayload.TYPE)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.example.mymod.network.GravityPayload(RemoteSettings.get().gravity));
			}
		});

		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !RemoteMenu.isVisible());
	}
}
