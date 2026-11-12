package com.example.mymod.client.fx;

import java.util.List;

import com.example.mymod.MyMod;
import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.network.CinemaPayload;
import com.example.mymod.network.FxPayload;
import com.example.mymod.skill.Cinema;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class CinemaClient {
	private static final Vec3 UP = new Vec3(0, 1, 0);

	public record Shot(Vec3 pos, float yaw, float pitch) {
	}

	private enum Phase { OFF, PRELUDE, WAIT, CHASE, IMPACT, OUTRO }

	private static Phase phase = Phase.OFF;
	private static Cinema.Story story = Cinema.Story.NONE;
	private static Vec3 target = Vec3.ZERO;
	private static float radius;
	private static float prelude;
	private static long phaseStart;
	private static long cinemaStart;
	private static FxPayload follow;
	private static long followStart;
	private static Vec3 space = Vec3.ZERO;
	private static Vec3 impactDir = new Vec3(1, 0, 0);
	private static float impactHold = 7f;
	private static Vec3 lastPos = Vec3.ZERO;
	private static Vec3 lastLook = new Vec3(0, 0, 1);
	private static long fadeInStart;
	private static long flashStart;
	private static int seed;

	private static final boolean[] CUES = new boolean[4];

	private CinemaClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(CinemaPayload.TYPE, (payload, context) -> start(payload));
		LevelRenderEvents.COLLECT_SUBMITS.register(CinemaClient::renderSpace);
		HudElementRegistry.addLast(MyMod.id("cinema"), CinemaClient::renderHud);

		for (Identifier id : List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
				VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
				VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.SCOREBOARD,
				VanillaHudElements.OVERLAY_MESSAGE)) {
			HudElementRegistry.replaceElement(id, original -> (g, delta) -> {
				if (!active()) {
					original.extractRenderState(g, delta);
				}
			});
		}
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> phase = Phase.OFF);
	}

	public static boolean active() {
		return phase != Phase.OFF;
	}

	public static void skip() {
		if (phase != Phase.OFF && phase != Phase.OUTRO) {
			setPhase(Phase.OUTRO);
		}
	}

	private static long now() {
		return System.nanoTime();
	}

	private static float time() {
		return (now() - phaseStart) / 1_000_000_000f;
	}

	private static void setPhase(Phase p) {
		phase = p;
		phaseStart = now();
	}

	static void start(CinemaPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Cinema.Story[] all = Cinema.Story.values();
		story = all[Mth.clamp(p.story(), 0, all.length - 1)];
		if (story == Cinema.Story.NONE) {
			return;
		}
		target = p.target();
		radius = p.radius();
		prelude = p.prelude();
		seed = mc.player.getRandom().nextInt();
		follow = null;
		Vec3 eye = mc.player.getEyePosition();
		Vec3 h = new Vec3(eye.x - target.x, 0, eye.z - target.z);
		impactDir = h.lengthSqr() < 1 ? new Vec3(1, 0, 0) : h.normalize();
		space = new Vec3(eye.x, 2400, eye.z);
		cinemaStart = now();
		flashStart = 0;
		fadeInStart = 0;
		java.util.Arrays.fill(CUES, false);
		setPhase(prelude > 0.05f ? Phase.PRELUDE : Phase.WAIT);
	}

	static void onFx(FxPayload fx) {
		if (follow != null || (phase != Phase.PRELUDE && phase != Phase.WAIT)) {
			return;
		}
		int k = fx.kind();
		if (k != Fx.MISSILE && k != Fx.ASTEROID && k != Fx.ROD && k != Fx.METEOR) {
			return;
		}
		Vec3 to = fx.b();
		double dist = Math.hypot(to.x - target.x, to.z - target.z);
		if (dist > Math.max(90, radius * 4)) {
			return;
		}
		follow = fx;
		followStart = now();
		if (phase == Phase.WAIT) {
			setPhase(Phase.CHASE);
		}
	}

	private static void update() {
		if (phase == Phase.OFF) {
			return;
		}
		float t = time();
		switch (phase) {
			case PRELUDE -> {
				cues(t / Math.max(0.1f, prelude));
				if (t >= prelude) {
					flashStart = now();
					if (follow != null) {
						followStart = now();
						setPhase(Phase.CHASE);
					} else {
						setPhase(Phase.WAIT);
					}
				}
			}
			case WAIT -> {
				if (follow != null) {
					setPhase(Phase.CHASE);
				} else if (t > (story == Cinema.Story.ORBITAL ? 0.4f : 2.5f)) {
					impactHold = story == Cinema.Story.ORBITAL ? 10f : 7f;
					setPhase(Phase.IMPACT);
				}
			}
			case CHASE -> {
				float tf = (now() - followStart) / 1_000_000_000f;
				if (tf >= FxClient.projectileFlight(follow) - 0.05f) {
					impactHold = 7f;
					setPhase(Phase.IMPACT);
				}
			}
			case IMPACT -> {
				if (t >= impactHold) {
					setPhase(Phase.OUTRO);
				}
			}
			case OUTRO -> {
				if (t >= 0.4f) {
					phase = Phase.OFF;
					fadeInStart = now();
				}
			}
			default -> {
			}
		}
	}

	private static void cues(float u) {
		switch (story) {
			case MISSILE -> {
				cue(0, u > 0.1f, ModSounds.ROCKET_LAUNCH, 0.7f);
				cue(1, u > 0.55f, ModSounds.FLYBY, 0.6f);
			}
			case ASTEROID -> {
				cue(0, u > 0.05f, ModSounds.ASTEROID_ENTRY, 0.6f);
				cue(1, u > 0.4f, ModSounds.FLYBY, 0.5f);
				cue(2, u > 0.8f, ModSounds.ASTEROID_ENTRY, 0.9f);
			}
			case ORBITAL -> {
				cue(0, u > 0.12f, ModSounds.SATELLITE_CHARGE, 0.8f);
				cue(1, u > 0.72f, ModSounds.SATELLITE_FIRE, 0.7f);
				cue(2, u > 0.72f, ModSounds.PLASMA, 0.8f);
			}
			default -> {
			}
		}
	}

	private static void cue(int i, boolean when, Holder<SoundEvent> sound, float pitch) {
		if (when && !CUES[i]) {
			CUES[i] = true;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
		}
	}

	public static Shot shot() {
		update();
		if (phase == Phase.OFF) {
			return null;
		}
		float t = time();
		Vec3 pos;
		Vec3 look;
		switch (phase) {
			case PRELUDE -> {
				SpaceScene.Shot s = SpaceScene.camera(story, t, prelude, space);
				pos = s.pos();
				look = s.look();
			}
			case WAIT -> {
				Vec3[] v = impactView(orbitClock());
				pos = v[0];
				look = v[1];
			}
			case CHASE -> {
				Vec3[] v = chaseView();
				pos = v[0];
				look = v[1];
			}
			case IMPACT -> {
				Vec3[] v = impactView(orbitClock());
				pos = v[0];
				look = v[1];
			}
			default -> {
				pos = lastPos;
				look = lastLook;
			}
		}
		lastPos = pos;
		lastLook = look;
		Vec3 d = look.subtract(pos);
		float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		return new Shot(pos, yaw, pitch);
	}

	private static float orbitClock() {
		return Math.max(0f, (now() - cinemaStart) / 1_000_000_000f - prelude);
	}

	private static Vec3[] impactView(float t) {
		double dist = Mth.clamp(radius * 2.5 + 40, 50, 260);
		double ang = t * 0.08;
		Vec3 dir = new Vec3(impactDir.x * Math.cos(ang) - impactDir.z * Math.sin(ang), 0,
				impactDir.x * Math.sin(ang) + impactDir.z * Math.cos(ang));
		Vec3 pos = target.add(dir.scale(dist)).add(0, dist * 0.42, 0);
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
			if (pos.y < gy + 6) {
				pos = new Vec3(pos.x, gy + 6, pos.z);
			}
		}
		Vec3 look = target.add(0, Math.min(radius * 0.35, 40), 0);
		return new Vec3[] {pos, look};
	}

	private static Vec3[] chaseView() {
		float tf = (now() - followStart) / 1_000_000_000f;
		float flight = Math.max(0.1f, FxClient.projectileFlight(follow));
		float tc = Math.min(tf, flight);
		Vec3 p = FxClient.projectilePos(follow, tc);
		Vec3 p2 = FxClient.projectilePos(follow, Math.min(flight, tc + 0.1f));
		Vec3 dir = p2.subtract(p);
		if (dir.lengthSqr() < 1e-6) {
			dir = follow.b().subtract(follow.a());
		}
		dir = dir.lengthSqr() < 1e-9 ? new Vec3(0, -1, 0) : dir.normalize();
		double size = FxClient.projectileSize(follow);
		double dist = Math.max(9, size * 1.8 + 6);
		Vec3 side = dir.cross(UP);
		side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		Vec3 chasePos = p.subtract(dir.scale(dist)).add(up.scale(dist * 0.32)).add(side.scale(dist * 0.22));
		Vec3 chaseLook = p.add(dir.scale(dist * 0.4));
		double k = SpaceScene.smooth((tf / flight - 0.72) / 0.28);
		Vec3[] iv = impactView(orbitClock());
		Vec3 pos = chasePos.add(iv[0].subtract(chasePos).scale(k));
		Vec3 look = chaseLook.add(p.subtract(chaseLook).scale(k));
		return new Vec3[] {pos, look};
	}

	private static void renderSpace(LevelRenderContext context) {
		if (phase != Phase.PRELUDE) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || camera.pos == null || camera.orientation == null) {
			return;
		}
		FxDraw d = new FxDraw(camera.pos, camera.orientation);
		MeshDraw mesh = new MeshDraw(camera.pos);
		SpaceScene.render(d, mesh, story, time(), prelude, space, camera.pos, seed);
		PoseStack pose = context.poseStack();
		mesh.flush(pose, context.submitNodeCollector());
		d.flush(pose, context.submitNodeCollector());
	}

	private static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		update();
		boolean on = phase != Phase.OFF;
		float after = fadeInStart == 0 ? 1f : (now() - fadeInStart) / 1_000_000_000f;
		if (!on && after >= 0.6f) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		float since = (now() - cinemaStart) / 1_000_000_000f;
		float bars = on ? Math.min(1f, since / 0.6f) : Math.max(0f, 1f - after / 0.6f);
		bars = 1f - (1f - bars) * (1f - bars);
		int bh = Math.round(h * 0.12f * bars);
		if (bh > 0) {
			g.fill(0, 0, w, bh, 0xFF000000);
			g.fill(0, h - bh, w, h, 0xFF000000);
		}
		Font font = Minecraft.getInstance().font;
		if (on && bh > 12) {
			Component title = Component.translatable("cinema.mymod.title." + story.name().toLowerCase());
			g.text(font, Component.literal("● ").append(title), 10, bh / 2 - 4, 0xFFFF5A4A);
			Component hint = Component.translatable("cinema.mymod.skip");
			g.text(font, hint, w - font.width(hint) - 10, bh / 2 - 4, 0xFF8A8A8A);
			Component caption = caption();
			if (caption != null) {
				g.centeredText(font, caption, w / 2, h - bh / 2 - 4, 0xFFFFFFFF);
			}
		}

		float white = 0f;
		if (phase == Phase.PRELUDE) {
			white = Math.max(0f, (time() - (prelude - 0.35f)) / 0.35f);
		}
		if (flashStart != 0) {
			white = Math.max(white, 1f - (now() - flashStart) / 600_000_000f);
		}
		if (white > 0.002f) {
			g.fill(0, 0, w, h, FxDraw.alpha(0xFFFFFFFF, Math.min(1f, white)));
		}

		float black = 0f;
		if (phase == Phase.PRELUDE || (phase == Phase.WAIT && prelude <= 0.05f)) {
			black = Math.max(0f, 1f - since / 0.5f);
		}
		if (phase == Phase.OUTRO) {
			black = Math.min(1f, time() / 0.4f);
		}
		if (!on) {
			black = Math.max(black, 1f - after / 0.4f);
		}
		if (black > 0.002f) {
			g.fill(0, 0, w, h, FxDraw.alpha(0xFF000000, Math.min(1f, black)));
		}
	}

	private static Component caption() {
		float t = time();
		String s = story.name().toLowerCase();
		switch (phase) {
			case PRELUDE -> {
				int i = Mth.clamp((int) (t / Math.max(0.1f, prelude) * 3), 0, 2);
				return Component.translatable("cinema.mymod." + s + "." + (i + 1));
			}
			case WAIT, CHASE -> {
				if (follow == null) {
					return Component.translatable("cinema.mymod.incoming");
				}
				float left = FxClient.projectileFlight(follow) - (now() - followStart) / 1_000_000_000f;
				return Component.translatable("cinema.mymod.countdown", String.format("%.1f", Math.max(0f, left)));
			}
			case IMPACT -> {
				return t < 3f ? Component.translatable("cinema.mymod.impact") : null;
			}
			default -> {
				return null;
			}
		}
	}
}
