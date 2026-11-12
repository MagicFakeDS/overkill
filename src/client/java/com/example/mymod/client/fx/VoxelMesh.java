package com.example.mymod.client.fx;

import java.util.ArrayList;
import java.util.List;

final class VoxelMesh {
	private static final double E = 0.012;
	private static final double[] UP = {0, 1, 0};
	private static final double[] DOWN = {0, -1, 0};
	private static final double[] PX = {1, 0, 0};
	private static final double[] NX = {-1, 0, 0};
	private static final double[] PZ = {0, 0, 1};
	private static final double[] NZ = {0, 0, -1};

	private final List<double[][]> quads = new ArrayList<>();
	private final List<Integer> colors = new ArrayList<>();

	private final int minX;
	private final int minZ;
	private final int sizeX;
	private final int sizeZ;
	private final int[] base;
	private final int[][] cols;

	private VoxelMesh(int minX, int minZ, int sizeX, int sizeZ, int[] base, int[][] cols) {
		this.minX = minX;
		this.minZ = minZ;
		this.sizeX = sizeX;
		this.sizeZ = sizeZ;
		this.base = base;
		this.cols = cols;
	}

	int size() {
		return quads.size();
	}

	private int at(int x, int y, int z) {
		int ix = x - minX;
		int iz = z - minZ;
		if (ix < 0 || iz < 0 || ix >= sizeX || iz >= sizeZ) {
			return 0;
		}
		int i = ix * sizeZ + iz;
		int[] c = cols[i];
		if (c == null) {
			return 0;
		}
		int k = y - base[i];
		return k < 0 || k >= c.length ? 0 : c[k];
	}

	static VoxelMesh build(int minX, int minZ, int sizeX, int sizeZ, int[] base, int[][] cols,
			double ox, double oy, double oz) {
		VoxelMesh m = new VoxelMesh(minX, minZ, sizeX, sizeZ, base, cols);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int ix = 0; ix < sizeX; ix++) {
			for (int iz = 0; iz < sizeZ; iz++) {
				int i = ix * sizeZ + iz;
				int[] c = cols[i];
				if (c == null) {
					continue;
				}
				int x = minX + ix;
				int z = minZ + iz;
				double x0 = x - ox + E;
				double x1 = x + 1 - ox - E;
				double z0 = z - oz + E;
				double z1 = z + 1 - oz - E;
				for (int k = 0; k < c.length; k++) {
					if (c[k] == 0) {
						continue;
					}
					int y = base[i] + k;
					if (m.at(x, y + 1, z) == 0) {
						double yt = y + 1 - oy - E;
						m.add(new double[] {x0, yt, z0}, new double[] {x1, yt, z0}, new double[] {x1, yt, z1},
								new double[] {x0, yt, z1}, UP, c[k]);
					}
					if (m.at(x, y - 1, z) == 0) {
						double yb = y - oy + E;
						m.add(new double[] {x0, yb, z0}, new double[] {x0, yb, z1}, new double[] {x1, yb, z1},
								new double[] {x1, yb, z0}, DOWN, MeshDraw.shade(c[k], 0.55f));
					}
				}

				for (int[] dir : dirs) {
					int runStart = -1;
					int runColor = 0;
					for (int k = 0; k <= c.length; k++) {
						int y = base[i] + k;
						boolean exposed = k < c.length && c[k] != 0 && m.at(x + dir[0], y, z + dir[1]) == 0;
						int color = exposed ? c[k] : 0;
						if (runStart >= 0 && (!exposed || color != runColor)) {
							m.side(dir, x0, x1, z0, z1, base[i] + runStart - oy, y - oy, runColor);
							runStart = -1;
						}
						if (exposed && runStart < 0) {
							runStart = k;
							runColor = color;
						}
					}
				}
			}
		}
		return m;
	}

	private void side(int[] dir, double x0, double x1, double z0, double z1, double y0, double y1, int color) {
		if (dir[0] == 1) {
			add(new double[] {x1, y0, z0}, new double[] {x1, y0, z1}, new double[] {x1, y1, z1}, new double[] {x1, y1, z0},
					PX, color);
		} else if (dir[0] == -1) {
			add(new double[] {x0, y0, z1}, new double[] {x0, y0, z0}, new double[] {x0, y1, z0}, new double[] {x0, y1, z1},
					NX, color);
		} else if (dir[1] == 1) {
			add(new double[] {x1, y0, z1}, new double[] {x0, y0, z1}, new double[] {x0, y1, z1}, new double[] {x1, y1, z1},
					PZ, color);
		} else {
			add(new double[] {x0, y0, z0}, new double[] {x1, y0, z0}, new double[] {x1, y1, z0}, new double[] {x0, y1, z0},
					NZ, color);
		}
	}

	private void add(double[] p0, double[] p1, double[] p2, double[] p3, double[] n, int color) {
		quads.add(new double[][] {p0, p1, p2, p3, n});
		colors.add(color);
	}

	void emit(MeshDraw mesh) {
		for (int q = 0; q < quads.size(); q++) {
			double[][] v = quads.get(q);
			mesh.quad(v[0], v[1], v[2], v[3], v[4], colors.get(q));
		}
	}
}
