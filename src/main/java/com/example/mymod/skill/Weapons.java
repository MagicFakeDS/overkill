package com.example.mymod.skill;

import static com.example.mymod.skill.SkillUtil.ground;
import static com.example.mymod.skill.SkillUtil.later;
import static com.example.mymod.skill.SkillUtil.sound;

import java.util.List;

import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

final class Weapons {
	private static final Skill.Range DAMAGE = new Skill.Range(0f, 5f, 1f);
	private static final Skill.Range SPEED = new Skill.Range(0.25f, 3f, 1f);

	private static final int WHITE_PAINT = 0xFFEAEAEA;
	private static final int STEEL = 0xFF8A9099;
	private static final int OLIVE = 0xFF5A6B3A;

	private Weapons() {
	}

	private static Skill w(String id, float min, float max, float def, Skill.Action action) {
		return new Skill(id, '\0', id, new Skill.Range(min, max, def), DAMAGE, SPEED, action);
	}

	static final List<Skill> ALL = List.of(

			w("icbm_nuke", 40, 140, 70, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), WHITE_PAINT, at -> Payloads.nuke(pl, at, p.radius(), p))),
			w("mirv", 10, 40, 20, (pl, t, p) ->
					Delivery.mirv(pl, t, p.speed(), 8, p.radius() * 4, WHITE_PAINT, at -> Payloads.nuke(pl, at, p.radius(), p))),
			w("icbm_ice", 40, 200, 100, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), 0xFFCDE8F8, at -> Payloads.freeze(pl, at, p.radius(), p))),
			w("icbm_hell", 40, 200, 90, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), 0xFF7A1A12, at -> Payloads.hell(pl, at, p.radius(), p))),
			w("icbm_end", 40, 200, 90, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), 0xFF3A2A50, at -> Payloads.endify(pl, at, p.radius(), p))),
			w("icbm_glass", 40, 200, 110, (pl, t, p) ->
					Delivery.icbm(pl, t, p.speed(), 0xFFF4E8C8, at -> Payloads.glass(pl, at, p.radius(), p))),

			w("cruise", 8, 40, 18, (pl, t, p) ->
					Delivery.cruise(pl, t, p.speed(), STEEL, at -> Payloads.crater(pl, at, p.radius(), p, 0.5f))),
			w("swarm", 2, 10, 5, (pl, t, p) ->
					Delivery.swarm(pl, t, p.speed(), 24, 40, at -> Payloads.crater(pl, at, p.radius(), p, 0.5f))),
			w("thermobaric", 20, 100, 45, (pl, t, p) ->
					Delivery.cruise(pl, t, p.speed(), 0xFF6A5A3A, at -> Payloads.firestorm(pl, at, p.radius(), p))),
			w("bunker_buster", 10, 60, 28, (pl, t, p) ->
					Delivery.drill(pl, t, p.speed(), 0xFFE8B820, p.radius() * 0.6,
							at -> Payloads.crater(pl, at, p.radius(), p, 1.0f))),
			w("seismic", 40, 200, 110, (pl, t, p) ->
					Delivery.drill(pl, t, p.speed(), 0xFF8A7050, 12, at -> Payloads.quake(pl, at, p.radius(), p))),

			w("aerial_bomb", 6, 40, 16, (pl, t, p) ->
					Delivery.bomb(pl, t, p.speed(), OLIVE, 5f, at -> Payloads.crater(pl, at, p.radius(), p, 0.5f))),
			w("carpet_bombing", 4, 16, 8, (pl, t, p) ->
					Delivery.carpet(pl, t, p.speed(), 16, 120, OLIVE, at -> Payloads.crater(pl, at, p.radius(), p, 0.5f))),
			w("cluster_bomb", 2, 8, 4, (pl, t, p) ->
					Delivery.bomb(pl, t, p.speed(), 0xFF4A5A6A, 5f, at -> Payloads.cluster(pl, at, 30, p, 40, p.radius()))),
			w("napalm", 10, 50, 25, (pl, t, p) ->
					Delivery.carpet(pl, t, p.speed(), 10, 100, 0xFF6A3A2A, at -> Payloads.firestorm(pl, at, p.radius() * 0.5f, p))),
			w("volcano_bomb", 20, 90, 45, (pl, t, p) ->
					Delivery.bomb(pl, t, p.speed(), 0xFF5A2018, 7f, at -> Payloads.volcano(pl, at, p.radius(), p))),
			w("gravity_bomb", 15, 80, 35, (pl, t, p) ->
					Delivery.bomb(pl, t, p.speed(), 0xFF4A2A6A, 6f, at -> Payloads.gravity(pl, at, p.radius(), p))),
			w("dirty_bomb", 30, 150, 80, (pl, t, p) ->
					Delivery.bomb(pl, t, p.speed(), 0xFFB8A020, 7f, at -> Payloads.radiation(pl, at, p.radius(), p))),

			w("plasma_column", 8, 60, 25, (pl, t, p) ->
					Delivery.satelliteBeam(pl, t, p.speed(), p.radius(), 0xFF9FE8FF, at -> Payloads.column(pl, at, p.radius(), p))),
			w("orbital_ring", 30, 160, 80, (pl, t, p) ->
					Delivery.satelliteBeam(pl, t, p.speed(), 8f, 0xFF9FE8FF, at -> Payloads.ring(pl, at, p.radius(), p))),
			w("ice_comet", 30, 150, 70, (pl, t, p) ->
					Delivery.asteroid(pl, t, p.speed(), Math.max(4f, p.radius() * 0.08f), Fx.ROCK_ICE, at -> {
						Payloads.crater(pl, at, p.radius() * 0.4f, p, 0.35f);
						Payloads.freeze(pl, at, p.radius(), p);
					})),
			w("meteor_swarm", 20, 100, 50, Weapons::meteorSwarm),
			w("moonfall", 80, 200, 150, (pl, t, p) ->
					Delivery.asteroid(pl, t, p.speed(), p.radius() * 0.12f, Fx.ROCK_MOON, at -> {
						Fx.tint(pl.level(), at, p.radius() * 30, 40f, 0x70504030);
						Payloads.nuke(pl, at, p.radius(), p);
					})),
			w("sun_spear", 20, 120, 60, Weapons::sunSpear),

			w("singularity", 20, 100, 50, (pl, t, p) ->
					Delivery.instant(pl, t, 0xFF8A3DFF, at -> Payloads.singularity(pl, at, p.radius(), p))),
			w("flattener", 50, 250, 150, (pl, t, p) ->
					Delivery.instant(pl, t, 0xFFDDF4FF, at -> Payloads.flatten(pl, at, p.radius(), p))),
			w("tectonic_rift", 80, 250, 160, (pl, t, p) ->
					Delivery.orbital(pl, t, p.speed(), at -> Payloads.quake(pl, at, p.radius(), p))),
			w("crater_field", 60, 200, 120, (pl, t, p) ->
					Delivery.mirv(pl, t, p.speed(), 20, p.radius(), 0xFF5A5A5A,
							at -> Payloads.crater(pl, at, Math.max(10f, p.radius() * 0.12f), p, 0.45f))),
			w("star_core", 40, 120, 70, (pl, t, p) ->
					Delivery.instant(pl, t, 0xFFFFE9A8, at -> Payloads.starCore(pl, at, p.radius(), p))),
			w("emp", 30, 200, 100, (pl, t, p) ->
					Delivery.cruise(pl, t, p.speed(), 0xFF2A4A7A, at -> Payloads.emp(pl, at, p.radius(), p))),
			w("doomsday", 20, 50, 30, (pl, t, p) -> {
				Fx.tint(pl.level(), t, 2000, 60f, 0x40FF2A10);
				Delivery.mirv(pl, t, p.speed(), 12, p.radius() * 6, 0xFF2A2A2A, at -> Payloads.nuke(pl, at, p.radius(), p));
			})
	);

	private static void meteorSwarm(net.minecraft.server.level.ServerPlayer player, Vec3 target,
			com.example.mymod.network.LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		sound(level, player.getEyePosition(), ModSounds.ASTEROID_ENTRY, 6f, 1.2f);
		for (int k = 0; k < 10; k++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = p.radius() * Math.sqrt(random.nextDouble());
			Vec3 pt = ground(level, target.x + Math.cos(a) * d, target.z + Math.sin(a) * d);
			double ang = random.nextDouble() * Math.PI * 2;
			Vec3 from = pt.add(Math.cos(ang) * 200, 260, Math.sin(ang) * 200);
			int fall = 50 + random.nextInt(20);
			int start = k * 6;
			later(start, () -> Fx.asteroid(level, from, pt, 3f + random.nextFloat() * 2, fall / 20f, Fx.ROCK_STONE));
			later(start + fall, () -> {
				sound(level, pt, ModSounds.SONIC_BOOM, 8f, 0.8f);
				Payloads.crater(player, pt, 14f, p, 0.4f);
			});
		}
	}

	private static void sunSpear(net.minecraft.server.level.ServerPlayer player, Vec3 target,
			com.example.mymod.network.LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 dir = flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : flat.normalize();
		Vec3 sat = target.add(0, 140, 0).subtract(dir.scale(30));
		Vec3 start = target.subtract(dir.scale(p.radius()));
		int steps = 16;
		Fx.satellite(level, sat.subtract(dir.scale(20)), sat.add(dir.scale(20)), 2.5f, 6f);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 2f, 0.9f);
		for (int k = 0; k < steps; k++) {
			Vec3 line = start.add(dir.scale(p.radius() * 2 * k / (steps - 1)));
			Vec3 pt = ground(level, line.x, line.z);
			later(20 + k * 3, () -> {
				Fx.beam(level, sat, pt, 3f, 0.5f, 0xFFFFE9A8);
				Fx.shockwave(level, pt, 10f, 0.6f, 0xFFFFD27A);
				sound(level, pt, ModSounds.PLASMA, 6f, 1.1f);
				Payloads.column(player, pt, 5f, p);
			});
		}
	}
}
