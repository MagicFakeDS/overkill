package com.example.mymod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class SpikeShape {
	private static final int EMBED = 4;

	public final double x;
	public final double z;
	public final int gy;
	public final float base;
	public final float height;
	public final float leanX;
	public final float leanZ;
	public final boolean pillar;
	public final WorldCarver.Spire spire;
	public final int minX;
	public final int minZ;
	public final int sizeX;
	public final int sizeZ;
	public final int[] lo;
	public final int[] hi;

	public int top = Integer.MIN_VALUE;
	public int bottom = Integer.MAX_VALUE;

	private SpikeShape(double x, double z, int gy, float base, float height, float leanX, float leanZ, boolean pillar,
			WorldCarver.Spire spire, int minX, int minZ, int sizeX, int sizeZ) {
		this.x = x;
		this.z = z;
		this.gy = gy;
		this.base = base;
		this.height = height;
		this.leanX = leanX;
		this.leanZ = leanZ;
		this.pillar = pillar;
		this.spire = spire;
		this.minX = minX;
		this.minZ = minZ;
		this.sizeX = sizeX;
		this.sizeZ = sizeZ;
		this.lo = new int[sizeX * sizeZ];
		this.hi = new int[sizeX * sizeZ];
	}

	public static SpikeShape build(LevelReader level, double x, double z, int gy, float base, float height,
			float leanX, float leanZ, boolean pillar, WorldCarver.Spire spire) {
		double tipX = x + leanX * height;
		double tipZ = z + leanZ * height;
		int minX = Mth.floor(Math.min(x, tipX) - base) - 1;
		int maxX = Mth.floor(Math.max(x, tipX) + base) + 1;
		int minZ = Mth.floor(Math.min(z, tipZ) - base) - 1;
		int maxZ = Mth.floor(Math.max(z, tipZ) + base) + 1;
		SpikeShape s = new SpikeShape(x, z, gy, base, height, leanX, leanZ, pillar, spire, minX, minZ,
				maxX - minX + 1, maxZ - minZ + 1);
		int h = Math.max(1, Math.round(height));
		for (int ix = 0; ix < s.sizeX; ix++) {
			for (int iz = 0; iz < s.sizeZ; iz++) {
				int i = ix * s.sizeZ + iz;
				double px = minX + ix + 0.5;
				double pz = minZ + iz + 0.5;
				int klo = Integer.MAX_VALUE;
				int khi = Integer.MIN_VALUE;
				for (int k = -EMBED; k <= h; k++) {
					double kk = Math.max(0, k);
					double r = pillar ? base : k <= 0 ? base : base * Math.pow(1 - k / (double) (h + 1), 0.62);
					if (inside(px - (x + leanX * kk), pz - (z + leanZ * kk), r, pillar)) {
						klo = Math.min(klo, k);
						khi = Math.max(khi, k);
					}
				}
				if (klo > khi) {
					s.lo[i] = 1;
					s.hi[i] = 0;
					continue;
				}
				int ylo = gy + klo;
				int yhi = gy + khi;
				if (klo <= 0) {
					int g = ground(level, minX + ix, minZ + iz);
					if (g < ylo) {
						ylo = Math.max(g, gy - 24);
					}
				}
				s.lo[i] = ylo;
				s.hi[i] = yhi;
				s.top = Math.max(s.top, yhi);
				s.bottom = Math.min(s.bottom, ylo);
			}
		}
		return s;
	}

	private static boolean inside(double dx, double dz, double r, boolean hex) {
		if (hex) {
			double ax = Math.abs(dx);
			double az = Math.abs(dz);
			return Math.max(ax * 0.8660254 + az * 0.5, az) <= r;
		}
		return dx * dx + dz * dz <= r * r;
	}

	public int index(int bx, int bz) {
		int ix = bx - minX;
		int iz = bz - minZ;
		if (ix < 0 || iz < 0 || ix >= sizeX || iz >= sizeZ) {
			return -1;
		}
		return ix * sizeZ + iz;
	}

	public BlockState block(int bx, int y, int bz) {
		int k = y - gy;
		int h = Math.round(height);
		float rnd = hash(bx, y, bz);
		boolean tip = !pillar && k >= h - 2;
		int i = index(bx, bz);
		boolean cap = pillar && i >= 0 && y == hi[i];
		return switch (spire) {
			case BASALT -> tip ? Blocks.MAGMA_BLOCK.defaultBlockState()
					: cap ? Blocks.POLISHED_BASALT.defaultBlockState()
					: k < h * 0.3 && !pillar ? Blocks.BLACKSTONE.defaultBlockState()
					: rnd < 0.6 ? Blocks.BASALT.defaultBlockState()
					: rnd < 0.88 ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
			case ICE -> tip || cap ? Blocks.SNOW_BLOCK.defaultBlockState()
					: rnd < 0.35 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
			case CRYSTAL -> k < 2 ? Blocks.CALCITE.defaultBlockState()
					: rnd < 0.12 ? Blocks.PURPLE_STAINED_GLASS.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState();
			case OBSIDIAN -> rnd < 0.2 || tip ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
			case END -> tip ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
					: k < h * 0.4 ? Blocks.END_STONE.defaultBlockState()
					: rnd < 0.5 ? Blocks.PURPUR_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
			case STONE -> cap ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
					: tip ? Blocks.TUFF.defaultBlockState()
					: rnd < 0.45 ? Blocks.STONE.defaultBlockState()
					: rnd < 0.7 ? Blocks.ANDESITE.defaultBlockState()
					: rnd < 0.88 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
		};
	}

	public int riseTicks() {
		return Mth.clamp(Math.round(8 + height * 0.25f), 8, 30);
	}

	public static int ground(LevelReader level, int bx, int bz) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
		int min = level.getMinY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 48 && y > min; i++) {
			BlockState st = level.getBlockState(pos.set(bx, y, bz));
			if (st.isAir() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.REPLACEABLE)
					|| !st.isSolid()) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	public static float hash(int x, int y, int z) {
		int h = x * 0x9E3779B1 ^ y * 0x7FEB352D ^ z * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h >>> 8) / (float) (1 << 24);
	}
}
