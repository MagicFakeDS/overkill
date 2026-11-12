package com.example.mymod.fx;

import com.example.mymod.MyMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ExplosionFx {
	public static final WeightedList<ExplosionParticleInfo> NO_BLOCK_PARTICLES =
			WeightedList.of(new ExplosionParticleInfo(ModParticles.NONE, 1f, 1f));

	private static final int DEBRIS_PER_TICK = 40;
	private static long debrisTick = -1;
	private static int debrisLeft;

	private static int noMushroom;

	private ExplosionFx() {
	}

	public static void withoutMushroom(Runnable action) {
		noMushroom++;
		try {
			action.run();
		} finally {
			noMushroom--;
		}
	}

	public static void onExplode(ServerLevel level, Vec3 pos, float power, Level.ExplosionInteraction interaction) {
		try {
			Fx.explosion(level, pos, power);
			ModSounds.playLayers(level, pos, ModSounds.forPower(power), 4f, 0.9f + level.getRandom().nextFloat() * 0.2f);
			Fx.shake(level, pos, Math.min(4f, 0.25f + power * 0.22f), 0.5f + power * 0.08f, power * 10 + 16);
			if (power >= 5f) {
				Fx.flash(level, pos, power * 8, 0.25f + power * 0.03f, 0xFFFFE6C0);
			}
			if (power >= 3f) {
				Fx.delayedSound(level, pos, ModSounds.EXPLOSION_FAR, 40, power * 40 + 60, 14,
						0.9f + level.getRandom().nextFloat() * 0.2f);
			}
			if (power >= 7f) {
				level.playSound(null, pos.x, pos.y, pos.z, ModSounds.RUMBLE, SoundSource.BLOCKS,
						Math.min(16f, power * 1.2f), 0.85f + level.getRandom().nextFloat() * 0.2f);
			}

			if (power >= 12f && noMushroom == 0) {
				double ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
						net.minecraft.util.Mth.floor(pos.x), net.minecraft.util.Mth.floor(pos.z));
				Vec3 base = ground <= pos.y + 2 && ground >= pos.y - power ? new Vec3(pos.x, ground, pos.z) : pos;
				Fx.mushroom(level, base, power * 0.55f, 14f);
				Fx.tint(level, base, power * 20, 6f, 0x40FF7A30);
				Fx.delayedSound(level, base, ModSounds.NUKE, power * 30, 10, 1.1f);
			}
			if (interaction == Level.ExplosionInteraction.TNT || interaction == Level.ExplosionInteraction.BLOCK) {
				flingDebris(level, pos, power, 1f);
			}
		} catch (RuntimeException e) {
			MyMod.LOGGER.error("Ошибка в эффекте взрыва", e);
		}
	}

	public static void flingDebris(ServerLevel level, Vec3 center, float power, float upBoost) {
		if (power < 2.5f) {
			return;
		}
		long tick = level.getGameTime();
		if (tick != debrisTick) {
			debrisTick = tick;
			debrisLeft = DEBRIS_PER_TICK;
		}
		RandomSource random = level.getRandom();
		int wanted = Math.min(Math.min((int) (36 * upBoost), (int) (power * power * 0.6f * upBoost)), debrisLeft);
		double r = power * 0.75;
		double speed = Math.sqrt(power) * 0.32;
		int made = 0;
		for (int attempt = 0; attempt < wanted * 4 && made < wanted; attempt++) {
			Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6 - 0.3, random.nextGaussian())
					.normalize().scale(r * Math.cbrt(random.nextDouble()));
			BlockPos pos = BlockPos.containing(center.add(offset));
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
					|| state.getDestroySpeed(level, pos) < 0 || state.getBlock().getExplosionResistance() > 20f) {
				continue;
			}

			if (!level.getBlockState(pos.above()).isAir()) {
				continue;
			}
			FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
			block.disableDrop();
			Vec3 out = pos.getCenter().subtract(center);
			Vec3 horizontal = new Vec3(out.x, 0, out.z);
			if (horizontal.lengthSqr() < 1e-4) {
				horizontal = new Vec3(random.nextGaussian(), 0, random.nextGaussian());
			}
			horizontal = horizontal.normalize();
			double s = speed * (0.6 + random.nextDouble() * 0.8);
			block.setDeltaMovement(horizontal.scale(s / upBoost).add(0, s * upBoost * (1.1 + random.nextDouble() * 0.9), 0));
			block.hurtMarked = true;
			made++;
		}
		debrisLeft -= made;
	}
}
