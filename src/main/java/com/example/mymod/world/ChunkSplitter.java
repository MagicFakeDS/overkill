package com.example.mymod.world;

import java.util.HashSet;
import java.util.Set;

import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.skill.SkillScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class ChunkSplitter {
	private static final int COLOR = 0xFF4FE0FF;
	private static final Set<BlockPos> ACTIVE = new HashSet<>();

	private ChunkSplitter() {
	}

	public static void activateCube(ServerLevel level, BlockPos pos) {
		if (!ACTIVE.add(pos.immutable())) {
			return;
		}
		run(level, pos, 4, 0.22f, true);
	}

	public static void run(ServerLevel level, BlockPos center, int chunkRadius, float fallChance, boolean cube) {
		RandomSource random = level.getRandom();
		int seed = random.nextInt();
		int charge = 60;
		Vec3 c = Vec3.atCenterOf(center);
		float cutSeconds = 2f + chunkRadius * 1.2f;

		Fx.chunkGrid(level, c, chunkRadius, charge / 20f + cutSeconds + 5f, COLOR, seed, fallChance, charge / 20f);
		Fx.star(level, c, 1.4f, charge / 20f, COLOR);
		ModSounds.play(level, c, ModSounds.SATELLITE_CHARGE, 4f, 0.6f);

		BlockPos key = center.immutable();
		SkillScheduler.schedule(charge, () -> {
			if (cube) {
				level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
			}
			Fx.flash(level, c, 160, 0.5f, 0xFFB0F4FF);
			Fx.shake(level, c, 2.5f, cutSeconds, 220);
			Fx.shockwave(level, c, chunkRadius * 20f, 1.6f, COLOR);
			ModSounds.play(level, c, ModSounds.CHUNK_CUT, 10f, 1f);

			WorldCarver.splitChunks(level, center, chunkRadius, fallChance, seed,
					packed -> dropChunkTop(level, (int) (packed >> 32), (int) packed, random));
			SkillScheduler.schedule(40, () -> ACTIVE.remove(key));
		});
	}

	private static void dropChunkTop(ServerLevel level, int cx, int cz, RandomSource random) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx * 16 + 8, cz * 16 + 8);
		Vec3 mid = new Vec3(cx * 16 + 8, top, cz * 16 + 8);
		Fx.chunkFall(level, cx, cz, top);
		ModSounds.play(level, mid, ModSounds.CHUNK_COLLAPSE, 6f, 0.8f + random.nextFloat() * 0.3f);
		Fx.shockwave(level, mid, 16f, 1.4f, 0xFFFF6A4A);
	}
}
