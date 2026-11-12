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

import com.example.mymod.fx.ExplosionFx;
import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.network.LaunchSkillPayload;
import com.example.mymod.world.WorldCarver;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Payloads {
	private Payloads() {
	}

	private static boolean blocks(LaunchSkillPayload p) {
		return p.breakBlocks();
	}

	private static void affect(ServerPlayer player, Vec3 at, double r, LaunchSkillPayload p,
			java.util.function.Consumer<LivingEntity> action) {
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
				e -> e.isAlive() && !SkillUtil.isProtected(player, e, p))) {
			if (e.position().distanceTo(at) <= r) {
				action.accept(e);
			}
		}
	}

	static void nuke(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 30, 1.4f, 0xFFFFFFFF);
		Fx.tint(level, at, r * 30, 14f + r * 0.1f, 0x55FF7A30);
		Fx.mushroom(level, at, r * 0.45f, 20f + r * 0.15f);
		Fx.shake(level, at, Math.min(6f, 2f + r * 0.05f), 4f, r * 20);
		Fx.delayedSound(level, at, r >= 60 ? ModSounds.TSAR : ModSounds.NUKE, r * 40, 9, 1f);
		hurtSphere(player, at, r * 1.4, 60f, p);
		push(player, at.add(0, 1, 0), r * 3, 3.0, p);
		ExplosionFx.withoutMushroom(() -> explode(player, at, Math.min(16f, r * 0.5f), p));
		if (blocks(p)) {
			WorldCarver.crater(level, at, r * 0.85f, 0.4f, 2.0f, p.fire(), true);
		}
	}

	static void crater(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p, float depth) {
		ServerLevel level = player.level();
		if (r <= 12 || !blocks(p)) {
			ExplosionFx.withoutMushroom(() -> explode(player, at, Math.min(16f, r), p));
			return;
		}
		ExplosionFx.withoutMushroom(() -> explode(player, at, 12f, p));
		hurtSphere(player, at, r * 1.2, 40f, p);
		WorldCarver.crater(level, at, r, depth, 1.8f, p.fire(), false);
	}

	static void erase(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.antimatter(level, at, r, 3f);
		sound(level, at, ModSounds.ANTIMATTER, 10f, 1.2f);
		later(39, () -> {
			hurtSphere(player, at, r, 1000f, p);
			if (blocks(p)) {
				WorldCarver.sphere(level, at, r);
			}
		});
		later(54, () -> {
			Fx.flash(level, at, r * 15, 0.6f, 0xFFE8DCFF);
			Fx.shockwave(level, at, r * 3f, 1.4f, 0xFFB49CFF);
		});
	}

	static void firestorm(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.fireball(level, at.add(0, 2, 0), r * 0.6f, 1.6f, 0xFFFF8A2A);
		Fx.shockwave(level, at, r * 2f, 1.8f, 0xFFFF7A2A);
		Fx.flash(level, at, r * 10, 0.6f, 0xFFFFC890);
		Fx.tint(level, at, r * 8, 8f, 0x40FF5A10);
		Fx.shake(level, at, 3f, 2f, r * 8);
		sound(level, at, ModSounds.FIRE_ROAR, 12f, 0.8f);
		hurtSphere(player, at, r, 25f, p);
		affect(player, at, r * 1.5, p, e -> e.igniteForSeconds(15f));
		ExplosionFx.withoutMushroom(() -> explode(player, at, 8f, p));
		if (blocks(p)) {
			WorldCarver.crater(level, at, r * 0.25f, 0.3f, 4f, true, true);
		}
		igniteArea(level, at, r, Math.round(r * 5));
	}

	static void freeze(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 15, 1.0f, 0xFFE0F4FF);
		Fx.tint(level, at, r * 12, 14f, 0x507FC8FF);
		Fx.mushroom(level, at, r * 0.3f, 16f);
		Fx.shockwave(level, at, r * 2f, 2.2f, 0xFF9FD8FF);
		Fx.shake(level, at, 3f, 2.5f, r * 10);
		sound(level, at, ModSounds.FREEZE, 14f, 0.9f);
		Fx.delayedSound(level, at, ModSounds.NUKE, r * 30, 10, 1.4f);
		hurtSphere(player, at, r * 0.4, 30f, p);
		affect(player, at, r * 1.2, p, e -> {
			e.setTicksFrozen(e.getTicksRequiredToFreeze() + 400);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 4));
		});
		if (blocks(p)) {
			WorldCarver.crater(level, at, r * 0.15f, 0.3f, 1.2f, false, false);
			WorldCarver.transform(level, at, r, WorldCarver.Surface.FREEZE, false);
		}
	}

	static void glass(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 25, 1.5f, 0xFFFFFFFF);
		Fx.tint(level, at, r * 20, 12f, 0x40FFF4E0);
		Fx.mushroom(level, at, r * 0.35f, 22f);
		Fx.shockwave(level, at, r * 2.2f, 2.5f, 0xFFFFF0D0);
		Fx.shake(level, at, 4f, 3f, r * 12);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 40, 9, 1.1f);
		hurtSphere(player, at, r, 80f, p);
		if (blocks(p)) {
			WorldCarver.crater(level, at, r * 0.2f, 0.35f, 1.1f, false, true);
			WorldCarver.transform(level, at, r, WorldCarver.Surface.GLASS, false);
		}
	}

	static void volcano(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 12, 0.8f, 0xFFFFB070);
		Fx.tint(level, at, r * 12, 16f, 0x50FF3A10);
		Fx.mushroom(level, at, r * 0.32f, 20f);
		Fx.fireball(level, at.add(0, 3, 0), r * 0.4f, 2f, 0xFFFF6A1A);
		Fx.shake(level, at, 4f, 4f, r * 10);
		sound(level, at, ModSounds.FIRE_ROAR, 14f, 0.6f);
		Fx.delayedSound(level, at, ModSounds.NUKE, r * 30, 10, 0.9f);
		hurtSphere(player, at, r * 0.6, 40f, p);
		repeat(10, 40, 3, i -> particles(level, ParticleTypes.LAVA, at.add(0, 2, 0), 20, r * 0.15, 0.6));
		if (blocks(p)) {
			WorldCarver.volcano(level, at, r * 0.45f, true);
			WorldCarver.transform(level, at, r, WorldCarver.Surface.HELL, true);
		}
	}

	static void column(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.shake(level, at, 3f, 3f, r * 10);
		Fx.shockwave(level, at, r * 3f, 1.5f, 0xFF9FE8FF);
		affect(player, at, r, p, e -> e.hurtServer(level, level.damageSources().fellOutOfWorld(), 1000f));
		if (blocks(p)) {
			WorldCarver.column(level, at, r);
		}
	}

	static void ring(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		Fx.flash(level, at, r * 15, 0.8f, 0xFFF0F8FF);
		Fx.shake(level, at, 4f, 4f, r * 6);
		for (int k = 0; k < 10; k++) {
			double a = Math.PI * 2 * k / 10;
			Vec3 pt = ground(level, at.x + Math.cos(a) * r, at.z + Math.sin(a) * r);
			later(k * 2, () -> {
				Fx.beam(level, pt.add(0, 150, 0), pt, 2.5f, 1.2f, 0xFF9FE8FF);
				Fx.mushroom(level, pt, 5f + random.nextFloat() * 2, 14f);
				ExplosionFx.withoutMushroom(() -> explode(player, pt, 6f, p));
			});
		}
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 6, 9, 1.2f);
		if (blocks(p)) {
			later(6, () -> WorldCarver.ring(level, at, r, Math.max(8, r * 0.25f)));
		}
	}

	static void quake(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.shake(level, at, 5f, 8f, r * 2.5);
		Fx.shockwave(level, at, r * 1.6f, 4f, 0xFFA08060);
		later(10, () -> Fx.shockwave(level, at, r * 1.2f, 3f, 0xFF806040));
		Fx.delayedSound(level, at, ModSounds.QUAKE, r * 3, 12, 1f);
		affect(player, at, r * 1.5, p, e -> e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0)));
		hurtSphere(player, at, r * 0.2, 20f, p);
		if (blocks(p)) {
			WorldCarver.crater(level, at, Math.min(30f, r * 0.15f), 0.6f, 1.2f, false, true);
			WorldCarver.fissures(level, at, r, Math.max(20, r * 0.45f), 7, p.fire());
		}
	}

	static void gravity(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		Fx.blackHole(level, at.add(0, r * 0.4, 0), r * 0.3f, 4f, 0xFFB06AFF);
		Fx.shockwave(level, at, r * 1.5f, 2f, 0xFFC08AFF);
		Fx.shake(level, at, 2f, 4f, r * 6);
		sound(level, at, ModSounds.ANTIMATTER, 10f, 0.6f);
		affect(player, at, r * 1.3, p, e -> e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 120, 3)));
		if (blocks(p)) {
			repeat(5, 40, 1, i -> {
				double a = random.nextDouble() * Math.PI * 2;
				double d = r * Math.sqrt(random.nextDouble());
				ExplosionFx.flingDebris(level, ground(level, at.x + Math.cos(a) * d, at.z + Math.sin(a) * d),
						12f, 4f);
			});
			later(50, () -> WorldCarver.sphere(level, at, r * 0.3f));
		}
	}

	static void radiation(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 15, 1.0f, 0xFFE0FFD0);
		Fx.tint(level, at, r * 15, 30f, 0x4060FF40);
		Fx.mushroom(level, at, r * 0.35f, 30f);
		Fx.shockwave(level, at, r * 2f, 2.5f, 0xFF7CFF5A);
		Fx.shake(level, at, 3f, 3f, r * 10);
		Fx.delayedSound(level, at, ModSounds.NUKE, r * 30, 9, 0.9f);
		hurtSphere(player, at, r * 0.4, 40f, p);
		repeat(20, 30, 20, i -> {
			affect(player, at, r, p, e -> {
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 2));
				e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
			});
			particles(level, ParticleTypes.HAPPY_VILLAGER, at.add(0, 3, 0), 30, r * 0.3, 0.05);
			sound(level, at, ModSounds.GEIGER, 3f, 1f);
		});
		if (blocks(p)) {
			WorldCarver.crater(level, at, r * 0.15f, 0.35f, 1.2f, false, true);
			WorldCarver.transform(level, at, r, WorldCarver.Surface.BLIGHT, false);
		}
	}

	static void hell(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 12, 0.8f, 0xFFFF9A6A);
		Fx.tint(level, at, r * 12, 20f, 0x50FF2A10);
		Fx.mushroom(level, at, r * 0.3f, 20f);
		Fx.shake(level, at, 4f, 3f, r * 10);
		sound(level, at, ModSounds.FIRE_ROAR, 14f, 0.5f);
		Fx.delayedSound(level, at, ModSounds.NUKE, r * 30, 9, 0.8f);
		hurtSphere(player, at, r * 0.5, 40f, p);
		if (blocks(p)) {
			WorldCarver.crater(level, at, r * 0.2f, 0.4f, 1.2f, true, true);
			WorldCarver.transform(level, at, r, WorldCarver.Surface.HELL, true);
		}
	}

	static void endify(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 12, 0.8f, 0xFFE8D0FF);
		Fx.tint(level, at, r * 12, 16f, 0x40B080FF);
		Fx.mushroom(level, at, r * 0.3f, 18f);
		Fx.shockwave(level, at, r * 2f, 2f, 0xFFC090FF);
		Fx.shake(level, at, 3f, 3f, r * 10);
		sound(level, at, SoundEvents.END_PORTAL_SPAWN, 10f, 0.6f);
		Fx.delayedSound(level, at, ModSounds.NUKE, r * 30, 9, 1.2f);
		hurtSphere(player, at, r * 0.5, 40f, p);
		if (blocks(p)) {
			WorldCarver.crater(level, at, r * 0.2f, 0.4f, 1.2f, false, false);
			WorldCarver.transform(level, at, r, WorldCarver.Surface.END, false);
		}
	}

	static void flatten(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 6, 0.8f, 0xFFF4FAFF);
		Fx.shockwave(level, at.add(0, 2, 0), r * 1.1f, 3f, 0xFFFFFFFF);
		later(5, () -> Fx.shockwave(level, at.add(0, 2, 0), r, 2.6f, 0xFF9FE8FF));
		Fx.shake(level, at, 4f, 4f, r * 1.5);
		Fx.delayedSound(level, at, ModSounds.TSAR, r * 2, 12, 1.3f);
		affect(player, at, r, p, e -> {
			if (e.getY() > at.y + 1) {
				e.hurtServer(level, level.damageSources().explosion(null, player), 200f);
			}
		});
		if (blocks(p)) {
			WorldCarver.flatten(level, at, r);
		}
	}

	static void cluster(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p, int count, float size) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		for (int k = 0; k < count; k++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = r * Math.sqrt(random.nextDouble());
			Vec3 pt = ground(level, at.x + Math.cos(a) * d, at.z + Math.sin(a) * d);
			later(random.nextInt(12), () -> crater(player, pt, size, p, 0.45f));
		}
	}

	static void singularity(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Vec3 c = at.add(0, r * 0.2, 0);
		Fx.blackHole(level, c, r * 0.6f, 7f, 0xFF8A3DFF);
		Fx.tint(level, c, r * 10, 9f, 0x50200040);
		sound(level, c, ModSounds.ANTIMATTER, 14f, 0.5f);
		Fx.shake(level, c, 3f, 7f, r * 6);
		repeat(0, 120, 1, i -> pull(player, c, r * 2.2, 0.15, p));
		for (int k = 1; k <= 5; k++) {
			float rk = r * k / 5f;
			later(k * 22, () -> {
				hurtSphere(player, c, rk, 1000f, p);
				if (blocks(p)) {
					WorldCarver.sphere(level, c, rk);
				}
				Fx.shockwave(level, at, rk * 1.5f, 1f, 0xFF8A3DFF);
			});
		}
		later(135, () -> {
			Fx.flash(level, c, r * 15, 0.8f, 0xFFE0C8FF);
			Fx.shockwave(level, at, r * 3f, 1.6f, 0xFFB49CFF);
			push(player, c, r * 3, 3.0, p);
			Fx.delayedSound(level, c, ModSounds.TSAR, r * 30, 9, 1.4f);
		});
	}

	static void starCore(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.star(level, at.add(0, r * 0.3, 0), r * 0.3f, 2.5f, 0xFFFFE9A8);
		sound(level, at, ModSounds.SATELLITE_CHARGE, 10f, 0.4f);
		later(50, () -> {
			Fx.flash(level, at, r * 25, 1.6f, 0xFFFFF6E0);
			Fx.tint(level, at, r * 20, 16f, 0x60FF8A20);
			Fx.fireball(level, at, r * 1.2f, 2.5f, 0xFFFF8A2A);
			Fx.mushroom(level, at, r * 0.4f, 24f);
			Fx.shockwave(level, at, r * 4f, 3f, 0xFFFFC060);
			Fx.shake(level, at, 6f, 5f, r * 15);
			Fx.delayedSound(level, at, ModSounds.TSAR, r * 40, 9, 0.9f);
			hurtSphere(player, at, r * 1.5, 500f, p);
			if (blocks(p)) {
				WorldCarver.sphere(level, at, r);
				WorldCarver.crater(level, at, r * 0.95f, 0.3f, 2.2f, true, true);
			}
		});
	}

	static void emp(ServerPlayer player, Vec3 at, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Fx.flash(level, at, r * 3, 0.5f, 0xFFB8E0FF);
		for (int k = 0; k < 4; k++) {
			int kk = k;
			later(k * 4, () -> Fx.shockwave(level, at.add(0, 1 + kk * 3, 0), r * (1.2f - kk * 0.2f), 2f, 0xFF7FC8FF));
		}
		sound(level, at, ModSounds.PLASMA, 14f, 0.6f);
		affect(player, at, r, p, e -> {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3));
			e.hurtServer(level, level.damageSources().lightningBolt(), 15f * p.damage());
		});
	}
}
