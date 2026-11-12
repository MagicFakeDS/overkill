package com.example.mymod.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.mymod.item.ModItems;
import com.example.mymod.skill.Skill;
import com.example.mymod.skill.Skills;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
	public static final double MAX_DISTANCE = 200;

	public static final int COOLDOWN_TICKS = 10;

	private static final Map<UUID, Integer> LAST_LAUNCH = new HashMap<>();

	private ModNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(LaunchSkillPayload.TYPE, LaunchSkillPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GravityPayload.TYPE, GravityPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CinemaPayload.TYPE, CinemaPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LaunchSkillPayload.TYPE,
				(payload, context) -> handleLaunch(context.server(), context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(GravityPayload.TYPE,
				(payload, context) -> com.example.mymod.world.GravityEngine.enabled = payload.enabled());
	}

	private static void handleLaunch(MinecraftServer server, ServerPlayer player, LaunchSkillPayload payload) {
		if (!player.getMainHandItem().is(ModItems.REMOTE)) {
			return;
		}
		if (payload.skill() < 0 || payload.skill() >= Skills.ALL.size()) {
			return;
		}
		int now = server.getTickCount();
		Integer last = LAST_LAUNCH.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS && now >= last) {
			return;
		}
		LAST_LAUNCH.put(player.getUUID(), now);

		Skill skill = Skills.ALL.get(payload.skill());
		HitResult hit = player.pick(MAX_DISTANCE, 1.0f, false);
		if (hit.getType() == HitResult.Type.MISS) {
			player.sendOverlayMessage(Component.translatable("message.mymod.remote.too_far"));
			return;
		}
		Vec3 target = hit.getLocation();

		LaunchSkillPayload safe = skill.sanitize(payload);
		com.example.mymod.world.GravityEngine.enabled = safe.gravity();

		com.example.mymod.skill.Cinema.Story story = safe.cinema()
				? com.example.mymod.skill.Cinema.storyFor(skill.id()) : com.example.mymod.skill.Cinema.Story.NONE;
		if (story != com.example.mymod.skill.Cinema.Story.NONE && ServerPlayNetworking.canSend(player, CinemaPayload.TYPE)) {
			int prelude = com.example.mymod.skill.Cinema.preludeTicks(story);
			ServerPlayNetworking.send(player, new CinemaPayload(story.ordinal(), target, safe.radius(), prelude / 20f));
			com.example.mymod.skill.SkillScheduler.schedule(prelude, () -> skill.action().launch(player, target, safe));
			return;
		}
		skill.action().launch(player, target, safe);
	}
}
