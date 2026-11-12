package com.example.mymod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.mymod.MyMod;
import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModParticles;
import com.example.mymod.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class FxClient {
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private static final Vec3 X = new Vec3(1, 0, 0);
	private static final Vec3 Z = new Vec3(0, 0, 1);

	private record Active(FxPayload fx, long start, Object data) {
		float age() {
			return (System.nanoTime() - start) / 1_000_000_000f;
		}
	}

	private static final List<Active> ACTIVE = new ArrayList<>();

	private FxClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, context) -> {
			boolean terrain = payload.kind() == Fx.SPIKE_RISE || payload.kind() == Fx.ISLAND_RISE;
			if (ACTIVE.size() < (terrain ? 900 : 400)) {
				Object data = switch (payload.kind()) {
					case Fx.CHUNK_FALL -> ChunkSlab.capture(Mth.floor(payload.a().x) >> 4, Mth.floor(payload.a().z) >> 4);
					case Fx.SPIKE_RISE, Fx.ISLAND_RISE -> TerrainFx.capture(payload);
					default -> null;
				};
				ACTIVE.add(new Active(payload, System.nanoTime(), data));
				CinemaClient.onFx(payload);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(FxClient::render);

		ParticleProviderRegistry.getInstance().register(ModParticles.NONE,
				(options, level, x, y, z, dx, dy, dz, random) -> null);
		HudElementRegistry.addLast(MyMod.id("screen_flash"), FxClient::renderFlash);
	}

	private static void render(LevelRenderContext context) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || camera.pos == null || camera.orientation == null) {
			return;
		}
		FxDraw d = new FxDraw(camera.pos, camera.orientation);
		MeshDraw mesh = new MeshDraw(camera.pos);

		int explosions = 0;
		for (Active active : ACTIVE) {
			if (active.fx().kind() == Fx.EXPLOSION) {
				explosions++;
			}
		}
		float quality = Mth.clamp(12f / Math.max(1, explosions), 0.25f, 1f);
		int mushrooms = 0;
		for (Active active : ACTIVE) {
			if (active.fx().kind() == Fx.MUSHROOM) {
				mushrooms++;
			}
		}
		float mushroomQuality = Mth.clamp(2.5f / Math.max(1, mushrooms), 0.3f, 1f);
		Iterator<Active> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Active active = it.next();
			FxPayload fx = active.fx();
			float t = active.age();
			if (t > fx.duration()) {
				it.remove();
				continue;
			}
			switch (fx.kind()) {
				case Fx.BEAM -> beam(d, fx, t);
				case Fx.MARK -> mark(d, fx, t);
				case Fx.ORBITAL -> orbital(d, fx, t);
				case Fx.SHOCKWAVE -> shockwave(d, fx, t);
				case Fx.FIREBALL -> fireball(d, fx, t);
				case Fx.BLACK_HOLE -> blackHole(d, fx, t);
				case Fx.METEOR -> meteor(d, fx, t);
				case Fx.STAR -> star(d, fx, t);
				case Fx.EXPLOSION -> explosion(d, fx, t, quality);
				case Fx.MUSHROOM -> mushroom(d, fx, t, mushroomQuality);
				case Fx.ROD -> rod(d, mesh, fx, t);
				case Fx.SATELLITE -> satellite(d, mesh, fx, t);
				case Fx.ASTEROID -> asteroid(d, mesh, fx, t);
				case Fx.MISSILE -> missile(d, mesh, fx, t, quality);
				case Fx.ANTIMATTER -> antimatter(d, fx, t);
				case Fx.CHUNK_GRID -> chunkGrid(d, fx, t);
				case Fx.CHUNK_FALL -> chunkFall(d, mesh, fx, (ChunkSlab) active.data(), t);
				case Fx.SPIKE_RISE -> TerrainFx.spike(d, mesh, fx, active.data(), t);
				case Fx.ISLAND_RISE -> TerrainFx.island(d, mesh, fx, active.data(), t);
				case Fx.DOME_GROW -> TerrainFx.dome(d, fx, t);
				default -> {
				}
			}
		}
		PoseStack pose = context.poseStack();
		mesh.flush(pose, context.submitNodeCollector());
		d.flush(pose, context.submitNodeCollector());
	}

	private static float envelope(float t, float duration, float in, float out) {
		float a = in <= 0 ? 1f : Math.min(1f, t / in);
		float b = out <= 0 ? 1f : Math.min(1f, (duration - t) / out);
		return Math.max(0f, Math.min(a, b));
	}

	private static float easeOutCubic(float x) {
		x = Math.max(0f, Math.min(1f, x));
		float t = 1f - x;
		return 1f - t * t * t;
	}

	private static float easeOutBack(float x) {
		x = Math.max(0f, Math.min(1f, x));
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float t = x - 1f;
		return 1f + c3 * t * t * t + c1 * t * t;
	}

	private static float frac(float x) {
		return x - (float) Math.floor(x);
	}

	private static Vec3[] basis(Vec3 dir) {
		Vec3 n = dir.normalize();
		Vec3 helper = Math.abs(n.y) < 0.9 ? UP : X;
		Vec3 u = n.cross(helper).normalize();
		Vec3 v = n.cross(u).normalize();
		return new Vec3[] {u, v};
	}

	private static void beam(FxDraw d, FxPayload fx, float t) {
		Vec3 a = fx.a();
		Vec3 b = fx.b();
		Vec3 path = b.subtract(a);
		double length = path.length();
		if (length < 0.01) {
			return;
		}
		Vec3 dir = path.scale(1 / length);
		float env = envelope(t, fx.duration(), 0.07f, 0.3f);
		float wobble = 1f + 0.12f * (float) Math.sin(t * 47 + fx.seed()) + 0.08f * (float) Math.sin(t * 73);
		float w = fx.radius() * easeOutBack(Math.min(1f, t / 0.12f)) * wobble * Math.max(0.05f, env);
		int color = fx.color();

		d.beam(a, b, w, color, env);

		Vec3[] uv = basis(dir);
		float step = 0.45f;
		for (int strand = 0; strand < 2; strand++) {
			Vec3 prev = null;
			for (double s = 0; s <= length; s += step) {
				double ang = s * 1.3 - t * 16 + strand * Math.PI;
				Vec3 off = uv[0].scale(Math.cos(ang) * w * 1.25).add(uv[1].scale(Math.sin(ang) * w * 1.25));
				Vec3 p = a.add(dir.scale(s)).add(off);
				if (prev != null) {
					float fade = (float) Math.min(1, Math.min(s, length - s) / 2.0);
					d.ribbon(prev, p, 0.05f + w * 0.12f, 0.05f + w * 0.12f,
							FxDraw.alpha(FxDraw.mix(color, FxDraw.WHITE, 0.5f), 0.75f * env * fade),
							FxDraw.alpha(FxDraw.mix(color, FxDraw.WHITE, 0.5f), 0.75f * env * fade));
				}
				prev = p;
			}
		}

		for (int k = 0; k < 4; k++) {
			float ph = frac(t * 2.4f + k / 4f);
			Vec3 c = a.add(dir.scale(length * ph));
			d.ring(c, uv[0], uv[1], w * (1.4f + 0.6f * ph), w * 0.35f, FxDraw.alpha(color, 0.55f * env), 24, 0, 0, 0);
		}

		float pulse = 1f + 0.25f * (float) Math.sin(t * 31 + fx.seed());
		d.glowBall(b, w * 2.4f * pulse, color, env);
		d.glowBall(a, w * 1.3f, color, env);
	}

	private static void mark(FxDraw d, FxPayload fx, float t) {
		Vec3 g = fx.a().add(0, 0.08, 0);
		float r = fx.radius();
		float p = Math.min(1f, t / fx.duration());
		float env = envelope(t, fx.duration(), 0.15f, 0.05f);
		float blink = 0.6f + 0.4f * (float) Math.sin(t * (10 + 20 * p));
		int color = fx.color();

		float ringR = r * (1.9f - 0.9f * easeOutCubic(p));
		d.groundRing(g, ringR, 0.18f + 0.1f * p, FxDraw.alpha(color, env * blink));
		d.flatDisc(g, X, Z, 0, ringR, FxDraw.alpha(color, 0.18f * env), FxDraw.alpha(color, 0.05f * env), 40, 0, 0, 0);

		d.ring(g, X, Z, r * 1.25f, 0.12f, FxDraw.alpha(color, 0.9f * env), 72, 8, 6f, t);

		for (int i = 0; i < 4; i++) {
			double ang = Math.PI / 2 * i + t * 0.8;
			Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
			d.ribbon(g.add(dir.scale(ringR * 0.35)), g.add(dir.scale(ringR * 0.9)), 0.08f, 0.08f,
					FxDraw.alpha(color, env), FxDraw.alpha(color, 0.2f * env));
		}

		float beamW = (0.05f + 0.12f * p) * (0.8f + 0.2f * blink);
		d.beam(g, g.add(0, 160, 0), beamW, color, env * (0.4f + 0.6f * p));
		d.glowBall(g.add(0, 0.3, 0), 0.5f + 0.8f * p, color, env);
	}

	private static void orbital(FxDraw d, FxPayload fx, float t) {
		Vec3 g = fx.a();
		float dur = fx.duration();
		float grow = easeOutBack(Math.min(1f, t / 0.2f));
		float shrink = t > dur - 0.4f ? 1f - easeOutCubic((t - (dur - 0.4f)) / 0.4f) : 1f;
		float env = Math.max(0f, Math.min(grow, 1f)) * shrink;
		float w = fx.radius() * grow * shrink * (1f + 0.06f * (float) Math.sin(t * 41 + fx.seed()));
		int color = fx.color();

		d.beam(g.add(0, -1, 0), g.add(0, 220, 0), w * 0.65f, color, env);
		d.ribbon(g.add(0, -1, 0), g.add(0, 220, 0), w * 1.6f, w * 1.6f,
				FxDraw.alpha(color, 0.18f * env), FxDraw.alpha(color, 0.18f * env));

		Vec3 base = g.add(0, 0.12, 0);
		d.flatDisc(base, X, Z, 0, w * 1.8f, FxDraw.alpha(FxDraw.WHITE, 0.6f * env), FxDraw.clear(color), 48, 0, 0, 0);
		d.glowBall(g.add(0, 0.8, 0), w * 1.5f, color, env);

		for (int k = 0; k < 5; k++) {
			float ph = frac(t * 2.0f + k / 5f);
			d.groundRing(base, w * (1.1f + 3.2f * ph), 0.3f + 0.9f * ph, FxDraw.alpha(color, (1 - ph) * 0.85f * env));
		}

		for (int k = 0; k < 4; k++) {
			float ph = frac(t * 1.6f + k / 4f);
			Vec3 c = g.add(0, (1 - ph) * 70, 0);
			d.ring(c, X, Z, w * 0.9f, w * 0.18f, FxDraw.alpha(FxDraw.WHITE, 0.55f * env * ph), 40, 0, 0, 0);
		}
	}

	private static void shockwave(FxDraw d, FxPayload fx, float t) {
		float p = t / fx.duration();
		float r = fx.radius() * easeOutCubic(p);
		float fade = (float) Math.pow(1 - p, 1.6);
		Vec3 c = fx.a().add(0, 0.12, 0);
		float thick = 0.5f + fx.radius() * 0.06f;
		d.groundRing(c, r, thick, FxDraw.alpha(fx.color(), 0.95f * fade));
		d.groundRing(c, r * 0.93f, thick * 2.6f, FxDraw.alpha(fx.color(), 0.25f * fade));

		d.groundRing(c.add(0, 0.02, 0), r, thick * 0.25f, FxDraw.alpha(FxDraw.WHITE, 0.8f * fade));
	}

	private static void fireball(FxDraw d, FxPayload fx, float t) {
		float p = t / fx.duration();
		float grow = easeOutCubic(Math.min(1f, p * 3.5f));
		float flicker = 1f + 0.05f * (float) Math.sin(t * 53 + fx.seed());
		float r = fx.radius() * (0.25f + 0.75f * grow) * flicker;
		float fade = (float) Math.pow(1 - p, 1.3);
		d.glowBall(fx.a(), r, fx.color(), fade);
		d.billboardRing(fx.a(), r * (1.2f + 0.8f * p), r * 0.12f, FxDraw.alpha(FxDraw.mix(fx.color(), FxDraw.WHITE, 0.4f), 0.7f * fade));
	}

	private static void blackHole(FxDraw d, FxPayload fx, float t) {
		float dur = fx.duration();
		float appear = easeOutBack(Math.min(1f, t / 0.6f));
		float collapse = t > dur - 0.35f ? 1f - easeOutCubic((t - (dur - 0.35f)) / 0.35f) : 1f;
		float s = Math.max(0f, appear * collapse);
		Vec3 c = fx.a();
		float core = fx.radius() * 0.42f * s * (1f + 0.03f * (float) Math.sin(t * 9));
		int purple = fx.color();
		int hot = 0xFFFFD08A;

		d.disc(c, core * 2.8f, FxDraw.alpha(purple, 0.35f * s), FxDraw.clear(purple), true);

		double tilt = Math.toRadians(20);
		Vec3 u = new Vec3(Math.cos(t * 0.3), 0, Math.sin(t * 0.3));
		Vec3 flat = new Vec3(-Math.sin(t * 0.3), 0, Math.cos(t * 0.3));
		Vec3 v = flat.scale(Math.cos(tilt)).add(UP.scale(Math.sin(tilt)));
		d.flatDisc(c, u, v, core * 1.25f, core * 2.0f, FxDraw.alpha(hot, 0.95f * s), FxDraw.alpha(purple, 0.7f * s), 72, 3, 5f, t);
		d.flatDisc(c, u, v, core * 2.0f, core * 3.4f, FxDraw.alpha(purple, 0.7f * s), FxDraw.clear(purple), 72, 3, 5f, t);

		d.darkDisc(c, core);

		d.billboardRing(c, core * 1.06f, core * 0.1f, FxDraw.alpha(hot, s));
		d.billboardRing(c, core * 1.06f, core * 0.03f, FxDraw.alpha(FxDraw.WHITE, s));
	}

	private static void meteor(FxDraw d, FxPayload fx, float t) {
		float p = Math.min(1f, t / fx.duration());
		float pp = p * p;
		Vec3 from = fx.a();
		Vec3 to = fx.b();
		Vec3 pos = from.add(to.subtract(from).scale(pp));
		Vec3 dir = to.subtract(from).normalize();
		int color = fx.color();
		float flicker = 1f + 0.15f * (float) Math.sin(t * 60 + fx.seed());

		d.ribbon(pos, pos.subtract(dir.scale(12)), 0.9f * flicker, 0.02f, FxDraw.alpha(color, 0.95f), FxDraw.clear(color));
		d.ribbon(pos, pos.subtract(dir.scale(7)), 1.9f, 0.1f, FxDraw.alpha(color, 0.35f), FxDraw.clear(color));
		d.ribbon(pos, pos.subtract(dir.scale(5)), 0.35f, 0.01f, FxDraw.alpha(FxDraw.WHITE, 0.9f), FxDraw.clear(FxDraw.WHITE));
		d.glowBall(pos, 1.1f * flicker, color, 1f);
	}

	private static void star(FxDraw d, FxPayload fx, float t) {
		float p = Math.min(1f, t / fx.duration());
		float r = fx.radius() * (0.15f + 0.85f * p * p);
		float throb = 1f + (0.08f + 0.2f * p) * (float) Math.sin(t * (14 + 40 * p));
		int color = fx.color();
		Vec3 c = fx.a();

		d.glowBall(c, r * 1.6f * throb, color, 0.6f + 0.4f * p);

		for (int i = 0; i < 8; i++) {
			double ang = Math.PI * 2 * i / 8 + t * 0.9;
			Vec3 dir = d.right.scale(Math.cos(ang)).add(d.up.scale(Math.sin(ang)));
			float len = r * (2.2f + 1.2f * (float) Math.sin(t * 7 + i));
			d.ribbon(c, c.add(dir.scale(len)), r * 0.12f, 0.01f, FxDraw.alpha(FxDraw.WHITE, 0.8f * p), FxDraw.clear(color));
		}

		for (int k = 0; k < 3; k++) {
			float ph = frac(t * 1.5f + k / 3f);
			d.billboardRing(c, fx.radius() * 3f * (1 - ph) + r, 0.15f, FxDraw.alpha(color, 0.7f * ph * (0.3f + 0.7f * p)));
		}
	}

	private static float rnd(int seed, int i, int k) {
		int h = seed * 0x9E3779B1 + i * 0x85EBCA77 + k * 0xC2B2AE3D;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		h *= 0x297A2D39;
		h ^= h >>> 15;
		return (h >>> 8) / (float) (1 << 24);
	}

	private static Vec3 upDir(int seed, int i, float upBias) {
		double x = rnd(seed, i, 11) * 2 - 1;
		double y = Math.abs(rnd(seed, i, 12) * 2 - 1) * upBias + (1 - upBias) * (rnd(seed, i, 13) * 2 - 1) + 0.12;
		double z = rnd(seed, i, 14) * 2 - 1;
		Vec3 v = new Vec3(x, y, z);
		return v.lengthSqr() < 1e-6 ? UP : v.normalize();
	}

	private static int fireColor(float temp) {
		if (temp > 0.75f) {
			return FxDraw.mix(0xFFFFB347, 0xFFFFF2C0, (temp - 0.75f) / 0.25f);
		}
		if (temp > 0.4f) {
			return FxDraw.mix(0xFFFF5A1F, 0xFFFFB347, (temp - 0.4f) / 0.35f);
		}
		return FxDraw.mix(0xFF7A1806, 0xFFFF5A1F, temp / 0.4f);
	}

	private static void explosion(FxDraw d, FxPayload fx, float t, float quality) {
		Vec3 c = fx.a();
		float power = fx.radius();
		float size = Math.max(0.7f, power * 0.9f);
		float dur = fx.duration();
		int seed = fx.seed();

		double groundY = c.y;
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(c.x), Mth.floor(c.z));
			if (h <= c.y + 1 && h >= c.y - size * 2) {
				groundY = h;
			}
		}
		Vec3 ground = new Vec3(c.x, groundY + 0.08, c.z);

		if (t < 0.3f) {
			float k = 1 - t / 0.3f;
			d.glowBall(c, size * 1.5f * (0.6f + 0.4f * easeOutCubic(t / 0.1f)), 0xFFFFF4D0, k * k * 1.2f);
		}

		if (t < 0.5f) {
			float p = t / 0.5f;
			d.billboardRing(c, size * 3.2f * easeOutCubic(p), 0.12f + size * 0.06f,
					FxDraw.alpha(FxDraw.WHITE, 0.55f * (1 - p)));
		}

		if (t < 1.1f) {
			float p = t / 1.1f;
			float fade = (float) Math.pow(1 - p, 1.5);
			float r = size * 4.2f * easeOutCubic(p);
			float thick = 0.3f + size * 0.12f;
			d.groundRing(ground, r, thick, FxDraw.alpha(0xFFFFC27A, 0.85f * fade));
			d.groundRing(ground, r * 0.9f, thick * 3, FxDraw.alpha(0xFFFF8A3A, 0.2f * fade));
		}
		if (t < 1.6f) {
			float p = t / 1.6f;
			d.flatDisc(ground, X, Z, 0, size * 1.8f, FxDraw.alpha(0xFFFF7A2A, 0.55f * (1 - p)),
					FxDraw.clear(0xFFFF7A2A), 32, 0, 0, 0);
		}

		if (power >= 6 && t < 1.6f) {
			float p = t / 1.6f;
			Vec3 top = c.add(0, size * 1.6f + t * size * 0.5f, 0);
			d.ribbon(ground, top, size * 0.5f, size * 0.25f,
					FxDraw.alpha(0xFFFF8A2A, 0.7f * (1 - p)), FxDraw.alpha(0xFFFF5A1F, 0.3f * (1 - p)));
		}

		int smokeCount = Math.round(Mth.clamp(10 + power * 3, 12, 40) * quality);
		for (int i = 0; i < smokeCount; i++) {
			float t0 = 0.12f + rnd(seed, i, 1) * 0.3f;
			if (t < t0) {
				continue;
			}
			float life = dur - t0;
			float s = (t - t0) / life;
			if (s >= 1) {
				continue;
			}
			double ang = rnd(seed, i, 2) * Math.PI * 2;
			float rise = size * (0.3f + rnd(seed, i, 4) * 0.9f) + (t - t0) * size * (0.35f + 0.25f * rnd(seed, i, 5));
			float horiz = size * (0.3f + 0.7f * rnd(seed, i, 3)) * (1 - (float) Math.exp(-(t - t0) * 3)) * (1 + rise / (size * 3));
			Vec3 pos = c.add(Math.cos(ang) * horiz, rise, Math.sin(ang) * horiz);
			float rad = size * (0.45f + 0.45f * rnd(seed, i, 6)) * (0.6f + 1.3f * s);
			float a = Math.min(1f, (t - t0) / 0.2f) * (float) Math.pow(1 - s, 1.4) * 0.8f;
			int light = FxDraw.mix(0xFF9A928A, 0xFFC4BCB4, s);
			int dark = FxDraw.mix(0xFF2E2926, 0xFF5E5A56, s);

			float fireLit = Math.max(0f, 1f - (t - t0) / 0.9f) * 0.7f;
			dark = FxDraw.mix(dark, 0xFF9A4212, fireLit);
			d.smoke(pos, (rad) * 1.05f, ((seed + i * 7919) & 1023) * 0.0061f + t * ((((seed + i * 7919) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + i * 7919) & 3, light, dark, (a) * 0.9f);
		}

		int fireCount = Math.round(Mth.clamp(8 + power * 2.5f, 10, 32) * quality);
		float expand = 1 - (float) Math.exp(-t * 5.5f);
		for (int i = 0; i < fireCount; i++) {
			float life = 0.8f + 0.5f * rnd(seed, i, 23) + power * 0.03f;
			float temp = 1 - t / life;
			if (temp <= 0) {
				continue;
			}
			Vec3 dir = upDir(seed, i, 0.8f);
			float dist = size * (0.2f + 0.8f * rnd(seed, i, 21));
			Vec3 pos = c.add(dir.scale(dist * expand)).add(0, t * size * 0.22f, 0);
			float rad = size * (0.35f + 0.45f * rnd(seed, i, 22)) * (0.45f + 0.9f * expand) * (1 + 0.12f * t);
			float flick = 0.85f + 0.15f * (float) Math.sin(t * 40 + i);
			int col = fireColor(temp);
			float rot = rnd(seed, i, 24) * 6.28f + t * (rnd(seed, i, 25) - 0.5f);
			d.fire(pos, rad * 1.5f, rot, i & 3, col, 0.95f * (float) Math.pow(temp, 0.7) * flick);
			if (temp > 0.35f) {
				d.fire(pos, rad * 0.75f, -rot, (i + 1) & 3, 0xFFFFF6D8, (temp - 0.35f) / 0.65f * 0.85f);
			}
		}

		int sparkCount = Math.round(Mth.clamp(14 + power * 4, 16, 64) * quality);
		for (int i = 0; i < sparkCount; i++) {
			float life = 0.5f + 1.1f * rnd(seed, i, 31);
			if (t > life) {
				continue;
			}
			Vec3 dir = upDir(seed, i + 500, 0.9f);
			float v = size * (1.6f + 3f * rnd(seed, i, 32));
			float gravity = 16f;
			Vec3 pos = c.add(dir.scale(v * t)).add(0, -0.5 * gravity * t * t, 0);
			Vec3 vel = dir.scale(v).add(0, -gravity * t, 0);
			Vec3 tail = pos.subtract(vel.normalize().scale(Math.min(vel.length() * 0.05, 1.4)));
			float k = t / life;
			int col = FxDraw.mix(0xFFFFF2B0, 0xFFFF6A1A, k);
			d.ribbon(pos, tail, 0.07f * (1 - k * 0.5f), 0.01f, FxDraw.alpha(col, 1 - k), FxDraw.clear(col));
		}

		int emberCount = Math.round(10 * quality);
		for (int i = 0; i < emberCount; i++) {
			float t0 = 0.3f + rnd(seed, i, 51) * 0.8f;
			if (t < t0) {
				continue;
			}
			float life = 1.5f + rnd(seed, i, 52);
			float s = (t - t0) / life;
			if (s >= 1) {
				continue;
			}
			double ang = rnd(seed, i, 53) * Math.PI * 2;
			float r = size * (0.3f + rnd(seed, i, 54));
			Vec3 pos = c.add(Math.cos(ang + t) * r, size * 0.5f + (t - t0) * size * 0.5f, Math.sin(ang + t) * r);
			float flick = 0.6f + 0.4f * (float) Math.sin(t * 25 + i * 3);
			d.disc(pos, 0.07f + size * 0.01f, FxDraw.alpha(0xFFFFA040, (1 - s) * flick), FxDraw.clear(0xFFFFA040), true, 6);
		}
	}

	private static void mushroom(FxDraw d, FxPayload fx, float t, float quality) {
		Vec3 g = fx.a();
		float s = fx.radius();
		float dur = fx.duration();
		int seed = fx.seed();
		float fadeAll = Mth.clamp((dur - t) / 4f, 0f, 1f);

		float h = s * (0.6f + 2.4f * (1 - (float) Math.exp(-t / 3.2f)));
		float ringR = s * (0.45f + 1.0f * (1 - (float) Math.exp(-t / 2.6f)));
		float tube = s * (0.32f + 0.38f * (1 - (float) Math.exp(-t / 3.0f)));
		Vec3 cap = g.add(0, h, 0);
		float heat = Mth.clamp(1 - t / 7f, 0f, 1f);
		float hot = Mth.clamp(1 - t / 2.4f, 0f, 1f);

		int smokeLight = FxDraw.mix(0xFFB8AEA4, 0xFF8E8780, Mth.clamp(t / dur, 0f, 1f));
		int smokeDark = FxDraw.mix(0xFF3A2F28, 0xFF4A4642, Mth.clamp(t / dur, 0f, 1f));
		if (fx.color() != 0xFFFFFFFF) {
			smokeLight = FxDraw.mix(smokeLight, fx.color(), 0.45f);
			smokeDark = FxDraw.mix(smokeDark, FxDraw.mix(fx.color(), 0xFF000000, 0.6f), 0.4f);
		}
		int fireLitDark = FxDraw.mix(smokeDark, 0xFFB4501A, heat * 0.85f);

		quality *= Mth.clamp((float) Math.sqrt(s / 10f), 1f, 2.4f);

		if (t < 0.6f) {
			float k = 1 - t / 0.6f;
			d.glowBall(g.add(0, s * 0.6, 0), s * 2.4f * (0.5f + 0.5f * easeOutCubic(t / 0.15f)), 0xFFFFF8E8, k * k * 1.6f);
		}

		if (t < 3f) {
			float p = t / 3f;
			float r = s * 9f * easeOutCubic(p);
			float thick = 0.6f + s * 0.15f;
			d.groundRing(g.add(0, 0.1, 0), r, thick, FxDraw.alpha(0xFFFFD8A0, 0.9f * (float) Math.pow(1 - p, 1.5)));
			d.groundRing(g.add(0, 0.1, 0), r * 0.95f, thick * 3, FxDraw.alpha(0xFFFF9A50, 0.25f * (1 - p)));
		}
		if (t < 1.0f) {
			float p = t / 1.0f;
			d.billboardRing(g.add(0, s * 0.6, 0), s * 6.5f * easeOutCubic(p), 0.25f + s * 0.05f,
					FxDraw.alpha(FxDraw.WHITE, 0.6f * (1 - p)));
		}

		int baseCount = Math.round(48 * quality);
		float baseR = s * (1.0f + 3.4f * (1 - (float) Math.exp(-t / 2.2f)));
		for (int i = 0; i < baseCount; i++) {
			double ang = Math.PI * 2 * (i + rnd(seed, i, 61) * 0.6) / baseCount;
			float rr = baseR * (0.7f + 0.3f * rnd(seed, i, 62));
			Vec3 pos = g.add(Math.cos(ang) * rr, s * 0.18f * (0.5f + rnd(seed, i, 63)), Math.sin(ang) * rr);
			float rad = s * (0.35f + 0.25f * rnd(seed, i, 64)) * (1 + t * 0.05f);
			d.smoke(pos, (rad) * 1.05f, ((seed + i * 31) & 1023) * 0.0061f + t * ((((seed + i * 31) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + i * 31) & 3, 0xFFA8957E, 0xFF4E4234, (0.75f * fadeAll * Math.min(1f, t / 0.5f)) * 0.9f);
		}

		int stemCount = Math.round(50 * quality);
		float stemTop = h - tube * 0.6f;
		float formed = Mth.clamp(t / 1.8f, 0f, 1f);
		for (int i = 0; i < stemCount; i++) {
			float u = frac(rnd(seed, i, 71) + t * 0.04f);
			if (u > formed) {
				continue;
			}
			float width = s * (0.18f + 0.24f * (1 - u) * (1 - u) + 0.08f * u) * (0.85f + 0.3f * rnd(seed, i, 72));
			double ang = rnd(seed, i, 73) * Math.PI * 2 + t * 0.3;
			float off = width * 0.5f * rnd(seed, i, 74);
			Vec3 pos = g.add(Math.cos(ang) * off, u * stemTop, Math.sin(ang) * off);
			int dark = FxDraw.mix(smokeDark, 0xFF9A4212, heat * 0.6f * (1 - u * 0.5f));
			d.smoke(pos, (width * (0.9f + 0.3f * rnd(seed, i, 75))) * 1.05f, ((seed + i * 53) & 1023) * 0.0061f + t * ((((seed + i * 53) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + i * 53) & 3, smokeLight, dark, (0.85f * fadeAll) * 0.9f);
		}

		if (heat > 0) {
			d.ribbon(g, cap, s * 0.12f, s * 0.22f, FxDraw.alpha(0xFFFF7A2A, 0.55f * heat), FxDraw.alpha(0xFFFFB050, 0.65f * heat));
		}

		if (hot > 0) {
			int fireCount = Math.round(26 * quality);
			for (int i = 0; i < fireCount; i++) {
				Vec3 dir = upDir(seed, i + 300, 0.3f);
				Vec3 pos = cap.add(dir.scale(tube * 1.2f * rnd(seed, i, 81)));
				float rad = tube * (0.6f + 0.5f * rnd(seed, i, 82));
				float flick = 0.85f + 0.15f * (float) Math.sin(t * 30 + i);
				int col = fireColor(0.1f + 0.9f * hot);
				d.fire(pos, rad * 1.5f, rnd(seed, i, 83) * 6.28f + t * 0.3f, i & 3, col, 0.9f * (float) Math.pow(hot, 0.6) * flick);
			}
			d.glowBall(cap, tube * 1.8f, 0xFFFFC070, hot);
		}

		int torusCount = Math.round(72 * quality);
		for (int i = 0; i < torusCount; i++) {
			double phi = Math.PI * 2 * (i + rnd(seed, i, 91) * 0.5) / torusCount;
			double theta = rnd(seed, i, 92) * Math.PI * 2 + t * 0.9;
			double radial = ringR + tube * Math.cos(theta);
			Vec3 pos = cap.add(Math.cos(phi) * radial, tube * Math.sin(theta), Math.sin(phi) * radial);
			float rad = tube * (0.75f + 0.35f * rnd(seed, i, 93));
			float under = (float) Math.max(0, -Math.sin(theta));
			int dark = FxDraw.mix(smokeDark, fireLitDark, 0.4f + 0.6f * under);
			d.smoke(pos, (rad) * 1.05f, ((seed + i * 97) & 1023) * 0.0061f + t * ((((seed + i * 97) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + i * 97) & 3, smokeLight, dark, (0.92f * fadeAll) * 0.9f);
			if (heat > 0.05f && under > 0.2f) {
				int glow = fireColor(0.2f + 0.6f * heat);
				d.fire(pos, rad * 1.1f, rnd(seed, i, 94) * 6.28f, i & 3, glow, 0.5f * heat * under);
			}
		}

		int domeCount = Math.round(26 * quality);
		for (int i = 0; i < domeCount; i++) {
			Vec3 dir = upDir(seed, i + 700, 1f);
			Vec3 pos = cap.add(dir.x * ringR * 0.75, tube * 0.6 + dir.y * tube * 0.8, dir.z * ringR * 0.75);
			float rad = tube * 0.9f * (0.8f + 0.4f * rnd(seed, i, 101));
			d.smoke(pos, (rad) * 1.05f, ((seed + i * 131) & 1023) * 0.0061f + t * ((((seed + i * 131) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + i * 131) & 3, FxDraw.mix(smokeLight, 0xFFD8D0C8, 0.4f), smokeDark, (0.9f * fadeAll) * 0.9f);
		}

		if (t > 0.6f && t < 6.5f) {
			float p = (t - 0.6f) / 5.9f;
			float ringH = h * 0.55f;
			float cr = s * (1.4f + 1.7f * easeOutCubic(p));
			float a = (float) Math.sin(Math.PI * Math.min(1f, p * 1.4f)) * 0.75f;
			int count = Math.round(28 * quality);
			for (int k = 0; k < count; k++) {
				double ang = Math.PI * 2 * (k + rnd(seed, k, 111) * 0.5) / count;
				Vec3 pos = g.add(Math.cos(ang) * cr, ringH + rnd(seed, k, 112) * s * 0.12f, Math.sin(ang) * cr);
				d.smoke(pos, (s * 0.32f) * 1.05f, ((seed + k * 151) & 1023) * 0.0061f + t * ((((seed + k * 151) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + k * 151) & 3, 0xFFFFFFFF, 0xFFC8D0D8, (a * fadeAll) * 0.9f);
			}
		}
	}

	private static void rod(FxDraw d, MeshDraw mesh, FxPayload fx, float t) {
		float fall = Math.max(0.1f, fx.radius());
		Vec3 from = fx.a();
		Vec3 to = fx.b();
		Vec3 dir = to.subtract(from).normalize();
		float p = Math.min(1f, t / fall);
		Vec3 head = from.add(to.subtract(from).scale(p));
		int plasma = 0xFF7FD4FF;
		float trailFade = t <= fall ? 1f : Math.max(0f, 1f - (t - fall) / (fx.duration() - fall));

		d.ribbon(from, head, 0.15f, 0.4f, FxDraw.alpha(0xFFB8E8FF, 0.15f * trailFade), FxDraw.alpha(0xFFDDF4FF, 0.55f * trailFade));
		if (t <= fall) {
			d.ribbon(head, head.subtract(dir.scale(16)), 1.1f, 0.05f, FxDraw.alpha(plasma, 0.85f), FxDraw.clear(plasma));
			d.ribbon(head, head.subtract(dir.scale(7)), 0.18f, 0.05f, FxDraw.alpha(FxDraw.WHITE, 1f), FxDraw.alpha(FxDraw.WHITE, 0.4f));
			d.glowBall(head, 1.8f, 0xFFAEE6FF, 1f);
			mesh.begin(head.subtract(dir.scale(3)), dir, t * 3, 7);
			Models.rod(mesh);

			Vec3[] uv = basis(dir);
			for (int k = 0; k < 6; k++) {
				float ph = frac(t * 7 + k / 6f);
				Vec3 c = head.subtract(dir.scale(ph * 12));
				d.ring(c, uv[0], uv[1], 0.6f + ph * 3.5f, 0.25f + ph * 0.4f, FxDraw.alpha(0xFFDDF4FF, 0.55f * (1 - ph)), 28, 0, 0, 0);
			}
		}
	}

	private static void satellite(FxDraw d, MeshDraw mesh, FxPayload fx, float t) {
		float p = t / fx.duration();
		Vec3 pos = fx.a().add(fx.b().subtract(fx.a()).scale(p));
		float env = envelope(t, fx.duration(), 0.6f, 0.8f);
		float size = fx.radius();
		mesh.begin(pos, new Vec3(0, -1, 0), t * 0.15, size * 4);
		Models.satellite(mesh);
		d.glowBall(pos, size * 0.6f, 0xFFDDEEFF, env * 0.6f);

		d.ribbon(pos.subtract(d.right.scale(size * 7)), pos.add(d.right.scale(size * 7)), size * 0.18f, size * 0.18f,
				FxDraw.alpha(0xFFCFE6FF, 0.6f * env), FxDraw.alpha(0xFFCFE6FF, 0.6f * env));
		d.ribbon(pos.subtract(d.up.scale(size * 4)), pos.add(d.up.scale(size * 4)), size * 0.14f, size * 0.14f,
				FxDraw.alpha(0xFFCFE6FF, 0.45f * env), FxDraw.alpha(0xFFCFE6FF, 0.45f * env));
		d.billboardRing(pos, size * 2.2f, size * 0.15f, FxDraw.alpha(0xFF9FD0FF, 0.5f * env));

		if (Math.sin(t * 9) > 0.3) {
			d.glowBall(pos.add(d.right.scale(size * 1.6)), size * 0.35f, 0xFFFF4040, env);
		}
	}

	private static void chunkGrid(FxDraw d, FxPayload fx, float t) {
		Vec3 c = fx.a();
		int n = Math.round(fx.radius());
		int color = fx.color();
		float charge = fx.extra() > 0 ? fx.extra() : 3f;
		float env = envelope(t, fx.duration(), 0.3f, 1.5f);
		int ccx = Mth.floor(c.x) >> 4;
		int ccz = Mth.floor(c.z) >> 4;
		float fallChance = (float) fx.b().x;
		long seed = fx.seed();

		if (t < charge + 0.5f) {
			hologramCube(d, c, t, charge, color);
		}
		float tc = t - charge;
		if (tc <= 0) {
			float pre = Mth.clamp(t / charge, 0f, 1f) * 0.25f * env;
			for (int dx = -n; dx <= n; dx++) {
				for (int dz = -n; dz <= n; dz++) {
					float dist = (float) Math.sqrt(dx * dx + dz * dz) / (n + 1);
					if (dist > t / charge) {
						continue;
					}
					chunkFrame(d, ccx + dx, ccz + dz, c.y, 1f, color, pre, 0.08f);
				}
			}
			return;
		}

		for (int dx = -n; dx <= n; dx++) {
			for (int dz = -n; dz <= n; dz++) {
				int cx = ccx + dx;
				int cz = ccz + dz;
				float tl = tc - (float) Math.sqrt(dx * dx + dz * dz) * 0.14f;
				if (tl < 0) {
					continue;
				}
				boolean fallen = com.example.mymod.world.WorldCarver.isFallen(cx, cz, seed, fallChance);
				float rise = easeOutBack(Math.min(1f, tl / 0.35f));
				float bright = tl < 0.25f ? 1.6f : tl < 1.8f ? 1f : Math.max(0.2f, 1f - (tl - 1.8f) / 2.5f);
				int col = fallen ? 0xFFFF4A3A : color;
				chunkFrame(d, cx, cz, c.y, rise, col, bright * env, fallen ? 0.16f : 0.1f);
				if (tl < 0.3f) {
					d.glowBall(new Vec3(cx * 16 + 8, c.y + 2, cz * 16 + 8), 4f, col, (0.3f - tl) / 0.3f);
				}
			}
		}

		double y0 = c.y - 40;
		double y1 = c.y + 60;
		double minX = (ccx - n) * 16.0;
		double maxX = (ccx + n + 1) * 16.0;
		double minZ = (ccz - n) * 16.0;
		double maxZ = (ccz + n + 1) * 16.0;
		double scan = y1 - frac(tc * 0.7f) * (y1 - y0);
		float cut = env * Math.max(0f, 1f - tc / (2.5f + n));
		if (cut > 0.01f) {
			for (int i = 0; i <= 2 * n + 1; i++) {
				gridPlane(d, new Vec3((ccx - n + i) * 16.0, 0, minZ), new Vec3((ccx - n + i) * 16.0, 0, maxZ), y0, y1, scan, color, cut);
				gridPlane(d, new Vec3(minX, 0, (ccz - n + i) * 16.0), new Vec3(maxX, 0, (ccz - n + i) * 16.0), y0, y1, scan, color, cut);
			}
		}
	}

	private static void chunkFrame(FxDraw d, int cx, int cz, double groundY, float rise, int color, float alpha, float width) {
		if (alpha <= 0.01f) {
			return;
		}
		double x0 = cx * 16 + 0.05;
		double x1 = cx * 16 + 15.95;
		double z0 = cz * 16 + 0.05;
		double z1 = cz * 16 + 15.95;
		double yb = groundY - 20;
		double yt = groundY - 20 + 40 * rise;
		Vec3[] p = {
				new Vec3(x0, yb, z0), new Vec3(x1, yb, z0), new Vec3(x1, yb, z1), new Vec3(x0, yb, z1),
				new Vec3(x0, yt, z0), new Vec3(x1, yt, z0), new Vec3(x1, yt, z1), new Vec3(x0, yt, z1)};
		int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		int core = FxDraw.alpha(FxDraw.mix(color, FxDraw.WHITE, 0.35f), Math.min(1f, 0.9f * alpha));
		int glow = FxDraw.alpha(color, Math.min(1f, 0.25f * alpha));
		for (int[] e : edges) {
			d.ribbon(p[e[0]], p[e[1]], width, width, core, core);
			d.ribbon(p[e[0]], p[e[1]], width * 5, width * 5, glow, glow);
		}
	}

	private static void hologramCube(FxDraw d, Vec3 base, float t, float charge, int color) {
		Vec3 center = base.add(0, 4.5 + Math.sin(t * 2.0) * 0.25, 0);
		float grow = easeOutBack(Math.min(1f, t / 0.9f));
		float burst = t > charge ? (t - charge) / 0.5f : 0f;
		float alpha = burst > 0 ? Math.max(0f, 1f - burst) : 1f;
		float charge01 = Mth.clamp(t / charge, 0f, 1f);
		float size = 3.0f * grow * (1f + burst * 7f);
		float spin = t * (0.6f + charge01 * 2.2f);

		wireCube(d, center, size, spin, spin * 0.6f, color, alpha, 0.05f * size);
		wireCube(d, center, size * 0.55f, -spin * 1.6f, spin * 1.1f, 0xFFB49CFF, alpha, 0.04f * size);

		d.glowBall(center, size * (0.15f + 0.08f * (float) Math.sin(t * 9)) * (0.6f + charge01), FxDraw.mix(color, FxDraw.WHITE, 0.5f), alpha);

		d.beam(base.add(0, 0.5, 0), center, 0.06f + 0.1f * charge01, color, alpha * (0.4f + 0.6f * charge01));

		for (int k = 0; k < 10; k++) {
			double a = t * (1.5 + k * 0.17) + k * 0.63;
			double tilt = k * 0.31;
			Vec3 o = new Vec3(Math.cos(a), Math.sin(a) * Math.sin(tilt), Math.sin(a) * Math.cos(tilt)).scale(size * 0.95);
			d.glowBall(center.add(o), 0.12f * size * 0.4f + 0.06f, color, alpha * 0.9f);
		}

		if (burst > 0 && burst < 1) {
			d.glowBall(center, 6f * burst + 2f, FxDraw.WHITE, (1f - burst) * 1.3f);
		}
	}

	private static void wireCube(FxDraw d, Vec3 c, float size, float ry, float rx, int color, float alpha, float width) {
		if (alpha <= 0.01f) {
			return;
		}
		Vec3[] p = new Vec3[8];
		double h = size / 2;
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? -h : h;
			double y = (i & 2) == 0 ? -h : h;
			double z = (i & 4) == 0 ? -h : h;
			double x1 = x * Math.cos(ry) + z * Math.sin(ry);
			double z1 = -x * Math.sin(ry) + z * Math.cos(ry);
			double y2 = y * Math.cos(rx) - z1 * Math.sin(rx);
			double z2 = y * Math.sin(rx) + z1 * Math.cos(rx);
			p[i] = c.add(x1, y2, z2);
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		int core = FxDraw.alpha(FxDraw.mix(color, FxDraw.WHITE, 0.5f), alpha);
		int glow = FxDraw.alpha(color, 0.35f * alpha);
		for (int[] e : edges) {
			d.ribbon(p[e[0]], p[e[1]], width, width, core, core);
			d.ribbon(p[e[0]], p[e[1]], width * 4, width * 4, glow, glow);
		}
		for (Vec3 corner : p) {
			d.glowBall(corner, width * 3.5f, color, alpha);
		}
	}

	private static void chunkFall(FxDraw d, MeshDraw mesh, FxPayload fx, ChunkSlab slab, float t) {
		if (slab == null) {
			return;
		}
		int seed = fx.seed();
		float shake = 0.8f;
		double drop = t < shake ? 0 : 0.5 * 18 * (t - shake) * (t - shake);
		double jx = 0;
		double jz = 0;
		double jy = 0;
		if (t < shake) {
			float k = t / shake;
			jx = Math.sin(t * 70 + seed) * 0.12 * k;
			jz = Math.cos(t * 63 + seed) * 0.12 * k;
			jy = -0.3 * k * k;
		}
		double tilt = t < shake ? 0 : Math.min(0.5, (t - shake) * 0.22);
		double axis = (seed & 1023) * 0.00614;
		Vec3 up = new Vec3(Math.cos(axis) * Math.sin(tilt), Math.cos(tilt), Math.sin(axis) * Math.sin(tilt));
		Vec3 center = new Vec3(slab.cx * 16 + 8 + jx, slab.baseY + 0.02 - drop + jy, slab.cz * 16 + 8 + jz);
		if (drop < 260) {
			mesh.begin(center, up, Math.PI / 2, 1);
			slab.emit(mesh);
		}

		double rimY = slab.minTop + 1.05;
		double x0 = slab.cx * 16;
		double z0 = slab.cz * 16;
		Vec3[] rim = {new Vec3(x0, rimY, z0), new Vec3(x0 + 16, rimY, z0), new Vec3(x0 + 16, rimY, z0 + 16), new Vec3(x0, rimY, z0 + 16)};
		float glow = Math.max(0f, 1f - t / 6f) * (0.75f + 0.25f * (float) Math.sin(t * 13 + seed));
		int hot = 0xFFFF6A2A;
		for (int k = 0; k < 4; k++) {
			Vec3 a = rim[k];
			Vec3 b = rim[(k + 1) % 4];
			d.ribbon(a, b, 0.22f, 0.22f, FxDraw.alpha(0xFFFFD08A, glow), FxDraw.alpha(0xFFFFD08A, glow));
			d.ribbon(a, b, 1.2f, 1.2f, FxDraw.alpha(hot, 0.35f * glow), FxDraw.alpha(hot, 0.35f * glow));

			d.quad(a, b, b.add(0, -26, 0), a.add(0, -26, 0), FxDraw.alpha(hot, 0.4f * glow), FxDraw.alpha(hot, 0.4f * glow),
					FxDraw.clear(hot), FxDraw.clear(hot), true);
		}

		for (int k = 0; k < 24; k++) {
			float age = t - k * 0.04f;
			if (age <= 0 || age > 4.5f) {
				continue;
			}
			int edge = k % 4;
			float along = rnd(seed, k, 1);
			Vec3 a = rim[edge];
			Vec3 b = rim[(edge + 1) % 4];
			Vec3 pos = a.add(b.subtract(a).scale(along)).add(0, age * 2.2, 0);
			d.smoke(pos, 1.6f + age * 1.3f, rnd(seed, k, 2) * 6.28f + age * 0.2f, k, 0xFFC0B098, 0xFF5E5040,
					0.55f * (1f - age / 4.5f));
		}

		if (t > shake && drop < 200) {
			for (int k = 0; k < 12; k++) {
				float ph = frac(t * 0.8f + k / 12f);
				double ang = k * Math.PI * 2 / 12;
				Vec3 pos = center.add(Math.cos(ang) * 9, 2 + ph * 10, Math.sin(ang) * 9);
				d.smoke(pos, 2f + ph * 3f, ph * 3f + k, k + 3, 0xFFB8A890, 0xFF4A3E32, 0.45f * (1 - ph));
			}
		}
	}

	private static void gridPlane(FxDraw d, Vec3 a, Vec3 b, double y0, double y1, double scan, int color, float alpha) {
		int faint = FxDraw.alpha(color, 0.10f * alpha);
		int clear = FxDraw.clear(color);
		Vec3 a0 = new Vec3(a.x, y0, a.z);
		Vec3 b0 = new Vec3(b.x, y0, b.z);
		Vec3 a1 = new Vec3(a.x, y1, a.z);
		Vec3 b1 = new Vec3(b.x, y1, b.z);

		Vec3 am = new Vec3(a.x, (y0 + y1) / 2, a.z);
		Vec3 bm = new Vec3(b.x, (y0 + y1) / 2, b.z);
		d.quad(a0, b0, bm, am, clear, clear, faint, faint, true);
		d.quad(am, bm, b1, a1, faint, faint, clear, clear, true);

		double h = 1.6;
		int bright = FxDraw.alpha(FxDraw.mix(color, FxDraw.WHITE, 0.4f), 0.85f * alpha);
		Vec3 sa = new Vec3(a.x, scan, a.z);
		Vec3 sb = new Vec3(b.x, scan, b.z);
		d.quad(new Vec3(a.x, scan - h, a.z), new Vec3(b.x, scan - h, b.z), sb, sa, clear, clear, bright, bright, true);
		d.quad(sa, sb, new Vec3(b.x, scan + h, b.z), new Vec3(a.x, scan + h, a.z), bright, bright, clear, clear, true);
	}

	private static void asteroid(FxDraw d, MeshDraw mesh, FxPayload fx, float t) {
		float p = Math.min(1f, t / fx.duration());
		Vec3 from = fx.a();
		Vec3 to = fx.b();
		Vec3 dir = to.subtract(from).normalize();
		Vec3 pos = from.add(to.subtract(from).scale(p));
		float size = fx.radius();
		int seed = fx.seed();
		float heat = 0.4f + 0.6f * p;
		float flick = 1f + 0.08f * (float) Math.sin(t * 40 + seed);

		for (int k = 0; k < 26; k++) {
			double s = k * size * 3.2;
			Vec3 wob = new Vec3(Math.sin(k * 1.7 + seed) , Math.cos(k * 2.3 + seed), Math.sin(k * 0.9)).scale(size * 0.4 * k / 26.0);
			Vec3 pp = pos.subtract(dir.scale(s)).add(wob);
			float rad = size * (0.9f + k * 0.07f);
			float a = 0.6f * (1 - k / 26f);
			d.smoke(pp, (rad) * 1.05f, ((seed + k * 977) & 1023) * 0.0061f + t * ((((seed + k * 977) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + k * 977) & 3, 0xFF6A625C, 0xFF201A16, (a) * 0.9f);
		}

		int trail = fx.variant() == Fx.ROCK_ICE ? 0xFF7FD4FF : 0xFFFF7A1C;
		d.ribbon(pos, pos.subtract(dir.scale(size * 45)), size * 1.5f * flick, size * 0.15f,
				FxDraw.alpha(trail, 0.85f * heat), FxDraw.clear(trail));
		d.ribbon(pos, pos.subtract(dir.scale(size * 90)), size * 0.6f, size * 0.05f,
				FxDraw.alpha(0xFFFFE8B0, 0.55f * heat), FxDraw.clear(0xFFFFE8B0));
		d.ribbon(pos.add(dir.scale(size * 1.1)), pos.subtract(dir.scale(size * 7)), size * 2.4f, size * 0.6f,
				FxDraw.alpha(0xFFFFB060, 0.45f * heat), FxDraw.clear(0xFFFFB060));

		d.glowBall(pos.add(dir.scale(size * 0.6)), size * 2.0f * flick, 0xFFFFB060, heat);

		int rock = fx.variant();
		int dark = rock == Fx.ROCK_ICE ? 0xFF7FA8C8 : rock == Fx.ROCK_MOON ? 0xFF5E5E62 : 0xFF2A2018;
		int light = rock == Fx.ROCK_ICE ? 0xFFE6F6FF : rock == Fx.ROCK_MOON ? 0xFFB4B4B8 : 0xFF5A4A3A;
		int glow = rock == Fx.ROCK_MOON ? 0 : rock == Fx.ROCK_ICE ? 0xFFC8F4FF : 0xFFFF8A2A;
		mesh.begin(pos, new Vec3(Math.sin(t * 0.7), Math.cos(t * 0.5), Math.sin(t * 0.3 + 1)), t * 0.6, 1);
		mesh.rock(size, seed, dark, light, glow, 10, 16);
		d.billboardRing(pos, size * 1.02f, size * 0.16f, FxDraw.alpha(0xFFFF8A2A, 0.9f * heat));

		for (int k = 0; k < 12; k++) {
			float ph = frac(t * 3 + k / 12f);
			Vec3 side = basis(dir)[k % 2].scale((rnd(seed, k, 1) - 0.5) * size * 3);
			Vec3 sp = pos.subtract(dir.scale(ph * size * 25)).add(side.scale(ph));
			d.ribbon(sp, sp.subtract(dir.scale(size * 1.5)), size * 0.08f, 0.01f,
					FxDraw.alpha(0xFFFFD080, 1 - ph), FxDraw.clear(0xFFFFD080));
		}
	}

	private static void antimatter(FxDraw d, FxPayload fx, float t) {
		Vec3 c = fx.a();
		float dur = fx.duration();
		float big = fx.radius();
		int seed = fx.seed();
		float grow = easeOutCubic(Math.min(1f, t / (dur * 0.65f)));
		float implode = t > dur * 0.85f ? 1f - easeOutCubic((t - dur * 0.85f) / (dur * 0.12f)) : 1f;
		float r = big * grow * Math.max(0f, implode);
		if (r < 0.05f) {
			return;
		}
		float pulse = 0.85f + 0.15f * (float) Math.sin(t * 7);

		d.disc(c, r, FxDraw.alpha(0xFF9FB8FF, 0.10f * pulse), FxDraw.alpha(0xFFDDE8FF, 0.45f * pulse), true, 48);
		d.billboardRing(c, r, Math.max(0.15f, r * 0.03f), FxDraw.alpha(FxDraw.WHITE, 0.95f));
		d.billboardRing(c, r * 1.04f, Math.max(0.3f, r * 0.06f), FxDraw.alpha(0xFFB49CFF, 0.5f));

		for (int k = 0; k < 3; k++) {
			double ax = t * (0.6 + k * 0.35) + k * 2.1;
			Vec3 u = new Vec3(Math.cos(ax), Math.sin(ax * 0.7) * 0.5, Math.sin(ax)).normalize();
			Vec3 v = u.cross(new Vec3(0.3 * k, 1, 0.2)).normalize();
			d.ring(c, u, v, r * (0.55f + 0.15f * k), Math.max(0.1f, r * 0.015f), FxDraw.alpha(0xFFE6D8FF, 0.6f), 64, 6, 4f, t);
		}

		int frame = (int) (t * 12);
		for (int k = 0; k < 7; k++) {
			Vec3 dirL = upDir(seed + frame * 31, k, 0f);
			Vec3 prev = c;
			for (int s = 1; s <= 6; s++) {
				Vec3 jitter = upDir(seed + frame * 57 + s, k, 0f).scale(r * 0.08);
				Vec3 next = c.add(dirL.scale(r * s / 6.0)).add(jitter);
				d.ribbon(prev, next, Math.max(0.05f, r * 0.012f), Math.max(0.05f, r * 0.012f),
						FxDraw.alpha(0xFFF0E8FF, 0.8f), FxDraw.alpha(0xFFF0E8FF, 0.8f));
				prev = next;
			}
		}
		d.glowBall(c, r * 0.25f * pulse, 0xFFE0D0FF, 1f);
	}

	static Vec3 projectilePos(FxPayload fx, float t) {
		return switch (fx.kind()) {
			case Fx.MISSILE -> trajectory(fx, Math.min(1f, t / Math.max(0.05f, fx.extra2())));
			case Fx.ROD -> fx.a().add(fx.b().subtract(fx.a()).scale(Math.min(1f, t / Math.max(0.1f, fx.radius()))));
			case Fx.METEOR -> {
				float p = Math.min(1f, t / fx.duration());
				yield fx.a().add(fx.b().subtract(fx.a()).scale(p * p));
			}
			default -> fx.a().add(fx.b().subtract(fx.a()).scale(Math.min(1f, t / fx.duration())));
		};
	}

	static float projectileFlight(FxPayload fx) {
		return switch (fx.kind()) {
			case Fx.MISSILE -> Math.max(0.05f, fx.extra2());
			case Fx.ROD -> Math.max(0.1f, fx.radius());
			default -> fx.duration();
		};
	}

	static float projectileSize(FxPayload fx) {
		return switch (fx.kind()) {
			case Fx.MISSILE, Fx.ASTEROID -> fx.radius();
			case Fx.ROD -> 3f;
			default -> 2f;
		};
	}

	private static Vec3 trajectory(FxPayload fx, float p) {
		int traj = (fx.variant() >> 8) & 0xFF;
		Vec3 a = fx.a();
		Vec3 b = fx.b();
		return switch (traj) {
			case Fx.TRAJ_FALL -> a.add(b.subtract(a).scale(p * p));
			case Fx.TRAJ_LINE -> a.add(b.subtract(a).scale(p));
			default -> a.add(b.subtract(a).scale(p)).add(0, 4 * fx.extra() * p * (1 - p), 0);
		};
	}

	private static void missile(FxDraw d, MeshDraw mesh, FxPayload fx, float t, float quality) {
		float flight = Math.max(0.05f, fx.extra2());
		int model = fx.variant() & 0xFF;
		int flags = (fx.variant() >> 16) & 0xFF;
		boolean engine = (flags & Fx.FLAG_ENGINE) != 0;
		boolean spin = (flags & Fx.FLAG_SPIN) != 0;
		boolean smoke = engine || (flags & Fx.FLAG_SMOKE) != 0;
		float p = Math.min(1f, t / flight);
		float scale = fx.radius();
		int seed = fx.seed();
		float after = Math.max(0f, t - flight);

		if (smoke) {
			int count = Math.round(Mth.clamp(46 * quality, 12, 46));
			float step = 0.02f;
			for (int k = 1; k <= count; k++) {
				float pk = p - k * step;
				if (pk <= 0) {
					break;
				}
				float age = (p - pk) * flight + after;
				float a = 0.6f * (float) Math.exp(-age / 3.0) * Math.min(1f, k / 2f);
				if (a < 0.01f) {
					continue;
				}
				Vec3 pos = trajectory(fx, pk).add(Math.sin(k * 1.3 + seed) * age * 0.4, age * 0.5, Math.cos(k * 1.7 + seed) * age * 0.4);
				float rad = scale * 0.07f + age * scale * 0.09f;
				d.smoke(pos, (rad) * 1.05f, ((seed + k * 131) & 1023) * 0.0061f + t * ((((seed + k * 131) >> 10) & 1) == 0 ? 0.05f : -0.05f), (seed + k * 131) & 3, 0xFFF0F0F0, 0xFF8C8C8C, (a) * 0.9f);
			}
		}
		if (t > flight) {
			return;
		}

		Vec3 pos = trajectory(fx, p);
		Vec3 next = trajectory(fx, Math.min(1f, p + 0.004f));
		Vec3 prev = trajectory(fx, Math.max(0f, p - 0.004f));
		Vec3 dir = next.subtract(prev);
		if (dir.lengthSqr() < 1e-10) {
			dir = fx.b().subtract(fx.a());
		}
		dir = dir.normalize();
		mesh.begin(pos, dir, spin ? t * 5 : 0, scale);
		Models.draw(mesh, model, fx.color());

		if (engine) {
			Vec3 nozzle = pos.subtract(dir.scale(scale * 0.56));
			float flick = 0.85f + 0.15f * (float) Math.sin(t * 60 + seed);
			d.ribbon(nozzle, nozzle.subtract(dir.scale(scale * 0.9 * flick)), scale * 0.06f, 0.01f,
					FxDraw.alpha(0xFFFFF0C0, 1f), FxDraw.clear(0xFFFFF0C0));
			d.ribbon(nozzle, nozzle.subtract(dir.scale(scale * 1.7 * flick)), scale * 0.12f, scale * 0.02f,
					FxDraw.alpha(0xFFFF7A1C, 0.8f), FxDraw.clear(0xFFFF7A1C));
			d.glowBall(nozzle, scale * 0.11f * flick, 0xFFFFC060, 1f);
			for (int i = 1; i <= 3; i++) {
				d.disc(nozzle.subtract(dir.scale(scale * 0.17 * i)), scale * 0.028f, FxDraw.alpha(FxDraw.WHITE, 0.8f / i),
						FxDraw.clear(FxDraw.WHITE), true, 8);
			}
		}

		if (model == Fx.MODEL_WARHEAD) {
			float heat = p;
			d.glowBall(pos.add(dir.scale(scale * 0.45)), scale * 0.4f, 0xFFFFB060, 0.4f + 0.6f * heat);
			d.ribbon(pos, pos.subtract(dir.scale(scale * 4)), scale * 0.22f, 0.01f,
					FxDraw.alpha(0xFFFF8A2A, 0.6f * heat), FxDraw.clear(0xFFFF8A2A));
		}
	}

	public static void applyShake(Vec3 cameraPos, PoseStack pose) {
		if (ACTIVE.isEmpty() || cameraPos == null) {
			return;
		}
		float total = 0f;
		for (Active active : ACTIVE) {
			FxPayload fx = active.fx();
			if (fx.kind() != Fx.SHAKE) {
				continue;
			}
			float p = active.age() / fx.duration();
			if (p >= 1f) {
				continue;
			}
			double range = Math.max(1, fx.b().x);
			float fall = (float) Math.max(0, 1 - cameraPos.distanceTo(fx.a()) / range);
			total += fx.radius() * (1 - p) * (1 - p) * fall;
		}
		if (total <= 0.001f) {
			return;
		}
		total = Math.min(total, 6f);
		float time = System.nanoTime() / 1_000_000_000f;
		float rz = total * ((float) Math.sin(time * 37) * 0.6f + (float) Math.sin(time * 61 + 1.3) * 0.4f);
		float rx = total * 0.6f * ((float) Math.sin(time * 43 + 2.1) * 0.6f + (float) Math.sin(time * 79) * 0.4f);
		pose.mulPose(Axis.ZP.rotationDegrees(rz));
		pose.mulPose(Axis.XP.rotationDegrees(rx));
	}

	private static void renderFlash(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition();
		float best = 0f;
		int color = 0xFFFFFFFF;
		for (Active active : ACTIVE) {
			FxPayload fx = active.fx();
			if (fx.kind() != Fx.FLASH && fx.kind() != Fx.TINT) {
				continue;
			}
			float p = active.age() / fx.duration();
			if (p >= 1f) {
				continue;
			}
			float fall = (float) Math.max(0, 1 - eye.distanceTo(fx.a()) / Math.max(1, fx.radius()));
			float a = fx.kind() == Fx.FLASH
					? (float) Math.pow(1 - p, 2) * fall
					: Math.min(1f, active.age() / 0.5f) * Math.min(1f, (1 - p) / 0.6f) * fall * ((fx.color() >>> 24) / 255f) * 0.45f;
			if (a > best) {
				best = a;
				color = fx.color();
			}
		}
		if (best > 0.003f) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), FxDraw.alpha(color | 0xFF000000, 0.85f * best));
		}
	}
}
