package com.example.mymod.skill;

import static com.example.mymod.skill.SkillUtil.UP;
import static com.example.mymod.skill.SkillUtil.explode;
import static com.example.mymod.skill.SkillUtil.ground;
import static com.example.mymod.skill.SkillUtil.hurtLine;
import static com.example.mymod.skill.SkillUtil.hurtSphere;
import static com.example.mymod.skill.SkillUtil.igniteArea;
import static com.example.mymod.skill.SkillUtil.later;
import static com.example.mymod.skill.SkillUtil.particleVelocity;
import static com.example.mymod.skill.SkillUtil.particles;
import static com.example.mymod.skill.SkillUtil.pull;
import static com.example.mymod.skill.SkillUtil.push;
import static com.example.mymod.skill.SkillUtil.repeat;
import static com.example.mymod.skill.SkillUtil.sound;
import static com.example.mymod.skill.SkillUtil.surface;

import java.util.ArrayList;
import java.util.List;

import com.example.mymod.fx.ExplosionFx;
import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.network.LaunchSkillPayload;
import com.example.mymod.world.ChunkSplitter;
import com.example.mymod.world.WorldCarver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class Skills {
	private static final Skill.Range DAMAGE = new Skill.Range(0f, 5f, 1f);
	private static final Skill.Range SPEED = new Skill.Range(0.25f, 3f, 1f);

	private static final int FIRE = 0xFFFF9A3C;
	private static final int FIRE_LIGHT = 0xFFFFC46B;
	private static final int LASER = 0xFF3FD8FF;
	private static final int GOLD = 0xFFFFD27A;
	private static final int WARNING = 0xFFFF3B3B;
	private static final int VOID = 0xFF8A3DFF;
	private static final int STAR = 0xFFFFE9A8;
	private static final int NOVA = 0xFFFF8A2A;
	private static final int STORM = 0xFF8FC8FF;

	public static final List<Skill> ALL;

	private static final List<Skill> BASE = List.of(
			new Skill("blast", '1', "blast", new Skill.Range(1f, 12f, 4f), DAMAGE, null, Skills::blast),
			new Skill("chain", '2', "chain", new Skill.Range(1f, 8f, 3f), DAMAGE, SPEED, Skills::chain),
			new Skill("meteors", '3', "meteors", new Skill.Range(3f, 30f, 10f), DAMAGE, SPEED, Skills::meteors),
			new Skill("laser", '4', "laser", new Skill.Range(0.5f, 4f, 1.2f), DAMAGE, SPEED, Skills::laser),
			new Skill("orbital", '5', "orbital", new Skill.Range(2f, 10f, 4f), DAMAGE, SPEED, Skills::orbital),
			new Skill("black_hole", '6', "black_hole", new Skill.Range(3f, 12f, 6f), DAMAGE, SPEED, Skills::blackHole),
			new Skill("supernova", '7', "supernova", new Skill.Range(4f, 16f, 8f), DAMAGE, SPEED, Skills::supernova),
			new Skill("storm", '8', "storm", new Skill.Range(4f, 20f, 10f), DAMAGE, SPEED, Skills::storm),
			new Skill("nuke", '9', "nuke", new Skill.Range(10f, 40f, 20f), DAMAGE, SPEED, Skills::nuke),
			new Skill("satellite", '0', "satellite", new Skill.Range(6f, 40f, 16f), DAMAGE, SPEED, Skills::satelliteBarrage),
			new Skill("rod", 'Z', "rod", new Skill.Range(4f, 16f, 8f), DAMAGE, SPEED, Skills::kineticRod),
			new Skill("nuke_rain", 'X', "nuke_rain", new Skill.Range(15f, 60f, 30f), DAMAGE, SPEED, Skills::nukeRain),
			new Skill("tsar", 'C', "tsar", new Skill.Range(40f, 160f, 80f), DAMAGE, SPEED, Skills::tsar),
			new Skill("asteroid", 'V', "asteroid", new Skill.Range(30f, 120f, 60f), DAMAGE, SPEED, Skills::asteroid),
			new Skill("antimatter", 'B', "antimatter", new Skill.Range(15f, 80f, 35f), DAMAGE, null, Skills::antimatter),
			new Skill("chunk_split", 'N', "chunk_split", new Skill.Range(1f, 8f, 4f), DAMAGE, SPEED, Skills::chunkSplit)
	);

	static {
		List<Skill> all = new ArrayList<>(BASE);
		all.addAll(Weapons.ALL);
		all.addAll(Apocalypse.ALL);
		all.addAll(Genesis.ALL);
		ALL = List.copyOf(all);
	}

	private Skills() {
	}

	private static void blast(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		explode(player, target, p.radius(), p);
	}

	private static void chain(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 path = target.subtract(eye);
		double distance = path.length();
		double start = Math.min(5.0, distance);
		double step = Math.max(2.0, p.radius() * 1.5);
		int count = Mth.clamp((int) Math.ceil((distance - start) / step), 1, 40);
		int delay = Math.max(1, Math.round(4f / p.speed()));
		Vec3 dir = path.normalize();

		Vec3 prev = eye.add(dir.scale(start));
		for (int i = 0; i <= count; i++) {
			double d = start + (distance - start) * i / count;
			Vec3 pos = eye.add(dir.scale(d));
			Vec3 from = prev;
			later(i * delay, () -> {
				Fx.beam(level, from, pos, 0.18f, Math.max(0.15f, delay / 20f + 0.15f), 0xFFFF7A2E);
				explode(player, pos, p.radius(), p);
			});
			prev = pos;
		}
	}

	private static void meteors(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		int count = 6 + Math.round(p.radius() / 2f);
		int interval = Math.max(1, Math.round(5f / p.speed()));
		int fall = 16;

		double skyAngle = random.nextDouble() * Math.PI * 2;
		Vec3 skyOffset = new Vec3(Math.cos(skyAngle) * 28, 45, Math.sin(skyAngle) * 28);

		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = p.radius() * Math.sqrt(random.nextDouble());
			double x = target.x + Math.cos(angle) * r;
			double z = target.z + Math.sin(angle) * r;
			Vec3 impact = new Vec3(x, surface(level, x, z), z);
			Vec3 from = impact.add(skyOffset);
			int startAt = i * interval;

			later(startAt, () -> {
				Fx.meteor(level, from, impact, fall / 20f, 0xFFFF7A1C);
			});
			ModSounds.incoming(level, impact, startAt + fall, 5f);
			later(startAt + fall, () -> {
				explode(player, impact, 3.5f, p);
			});
		}
	}

	private static void laser(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float width = p.radius();
		Vec3 look = player.getViewVector(1f);
		Vec3 right = look.cross(UP).normalize();
		Vec3 origin = player.getEyePosition().add(look.scale(0.8)).add(right.scale(0.3)).add(0, -0.2, 0);
		int charge = 10;
		int beamTicks = Mth.clamp(Math.round(32f / p.speed()), 8, 120);

		Fx.fireball(level, origin, width * 0.7f, charge / 20f + 0.1f, LASER);
		sound(level, origin, SoundEvents.WARDEN_SONIC_CHARGE, 1.5f, 1.6f);
		repeat(0, charge / 2, 2, i -> {
			Vec3 from = origin.add((Math.random() - 0.5) * 3, (Math.random() - 0.5) * 3, (Math.random() - 0.5) * 3);
			particleVelocity(level, ParticleTypes.ELECTRIC_SPARK, from, origin.subtract(from).scale(0.25));
		});

		later(charge, () -> {
			Fx.beam(level, origin, target, width * 0.5f, beamTicks / 20f + 0.3f, LASER);
			Fx.flash(level, target, 24, 0.25f, 0xFFB8F2FF);
			Fx.shake(level, origin, 0.4f, beamTicks / 20f, 12);
			sound(level, origin, SoundEvents.WARDEN_SONIC_BOOM, 2f, 1.4f);
			sound(level, target, SoundEvents.BEACON_ACTIVATE, 3f, 1.8f);
		});
		repeat(charge, beamTicks / 2, 2, i -> {
			hurtLine(player, origin, target, width * 0.6, 3f, p);
			particles(level, ParticleTypes.ELECTRIC_SPARK, target, 14, 0.4, 0.4);
			particles(level, ParticleTypes.END_ROD, target, 4, 0.2, 0.25);
			if (i % 3 == 0) {
				explode(player, target, 1f + width * 0.8f, p);
				Fx.shockwave(level, target, width * 3.5f, 0.5f, LASER);
			}
		});

		later(charge + beamTicks, () -> {
			Fx.fireball(level, target, width * 2.2f, 0.8f, 0xFF9FF0FF);
			Fx.shockwave(level, target, width * 6f, 1.0f, LASER);
			explode(player, target, 1.5f + width * 1.5f, p);
		});
	}

	private static void orbital(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		int charge = Mth.clamp(Math.round(32f / p.speed()), 8, 120);
		int beamTicks = 40;
		Vec3 ground = target;

		Fx.mark(level, ground, r, charge / 20f + 0.05f, WARNING);
		sound(level, ground, SoundEvents.BEACON_POWER_SELECT, 4f, 0.6f);
		repeat(0, Math.max(1, charge / 8), 8, i ->
				sound(level, ground, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3f, 0.6f + i * 0.15f));

		later(charge, () -> {
			Fx.orbital(level, ground, r, beamTicks / 20f + 0.4f, GOLD);
			Fx.flash(level, ground, r * 15, 0.5f, 0xFFFFF0C0);
			Fx.shake(level, ground, 2.0f, beamTicks / 20f + 0.3f, r * 12 + 30);
			sound(level, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 6f, 0.7f);
			sound(level, ground, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.6f);
			sound(level, ground, SoundEvents.BEACON_ACTIVATE, 4f, 0.5f);
		});
		repeat(charge, beamTicks / 3, 3, i -> {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = r * Math.sqrt(random.nextDouble());
			double x = ground.x + Math.cos(angle) * d;
			double z = ground.z + Math.sin(angle) * d;
			Vec3 pt = new Vec3(x, surface(level, x, z), z);
			explode(player, pt, 2.5f + random.nextFloat(), p);
			hurtSphere(player, ground.add(0, r * 0.5, 0), r, 4f, p);
			particles(level, ParticleTypes.END_ROD, pt, 10, 0.5, 0.4);
			particles(level, ColorParticleOption.create(ParticleTypes.FLASH, GOLD), pt.add(0, 1, 0), 1, 0, 0);
			if (i % 3 == 0) {
				Fx.shockwave(level, ground, r * 3f, 0.8f, GOLD);
			}
		});

		later(charge + beamTicks, () -> {
			Fx.fireball(level, ground.add(0, 1, 0), r * 1.6f, 1.0f, GOLD);
			Fx.shockwave(level, ground, r * 5f, 1.4f, FIRE_LIGHT);
			Fx.shake(level, ground, 1.5f, 0.8f, r * 10 + 30);
			explode(player, ground, Math.min(r * 1.5f, 14f), p);
		});
	}

	private static void blackHole(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		Vec3 center = target.add(0, r * 0.5 + 1.5, 0);
		int life = Mth.clamp(Math.round(70f / p.speed()), 20, 240);
		List<FallingBlockEntity> sucked = new ArrayList<>();

		Fx.blackHole(level, center, r, life / 20f + 0.35f, VOID);
		Fx.flash(level, center, r * 6, 0.3f, 0xFFD8B8FF);
		sound(level, center, SoundEvents.PORTAL_TRIGGER, 3f, 0.5f);
		sound(level, center, SoundEvents.END_PORTAL_SPAWN, 2f, 1.5f);

		repeat(0, life, 1, tick -> {
			pull(player, center, r * 2.6, 0.12, p);

			for (int k = 0; k < 4; k++) {
				double a = random.nextDouble() * Math.PI * 2;
				double dist = r * (1.2 + random.nextDouble() * 1.4);
				Vec3 from = center.add(Math.cos(a) * dist, (random.nextDouble() - 0.5) * r, Math.sin(a) * dist);
				Vec3 to = center.subtract(from);
				Vec3 swirl = to.cross(UP).normalize().scale(0.25);
				particleVelocity(level, ParticleTypes.REVERSE_PORTAL, from, to.scale(0.08).add(swirl));
			}
			particles(level, new DustParticleOptions(0x8A3DFF, 1.6f), center, 3, r * 0.6, 0);
			if (tick % 10 == 0) {
				hurtSphere(player, center, r * 0.45, 4f, p);
			}
			if (tick % 20 == 0) {
				sound(level, center, SoundEvents.PORTAL_TRAVEL, 0.6f, 2f);
			}

			if (p.breakBlocks() && tick % 2 == 0 && sucked.size() < 80) {
				for (int k = 0; k < 2; k++) {
					BlockPos pos = BlockPos.containing(
							target.x + (random.nextDouble() - 0.5) * 2 * r,
							target.y + (random.nextDouble() - 0.8) * r,
							target.z + (random.nextDouble() - 0.5) * 2 * r);
					if (pos.getCenter().distanceTo(target) > r) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
							|| state.getDestroySpeed(level, pos) < 0) {
						continue;
					}
					FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
					block.disableDrop();
					block.setDeltaMovement(center.subtract(pos.getCenter()).normalize().scale(0.5).add(0, 0.4, 0));
					sucked.add(block);
				}
			}
			for (FallingBlockEntity block : sucked) {
				if (block.isRemoved()) {
					continue;
				}
				Vec3 to = center.subtract(block.position());
				if (to.length() < r * 0.35) {
					particles(level, ParticleTypes.PORTAL, block.position(), 8, 0.3, 0.5);
					block.discard();
				} else {
					block.setDeltaMovement(block.getDeltaMovement().scale(0.8).add(to.normalize().scale(0.25)).add(0, 0.04, 0));
					block.hurtMarked = true;
				}
			}
		});

		later(life, () -> {
			for (FallingBlockEntity block : sucked) {
				if (!block.isRemoved()) {
					block.discard();
				}
			}
			Fx.fireball(level, center, r * 1.4f, 0.8f, 0xFFE0B8FF);
			Fx.shockwave(level, BlockPos.containing(target).getBottomCenter().add(0, 1, 0), r * 4f, 1.2f, VOID);
			Fx.flash(level, center, r * 10, 0.45f, 0xFFE0B0FF);
			Fx.shake(level, center, 1.6f, 0.9f, r * 8 + 30);
			explode(player, center, Math.min(r * 1.2f, 12f), p);
			push(player, center, r * 3, 1.6, p);
			particles(level, ParticleTypes.REVERSE_PORTAL, center, 120, r * 0.4, 1.2);
			sound(level, center, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 4f, 0.5f);
		});
	}

	private static void supernova(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		Vec3 center = target.add(0, 1.5, 0);
		int charge = Mth.clamp(Math.round(30f / p.speed()), 8, 120);

		Fx.star(level, center, r * 0.45f, charge / 20f + 0.05f, STAR);
		sound(level, center, SoundEvents.BEACON_ACTIVATE, 3f, 0.6f);
		later(charge / 2, () -> sound(level, center, SoundEvents.WARDEN_SONIC_CHARGE, 3f, 0.7f));
		repeat(0, charge / 2, 2, i -> {
			for (int k = 0; k < 6; k++) {
				Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
				Vec3 from = center.add(dir.scale(r * 1.6));
				particleVelocity(level, ParticleTypes.END_ROD, from, center.subtract(from).scale(0.12));
			}
		});

		later(charge, () -> {
			Fx.flash(level, center, r * 20, 1.0f, 0xFFFFF6E0);
			Fx.fireball(level, center, r * 1.7f, 1.6f, NOVA);
			Fx.fireball(level, center, r * 0.9f, 0.9f, STAR);
			Fx.shockwave(level, target, r * 5f, 1.8f, 0xFFFFB050);
			Fx.shake(level, center, 3.0f, 1.8f, r * 14 + 40);
			sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.6f);
			explode(player, center, Math.min(r, 16f), p);
			hurtSphere(player, center, r * 1.5, 10f, p);
			push(player, center, r * 3, 2.0, p);
			particles(level, ParticleTypes.LAVA, center, 40, r * 0.4, 0.3);
		});
		later(charge + 3, () -> Fx.shockwave(level, target, r * 3f, 1.4f, 0xFFFFFFFF));

		for (int k = 0; k < 8; k++) {
			double a = Math.PI * 2 * k / 8;
			Vec3 pt = center.add(Math.cos(a) * r * 0.8, 0, Math.sin(a) * r * 0.8);
			later(charge + 2 + k, () -> explode(player, pt, r * 0.45f, p));
		}

		repeat(charge + 4, 20, 3, i -> {
			double h = i * 0.8;
			particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.add(0, h, 0), 6, r * 0.25, 0.02);
			particles(level, ParticleTypes.FLAME, center.add(0, h * 0.5, 0), 4, r * 0.3, 0.05);
			if (i > 14) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + h, center.z, 20, r * 0.9, 0.5, r * 0.9, 0.02);
			}
		});
	}

	private static void storm(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		int count = 6 + Math.round(r / 2f);
		int interval = Math.max(1, Math.round(6f / p.speed()));

		sound(level, target, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.5f);
		Fx.shockwave(level, target, r * 1.3f, 1.2f, STORM);

		repeat(4, count, interval, i -> {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = r * Math.sqrt(random.nextDouble());
			double x = target.x + Math.cos(angle) * d;
			double z = target.z + Math.sin(angle) * d;
			Vec3 pt = new Vec3(x, surface(level, x, z), z);

			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(pt.x, pt.y, pt.z);
				bolt.setCause(player);

				bolt.setVisualOnly(!p.fire());
				level.addFreshEntity(bolt);
			}
			hurtSphere(player, pt.add(0, 1, 0), 3, 6f, p);
			if (p.breakBlocks()) {
				explode(player, pt, 1.5f, p);
			}
			Fx.shockwave(level, pt, 5f, 0.6f, STORM);
			Fx.fireball(level, pt.add(0, 0.5, 0), 1.6f, 0.35f, 0xFFDDEEFF);
			Fx.flash(level, pt, 50, 0.15f, 0xFFDDEEFF);
			particles(level, ParticleTypes.ELECTRIC_SPARK, pt.add(0, 0.5, 0), 25, 1.0, 0.6);
		});
	}

	private static void nuke(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		int fall = Mth.clamp(Math.round(30f / p.speed()), 10, 80);
		Vec3 sky = target.add(6, 90, 3);

		Fx.missile(level, sky, target, Fx.MODEL_BOMB, Fx.TRAJ_FALL, Fx.FLAG_SPIN, 7f, fall / 20f, 0f, 0xFF4A5A3A, 0f);
		ModSounds.incoming(level, target, fall, 10f);
		later(fall, () -> detonateNuke(player, target, r, p));
	}

	private static void detonateNuke(ServerPlayer player, Vec3 ground, float r, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();

		Fx.flash(level, ground, r * 30, 1.4f, 0xFFFFFFFF);
		Fx.tint(level, ground, r * 30, 14f, 0x55FF7A30);
		Fx.mushroom(level, ground, r * 0.55f, 20f);
		Fx.shake(level, ground, 5f, 4f, r * 20);
		Fx.delayedSound(level, ground, ModSounds.NUKE, r * 40, 9, 1f);

		hurtSphere(player, ground, r * 1.4, 30f, p);
		push(player, ground.add(0, 1, 0), r * 3, 3.0, p);
		ExplosionFx.withoutMushroom(() -> explode(player, ground, Math.min(16f, r * 0.6f), p));

		if (p.breakBlocks()) {
			WorldCarver.crater(level, ground, r * 0.9f, 0.4f, 2.0f, p.fire(), true);
		}
		if (p.fire() && !p.breakBlocks()) {
			later(18, () -> igniteArea(level, ground, r, Math.round(r * 3)));
		}
	}

	private static void satelliteBarrage(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		int count = 8 + Math.round(r / 2f);
		int interval = Math.max(2, Math.round(5f / p.speed()));
		int warmup = 30;
		int total = warmup + count * interval + 20;

		double ang = random.nextDouble() * Math.PI * 2;
		Vec3 across = new Vec3(Math.cos(ang), 0, Math.sin(ang));
		Vec3 satFrom = target.add(across.scale(-70)).add(0, 120, 0);
		Vec3 satTo = target.add(across.scale(70)).add(0, 120, 0);

		Fx.satellite(level, satFrom, satTo, 2.2f, total / 20f);
		Fx.mark(level, target, r, warmup / 20f, 0xFF7FE0FF);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 2f, 1f);

		for (int k = 0; k < count; k++) {
			int at = warmup + k * interval;
			boolean big = k % 3 == 2;
			double a = random.nextDouble() * Math.PI * 2;
			double d = r * Math.sqrt(random.nextDouble());
			Vec3 impact = ground(level, target.x + Math.cos(a) * d, target.z + Math.sin(a) * d);
			float pitch = 0.9f + random.nextFloat() * 0.25f;
			later(at, () -> {
				Vec3 sat = satFrom.add(satTo.subtract(satFrom).scale(at / (double) total));
				Fx.beam(level, sat, impact, big ? 1.6f : 1.0f, 0.5f, 0xFF7FE0FF);
				Fx.shockwave(level, impact, big ? 10f : 6f, 0.7f, 0xFF7FE0FF);
				sound(level, impact, ModSounds.SATELLITE_FIRE, 5f, pitch);
				if (big) {
					ExplosionFx.withoutMushroom(() -> explode(player, impact, 6f, p));
					Fx.mushroom(level, impact, 4.5f, 11f);
					Fx.delayedSound(level, impact, ModSounds.NUKE, 220, 12, 1.35f);
				} else {
					explode(player, impact, 3.5f, p);
				}
			});
		}
	}

	private static void kineticRod(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		int fall = Mth.clamp(Math.round(24f / p.speed()), 8, 80);
		Vec3 from = target.add(10, 260, 5);

		Fx.rod(level, from, target, fall / 20f, 3f);
		Fx.mark(level, target, r * 0.6f, fall / 20f, WARNING);
		sound(level, player.getEyePosition(), ModSounds.SATELLITE_CHARGE, 1.5f, 0.7f);

		later(fall, () -> {
			Vec3 deep = target.add(0, -r * 0.25, 0);
			sound(level, target, ModSounds.SONIC_BOOM, 8f, 1f);
			Fx.delayedSound(level, target, ModSounds.NUKE, r * 30, 10, 1.4f);
			Fx.flash(level, target, r * 15, 0.6f, 0xFFE8F6FF);
			Fx.shake(level, target, 4f, 2.2f, r * 15);
			hurtSphere(player, target, r * 1.2, 25f, p);
			if (p.breakBlocks()) {
				ExplosionFx.flingDebris(level, target, r * 1.2f, 2.2f);
			}
			ExplosionFx.withoutMushroom(() -> explode(player, deep, Math.min(16f, r * 1.3f), p));
			Fx.mushroom(level, target, r * 0.45f, 12f);
			Fx.shockwave(level, target, r * 5f, 1.4f, 0xFFDDF4FF);
		});
	}

	private static void nukeRain(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		int count = 5 + Math.round(r / 8f);
		int interval = Math.max(4, Math.round(14f / p.speed()));
		int fall = 22;

		later(fall, () -> Fx.tint(level, target, r * 6 + 200, count * interval / 20f + 8f, 0x40FF6A20));
		for (int k = 0; k < count; k++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = r * Math.sqrt(random.nextDouble());
			Vec3 impact = ground(level, target.x + Math.cos(a) * d, target.z + Math.sin(a) * d);
			Vec3 sky = impact.add(random.nextDouble() * 20 - 10, 80, random.nextDouble() * 20 - 10);
			float size = 6f + random.nextFloat() * 2.5f;
			int start = k * interval;
			later(start, () -> {
				Fx.missile(level, sky, impact, Fx.MODEL_WARHEAD, Fx.TRAJ_FALL, 0, 4f, fall / 20f, 0f, 0xFF4A4038, 0f);
			});
			ModSounds.incoming(level, impact, start + fall, 6f);
			later(start + fall, () -> {
				Fx.flash(level, impact, 160, 0.5f, 0xFFFFF4E0);
				Fx.mushroom(level, impact, size, 15f);
				Fx.shake(level, impact, 2f, 1.5f, 120);
				Fx.delayedSound(level, impact, ModSounds.NUKE, 300, 10, 1.15f);
				ExplosionFx.withoutMushroom(() -> explode(player, impact, 7f, p));
			});
		}
	}

	private static void tsar(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		int fall = Mth.clamp(Math.round(50f / p.speed()), 16, 120);
		Vec3 sky = target.add(12, 160, 6);

		Fx.missile(level, sky, target, Fx.MODEL_BOMB, Fx.TRAJ_FALL, Fx.FLAG_SPIN, 12f, fall / 20f, 0f, 0xFF2E3238, 0f);
		ModSounds.incoming(level, target, fall, 14f);
		later(fall, () -> {
			Fx.flash(level, target, r * 40, 2.0f, 0xFFFFFFFF);
			later(8, () -> Fx.flash(level, target, r * 40, 1.5f, 0xFFFFE8C8));
			Fx.tint(level, target, r * 40, 30f, 0x70FF6A20);
			Fx.mushroom(level, target, r * 0.42f, 40f);
			Fx.shake(level, target, 6f, 8f, r * 30);
			Fx.shockwave(level, target, r * 6f, 4f, 0xFFFFD8A0);
			later(6, () -> Fx.shockwave(level, target, r * 4f, 3.5f, 0xFFFFFFFF));
			Fx.delayedSound(level, target, ModSounds.TSAR, r * 60, 8, 1f);

			hurtSphere(player, target, r * 1.8, 200f, p);
			push(player, target.add(0, 2, 0), r * 3.5, 4.0, p);
			ExplosionFx.withoutMushroom(() -> explode(player, target, 16f, p));
			if (p.breakBlocks()) {
				WorldCarver.crater(level, target, r, 0.42f, 2.2f, p.fire(), true);
			} else if (p.fire()) {
				igniteArea(level, target, r * 2, Math.round(r * 4));
			}
		});
	}

	private static void asteroid(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		float r = p.radius();
		int fall = Mth.clamp(Math.round(80f / p.speed()), 30, 200);
		double ang = random.nextDouble() * Math.PI * 2;
		Vec3 from = target.add(Math.cos(ang) * 260, 300, Math.sin(ang) * 260);
		float rock = Math.max(3f, r * 0.09f);

		Fx.asteroid(level, from, target, rock, fall / 20f);
		Fx.mark(level, target, r * 0.5f, fall / 20f, WARNING);
		sound(level, player.getEyePosition(), ModSounds.ASTEROID_ENTRY, 6f, 1f);
		later(fall / 2, () -> sound(level, target, ModSounds.ASTEROID_ENTRY, 12f, 0.8f));

		later(fall, () -> {
			Fx.flash(level, target, r * 30, 1.6f, 0xFFFFF0D8);
			Fx.tint(level, target, r * 30, 35f, 0x70503018);
			Fx.mushroom(level, target, r * 0.38f, 38f);
			Fx.shake(level, target, 6f, 7f, r * 25);
			Fx.shockwave(level, target, r * 5f, 3.5f, 0xFFFFC880);
			sound(level, target, ModSounds.SONIC_BOOM, 12f, 0.6f);
			Fx.delayedSound(level, target, ModSounds.TSAR, r * 50, 9, 0.85f);
			hurtSphere(player, target, r * 1.5, 150f, p);
			push(player, target.add(0, 2, 0), r * 3, 3.5, p);
			ExplosionFx.withoutMushroom(() -> explode(player, target, 16f, p));
			if (p.breakBlocks()) {
				repeat(1, 10, 1, i -> ExplosionFx.flingDebris(level, target, Math.min(40f, r * 0.6f), 2.5f));
				WorldCarver.crater(level, target, r, 0.32f, 2.4f, p.fire(), true);
			}
		});
	}

	private static void antimatter(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		ServerLevel level = player.level();
		float r = p.radius();
		float seconds = 4.5f;
		int full = Math.round(seconds * 0.65f * 20);
		int implode = Math.round(seconds * 0.9f * 20);

		Fx.antimatter(level, target, r, seconds);
		sound(level, target, ModSounds.ANTIMATTER, 10f, 1f);
		repeat(0, full / 4, 4, i -> {
			Vec3 dir = new Vec3(level.getRandom().nextGaussian(), level.getRandom().nextGaussian(),
					level.getRandom().nextGaussian()).normalize();
			particleVelocity(level, ParticleTypes.END_ROD, target.add(dir.scale(r * 1.4)), dir.scale(-0.8));
		});
		later(full, () -> {
			hurtSphere(player, target, r, 1000f, p);
			if (p.breakBlocks()) {
				WorldCarver.sphere(level, target, r);
			}
		});
		later(implode, () -> {
			Fx.flash(level, target, r * 20, 0.9f, 0xFFE8DCFF);
			Fx.shockwave(level, target, r * 3f, 1.6f, 0xFFB49CFF);
			Fx.shake(level, target, 4f, 2f, r * 12);
			pull(player, target, r * 2.5, 1.2, p);
			later(6, () -> push(player, target, r * 3, 2.5, p));
			Fx.delayedSound(level, target, ModSounds.NUKE, r * 30, 10, 1.6f);
		});
	}

	private static void chunkSplit(ServerPlayer player, Vec3 target, LaunchSkillPayload p) {
		int chunks = Math.max(1, Math.round(p.radius()));
		float fall = Mth.clamp(0.15f * p.speed(), 0.03f, 0.5f);
		ChunkSplitter.run(player.level(), BlockPos.containing(target), chunks, fall, false);
	}
}
