package com.example.mymod.skill;

import static com.example.mymod.skill.SkillUtil.explode;
import static com.example.mymod.skill.SkillUtil.ground;
import static com.example.mymod.skill.SkillUtil.hurtSphere;
import static com.example.mymod.skill.SkillUtil.igniteArea;
import static com.example.mymod.skill.SkillUtil.later;
import static com.example.mymod.skill.SkillUtil.particles;
import static com.example.mymod.skill.SkillUtil.pull;
import static com.example.mymod.skill.SkillUtil.push;
import static com.example.mymod.skill.SkillUtil.repeat;
import static com.example.mymod.skill.SkillUtil.sound;

import java.util.List;
import java.util.function.Consumer;

import com.example.mymod.fx.ExplosionFx;
import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.network.LaunchSkillPayload;
import com.example.mymod.world.ChunkSplitter;
import com.example.mymod.world.WorldCarver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Apocalypse {
	private static final Skill.Range DAMAGE = new Skill.Range(0f, 5f, 1f);
	private static final Skill.Range SPEED = new Skill.Range(0.25f, 3f, 1f);
	private static final int DEATH_GREEN = 0xFF60FF60;
	private static final int VOID_PURPLE = 0xFF8A3DFF;
	private static final int CUT_ORANGE = 0xFFFFB46A;
	private static final int BLACK_PAINT = 0xFF1E1E22;

	private Apocalypse() {
	}

	private static Skill w(String id, float min, float max, float def, Skill.Action action) {
		return new Skill(id, '\0', id, new Skill.Range(min, max, def), DAMAGE, SPEED, action);
	}

	static final List<Skill> ALL = List.of(

			w("hyper_tsar", 120, 400, 200, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), BLACK_PAINT, at -> hyperNuke(pl, at, p.radius(), p))),
			w("nuke_chain", 30, 100, 50, Apocalypse::nukeChain),
			w("nuke_ring", 40, 120, 60, Apocalypse::nukeRing),
			w("armageddon", 30, 80, 45, Apocalypse::armageddon),
			w("cobalt_bomb", 60, 250, 120, (pl, t, p) ->
					Delivery.bomb(pl, t, p.speed(), 0xFF2A4A8A, 9f, at -> cobalt(pl, at, p.radius(), p))),
			w("sun_drop", 60, 200, 100, Apocalypse::sunDrop),

			w("planet_killer", 150, 450, 250, (pl, t, p) ->
					Delivery.asteroid(pl, t, p.speed(), p.radius() * 0.1f, Fx.ROCK_STONE, at -> planetKiller(pl, at, p.radius(), p))),
			w("comet_storm", 60, 250, 120, Apocalypse::cometStorm),
			w("moon_shatter", 100, 300, 160, Apocalypse::moonShatter),
			w("rod_storm", 50, 200, 100, Apocalypse::rodStorm),
			w("orbital_cannon", 30, 120, 60, (pl, t, p) ->
					Delivery.satelliteBeam(pl, t, p.speed(), p.radius(), 0xFF9FE8FF, at -> orbitalCannon(pl, at, p.radius(), p))),
			w("laser_grid", 60, 250, 120, Apocalypse::laserGrid),
			w("planet_cutter", 100, 500, 250, Apocalypse::planetCutter),
			w("death_ray", 40, 150, 70, (pl, t, p) ->
					Delivery.satelliteBeam(pl, t, p.speed(), p.radius(), DEATH_GREEN, at -> deathRay(pl, at, p.radius(), p))),

			w("mega_tsunami", 50, 200, 100, (pl, t, p) ->
					Delivery.bomb(pl, t, p.speed(), 0xFF2A70C8, 8f, at -> tsunami(pl, at, p.radius(), p))),
			w("lava_ocean", 60, 250, 120, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), 0xFF8A2A12, at -> lavaOcean(pl, at, p.radius(), p))),
			w("earth_spears", 40, 200, 90, (pl, t, p) ->
					Delivery.drill(pl, t, p.speed(), 0xFF5A5048, 0, at -> earthSpears(pl, at, p.radius(), p))),
			w("ice_age", 150, 500, 250, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), 0xFFCDE8F8, at -> iceAge(pl, at, p.radius(), p))),
			w("crystal_plague", 50, 250, 120, (pl, t, p) ->
					Delivery.instant(pl, t, 0xFFB46AFF, at -> crystalPlague(pl, at, p.radius(), p))),
			w("world_crack", 200, 500, 300, (pl, t, p) ->
					Delivery.orbital(pl, t, p.speed(), at -> worldCrack(pl, at, p.radius(), p))),
			w("supervolcano", 80, 250, 130, (pl, t, p) ->
					Delivery.drill(pl, t, p.speed(), 0xFF6A2A18, 0, at -> supervolcano(pl, at, p.radius(), p))),
			w("obsidian_tomb", 30, 120, 50, (pl, t, p) ->
					Delivery.instant(pl, t, 0xFF6A3AAA, at -> obsidianTomb(pl, at, p.radius(), p))),

			w("void_bloom", 20, 70, 35, Apocalypse::voidBloom),
			w("twin_singularity", 40, 150, 70, Apocalypse::twinSingularity),
			w("gamma_burst", 200, 500, 300, (pl, t, p) ->
					Delivery.instant(pl, t, 0xFFD8FFD0, at -> gammaBurst(pl, at, p.radius(), p))),
			w("end_invasion", 100, 400, 200, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), 0xFF3A2A50, at -> endInvasion(pl, at, p.radius(), p))),
			w("hell_gate", 150, 400, 220, (pl, t, p) ->
					Delivery.orbital(pl, t, p.speed(), at -> hellGate(pl, at, p.radius(), p))),
			w("antimatter_rain", 60, 250, 120, Apocalypse::antimatterRain),
			w("big_bang", 80, 250, 130, Apocalypse::bigBang),
			w("final_judgment", 150, 500, 250, Apocalypse::finalJudgment)
	);

	private static Vec3 flatDir(ServerPlayer player) {
		Vec3 look = player.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : flat.normalize();
	}

	private static Vec3 scatter(ServerLevel level, Vec3 c, double r, RandomSource random) {
		double a = random.nextDouble() * Math.PI * 2;
		double d = r * Math.sqrt(random.nextDouble());
		return ground(level, c.x + Math.cos(a) * d, c.z + Math.sin(a) * d);
	}

	private static Vec3 onCircle(ServerLevel level, Vec3 c, double r, double angle) {
		return ground(level, c.x + Math.cos(angle) * r, c.z + Math.sin(angle) * r);
	}

	private static void warhead(ServerLevel level, Vec3 to, int ticks, int color, float scale, boolean whistle,
			Runnable impact) {
		RandomSource random = level.getRandom();
		Vec3 from = to.add(random.nextGaussian() * 20, 220, random.nextGaussian() * 20);
		Fx.missile(level, from, to, Fx.MODEL_WARHEAD, Fx.TRAJ_FALL, 0, scale, ticks / 20f, 0f, color, 0f);
		if (whistle) {
			ModSounds.incoming(level, to, ticks, 9f);
		}
		later(ticks, impact);
	}

	private static void living(ServerPlayer player, Vec3 at, double r, LaunchSkillPayload p, Consumer<LivingEntity> action) {
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
				e -> e.isAlive() && !SkillUtil.isProtected(player, e, p))) {
			if (e.position().distanceTo(at) <= r) {
				action.accept(e);
			}
		}
	}

	private static void waves(ServerLevel level, Vec3 at, float r0, float step, int count, int interval, float seconds,
			int first, int rest) {
		for (int k = 0; k < count; k++) {
			int kk = k;
			later(k * interval, () -> Fx.shockwave(level, at, r0 + step * kk, seconds + kk * 0.5f, kk == 0 ? first : rest));
		}
	}

	static void hyperNuke(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 40, 2.2f, 0xFFFFFFFF);
		Fx.tint(level, at, r * 40, 25f + r * 0.05f, 0x66FF6A20);
		Fx.fireball(level, at.add(0, r * 0.08, 0), r * 0.35f, 3f, 0xFFFFB060);
		Fx.mushroom(level, at, r * 0.42f, 30f + r * 0.05f);
		waves(level, at, r * 3f, r * 1.6f, 3, 12, 3f, 0xFFFFFFFF, 0xFFFFB070);
		Fx.shake(level, at, 8f, 8f, r * 25);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 60, 9, 0.8f);
		later(40, () -> Fx.delayedSound(level, at, ModSounds.RUMBLE, r * 60, 9, 0.7f));
		hurtSphere(player, at, r * 1.6, 300f, p);
		push(player, at.add(0, 1, 0), r * 3.5, 4.0, p);
		ExplosionFx.withoutMushroom(() -> explode(player, at, 16f, p));
		if (p.breakBlocks()) {
			WorldCarver.crater(level, at, r * 0.85f, 0.45f, 2.4f, p.fire(), true);
		}
		if (p.fire()) {
			igniteArea(level, at, r * 2, Math.round(r * 2));
		}
	}

	private static void sweepCut(ServerPlayer player, Vec3 a, Vec3 b, float width, int sweep, int color,
			LaunchSkillPayload p) {
		ServerLevel level = player.level();
		int beams = Mth.clamp((int) (a.distanceTo(b) / 8), 12, 80);
		for (int k = 0; k < beams; k++) {
			double f = k / (double) (beams - 1);
			Vec3 q = a.add(b.subtract(a).scale(f));
			Vec3 pt = ground(level, q.x, q.z);
			int delay = (int) Math.round(f * sweep);
			boolean loud = k % 4 == 0;
			later(delay, () -> {
				Fx.beam(level, pt.add(0, 220, 0), pt, width, 0.5f, color);
				Fx.shockwave(level, pt, width * 2.5f, 0.7f, color);
				if (loud) {
					Fx.fireball(level, pt, width, 0.6f, 0xFFFF9A40);
					sound(level, pt, ModSounds.PLASMA, 10f, 0.8f);
					Fx.shake(level, pt, 2f, 0.5f, 100);
				}
				hurtSphere(player, pt, width * 1.5, 1000f, p);
			});
		}
		if (p.breakBlocks()) {
			WorldCarver.trench(level, a, b, width, 0, true);
		}
	}

	private static void nukeChain(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Vec3 dir = flatDir(player);
		float r = p.radius();
		int n = 7;
		double step = r * 1.6;
		Vec3 start = target.subtract(dir.scale(step * (n - 1) / 2.0));
		int interval = Mth.clamp(Math.round(16f / p.speed()), 4, 60);
		sound(level, player.getEyePosition(), ModSounds.ROCKET_LAUNCH, 3f, 0.6f);
		for (int k = 0; k < n; k++) {
			Vec3 line = start.add(dir.scale(step * k));
			Vec3 pt = ground(level, line.x, line.z);
			later(k * interval, () -> warhead(level, pt, 40, 0xFF3A3A3A, 4.5f, true, () -> Payloads.nuke(player, pt, r, p)));
		}
	}

	private static void nukeRing(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		int n = 12;
		Fx.mark(level, target, r, 4.5f, 0xFFFF3B3B);
		sound(level, player.getEyePosition(), ModSounds.ROCKET_LAUNCH, 3f, 0.6f);
		for (int k = 0; k < n; k++) {
			Vec3 pt = onCircle(level, target, r * 2.6, Math.PI * 2 * k / n);
			warhead(level, pt, 44 + k * 2, 0xFF4A4038, 4.5f, k % 3 == 0, () -> Payloads.nuke(player, pt, r * 0.6f, p));
		}
		warhead(level, target, 90, BLACK_PAINT, 7f, true, () -> hyperNuke(player, target, r * 1.4f, p));
	}

	private static void armageddon(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		Fx.tint(level, target, 4000, 120f, 0x50FF2A10);
		Fx.delayedSound(level, target, ModSounds.SATELLITE_CHARGE, 4000, 40, 0.4f);
		for (int k = 0; k < 4; k++) {
			Vec3 c = scatter(level, target, r * 5, random);
			later(k * 30, () -> Delivery.mirv(player, c, p.speed(), 10, r * 4, 0xFF2A2A2A, at -> Payloads.nuke(player, at, r, p)));
		}
	}

	private static void cobalt(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.tint(level, at, r * 20, 60f, 0x5040A0FF);
		Payloads.radiation(player, at, r * 2f, p);
		Payloads.crater(player, at, r * 0.45f, p, 0.45f);
		waves(level, at, r * 3f, r * 1.5f, 2, 10, 3f, 0xFF7FC8FF, 0xFF4F8AFF);
		living(player, at, r * 3, p, e -> e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 2)));
	}

	private static void sunDrop(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Vec3 sky = target.add(0, 180, 0);
		int charge = Mth.clamp(Math.round(80f / p.speed()), 30, 200);
		int fall = 30;
		Fx.star(level, sky, r * 0.22f, (charge + fall) / 20f, 0xFFFFE9A8);
		Fx.tint(level, target, r * 20, (charge + fall) / 20f + 20f, 0x40FFB040);
		Fx.mark(level, target, r * 0.6f, (charge + fall) / 20f, 0xFFFFB040);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 4f, 0.4f);
		later(charge, () -> {
			Fx.meteor(level, sky, target, fall / 20f, 0xFFFFC860);
			sound(level, target, ModSounds.ASTEROID_ENTRY, 16f, 0.5f);
		});
		ModSounds.incoming(level, target, charge + fall, 16f);
		later(charge + fall, () -> {
			Fx.flash(level, target, r * 40, 2.4f, 0xFFFFF6E0);
			Fx.fireball(level, target, r * 0.9f, 3f, 0xFFFF9A30);
			Fx.mushroom(level, target, r * 0.45f, 30f);
			waves(level, target, r * 2.5f, r * 1.5f, 4, 8, 3f, 0xFFFFFFFF, 0xFFFFC060);
			Fx.shake(level, target, 8f, 7f, r * 20);
			Fx.delayedSound(level, target, ModSounds.TSAR, r * 50, 9, 0.7f);
			hurtSphere(player, target, r * 1.6, 500f, p);
			push(player, target, r * 3, 4.0, p);
			living(player, target, r * 3, p, e -> e.igniteForSeconds(20f));
			if (p.breakBlocks()) {
				WorldCarver.sphere(level, target, r * 0.55f);
				WorldCarver.crater(level, target, r * 0.95f, 0.35f, 2.2f, true, true);
				WorldCarver.transform(level, target, r * 2.4f, WorldCarver.Surface.GLASS, false);
			}
		});
	}

	private static void planetKiller(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		hyperNuke(player, at, r * 0.7f, p);
		Fx.tint(level, at, r * 40, 60f, 0x60402010);
		later(20, () -> {
			Fx.shockwave(level, at, r * 6f, 6f, 0xFFA08060);
			Fx.shake(level, at, 6f, 10f, r * 20);
			Fx.delayedSound(level, at, ModSounds.QUAKE, r * 20, 12, 0.8f);
			if (p.breakBlocks()) {
				WorldCarver.fissures(level, at, r * 2f, Math.max(40f, r * 0.3f), 12, true);
			}
		});
	}

	private static void cometStorm(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		sound(level, player.getEyePosition(), ModSounds.ASTEROID_ENTRY, 8f, 0.9f);
		Fx.tint(level, target, r * 10, 20f, 0x30A0C8FF);
		for (int k = 0; k < 24; k++) {
			Vec3 pt = scatter(level, target, r, random);
			boolean ice = random.nextFloat() < 0.4f;
			float size = 3.5f + random.nextFloat() * 4f;
			double ang = random.nextDouble() * Math.PI * 2;
			Vec3 from = pt.add(Math.cos(ang) * 220, 300, Math.sin(ang) * 220);
			int fall = 55 + random.nextInt(25);
			int start = Math.round(k * 5 / p.speed());
			later(start, () -> Fx.asteroid(level, from, pt, size, fall / 20f, ice ? Fx.ROCK_ICE : Fx.ROCK_STONE));
			if (k % 3 == 0) {
				ModSounds.incoming(level, pt, start + fall, 8f);
			}
			later(start + fall, () -> {
				sound(level, pt, ModSounds.SONIC_BOOM, 10f, 0.7f);
				Payloads.crater(player, pt, size * 4f, p, 0.45f);
				if (ice && p.breakBlocks()) {
					WorldCarver.transform(level, pt, size * 8, WorldCarver.Surface.FREEZE, false);
				}
			});
		}
	}

	private static void moonShatter(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		Fx.tint(level, target, r * 30, 40f, 0x60504030);
		sound(level, player.getEyePosition(), ModSounds.ASTEROID_ENTRY, 8f, 0.5f);
		for (int k = 0; k < 7; k++) {
			Vec3 pt = scatter(level, target, r * 1.2, random);
			float size = 9f + random.nextFloat() * 6f;
			double ang = random.nextDouble() * Math.PI * 2;
			Vec3 from = pt.add(Math.cos(ang) * 260, 320, Math.sin(ang) * 260);
			int fall = Math.round((80 + random.nextInt(40)) / p.speed());
			int start = k * 12;
			later(start, () -> Fx.asteroid(level, from, pt, size, fall / 20f, Fx.ROCK_MOON));
			ModSounds.incoming(level, pt, start + fall, 14f);
			later(start + fall, () -> {
				sound(level, pt, ModSounds.SONIC_BOOM, 16f, 0.5f);
				Payloads.nuke(player, pt, r * 0.28f, p);
			});
		}
	}

	private static void rodStorm(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 2f, 0.6f);
		Fx.mark(level, target, r, 3f, 0xFFFF3B3B);
		for (int k = 0; k < 30; k++) {
			Vec3 pt = scatter(level, target, r, random);
			int start = Math.round(k * 3 / p.speed()) + 10;
			int fall = 22;
			Vec3 from = pt.add(random.nextGaussian() * 12, 260, random.nextGaussian() * 12);
			later(start, () -> Fx.rod(level, from, pt, fall / 20f, 2f));
			later(start + fall, () -> {
				sound(level, pt, ModSounds.SONIC_BOOM, 10f, 0.9f);
				Fx.shockwave(level, pt, 14f, 0.8f, 0xFFFFFFFF);
				Fx.flash(level, pt, 120, 0.25f, 0xFFFFFFFF);
				hurtSphere(player, pt, 6, 200f, p);
				ExplosionFx.withoutMushroom(() -> explode(player, pt, 6f, p));
				if (p.breakBlocks()) {
					WorldCarver.column(level, pt, 2.5f);
					WorldCarver.crater(level, pt, 9f, 0.5f, 1.5f, false, true);
				}
			});
		}
	}

	private static void orbitalCannon(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.orbital(level, at, r, 5f, 0xFFE8FAFF);
		waves(level, at, r * 3f, r * 1.2f, 3, 10, 2.5f, 0xFFFFFFFF, 0xFF9FE8FF);
		Fx.shake(level, at, 6f, 5f, r * 12);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 30, 9, 1.3f);
		Payloads.column(player, at, r, p);
		if (p.breakBlocks()) {
			WorldCarver.transform(level, at, r * 2.5f, WorldCarver.Surface.GLASS, false);
		}
	}

	private static void laserGrid(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		int lines = 5;
		Vec3 sat = target.add(0, 160, 0);
		int sweep = Mth.clamp(Math.round(r / 5f / p.speed()), 10, 120);
		Fx.satellite(level, sat.add(-40, 0, 0), sat.add(40, 0, 0), 3f, 6f + lines * 2 * 0.4f + sweep / 20f);
		Fx.mark(level, target, r, 2f, 0xFF5AE6FF);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 2f, 0.7f);
		for (int axis = 0; axis < 2; axis++) {
			for (int i = 0; i < lines; i++) {
				double off = -r + 2.0 * r * i / (lines - 1);
				Vec3 a = axis == 0 ? target.add(off, 0, -r) : target.add(-r, 0, off);
				Vec3 b = axis == 0 ? target.add(off, 0, r) : target.add(r, 0, off);
				later(40 + (axis * lines + i) * 8, () -> sweepCut(player, a, b, 2.5f, sweep, 0xFF5AE6FF, p));
			}
		}
	}

	private static void planetCutter(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Vec3 dir = flatDir(player);
		Vec3 a = target.subtract(dir.scale(r));
		Vec3 b = target.add(dir.scale(r));
		float width = Mth.clamp(r * 0.035f, 5f, 16f);
		int charge = Mth.clamp(Math.round(50f / p.speed()), 20, 160);
		int sweep = Mth.clamp(Math.round(r / 6f / p.speed()), 30, 200);
		Fx.satellite(level, a.add(0, 200, 0).subtract(dir.scale(30)), b.add(0, 200, 0).add(dir.scale(30)), 4f,
				(charge + sweep) / 20f + 3f);
		Fx.mark(level, a, width * 2, charge / 20f, 0xFFFF6A2A);
		Fx.tint(level, target, r * 4, (charge + sweep) / 20f + 10f, 0x40FF6A20);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 3f, 0.6f);
		later(charge, () -> sweepCut(player, a, b, width, sweep, CUT_ORANGE, p));
		later(charge + sweep, () -> Fx.delayedSound(level, target, ModSounds.TSAR, r * 4, 9, 1.4f));
	}

	private static void deathRay(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.orbital(level, at, r, 4f, DEATH_GREEN);
		Fx.tint(level, at, r * 15, 20f, 0x4040FF40);
		Fx.flash(level, at, r * 20, 1.2f, 0xFFCCFFCC);
		waves(level, at, r * 3f, r, 2, 10, 2.5f, DEATH_GREEN, 0xFFCCFFCC);
		Fx.shake(level, at, 5f, 4f, r * 12);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 30, 9, 1.5f);
		hurtSphere(player, at, r * 1.2, 2000f, p);
		if (p.breakBlocks()) {
			WorldCarver.sphere(level, at, r);
			WorldCarver.transform(level, at, r * 1.8f, WorldCarver.Surface.BLIGHT, false);
		}
	}

	private static void tsunami(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 10, 0.6f, 0xFFD0F0FF);
		Fx.tint(level, at, r * 10, 15f, 0x403A90FF);
		waves(level, at, r * 1.5f, r, 5, 8, 3f, 0xFFE0F6FF, 0xFF4FA8FF);
		Fx.shake(level, at, 4f, 5f, r * 10);
		sound(level, at, SoundEvents.GENERIC_SPLASH, 16f, 0.5f);
		sound(level, at, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 16f, 0.6f);
		Fx.delayedSound(level, at, ModSounds.NUKE, r * 20, 9, 1.2f);
		repeat(0, 20, 2, i -> particles(level, ParticleTypes.SPLASH, at.add(0, 3 + i, 0), 300, r * 0.3, 1.0));
		repeat(0, 10, 3, i -> particles(level, ParticleTypes.CLOUD, at.add(0, 6 + i * 2, 0), 60, r * 0.2, 0.3));
		push(player, at.add(0, 1, 0), r * 4, 5.0, p);
		hurtSphere(player, at, r * 0.8, 60f, p);
		living(player, at, r * 3, p, e -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2)));
		if (p.breakBlocks()) {
			WorldCarver.lake(level, at, r, 0.3f);
		}
	}

	private static void lavaOcean(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 15, 1.0f, 0xFFFFB070);
		Fx.tint(level, at, r * 15, 40f, 0x60FF3A10);
		Fx.fireball(level, at.add(0, 3, 0), r * 0.5f, 2.5f, 0xFFFF6A1A);
		Fx.mushroom(level, at, r * 0.35f, 25f);
		waves(level, at, r * 3f, r, 2, 10, 3f, 0xFFFF6A2A, 0xFFFF9A50);
		Fx.shake(level, at, 5f, 5f, r * 12);
		sound(level, at, ModSounds.FIRE_ROAR, 16f, 0.5f);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 40, 9, 0.7f);
		hurtSphere(player, at, r * 0.8, 100f, p);
		living(player, at, r * 1.5, p, e -> e.igniteForSeconds(30f));
		repeat(10, 60, 2, i -> particles(level, ParticleTypes.LAVA, at.add(0, 2, 0), 30, r * 0.3, 0.8));
		if (p.breakBlocks()) {
			WorldCarver.volcano(level, at, r * 0.8f, true);
			WorldCarver.transform(level, at, r * 1.6f, WorldCarver.Surface.HELL, p.fire());
		}
	}

	private static void earthSpears(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.shake(level, at, 6f, 6f, r * 4);
		waves(level, at, r * 1.5f, r * 0.5f, 2, 8, 3f, 0xFFA08060, 0xFFFF7A3A);
		sound(level, at, ModSounds.QUAKE, 16f, 0.7f);
		Fx.delayedSound(level, at, ModSounds.QUAKE, r * 4, 12, 0.6f);
		push(player, at.add(0, -8, 0), r, 2.5, p);
		hurtSphere(player, at, r, 40f, p);
		if (p.breakBlocks()) {
			int count = Math.round(Mth.clamp(r * 0.35f, 12, 70));
			WorldCarver.spikes(level, at, r, count, 30 + r * 0.25f, 4 + r * 0.035f, WorldCarver.Spire.BASALT);
			WorldCarver.fissures(level, at, r * 0.8f, 20, 6, true);
		}
	}

	private static void iceAge(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Payloads.freeze(player, at, r, p);
		Fx.tint(level, at, r * 15, 90f, 0x507FC8FF);
		later(20, () -> {
			Fx.shockwave(level, at, r * 3f, 4f, 0xFFE0F4FF);
			sound(level, at, ModSounds.FREEZE, 16f, 0.6f);
		});
		living(player, at, r * 1.5, p, e -> {
			e.setTicksFrozen(e.getTicksRequiredToFreeze() + 900);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 5));
			e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 600, 2));
		});
		if (p.breakBlocks()) {
			int count = Math.round(Mth.clamp(r * 0.15f, 15, 70));
			later(10, () -> WorldCarver.spikes(level, at, r, count, 45, 7, WorldCarver.Spire.ICE));
		}
	}

	private static void crystalPlague(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		Fx.flash(level, at, r * 10, 0.8f, 0xFFF0D8FF);
		Fx.tint(level, at, r * 12, 30f, 0x40B46AFF);
		waves(level, at, r * 2f, -r * 0.6f, 2, 8, 3f, 0xFFB46AFF, 0xFFF0D8FF);
		Fx.shake(level, at, 3f, 4f, r * 6);
		sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 16f, 0.5f);
		repeat(0, 12, 5, i -> sound(level, scatter(level, at, r, random), SoundEvents.AMETHYST_CLUSTER_BREAK, 8f,
				0.6f + i * 0.05f));
		Fx.delayedSound(level, at, ModSounds.NUKE, r * 15, 9, 1.6f);
		living(player, at, r, p, e -> {
			e.hurtServer(level, level.damageSources().magic(), 20f * p.damage());
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 300, 3));
		});
		if (p.breakBlocks()) {
			WorldCarver.transform(level, at, r * 0.6f, WorldCarver.Surface.GLASS, false);
			int count = Math.round(Mth.clamp(r * 0.3f, 15, 70));
			WorldCarver.spikes(level, at, r, count, 22 + r * 0.12f, 3.5f, WorldCarver.Spire.CRYSTAL);
		}
	}

	private static void worldCrack(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 6, 0.6f, 0xFFFFD0A0);
		Fx.shake(level, at, 8f, 12f, r * 3);
		waves(level, at, r * 1.2f, r * 0.6f, 3, 15, 4f, 0xFFFF6A2A, 0xFFA08060);
		Fx.delayedSound(level, at, ModSounds.QUAKE, r * 3, 12, 0.6f);
		later(30, () -> Fx.delayedSound(level, at, ModSounds.TSAR, r * 3, 9, 0.6f));
		Fx.tint(level, at, r * 6, 40f, 0x40FF4A10);
		living(player, at, r * 1.5, p, e -> e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0)));
		hurtSphere(player, at, r * 0.15, 40f, p);
		if (p.breakBlocks()) {
			WorldCarver.fissures(level, at, r, Math.max(60f, r * 0.4f), 14, true);
			later(10, () -> ChunkSplitter.run(level, BlockPos.containing(at), 3, 0.3f, false));
		}
	}

	private static void supervolcano(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		Fx.flash(level, at, r * 15, 1f, 0xFFFFB070);
		Fx.tint(level, at, r * 20, 60f, 0x60301008);
		Fx.fireball(level, at.add(0, 4, 0), r * 0.4f, 3f, 0xFFFF5A10);
		Fx.mushroom(level, at, r * 0.4f, 40f);
		Fx.shockwave(level, at, r * 3f, 4f, 0xFFFF6A2A);
		Fx.shake(level, at, 7f, 10f, r * 12);
		sound(level, at, ModSounds.FIRE_ROAR, 16f, 0.4f);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 40, 9, 0.6f);
		later(40, () -> Fx.delayedSound(level, at, ModSounds.QUAKE, r * 20, 12, 0.5f));
		hurtSphere(player, at, r * 0.5, 200f, p);
		repeat(5, 80, 2, i -> particles(level, ParticleTypes.LAVA, at.add(0, 3, 0), 40, r * 0.2, 1.0));
		for (int k = 0; k < 18; k++) {
			Vec3 pt = scatter(level, at, r * 1.6, random);
			int delay = 10 + k * 4;
			int fall = 40 + random.nextInt(20);
			float size = 2f + random.nextFloat() * 2f;
			later(delay, () -> Fx.asteroid(level, at.add(0, 8, 0), pt, size, fall / 20f, Fx.ROCK_STONE));
			later(delay + fall, () -> {
				Payloads.crater(player, pt, 7f, p, 0.4f);
				if (p.fire()) {
					igniteArea(level, pt, 6, 8);
				}
			});
		}
		if (p.breakBlocks()) {
			WorldCarver.volcano(level, at, r * 0.5f, true);
			WorldCarver.transform(level, at, r * 1.8f, WorldCarver.Surface.HELL, p.fire());
		}
	}

	private static void obsidianTomb(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 6, 0.5f, 0xFFB080FF);
		Fx.shockwave(level, at, r * 1.2f, 1.5f, VOID_PURPLE);
		sound(level, at, SoundEvents.END_PORTAL_SPAWN, 12f, 0.5f);
		Fx.shake(level, at, 3f, 2f, r * 4);
		living(player, at, r, p, e -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3)));
		if (p.breakBlocks()) {
			WorldCarver.crater(level, at, r * 0.75f, 0.3f, 1.0f, p.fire(), true);
			WorldCarver.dome(level, at.add(0, -3, 0), r, 2.5f, Blocks.OBSIDIAN.defaultBlockState(),
					Blocks.CRYING_OBSIDIAN.defaultBlockState());
		}
		later(40, () -> {
			Fx.fireball(level, at.add(0, 2, 0), r * 0.8f, 2f, 0xFFFF7A2A);
			Fx.flash(level, at, r * 8, 0.6f, 0xFFFFC890);
			sound(level, at, ModSounds.FIRE_ROAR, 16f, 0.6f);
			Fx.delayedSound(level, at, ModSounds.NUKE, r * 20, 9, 0.8f);
			hurtSphere(player, at, r * 0.95, 400f, p);
			living(player, at, r, p, e -> e.igniteForSeconds(20f));
		});
		repeat(40, 30, 3, i -> particles(level, ParticleTypes.LAVA, at.add(0, 2, 0), 20, r * 0.4, 0.5));
	}

	private static void voidBloom(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Payloads.erase(player, target, r * 1.3f, p);
		for (int k = 0; k < 8; k++) {
			Vec3 pt = onCircle(level, target, r * 2.2, Math.PI * 2 * k / 8);
			later(20 + k * 6, () -> Payloads.erase(player, pt, r, p));
		}
		later(110, () -> {
			Fx.flash(level, target, r * 20, 1f, 0xFFE8DCFF);
			Fx.shockwave(level, target, r * 6f, 2.5f, 0xFFB49CFF);
			Fx.delayedSound(level, target, ModSounds.TSAR, r * 20, 9, 1.6f);
		});
	}

	private static void twinSingularity(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Vec3 dir = flatDir(player);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 c = target.add(0, r * 0.3, 0);
		Vec3 a = c.add(side.scale(r * 0.9));
		Vec3 b = c.subtract(side.scale(r * 0.9));
		Fx.blackHole(level, a, r * 0.3f, 4f, VOID_PURPLE);
		Fx.blackHole(level, b, r * 0.3f, 4f, 0xFFFF3DA8);
		Fx.beam(level, a, b, 1.5f, 4f, 0xFFE0C8FF);
		Fx.tint(level, c, r * 10, 12f, 0x50200040);
		sound(level, c, ModSounds.ANTIMATTER, 14f, 0.4f);
		Fx.shake(level, c, 3f, 4f, r * 6);
		repeat(0, 80, 1, i -> {
			pull(player, a, r * 1.5, 0.12, p);
			pull(player, b, r * 1.5, 0.12, p);
		});
		if (p.breakBlocks()) {
			later(30, () -> {
				WorldCarver.sphere(level, a, r * 0.3f);
				WorldCarver.sphere(level, b, r * 0.3f);
			});
		}
		later(80, () -> {
			Fx.flash(level, c, r * 25, 1.2f, 0xFFF0E0FF);
			Payloads.singularity(player, target, r, p);
		});
	}

	private static void gammaBurst(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.beam(level, at.add(0, 300, 0), at, 10f, 2.5f, 0xFFD8FFD0);
		Fx.flash(level, at, r * 30, 3f, 0xFFFFFFFF);
		Fx.tint(level, at, r * 20, 30f, 0x50D0FF90);
		waves(level, at, r, r * 0.7f, 3, 10, 3f, 0xFFFFFFFF, 0xFF9AFF7A);
		Fx.shake(level, at, 4f, 4f, r * 4);
		sound(level, at, ModSounds.PLASMA, 16f, 0.4f);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 10, 9, 1.8f);
		hurtSphere(player, at, r * 0.1, 500f, p);
		repeat(10, 20, 20, i -> {
			living(player, at, r, p, e -> {
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 3));
				e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 2));
				e.igniteForSeconds(3f);
			});
			sound(level, at, ModSounds.GEIGER, 4f, 1f);
		});
		if (p.breakBlocks()) {
			WorldCarver.crater(level, at, r * 0.08f, 0.5f, 1.5f, false, true);
			WorldCarver.transform(level, at, r, WorldCarver.Surface.GLASS, false);
		}
	}

	private static void endInvasion(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Payloads.endify(player, at, r, p);
		Fx.tint(level, at, r * 15, 60f, 0x50B080FF);
		Fx.shockwave(level, at, r * 3f, 4f, 0xFFC090FF);
		sound(level, at, SoundEvents.ENDER_DRAGON_GROWL, 16f, 0.6f);
		living(player, at, r, p, e -> e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 2)));
		if (p.breakBlocks()) {
			int count = Math.round(Mth.clamp(r * 0.12f, 12, 60));
			later(10, () -> WorldCarver.spikes(level, at, r, count, 55, 4.5f, WorldCarver.Spire.END));
		}
	}

	private static void hellGate(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Payloads.hell(player, at, r, p);
		Fx.tint(level, at, r * 15, 60f, 0x60FF2A10);
		Fx.shake(level, at, 6f, 8f, r * 4);
		sound(level, at, SoundEvents.WITHER_SPAWN, 16f, 0.5f);
		if (p.breakBlocks()) {
			WorldCarver.fissures(level, at, r, Math.max(40f, r * 0.25f), 10, true);
		}
		for (int k = 0; k < 6; k++) {
			Vec3 pt = onCircle(level, at, r * 0.55, Math.PI * 2 * k / 6);
			later(20 + k * 8, () -> {
				Fx.fireball(level, pt.add(0, 3, 0), 14f, 1.5f, 0xFFFF6A1A);
				Fx.mushroom(level, pt, 10f, 14f);
				sound(level, pt, ModSounds.FIRE_ROAR, 12f, 0.6f);
				if (p.breakBlocks()) {
					WorldCarver.volcano(level, pt, Math.max(12f, r * 0.08f), p.fire());
				}
			});
		}
	}

	private static void antimatterRain(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		Fx.tint(level, target, r * 10, 30f, 0x40602090);
		sound(level, player.getEyePosition(), ModSounds.ANTIMATTER, 4f, 0.5f);
		for (int k = 0; k < 24; k++) {
			Vec3 pt = scatter(level, target, r, random);
			float size = 8f + random.nextFloat() * 10f;
			int delay = Math.round(k * 4 / p.speed());
			boolean whistle = k % 4 == 0;
			later(delay, () -> warhead(level, pt, 40, VOID_PURPLE, 3.5f, whistle, () -> Payloads.erase(player, pt, size, p)));
		}
	}

	private static void bigBang(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Vec3 c = target.add(0, r * 0.25, 0);
		int charge = Mth.clamp(Math.round(100f / p.speed()), 40, 240);
		Fx.star(level, c, r * 0.12f, charge / 20f + 0.5f, 0xFFFFFFFF);
		Fx.tint(level, c, r * 40, charge / 20f, 0x70000010);
		sound(level, c, ModSounds.SATELLITE_CHARGE, 16f, 0.3f);
		later(Math.max(0, charge - 40), () -> sound(level, c, ModSounds.ANTIMATTER, 16f, 0.3f));
		repeat(0, charge, 1, i -> pull(player, c, r * 3, 0.08, p));
		later(charge, () -> {
			Fx.flash(level, c, r * 80, 3.5f, 0xFFFFFFFF);
			Fx.tint(level, c, r * 80, 60f, 0x60FFB060);
			Fx.fireball(level, c, r * 1.1f, 4f, 0xFFFFE0A0);
			Fx.antimatter(level, c, r, 3f);
			Fx.mushroom(level, target, r * 0.55f, 45f);
			waves(level, target, r * 2f, r * 2f, 5, 10, 3f, 0xFFFFFFFF, 0xFFFFB070);
			Fx.shake(level, c, 10f, 12f, r * 40);
			Fx.delayedSound(level, c, ModSounds.TSAR, r * 80, 9, 0.5f);
			later(30, () -> Fx.delayedSound(level, c, ModSounds.RUMBLE, r * 80, 9, 0.5f));
			hurtSphere(player, c, r * 2.5, 5000f, p);
			push(player, c, r * 6, 6.0, p);
			if (p.breakBlocks()) {
				WorldCarver.sphere(level, c, r);
				WorldCarver.crater(level, target, r * 1.5f, 0.35f, 2.5f, p.fire(), true);
			}
		});
	}

	private static void finalJudgment(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Vec3 dir = flatDir(player);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Fx.tint(level, target, r * 30, 90f, 0x60FF1A08);
		sound(level, player.getEyePosition(), SoundEvents.WITHER_SPAWN, 4f, 0.4f);

		for (int k = 0; k < 8; k++) {
			Vec3 pt = onCircle(level, target, r * 0.6, Math.PI * 2 * k / 8);
			later(20 + k * 6, () -> {
				Fx.orbital(level, pt, 10f, 3f, 0xFFFF6A4A);
				Fx.flash(level, pt, 300, 0.4f, 0xFFFFD0C0);
				sound(level, pt, ModSounds.PLASMA, 12f, 0.7f);
				Payloads.column(player, pt, 8f, p);
			});
		}

		float width = Mth.clamp(r * 0.03f, 5f, 12f);
		later(80, () -> {
			sweepCut(player, target.subtract(dir.scale(r)), target.add(dir.scale(r)), width, 50, CUT_ORANGE, p);
			sweepCut(player, target.subtract(side.scale(r)), target.add(side.scale(r)), width, 50, CUT_ORANGE, p);
		});

		later(150, () -> warhead(level, target, 40, BLACK_PAINT, 8f, true, () -> hyperNuke(player, target, r * 0.6f, p)));
	}
}
