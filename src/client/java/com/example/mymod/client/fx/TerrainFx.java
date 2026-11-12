package com.example.mymod.client.fx;

import com.example.mymod.fx.Fx;
import com.example.mymod.network.FxPayload;
import com.example.mymod.world.IslandShape;
import com.example.mymod.world.SpikeShape;
import com.example.mymod.world.WorldCarver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class TerrainFx {
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private static final double ALIGN = Math.PI / 2;

	private TerrainFx() {
	}

	record SpikeData(SpikeShape shape, VoxelMesh mesh) {
	}

	record IslandData(IslandShape shape, VoxelMesh mesh, int[] debrisColors) {
	}

	static Object capture(FxPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		Vec3 a = fx.a();
		if (!level.hasChunk(Mth.floor(a.x) >> 4, Mth.floor(a.z) >> 4)) {
			return null;
		}
		if (fx.kind() == Fx.SPIKE_RISE) {
			Vec3 b = fx.b();
			int variant = fx.variant();
			WorldCarver.Spire spire = WorldCarver.Spire.values()[Math.min(variant & 0xFF, WorldCarver.Spire.values().length - 1)];
			SpikeShape s = SpikeShape.build(level, a.x, a.z, (int) Math.round(a.y), fx.radius(), (float) b.y, (float) b.x,
					(float) b.z, (variant & 256) != 0, spire);
			if (s.top == Integer.MIN_VALUE) {
				return null;
			}
			int[] base = new int[s.lo.length];
			int[][] cols = new int[s.lo.length][];
			for (int ix = 0; ix < s.sizeX; ix++) {
				for (int iz = 0; iz < s.sizeZ; iz++) {
					int i = ix * s.sizeZ + iz;
					if (s.lo[i] > s.hi[i]) {
						continue;
					}
					int x = s.minX + ix;
					int z = s.minZ + iz;
					base[i] = s.lo[i];
					int[] c = new int[s.hi[i] - s.lo[i] + 1];
					for (int k = 0; k < c.length; k++) {
						int y = s.lo[i] + k;
						c[k] = vary(spikeColor(s.block(x, y, z)), SpikeShape.hash(x, y, z));
					}
					cols[i] = c;
				}
			}
			VoxelMesh mesh = VoxelMesh.build(s.minX, s.minZ, s.sizeX, s.sizeZ, base, cols, a.x, a.y, a.z);
			return new SpikeData(s, mesh);
		}
		if (fx.kind() == Fx.ISLAND_RISE) {
			IslandShape s = new IslandShape(a.x, (int) Math.round(a.y), a.z, fx.radius(), fx.seed());
			int n = s.size;
			int[] base = new int[n * n];
			int[][] cols = new int[n * n][];
			int[] debris = new int[8];
			int nd = 0;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int ix = 0; ix < n; ix++) {
				for (int iz = 0; iz < n; iz++) {
					int x = s.minX + ix;
					int z = s.minZ + iz;
					if (!s.inside(x, z)) {
						continue;
					}
					int b = Math.max(level.getMinY() + 1, s.bottom(x, z));
					int top = IslandShape.top(level, x, z);
					if (top < b) {
						continue;
					}
					int i = ix * n + iz;
					int[] c = new int[top - b + 1];
					for (int k = 0; k < c.length; k++) {
						BlockState st = level.getBlockState(pos.set(x, b + k, z));
						if (st.isAir() || st.hasBlockEntity()) {
							continue;
						}
						int col = st.getMapColor(level, pos).col;
						c[k] = vary(0xFF000000 | (col == 0 ? 0x707070 : col), SpikeShape.hash(x, b + k, z));
						if (k < 6 && nd < debris.length && (ix + iz) % 7 == 0) {
							debris[nd++] = c[k];
						}
					}
					base[i] = b;
					cols[i] = c;
				}
			}
			while (nd < debris.length) {
				debris[nd++] = 0xFF6B5A4A;
			}
			VoxelMesh mesh = VoxelMesh.build(s.minX, s.minZ, n, n, base, cols, a.x, a.y, a.z);
			return new IslandData(s, mesh, debris);
		}
		return null;
	}

	private static int spikeColor(BlockState st) {
		Block b = st.getBlock();
		int c;
		if (b == Blocks.BASALT) {
			c = 0x5A5960;
		} else if (b == Blocks.SMOOTH_BASALT) {
			c = 0x4A4950;
		} else if (b == Blocks.POLISHED_BASALT) {
			c = 0x68676E;
		} else if (b == Blocks.BLACKSTONE) {
			c = 0x2E2932;
		} else if (b == Blocks.MAGMA_BLOCK) {
			c = 0xC2561E;
		} else if (b == Blocks.PACKED_ICE) {
			c = 0x8DB4FA;
		} else if (b == Blocks.BLUE_ICE) {
			c = 0x74A7FD;
		} else if (b == Blocks.SNOW_BLOCK) {
			c = 0xF4FAFA;
		} else if (b == Blocks.CALCITE) {
			c = 0xDFE0DC;
		} else if (b == Blocks.AMETHYST_BLOCK) {
			c = 0x9470D4;
		} else if (b == Blocks.PURPLE_STAINED_GLASS) {
			c = 0xA05ADC;
		} else if (b == Blocks.OBSIDIAN) {
			c = 0x1A1426;
		} else if (b == Blocks.CRYING_OBSIDIAN) {
			c = 0x3A1468;
		} else if (b == Blocks.END_STONE) {
			c = 0xDBDE9E;
		} else if (b == Blocks.PURPUR_BLOCK) {
			c = 0xA97DA9;
		} else if (b == Blocks.ANDESITE) {
			c = 0x8A8A8B;
		} else if (b == Blocks.COBBLESTONE) {
			c = 0x7A7A7A;
		} else if (b == Blocks.TUFF) {
			c = 0x6C6D66;
		} else if (b == Blocks.MOSSY_COBBLESTONE) {
			c = 0x6E7A5A;
		} else {
			c = 0x7E7E7E;
		}
		return 0xFF000000 | c;
	}

	private static int vary(int color, float rnd) {
		return MeshDraw.shade(color, 0.9f + 0.18f * rnd);
	}

	private static int glowColor(WorldCarver.Spire spire) {
		return switch (spire) {
			case BASALT -> 0xFFFF6A2A;
			case ICE -> 0xFF9FD8FF;
			case CRYSTAL -> 0xFFD08CFF;
			case OBSIDIAN, END -> 0xFFB46AFF;
			case STONE -> 0xFFFFB070;
		};
	}

	private static int dustLight(WorldCarver.Spire spire) {
		return switch (spire) {
			case ICE -> 0xFFE8F4FF;
			case CRYSTAL -> 0xFFE0D0F0;
			case END -> 0xFFE8E4C0;
			default -> 0xFFB8A890;
		};
	}

	private static float rnd(int seed, int i, int k) {
		return SpikeShape.hash(seed, i * 31 + 7, k * 131 + 3);
	}

	private static float clamp01(float x) {
		return Math.max(0f, Math.min(1f, x));
	}

	private static double thrust(double u) {
		u = Math.max(0, Math.min(1, u));
		double c1 = 1.25;
		double c3 = c1 + 1;
		double t = u - 1;
		return 1 + c3 * t * t * t + c1 * t * t;
	}

	static void spike(FxDraw d, MeshDraw mesh, FxPayload fx, Object data, float t) {
		if (!(data instanceof SpikeData sd)) {
			return;
		}
		SpikeShape s = sd.shape();
		float pre = fx.extra();
		float rise = Math.max(0.2f, fx.extra2());
		Vec3 base = fx.a();
		int seed = fx.seed();
		int glow = glowColor(s.spire);
		int dust = dustLight(s.spire);
		Vec3 ground = base.add(0, 1.04, 0);

		float crack = t < pre ? t / Math.max(0.05f, pre) : Math.max(0f, 1f - (t - pre) / 1.4f);
		if (crack > 0.01f) {
			int n = 7;
			float grow = clamp01(t / Math.max(0.05f, pre) * 1.3f);
			for (int k = 0; k < n; k++) {
				double ang = Math.PI * 2 * k / n + rnd(seed, k, 1) * 0.8;
				double len = s.base * (1.3 + rnd(seed, k, 2) * 1.2) * grow;
				Vec3 p1 = ground.add(Math.cos(ang) * len, 0, Math.sin(ang) * len);
				d.ribbon(ground, p1, 0.28f, 0.04f, FxDraw.alpha(glow, crack), FxDraw.alpha(glow, 0.1f * crack));
			}
			d.glowBall(ground, s.base * 1.1f * crack, glow, 0.55f * crack);
		}

		if (t < pre + 0.4f) {
			for (int k = 0; k < 5; k++) {
				double ang = rnd(seed, k, 3) * Math.PI * 2;
				double r = s.base * (0.6 + rnd(seed, k, 4));
				float a = clamp01(t / Math.max(0.05f, pre)) * 0.35f;
				d.smoke(ground.add(Math.cos(ang) * r, t * 0.8, Math.sin(ang) * r), s.base * 0.5f + 0.6f, rnd(seed, k, 5) * 6f,
						k, dust, MeshDraw.shade(dust, 0.45f), a);
			}
		}
		if (t < pre) {
			return;
		}

		float tb = t - pre;
		double u = tb / rise;
		double total = (s.top - s.bottom) + 3;
		double off = -total * (1 - thrust(u));
		double jit = Math.max(0, 1 - u) * 0.14;

		Vec3 pos = base.add(s.leanX * off + Math.sin(t * 71 + seed) * jit, off, s.leanZ * off + Math.cos(t * 67 + seed) * jit);
		mesh.begin(pos, UP, ALIGN, 1);
		sd.mesh().emit(mesh);

		Vec3 tip = new Vec3(s.x + s.leanX * (s.height + off), s.top + 1 + off, s.z + s.leanZ * (s.height + off));
		if (s.spire != WorldCarver.Spire.STONE && !s.pillar) {
			float pulse = 0.6f + 0.25f * (float) Math.sin(t * 9 + seed);
			d.glowBall(tip, 1.2f + s.base * 0.35f, glow, pulse * Math.max(0.35f, 1f - tb / 3f));
		}

		if (tb < 2.6f) {
			for (int k = 0; k < 10; k++) {
				double ang = Math.PI * 2 * k / 10 + rnd(seed, k, 6);
				double r = s.base * (0.9 + tb * 1.6);
				float a = 0.6f * (1f - tb / 2.6f);
				d.smoke(ground.add(Math.cos(ang) * r, tb * 1.3, Math.sin(ang) * r), s.base * 0.55f + tb * 1.6f,
						rnd(seed, k, 7) * 6f + tb * 0.3f, k + 2, dust, MeshDraw.shade(dust, 0.4f), a);
			}
		}
		if (tb < 2.2f) {
			int rock = sd.mesh().size() > 0 ? MeshDraw.shade(spikeColor(s.block(Mth.floor(s.x), s.gy, Mth.floor(s.z))), 0.9f)
					: 0xFF6A6A6A;
			int count = Mth.clamp(Math.round(s.base * 2.5f), 4, 14);
			for (int k = 0; k < count; k++) {
				double ang = rnd(seed, k, 8) * Math.PI * 2;
				double sp = 3 + rnd(seed, k, 9) * 6;
				double vy = 6 + rnd(seed, k, 10) * 9;
				double px = base.x + Math.cos(ang) * (s.base * 0.6 + sp * tb);
				double pz = base.z + Math.sin(ang) * (s.base * 0.6 + sp * tb);
				double py = base.y + 1 + vy * tb - 9 * tb * tb;
				if (py < base.y - 1) {
					continue;
				}
				double size = 0.18 + rnd(seed, k, 11) * 0.35;
				mesh.begin(new Vec3(px, py, pz), new Vec3(Math.sin(tb * 7 + k), 1, Math.cos(tb * 5 + k)), tb * 9, 1);
				mesh.box(-size, -size, -size, size, size, size, k % 3 == 0 ? MeshDraw.shade(rock, 0.7f) : rock);
			}
		}
	}

	static void island(FxDraw d, MeshDraw mesh, FxPayload fx, Object data, float t) {
		if (!(data instanceof IslandData id)) {
			return;
		}
		IslandShape s = id.shape();
		float pre = fx.extra();
		float rise = Math.max(0.5f, fx.extra2());
		double lift = fx.b().x;
		Vec3 c = fx.a();
		int seed = fx.seed();
		double u = clamp01((t - pre) / rise);
		double y = lift * IslandShape.ease(u);
		double shake = t < pre ? 0.18 * (t / pre) : 0.18 * Math.max(0, 1 - u * 3);
		double tilt = 0.045 * Math.sin(u * Math.PI);
		double tiltAxis = (seed & 1023) * 0.00614;
		Vec3 up = new Vec3(Math.cos(tiltAxis) * Math.sin(tilt), Math.cos(tilt), Math.sin(tiltAxis) * Math.sin(tilt));
		Vec3 origin = c.add(Math.sin(t * 63 + seed) * shake, y, Math.cos(t * 57 + seed) * shake);
		mesh.begin(origin, up, ALIGN, 1);
		id.mesh().emit(mesh);

		int magic = 0xFF7FE8FF;
		float r = s.radius;

		float rimA = t < pre ? t / pre : Math.max(0f, 1f - (t - pre) / 1.5f);
		if (rimA > 0.01f) {
			int seg = 36;
			for (int k = 0; k < seg; k++) {
				double a0 = Math.PI * 2 * k / seg;
				double a1 = Math.PI * 2 * (k + 1) / seg;
				Vec3 p0 = c.add(Math.cos(a0) * r, 1.05, Math.sin(a0) * r);
				Vec3 p1 = c.add(Math.cos(a1) * r, 1.05, Math.sin(a1) * r);
				d.ribbon(p0, p1, 0.35f, 0.35f, FxDraw.alpha(magic, 0.9f * rimA), FxDraw.alpha(magic, 0.9f * rimA));
			}
		}
		float tb = t - pre;
		if (tb > -0.4f && tb < 3f) {
			float a = 0.55f * Math.min(1f, (tb + 0.4f) / 0.4f) * (1f - Math.max(0f, tb) / 3f);
			for (int k = 0; k < 18; k++) {
				double ang = Math.PI * 2 * k / 18 + rnd(seed, k, 1) * 0.3;
				double rr = r * (1.02 + Math.max(0, tb) * 0.12);
				d.smoke(c.add(Math.cos(ang) * rr, 1 + Math.max(0, tb) * 1.5, Math.sin(ang) * rr), 2.2f + Math.max(0, tb) * 1.8f,
						rnd(seed, k, 2) * 6f, k, 0xFFB8A890, 0xFF4A3E32, a);
			}
		}
		if (t < pre) {
			return;
		}

		double bottomY = c.y + y - s.depth * 0.7;
		float glowA = (float) Math.min(1, u * 4) * (float) Math.max(0.25, 1 - Math.max(0, t - pre - rise) / 2.5);
		float pulse = 0.75f + 0.25f * (float) Math.sin(t * 6);
		d.groundRing(new Vec3(c.x, bottomY, c.z), r * 0.55f, 0.6f, FxDraw.alpha(magic, 0.7f * glowA * pulse));
		d.glowBall(new Vec3(c.x, bottomY - 1, c.z), r * 0.45f, magic, 0.35f * glowA * pulse);
		for (int k = 0; k < 20; k++) {
			float ph = frac((float) (t * 0.6 + rnd(seed, k, 3)));
			double ang = rnd(seed, k, 4) * Math.PI * 2;
			double rr = r * 0.8 * Math.sqrt(rnd(seed, k, 5));
			double yy = c.y + (bottomY - c.y) * ph;
			d.glowBall(new Vec3(c.x + Math.cos(ang) * rr, yy, c.z + Math.sin(ang) * rr), 0.35f, magic, 0.8f * glowA * (1 - ph));
		}

		for (int k = 0; k < 30; k++) {
			float ts = rise * 0.95f * rnd(seed, k, 6);
			float age = tb - ts;
			if (age < 0 || age > 2.6f) {
				continue;
			}
			double ang = rnd(seed, k, 7) * Math.PI * 2;
			double rr = r * 0.7 * Math.sqrt(rnd(seed, k, 8));
			double startY = c.y + lift * IslandShape.ease(ts / rise) - s.depth * (0.3 + 0.6 * (1 - rr / r));
			double py = startY - 9 * age * age;
			if (py < c.y - s.depth) {
				continue;
			}
			double size = 0.25 + rnd(seed, k, 9) * 0.5;
			mesh.begin(new Vec3(c.x + Math.cos(ang) * rr, py, c.z + Math.sin(ang) * rr),
					new Vec3(Math.sin(age * 5 + k), 1, Math.cos(age * 4 + k)), age * 7, 1);
			mesh.box(-size, -size, -size, size, size, size, id.debrisColors()[k % id.debrisColors().length]);
			if (k % 3 == 0) {
				d.smoke(new Vec3(c.x + Math.cos(ang) * rr, py + 1.5, c.z + Math.sin(ang) * rr), 1.2f + age, age + k, k,
						0xFFB8A890, 0xFF4A3E32, 0.35f * (1 - age / 2.6f));
			}
		}
	}

	private static float frac(float x) {
		return x - (float) Math.floor(x);
	}

	static void dome(FxDraw d, FxPayload fx, float t) {
		Vec3 c = fx.a();
		float rad = fx.radius();
		float grow = Math.max(0.2f, fx.extra());
		int col = fx.color();
		float g = clamp01(t / grow);
		float fade = t > grow ? Math.max(0f, 1f - (t - grow) / 1.5f) : 1f;
		double hMin = -6;
		double front = hMin + (rad - hMin) * g;

		int meridians = 24;
		int steps = 16;
		for (int m = 0; m < meridians; m++) {
			double a = Math.PI * 2 * m / meridians + t * 0.04;
			Vec3 prev = null;
			for (int k = 0; k <= steps; k++) {
				double h = hMin + (front - hMin) * k / steps;
				double rr = Math.sqrt(Math.max(0, rad * rad - h * h));
				Vec3 p = c.add(Math.cos(a) * rr, h, Math.sin(a) * rr);
				if (prev != null) {
					d.ribbon(prev, p, 0.12f, 0.12f, FxDraw.alpha(col, 0.45f * fade), FxDraw.alpha(col, 0.45f * fade));
				}
				prev = p;
			}
		}

		for (double h = 0; h < front; h += Math.max(3, rad / 7)) {
			double rr = Math.sqrt(Math.max(0, rad * rad - h * h));
			d.groundRing(c.add(0, h, 0), (float) rr, 0.18f, FxDraw.alpha(col, 0.35f * fade));
		}

		if (g < 1f) {
			double rr = Math.sqrt(Math.max(0, rad * rad - front * front));
			d.groundRing(c.add(0, front, 0), (float) rr, 0.6f, FxDraw.alpha(FxDraw.mix(col, FxDraw.WHITE, 0.5f), 0.95f));
			for (int k = 0; k < 16; k++) {
				double a = Math.PI * 2 * k / 16 + t * 0.8;
				d.glowBall(c.add(Math.cos(a) * rr, front, Math.sin(a) * rr), 0.7f, col, 0.9f);
			}
		}
	}
}
