package com.example.mymod.skill;

import static com.example.mymod.skill.SkillUtil.ground;
import static com.example.mymod.skill.SkillUtil.later;
import static com.example.mymod.skill.SkillUtil.sound;

import java.util.function.Consumer;

import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

final class Delivery {
	private Delivery() {
	}

	static void icbm(ServerPlayer player, Vec3 target, float speed, int paint, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		double ang = random.nextDouble() * Math.PI * 2;
		Vec3 from = new Vec3(target.x + Math.cos(ang) * 380, target.y, target.z + Math.sin(ang) * 380);
		int flight = Mth.clamp(Math.round(140f / speed), 50, 400);
		Fx.missile(level, from, target, Fx.MODEL_ICBM, Fx.TRAJ_ARC, Fx.FLAG_ENGINE, 16f, flight / 20f, 6f, paint, 260f);
		sound(level, player.getEyePosition(), ModSounds.ROCKET_LAUNCH, 3f, 0.7f);
		ModSounds.incoming(level, target, flight, 12f);
		later(flight, () -> impact.accept(target));
	}

	static void mirv(ServerPlayer player, Vec3 target, float speed, int count, float spread, int paint,
			Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		double ang = random.nextDouble() * Math.PI * 2;
		Vec3 from = new Vec3(target.x + Math.cos(ang) * 380, target.y, target.z + Math.sin(ang) * 380);
		Vec3 split = target.add(0, 200, 0);
		int flight = Mth.clamp(Math.round(110f / speed), 40, 300);
		Fx.missile(level, from, split, Fx.MODEL_ICBM, Fx.TRAJ_ARC, Fx.FLAG_ENGINE, 16f, flight / 20f, 6f, paint, 140f);
		sound(level, player.getEyePosition(), ModSounds.ROCKET_LAUNCH, 3f, 0.7f);
		later(flight, () -> {
			Fx.flash(level, split, 300, 0.25f, 0xFFFFF0D0);
			Fx.fireball(level, split, 6f, 0.8f, 0xFFFFB060);
			sound(level, split, ModSounds.SONIC_BOOM, 16f, 1.2f);
			for (int k = 0; k < count; k++) {
				double a = random.nextDouble() * Math.PI * 2;
				double d = spread * Math.sqrt(random.nextDouble());
				Vec3 pt = ground(level, target.x + Math.cos(a) * d, target.z + Math.sin(a) * d);
				int fall = 36 + random.nextInt(14);
				int delay = k * 2;
				later(delay, () -> Fx.missile(level, split, pt, Fx.MODEL_WARHEAD, Fx.TRAJ_FALL, 0, 4f, fall / 20f, 0f,
						0xFF4A4038, 0f));
				ModSounds.incoming(level, pt, delay + fall, 8f);
				later(delay + fall, () -> impact.accept(pt));
			}
		});
	}

	static void cruise(ServerPlayer player, Vec3 target, float speed, int paint, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		Vec3 from = player.getEyePosition().add(player.getViewVector(1f).scale(3)).add(0, 1, 0);
		double dist = from.distanceTo(target);
		int flight = Mth.clamp((int) Math.round(dist / (30 * speed) * 20), 10, 300);
		Fx.missile(level, from, target, Fx.MODEL_CRUISE, Fx.TRAJ_ARC, Fx.FLAG_ENGINE, 7f, flight / 20f, 3f, paint,
				(float) Math.min(25, dist * 0.08));
		sound(level, from, ModSounds.ROCKET_LAUNCH, 5f, 1.1f);
		ModSounds.incoming(level, target, flight, 8f);
		later(flight, () -> impact.accept(target));
	}

	static void swarm(ServerPlayer player, Vec3 target, float speed, int count, float spread, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		for (int k = 0; k < count; k++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = spread * Math.sqrt(random.nextDouble());
			Vec3 pt = ground(level, target.x + Math.cos(a) * d, target.z + Math.sin(a) * d);
			Vec3 offset = new Vec3(random.nextGaussian(), 1 + random.nextDouble(), random.nextGaussian());
			float apex = 6 + random.nextFloat() * 18;
			later(k * 2, () -> {
				Vec3 from = player.getEyePosition().add(offset);
				int flight = Mth.clamp((int) Math.round(from.distanceTo(pt) / (40 * speed) * 20), 8, 200);
				Fx.missile(level, from, pt, Fx.MODEL_ROCKET, Fx.TRAJ_ARC, Fx.FLAG_ENGINE, 2.4f, flight / 20f, 1.5f,
						0xFFB0B4BA, apex);
				sound(level, from, ModSounds.ROCKET_LAUNCH, 1.5f, 1.6f + random.nextFloat() * 0.3f);
				later(flight, () -> impact.accept(pt));
			});
		}
	}

	static void bomb(ServerPlayer player, Vec3 target, float speed, int paint, float scale, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		Vec3 from = target.add(25, 170, 8);
		int flight = Mth.clamp(Math.round(70f / speed), 25, 200);
		Fx.missile(level, from, target, Fx.MODEL_BOMB, Fx.TRAJ_FALL, Fx.FLAG_SPIN, scale, flight / 20f, 0f, paint, 0f);
		ModSounds.incoming(level, target, flight, 10f);
		later(flight, () -> impact.accept(target));
	}

