package com.example.mymod.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class ChunkSlab {
	static final int DEPTH = 48;
	static final int STEP = 4;
	private static final int BOTTOM = 0xFF1C1A1E;

	final int cx;
	final int cz;
	final int[] top = new int[256];
	final int[] color = new int[256];
	final int[][] wall = new int[256][];
	double baseY;
	int minTop = Integer.MAX_VALUE;

	private ChunkSlab(int cx, int cz) {
		this.cx = cx;
		this.cz = cz;
	}

	static ChunkSlab capture(int cx, int cz) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !level.hasChunk(cx, cz)) {
			return null;
		}
		ChunkSlab s = new ChunkSlab(cx, cz);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long sum = 0;
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int i = lx * 16 + lz;
				int x = cx * 16 + lx;
				int z = cz * 16 + lz;
				int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				s.top[i] = h;
				s.color[i] = colorAt(level, pos.set(x, h, z));
				if (lx == 0 || lx == 15 || lz == 0 || lz == 15) {
					int[] layers = new int[DEPTH / STEP];
					for (int k = 0; k < layers.length; k++) {
						layers[k] = colorAt(level, pos.set(x, h - (k + 1) * STEP + 2, z));
					}
					s.wall[i] = layers;
				}
				sum += h;
				s.minTop = Math.min(s.minTop, h);
			}
		}
		s.baseY = sum / 256.0;
		return s;
	}

	private static int colorAt(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return 0xFF4A4644;
		}
		int c = state.getMapColor(level, pos).col;
		return 0xFF000000 | (c == 0 ? 0x6A6A6A : c);
	}

	void emit(MeshDraw m) {
		double bottom = minTop - baseY - DEPTH;
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int i = lx * 16 + lz;
				double x0 = lx - 8;
				double x1 = x0 + 1;
				double z0 = lz - 8;
				double z1 = z0 + 1;
				double yt = top[i] + 1 - baseY;
				int c = color[i];

				m.quad(new double[] {x0, yt, z0}, new double[] {x1, yt, z0}, new double[] {x1, yt, z1},
						new double[] {x0, yt, z1}, new double[] {0, 1, 0}, c);

				if (lx < 15) {
					step(m, x1, z0, z1, true, yt, top[i + 16] + 1 - baseY, c, color[i + 16]);
				}
				if (lz < 15) {
					step(m, z1, x0, x1, false, yt, top[i + 1] + 1 - baseY, c, color[i + 1]);
				}

				if (lx == 0) {
					wallSide(m, i, x0, z0, x0, z1, -1, 0, yt, bottom);
				}
				if (lx == 15) {
					wallSide(m, i, x1, z1, x1, z0, 1, 0, yt, bottom);
				}
				if (lz == 0) {
					wallSide(m, i, x1, z0, x0, z0, 0, -1, yt, bottom);
				}
				if (lz == 15) {
					wallSide(m, i, x0, z1, x1, z1, 0, 1, yt, bottom);
				}
			}
		}

		m.quad(new double[] {-8, bottom, -8}, new double[] {8, bottom, -8}, new double[] {8, bottom, 8},
				new double[] {-8, bottom, 8}, new double[] {0, -1, 0}, BOTTOM);
	}

	private static void step(MeshDraw m, double at, double a0, double a1, boolean alongX, double h0, double h1,
			int c0, int c1) {
		if (Math.abs(h0 - h1) < 0.01) {
			return;
		}
		double lo = Math.min(h0, h1);
		double hi = Math.max(h0, h1);
		int c = MeshDraw.shade(h0 > h1 ? c0 : c1, 0.78f);
		double dir = h0 > h1 ? 1 : -1;
		if (alongX) {
			m.quad(new double[] {at, lo, a0}, new double[] {at, lo, a1}, new double[] {at, hi, a1},
					new double[] {at, hi, a0}, new double[] {dir, 0, 0}, c);
		} else {
			m.quad(new double[] {a0, lo, at}, new double[] {a1, lo, at}, new double[] {a1, hi, at},
					new double[] {a0, hi, at}, new double[] {0, 0, dir}, c);
		}
	}

	private void wallSide(MeshDraw m, int i, double ax, double az, double bx, double bz, double nx, double nz,
			double yt, double bottom) {
		int[] layers = wall[i];
		double y = yt;
		int k = -1;
		while (y > bottom + 0.01) {
			double y2 = Math.max(bottom, k < 0 ? y - 1 : y - STEP);
			int c = k < 0 ? MeshDraw.shade(color[i], 0.85f)
					: MeshDraw.shade(layers[Math.min(k, layers.length - 1)], 0.8f - 0.25f * k / layers.length);
			m.quad(new double[] {ax, y2, az}, new double[] {bx, y2, bz}, new double[] {bx, y, bz},
					new double[] {ax, y, az}, new double[] {nx, 0, nz}, c);
			y = y2;
			k++;
		}
	}
}
