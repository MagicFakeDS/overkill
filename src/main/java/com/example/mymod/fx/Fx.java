package com.example.mymod.fx;

import java.util.concurrent.ThreadLocalRandom;

import com.example.mymod.network.FxPayload;
import com.example.mymod.skill.SkillScheduler;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class Fx {
	public static final int BEAM = 1;
	public static final int MARK = 2;
	public static final int ORBITAL = 3;
	public static final int SHOCKWAVE = 4;
	public static final int FIREBALL = 5;
	public static final int BLACK_HOLE = 6;
	public static final int METEOR = 7;
	public static final int STAR = 8;
	public static final int SHAKE = 9;
	public static final int FLASH = 10;
	public static final int EXPLOSION = 11;
	public static final int MUSHROOM = 12;
	public static final int ROD = 13;
	public static final int SATELLITE = 14;
	public static final int TINT = 15;
	public static final int ASTEROID = 16;
	public static final int ANTIMATTER = 17;
	public static final int CHUNK_GRID = 18;
	public static final int MISSILE = 19;
	public static final int CHUNK_FALL = 20;

	public static final int SPIKE_RISE = 21;

	public static final int ISLAND_RISE = 22;

	public static final int DOME_GROW = 23;

	public static final int MODEL_ICBM = 1;
	public static final int MODEL_CRUISE = 2;
	public static final int MODEL_BOMB = 3;
	public static final int MODEL_WARHEAD = 4;
	public static final int MODEL_ROCKET = 5;
	public static final int MODEL_DRILL = 6;
	public static final int MODEL_ROD = 7;

	public static final int TRAJ_ARC = 0;
	public static final int TRAJ_FALL = 1;
	public static final int TRAJ_LINE = 2;

	public static final int FLAG_ENGINE = 1;
	public static final int FLAG_SPIN = 2;
	public static final int FLAG_SMOKE = 4;

	public static final int ROCK_STONE = 0;
	public static final int ROCK_ICE = 1;
	public static final int ROCK_MOON = 2;

	private static final double VIEW_RANGE = 320;

	private Fx() {
	}

	private static void send(ServerLevel level, Vec3 near, double range, FxPayload payload) {
		for (ServerPlayer player : PlayerLookup.around(level, near, range)) {
			if (ServerPlayNetworking.canSend(player, FxPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void send(ServerLevel level, int type, Vec3 a, Vec3 b, float radius, float duration, int color) {
		send(level, a, VIEW_RANGE, new FxPayload(type, a, b, radius, duration, color, ThreadLocalRandom.current().nextInt()));
	}

	public static void beam(ServerLevel level, Vec3 a, Vec3 b, float width, float seconds, int color) {
		send(level, BEAM, a, b, width, seconds, color);
	}

	public static void mark(ServerLevel level, Vec3 ground, float radius, float seconds, int color) {
		send(level, MARK, ground, ground, radius, seconds, color);
	}

	public static void orbital(ServerLevel level, Vec3 ground, float radius, float seconds, int color) {
		send(level, ORBITAL, ground, ground, radius, seconds, color);
	}

	public static void shockwave(ServerLevel level, Vec3 center, float radius, float seconds, int color) {
		send(level, SHOCKWAVE, center, center, radius, seconds, color);
	}

	public static void fireball(ServerLevel level, Vec3 center, float radius, float seconds, int color) {
		send(level, FIREBALL, center, center, radius, seconds, color);
	}

	public static void blackHole(ServerLevel level, Vec3 center, float radius, float seconds, int color) {
		send(level, BLACK_HOLE, center, center, radius, seconds, color);
	}

	public static void meteor(ServerLevel level, Vec3 from, Vec3 to, float seconds, int color) {
		send(level, METEOR, from, to, 1f, seconds, color);
	}

	public static void star(ServerLevel level, Vec3 center, float radius, float seconds, int color) {
		send(level, STAR, center, center, radius, seconds, color);
	}

	public static void explosion(ServerLevel level, Vec3 center, float power) {
		send(level, EXPLOSION, center, center, power, Math.max(2.5f, Math.min(7.5f, 2.2f + power * 0.35f)), 0xFFFFFFFF);
	}

	public static void mushroom(ServerLevel level, Vec3 ground, float size, float seconds) {
		send(level, ground, VIEW_RANGE + size * 6, new FxPayload(MUSHROOM, ground, ground, size, seconds, 0xFFFFFFFF,
				ThreadLocalRandom.current().nextInt()));
	}

	public static void rod(ServerLevel level, Vec3 from, Vec3 to, float fallSeconds, float trailSeconds) {
		send(level, to, VIEW_RANGE, new FxPayload(ROD, from, to, fallSeconds, fallSeconds + trailSeconds, 0xFFFFFFFF,
				ThreadLocalRandom.current().nextInt()));
	}

	public static void satellite(ServerLevel level, Vec3 from, Vec3 to, float size, float seconds) {
		send(level, from.add(to).scale(0.5), VIEW_RANGE + 200, new FxPayload(SATELLITE, from, to, size, seconds, 0xFFFFFFFF, 0));
	}

	public static void tint(ServerLevel level, Vec3 center, float range, float seconds, int color) {
		send(level, center, range, new FxPayload(TINT, center, center, range, seconds, color, 0));
	}

	public static void asteroid(ServerLevel level, Vec3 from, Vec3 to, float size, float seconds) {
		asteroid(level, from, to, size, seconds, ROCK_STONE);
	}

	public static void asteroid(ServerLevel level, Vec3 from, Vec3 to, float size, float seconds, int rock) {
		send(level, to, VIEW_RANGE + 300 + size * 4, new FxPayload(ASTEROID, from, to, size, seconds, 0xFFFFFFFF,
				ThreadLocalRandom.current().nextInt(), rock, 0f, 0f));
	}

	public static void missile(ServerLevel level, Vec3 from, Vec3 to, int model, int traj, int flags, float scale,
			float flightSeconds, float lingerSeconds, int color, float apex) {
		int variant = model | (traj << 8) | (flags << 16);
		Vec3 mid = from.add(to).scale(0.5);
		send(level, mid, VIEW_RANGE + from.distanceTo(to) / 2 + apex, new FxPayload(MISSILE, from, to, scale,
				flightSeconds + lingerSeconds, color, ThreadLocalRandom.current().nextInt(), variant, apex, flightSeconds));
	}

	public static void antimatter(ServerLevel level, Vec3 center, float radius, float seconds) {
		send(level, center, VIEW_RANGE + radius * 2, new FxPayload(ANTIMATTER, center, center, radius, seconds, 0xFFFFFFFF,
				ThreadLocalRandom.current().nextInt()));
	}

	public static void chunkGrid(ServerLevel level, Vec3 center, int chunkRadius, float seconds, int color,
			int seed, float fallChance, float chargeSeconds) {
		send(level, center, VIEW_RANGE, new FxPayload(CHUNK_GRID, center, new Vec3(fallChance, 0, 0), chunkRadius,
				seconds, color, seed, 0, chargeSeconds, 0f));
	}

	public static void chunkFall(ServerLevel level, int cx, int cz, int topY) {
		Vec3 mid = new Vec3(cx * 16 + 8, topY, cz * 16 + 8);
		send(level, mid, VIEW_RANGE, new FxPayload(CHUNK_FALL, mid, mid, 16f, 6.5f, 0xFFFFFFFF,
				ThreadLocalRandom.current().nextInt()));
	}

	public static void spikeRise(ServerLevel level, com.example.mymod.world.SpikeShape s, int pre, int rise) {
		Vec3 a = new Vec3(s.x, s.gy, s.z);
		send(level, a, VIEW_RANGE + s.height, new FxPayload(SPIKE_RISE, a, new Vec3(s.leanX, s.height, s.leanZ), s.base,
				(pre + rise) / 20f + 2.5f, 0, ThreadLocalRandom.current().nextInt(),
				s.spire.ordinal() | (s.pillar ? 256 : 0), pre / 20f, rise / 20f));
	}

	public static void islandRise(ServerLevel level, com.example.mymod.world.IslandShape s, int lift, int pre, int rise) {
		Vec3 a = new Vec3(s.cx, s.gy, s.cz);
		send(level, a, VIEW_RANGE + lift + s.radius, new FxPayload(ISLAND_RISE, a, new Vec3(lift, 0, 0), s.radius,
				(pre + rise) / 20f + 3f, 0, s.seed, 0, pre / 20f, rise / 20f));
	}

	public static void domeGrow(ServerLevel level, Vec3 center, float radius, float growSeconds, int color) {
		send(level, center, VIEW_RANGE + radius, new FxPayload(DOME_GROW, center, center, radius, growSeconds + 1.5f,
				color, 0, 0, growSeconds, 0f));
	}

	public static void shake(ServerLevel level, Vec3 center, float strength, float seconds, double range) {
		send(level, center, range, new FxPayload(SHAKE, center, new Vec3(range, 0, 0), strength, seconds, 0, 0));
	}

	public static void delayedSound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, double range,
			double blocksPerTick, float pitch) {
		delayedSound(level, pos, sound, 0, range, blocksPerTick, pitch);
	}

	public static void delayedSound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, double minDist, double range,
			double blocksPerTick, float pitch) {
		for (ServerPlayer player : level.players()) {
			Vec3 eye = player.getEyePosition();
			double dist = eye.distanceTo(pos);
			if (dist > range || dist < minDist) {
				continue;
			}
			int delay = (int) (dist / blocksPerTick);
			float volume = (float) Math.max(0.5, 4.0 * (1 - dist / range));

			Vec3 at = dist > 12 ? eye.add(pos.subtract(eye).normalize().scale(12)) : pos;
			long seed = ThreadLocalRandom.current().nextLong();
			SkillScheduler.schedule(delay, () -> {
				if (!player.hasDisconnected()) {
					player.connection.send(new ClientboundSoundPacket(sound, SoundSource.BLOCKS, at.x, at.y, at.z,
							volume, pitch, seed));
				}
			});
			for (ModSounds.Layer layer : ModSounds.layers(sound)) {
				SkillScheduler.schedule(delay + layer.delay(), () -> {
					if (!player.hasDisconnected()) {
						player.connection.send(new ClientboundSoundPacket(layer.sound(), SoundSource.BLOCKS, at.x, at.y,
								at.z, volume * layer.volume(), pitch, seed + 1));
					}
				});
			}
		}
	}

	public static void flash(ServerLevel level, Vec3 center, float range, float seconds, int color) {
		send(level, center, range, new FxPayload(FLASH, center, center, range, seconds, color, 0));
	}
}