	static void carpet(ServerPlayer player, Vec3 target, float speed, int count, float length, int paint,
			Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 dir = flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : flat.normalize();
		Vec3 start = target.subtract(dir.scale(length / 2));
		int interval = Math.max(1, Math.round(4f / speed));
		for (int k = 0; k < count; k++) {
			Vec3 line = start.add(dir.scale(length * k / Math.max(1, count - 1)));
			Vec3 pt = ground(level, line.x, line.z);
			int delay = k * interval;
			later(delay, () -> {
				Fx.missile(level, pt.add(0, 140, 0).subtract(dir.scale(20)), pt, Fx.MODEL_BOMB, Fx.TRAJ_FALL, Fx.FLAG_SPIN,
						4.5f, 2f, 0f, paint, 0f);
			});
			ModSounds.incoming(level, pt, delay + 40, 5f);
			later(delay + 40, () -> impact.accept(pt));
		}
	}

	static void drill(ServerPlayer player, Vec3 target, float speed, int paint, double depth, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		Vec3 from = target.add(6, 140, 3);
		int flight = Mth.clamp(Math.round(60f / speed), 20, 200);
		Fx.missile(level, from, target, Fx.MODEL_DRILL, Fx.TRAJ_FALL, Fx.FLAG_ENGINE | Fx.FLAG_SPIN, 6f, flight / 20f, 2f,
				paint, 0f);
		ModSounds.incoming(level, target, flight, 8f);
		later(flight, () -> {
			sound(level, target, ModSounds.QUAKE, 6f, 1.4f);
			Fx.shockwave(level, target, 8f, 0.8f, 0xFF8A7050);
			Fx.shake(level, target, 1.5f, 1f, 80);
		});
		later(flight + 15, () -> impact.accept(target.add(0, -depth, 0)));
	}

	static void orbital(ServerPlayer player, Vec3 target, float speed, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		int fall = Mth.clamp(Math.round(30f / speed), 10, 100);
		Fx.rod(level, target.add(10, 260, 5), target, fall / 20f, 3f);
		Fx.mark(level, target, 12f, fall / 20f, 0xFFFF3B3B);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 1.5f, 0.7f);
		ModSounds.incoming(level, target, fall, 12f);
		later(fall, () -> {
			sound(level, target, ModSounds.SONIC_BOOM, 12f, 0.8f);
			impact.accept(target);
		});
	}

	static void satelliteBeam(ServerPlayer player, Vec3 target, float speed, float beam, int color, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		double ang = random.nextDouble() * Math.PI * 2;
		Vec3 across = new Vec3(Math.cos(ang), 0, Math.sin(ang));
		int charge = Mth.clamp(Math.round(50f / speed), 20, 160);
		Fx.satellite(level, target.add(across.scale(-40)).add(0, 130, 0), target.add(across.scale(40)).add(0, 130, 0),
				2.5f, charge / 20f + 4f);
		Fx.mark(level, target, beam, charge / 20f, color);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 2f, 0.8f);
		later(charge, () -> {
			Fx.orbital(level, target, beam, 3f, color);
			Fx.flash(level, target, beam * 12, 0.5f, 0xFFF0F8FF);
			sound(level, target, ModSounds.PLASMA, 10f, 0.8f);
			sound(level, target, ModSounds.SATELLITE_FIRE, 10f, 0.6f);
			impact.accept(target);
		});
	}

	static void asteroid(ServerPlayer player, Vec3 target, float speed, float rock, int variant, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		int fall = Mth.clamp(Math.round(90f / speed), 30, 260);
		double ang = random.nextDouble() * Math.PI * 2;
		Vec3 from = target.add(Math.cos(ang) * 280, 320, Math.sin(ang) * 280);
		Fx.asteroid(level, from, target, rock, fall / 20f, variant);
		Fx.mark(level, target, Math.max(8, rock * 3), fall / 20f, 0xFFFF3B3B);
		sound(level, player.getEyePosition(), ModSounds.ASTEROID_ENTRY, 6f, variant == Fx.ROCK_MOON ? 0.6f : 1f);
		later(fall / 2, () -> sound(level, target, ModSounds.ASTEROID_ENTRY, 14f, 0.7f));
		ModSounds.incoming(level, target, fall, 16f);
		later(fall, () -> {
			sound(level, target, ModSounds.SONIC_BOOM, 16f, 0.5f);
			impact.accept(target);
		});
	}

	static void instant(ServerPlayer player, Vec3 target, int color, Consumer<Vec3> impact) {
		ServerLevel level = player.level();
		Fx.star(level, target.add(0, 2, 0), 3f, 1.2f, color);
		sound(level, target, ModSounds.SATELLITE_CHARGE, 6f, 0.5f);
		later(24, () -> impact.accept(target));
	}
}
