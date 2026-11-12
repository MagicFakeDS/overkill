package com.example.mymod.world;

import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;

public final class IslandShape {
	public final double cx;
	public final double cz;
	public final int gy;
	public final float radius;
	public final int seed;
	public final float depth;
	public final int minX;
	public final int minZ;
	public final int size;
	private final float s1;
	private final float s2;
	private final float s3;

	public IslandShape(double cx, int gy, double cz, float radius, int seed) {
		this.cx = cx;
		this.cz = cz;
		this.gy = gy;
		this.radius = radius;
		this.seed = seed;
		this.depth = radius * 0.9f + 3f;
		float maxR = radius * 1.26f + 1;
		this.minX = Mth.floor(cx - maxR);
		this.minZ = Mth.floor(cz - maxR);
		this.size = Mth.ceil(maxR * 2) + 1;
		this.s1 = (seed & 0xFF) / 40f;
		this.s2 = ((seed >> 8) & 0xFF) / 40f;
		this.s3 = ((seed >> 16) & 0xFF) / 40f;
	}

	public double norm(int x, int z) {
		double dx = x + 0.5 - cx;
		double dz = z + 0.5 - cz;
		double a = Math.atan2(dz, dx);
		double edge = 1 + 0.12 * Math.sin(3 * a + s1) + 0.08 * Math.sin(5 * a + s2) + 0.05 * Math.sin(9 * a + s3);
		return Math.sqrt(dx * dx + dz * dz) / (radius * edge);
	}

	public boolean inside(int x, int z) {
		return norm(x, z) <= 1;
	}

	public int bottom(int x, int z) {
		double n = Math.min(1, norm(x, z));
		double dep = depth * Math.pow(1 - n * n, 0.7) + 1 + 3 * SpikeShape.hash(x, seed, z);
		return gy - (int) Math.round(dep);
	}

	public static int top(LevelReader level, int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
	}

	public int index(int x, int z) {
		int ix = x - minX;
		int iz = z - minZ;
		if (ix < 0 || iz < 0 || ix >= size || iz >= size) {
			return -1;
		}
		return ix * size + iz;
	}

	public static double ease(double u) {
		u = Math.max(0, Math.min(1, u));
		return u * u * u * (u * (u * 6 - 15) + 10);
	}
}
