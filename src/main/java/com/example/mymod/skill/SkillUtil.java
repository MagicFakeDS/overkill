package com.example.mymod.skill;

import java.util.List;
import java.util.function.IntConsumer;

import com.example.mymod.network.LaunchSkillPayload;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SkillUtil {
	static final Vec3 UP = new Vec3(0, 1, 0);

	private SkillUtil() {
	}

	static void repeat(int start, int times, int interval, IntConsumer action) {
		for (int i = 0; i < times; i++) {
			int index = i;
			SkillScheduler.schedule(start + i * interval, () -> action.accept(index));
		}
	}

	static void later(int ticks, Runnable action) {
		SkillScheduler.schedule(ticks, action);
	}

	static void explode(ServerPlayer player, Vec3 pos, float power, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
				return !isProtected(player, entity, p) && super.shouldDamageEntity(explosion, entity);
			}

			@Override
			public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
				return super.getEntityDamageAmount(explosion, entity, exposure) * p.damage();
			}

			@Override
			public float getKnockbackMultiplier(Entity entity) {
				return isProtected(player, entity, p) ? 0f : super.getKnockbackMultiplier(entity);
			}
		};
		Level.ExplosionInteraction interaction = p.breakBlocks()
				? Level.ExplosionInteraction.TNT
				: Level.ExplosionInteraction.NONE;
		level.explode(player, null, calculator, pos.x, pos.y, pos.z, power, p.fire(), interaction);
	}

	static boolean isProtected(ServerPlayer player, Entity entity, LaunchSkillPayload p) {
		return p.protectSelf() && entity.getUUID().equals(player.getUUID());
	}

	private static List<LivingEntity> living(ServerPlayer player, AABB box, LaunchSkillPayload p) {
		return player.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !isProtected(player, e, p));
	}

	static void hurtSphere(ServerPlayer player, Vec3 c, double radius, float damage, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		for (LivingEntity e : living(player, new AABB(c, c).inflate(radius + 2), p)) {
			if (e.getBoundingBox().getCenter().distanceTo(c) <= radius + e.getBbWidth() / 2) {
				e.hurtServer(level, level.damageSources().indirectMagic(player, player), damage * p.damage());
				if (p.fire()) {
					e.igniteForSeconds(4f);
				}
			}
		}
	}

	static void hurtLine(ServerPlayer player, Vec3 a, Vec3 b, double width, float damage, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		AABB box = new AABB(a, b).inflate(width + 2);
		for (LivingEntity e : living(player, box, p)) {
			Vec3 c = e.getBoundingBox().getCenter();
			if (distanceToSegment(c, a, b) <= width + e.getBbWidth() / 2) {
				e.hurtServer(level, level.damageSources().indirectMagic(player, player), damage * p.damage());
				if (p.fire()) {
					e.igniteForSeconds(3f);
				}
			}
		}
	}

	static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1e-9) {
			return point.distanceTo(a);
		}
		double t = Mth.clamp(point.subtract(a).dot(ab) / len2, 0, 1);
		return point.distanceTo(a.add(ab.scale(t)));
	}

	static void pull(ServerPlayer player, Vec3 c, double radius, double strength, LaunchSkillPayload p) {
		AABB box = new AABB(c, c).inflate(radius);
		for (Entity e : player.level().getEntities((Entity) null, box, e -> !e.isSpectator() && !isProtected(player, e, p))) {
			Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
			double dist = to.length();
			if (dist < 0.3 || dist > radius) {
				continue;
			}
			double force = strength * (1.0 - dist / radius) + 0.02;
			e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(to.scale(force / dist)));
			e.hurtMarked = true;
		}
	}

	static void push(ServerPlayer player, Vec3 c, double radius, double strength, LaunchSkillPayload p) {
		AABB box = new AABB(c, c).inflate(radius);
		for (Entity e : player.level().getEntities((Entity) null, box, e -> !e.isSpectator() && !isProtected(player, e, p))) {
			Vec3 away = e.position().subtract(c);
			double dist = away.length();
			if (dist > radius || dist < 0.01) {
				continue;
			}
			double force = strength * (1.0 - dist / radius);
			e.setDeltaMovement(e.getDeltaMovement().add(away.scale(force / dist)).add(0, force * 0.5, 0));
			e.hurtMarked = true;
		}
	}

	static void igniteArea(ServerLevel level, Vec3 c, double r, int count) {
		net.minecraft.util.RandomSource random = level.getRandom();
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = r * Math.sqrt(random.nextDouble());
			double x = c.x + Math.cos(a) * d;
			double z = c.z + Math.sin(a) * d;
			net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, surface(level, x, z), z);
			if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
				level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
			}
		}
	}

	static Vec3 ground(ServerLevel level, double x, double z) {
		return new Vec3(x, surface(level, x, z), z);
	}

	static double surface(ServerLevel level, double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
	}

	static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
		com.example.mymod.fx.ModSounds.play(level, pos, sound, volume, pitch);
	}

	static <T extends ParticleOptions> void particles(ServerLevel level, T type, Vec3 pos, int count, double spread, double speed) {
		level.sendParticles(type, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	static <T extends ParticleOptions> void particleVelocity(ServerLevel level, T type, Vec3 pos, Vec3 velocity) {
		level.sendParticles(type, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
	}
}
