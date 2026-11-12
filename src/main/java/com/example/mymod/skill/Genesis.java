package com.example.mymod.skill;

import static com.example.mymod.skill.SkillUtil.ground;
import static com.example.mymod.skill.SkillUtil.hurtSphere;
import static com.example.mymod.skill.SkillUtil.later;
import static com.example.mymod.skill.SkillUtil.sound;

import java.util.List;

import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.network.LaunchSkillPayload;
import com.example.mymod.world.WorldCarver;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Genesis {
	private static final Skill.Range DAMAGE = new Skill.Range(0f, 5f, 1f);
	private static final Skill.Range SPEED = new Skill.Range(0.25f, 3f, 1f);

	private Genesis() {
	}

	private static Skill w(String id, float min, float max, float def, Skill.Action action) {
		return new Skill(id, '\0', id, new Skill.Range(min, max, def), DAMAGE, SPEED, action);
	}

	static final List<Skill> ALL = List.of(
			w("basalt_columns", 12, 50, 25, Genesis::basaltColumns),
			w("great_wall", 20, 120, 50, Genesis::greatWall),
			w("stone_cage", 6, 30, 12, Genesis::stoneCage),
			w("spike_crown", 15, 80, 35, Genesis::spikeCrown),
			w("sky_islands", 30, 120, 60, Genesis::skyIslands),
			w("ascension", 10, 40, 18, Genesis::ascension)
	);

	private static Vec3 flatDir(ServerPlayer player) {
		Vec3 look = player.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : flat.normalize();
	}

	private static void forLiving(ServerPlayer player, Vec3 at, double r, LaunchSkillPayload p,
			java.util.function.Consumer<LivingEntity> action) {
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
				e -> e.isAlive() && !SkillUtil.isProtected(player, e, p))) {
			if (e.position().distanceTo(at) <= r) {
				action.accept(e);
			}
		}
	}

	private static void basaltColumns(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		float hex = 2.2f;
		double stepX = hex * 1.732;
		double stepZ = hex * 2.0;
		float wave = 1.4f * p.speed();
		Fx.shake(level, target, 3f, 3f + r / 10f, r * 3);
		Fx.shockwave(level, target, r * 1.3f, 2.5f, 0xFFFF7A3A);
		sound(level, target, ModSounds.QUAKE, 12f, 0.6f);
		int cols = (int) (r / stepX) + 1;
		int rows = (int) (r / stepZ) + 1;
		for (int i = -cols; i <= cols; i++) {
			for (int j = -rows; j <= rows; j++) {
				double x = target.x + i * stepX;
				double z = target.z + j * stepZ + ((i & 1) != 0 ? hex : 0);
				double d = Math.hypot(x - target.x, z - target.z);
				if (d > r) {
					continue;
				}
				float h = (float) ((6 + 22 * (1 - d / r)) * (0.7 + random.nextFloat() * 0.6));
				int delay = (int) (d / wave) + random.nextInt(3);
				WorldCarver.spike(level, x, z, hex, h, 0, 0, true, WorldCarver.Spire.BASALT, delay);
			}
		}
		forLiving(player, target, r, p, e -> later((int) (e.position().distanceTo(target) / wave) + 10, () -> {
			e.setDeltaMovement(e.getDeltaMovement().add(0, 1.3, 0));
			e.hurtMarked = true;
			e.hurtServer(level, level.damageSources().magic(), 6f * p.damage());
		}));
	}

	private static void greatWall(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		Vec3 dir = flatDir(player);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		float w = 2.2f;
		double step = w * 1.4;
		int n = (int) (r / step);
		float baseH = 16 + r * 0.08f;
		int speed = Math.max(1, Math.round(2 / p.speed()));
		Fx.shake(level, target, 3f, 4f, r * 2);
		sound(level, target, ModSounds.QUAKE, 14f, 0.5f);
		for (int k = -n; k <= n; k++) {
			Vec3 q = target.add(side.scale(k * step));
			float h = baseH + ((k & 1) == 0 ? 3 : 0) + random.nextFloat() * 1.5f;
			int delay = Math.abs(k) * speed;
			WorldCarver.spike(level, q.x, q.z, w, h, 0, 0, true, WorldCarver.Spire.STONE, delay);
			Vec3 back = q.add(dir.scale(w * 1.5));
			WorldCarver.spike(level, back.x, back.z, w, h - 4, 0, 0, true, WorldCarver.Spire.STONE, delay + 1);
		}
		forLiving(player, target, r, p, e -> {
			Vec3 rel = e.position().subtract(target);
			double along = rel.dot(side);
			double across = rel.dot(dir);
			if (Math.abs(across) < w * 3 && Math.abs(along) < r) {
				later((int) (Math.abs(along) / step) * speed + 10, () -> {
					e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(Math.signum(across + 0.01) * 1.2)).add(0, 1.0, 0));
					e.hurtMarked = true;
				});
			}
		});
	}

	private static void stoneCage(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		int n = Mth.clamp(Math.round(r * 1.3f), 8, 36);
		float h = r * 1.5f + 8;
		float base = Mth.clamp(r * 0.15f, 1.8f, 3.5f);
		sound(level, target, ModSounds.QUAKE, 10f, 0.8f);
		Fx.shockwave(level, target, r * 1.6f, 1.5f, 0xFFFFB070);
		for (int k = 0; k < n; k++) {
			double a = Math.PI * 2 * k / n;
			double x = target.x + Math.cos(a) * r;
			double z = target.z + Math.sin(a) * r;
			float lean = r * 0.92f / h;
			WorldCarver.spike(level, x, z, base, h, (float) (-Math.cos(a) * lean), (float) (-Math.sin(a) * lean), false,
					WorldCarver.Spire.STONE, k * Math.max(1, Math.round(2 / p.speed())));
		}
		forLiving(player, target, r, p, e -> {
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3));
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
		});
	}

	private static void spikeCrown(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Fx.flash(level, target, r * 8, 0.6f, 0xFFE0C8FF);
		Fx.shockwave(level, target, r * 2f, 2f, 0xFFB46AFF);
		Fx.shake(level, target, 4f, 3f, r * 4);
		sound(level, target, SoundEvents.END_PORTAL_SPAWN, 12f, 0.6f);
		Payloads.crater(player, target, r * 0.3f, p, 0.4f);
		hurtSphere(player, target, r * 0.4, 40f, p);
		for (int ring = 0; ring < 3; ring++) {
			double rr = r * (0.35 + ring * 0.32);
			int n = Mth.clamp((int) Math.round(rr * 0.5), 8, 40);
			float h = 18 + r * 0.35f * (1 - ring * 0.2f);
			float base = 2.5f + r * 0.03f;
			float lean = 0.25f + ring * 0.08f;
			for (int k = 0; k < n; k++) {
				double a = Math.PI * 2 * k / n + ring * 0.3;
				WorldCarver.spike(level, target.x + Math.cos(a) * rr, target.z + Math.sin(a) * rr, base, h,
						(float) (Math.cos(a) * lean), (float) (Math.sin(a) * lean), false, WorldCarver.Spire.OBSIDIAN,
						6 + ring * 7 + k % 3);
			}
		}
		later(8, () -> SkillUtil.push(player, target, r * 1.5, 2.5, p));
	}

	private static void skyIslands(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		Fx.tint(level, target, r * 6, 20f, 0x307FE8FF);
		for (int k = 0; k < 7; k++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = r * Math.sqrt(random.nextDouble());
			Vec3 pt = ground(level, target.x + Math.cos(a) * d, target.z + Math.sin(a) * d);
			float ir = Mth.clamp(r * 0.14f, 7f, 18f) * (0.7f + random.nextFloat() * 0.6f);
			int lift = 35 + random.nextInt(35);
			int rise = Math.round((70 + random.nextInt(30)) / p.speed());
			later(k * 15, () -> WorldCarver.island(level, pt.x, pt.z, ir, lift, 18, Math.max(20, rise)));
		}
	}

	private static void ascension(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		Fx.tint(level, target, r * 8, 12f, 0x407FE8FF);
		Fx.flash(level, target, r * 6, 0.6f, 0xFFE0FAFF);
		sound(level, target, SoundEvents.BEACON_POWER_SELECT, 12f, 0.5f);
		int rise = Math.max(30, Math.round(100f / p.speed()));
		WorldCarver.island(level, target.x, target.z, r, Math.round(45 + r), 24, rise);
	}
}
