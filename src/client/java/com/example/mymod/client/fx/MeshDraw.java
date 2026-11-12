package com.example.mymod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.example.mymod.MyMod;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

final class MeshDraw {
	private static final Identifier WHITE = MyMod.id("textures/misc/white.png");
	private static final int LIGHT = 0xF000F0;

	private final Vec3 cam;

	private final List<float[]> quads = new ArrayList<>();

	private Vec3 origin = Vec3.ZERO;
	private Vec3 ax = new Vec3(1, 0, 0);
	private Vec3 ay = new Vec3(0, 1, 0);
	private Vec3 az = new Vec3(0, 0, 1);
	private double scale = 1;

	MeshDraw(Vec3 cam) {
		this.cam = cam;
	}

	void begin(Vec3 pos, Vec3 forward, double roll, double scale) {
		this.origin = pos;
		this.scale = scale;
		ay = forward.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : forward.normalize();
		Vec3 helper = Math.abs(ay.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 x0 = helper.cross(ay).normalize();
		Vec3 z0 = ay.cross(x0).normalize();
		double c = Math.cos(roll);
		double s = Math.sin(roll);
		ax = x0.scale(c).add(z0.scale(s));
		az = ax.cross(ay).normalize();
	}

	private Vec3 tp(double x, double y, double z) {
		return origin.add(ax.scale(x * scale)).add(ay.scale(y * scale)).add(az.scale(z * scale));
	}

	private Vec3 tn(double x, double y, double z) {
		Vec3 n = ax.scale(x).add(ay.scale(y)).add(az.scale(z));
		return n.lengthSqr() < 1e-12 ? new Vec3(0, 1, 0) : n.normalize();
	}

	void quadN(double[] p0, double[] p1, double[] p2, double[] p3,
			double[] n0, double[] n1, double[] n2, double[] n3, int color) {
		Vec3[] p = {tp(p0[0], p0[1], p0[2]), tp(p1[0], p1[1], p1[2]), tp(p2[0], p2[1], p2[2]), tp(p3[0], p3[1], p3[2])};
		Vec3[] n = {tn(n0[0], n0[1], n0[2]), tn(n1[0], n1[1], n1[2]), tn(n2[0], n2[1], n2[2]), tn(n3[0], n3[1], n3[2])};
		Vec3 avg = n[0].add(n[1]).add(n[2]).add(n[3]);
		Vec3 face = p[1].subtract(p[0]).cross(p[2].subtract(p[0]));
		if (face.lengthSqr() < 1e-14) {
			face = p[2].subtract(p[0]).cross(p[3].subtract(p[0]));
		}
		if (face.dot(avg) < 0) {
			Vec3 t = p[0];
			p[0] = p[3];
			p[3] = t;
			t = p[1];
			p[1] = p[2];
			p[2] = t;
			t = n[0];
			n[0] = n[3];
			n[3] = t;
			t = n[1];
			n[1] = n[2];
			n[2] = t;
		}
		float[] q = new float[25];
		for (int k = 0; k < 4; k++) {
			q[k * 3] = (float) (p[k].x - cam.x);
			q[k * 3 + 1] = (float) (p[k].y - cam.y);
			q[k * 3 + 2] = (float) (p[k].z - cam.z);
			q[12 + k * 3] = (float) n[k].x;
			q[12 + k * 3 + 1] = (float) n[k].y;
			q[12 + k * 3 + 2] = (float) n[k].z;
		}
		q[24] = Float.intBitsToFloat(color);
		quads.add(q);
	}

	void quad(double[] p0, double[] p1, double[] p2, double[] p3, double[] hint, int color) {
		quadN(p0, p1, p2, p3, hint, hint, hint, hint, color);
	}

	void lathe(double[][] profile, int seg, int... colors) {
		lathe(profile, seg, false, colors);
	}

	void latheInward(double[][] profile, int seg, int... colors) {
		lathe(profile, seg, true, colors);
	}

	private void lathe(double[][] profile, int seg, boolean inward, int... colors) {
		int n = profile.length;

		double[][] pn = new double[n][];
		for (int i = 0; i < n; i++) {
			double[] a = profile[Math.max(0, i - 1)];
			double[] b = profile[Math.min(n - 1, i + 1)];
			double dy = b[0] - a[0];
			double dr = b[1] - a[1];
			double len = Math.sqrt(dy * dy + dr * dr);
			pn[i] = len < 1e-9 ? new double[] {1, 0} : new double[] {dy / len, -dr / len};
			if (inward) {
				pn[i] = new double[] {-pn[i][0], -pn[i][1]};
			}
		}
		for (int i = 0; i < n - 1; i++) {
			int color = colors[Math.min(i, colors.length - 1)];
			double y0 = profile[i][0];
			double r0 = profile[i][1];
			double y1 = profile[i + 1][0];
			double r1 = profile[i + 1][1];
			if (Math.abs(y1 - y0) < 1e-9 && Math.abs(r1 - r0) < 1e-9) {
				continue;
			}
			for (int j = 0; j < seg; j++) {
				double a0 = Math.PI * 2 * j / seg;
				double a1 = Math.PI * 2 * (j + 1) / seg;
				double c0 = Math.cos(a0);
				double s0 = Math.sin(a0);
				double c1 = Math.cos(a1);
				double s1 = Math.sin(a1);
				quadN(new double[] {c0 * r0, y0, s0 * r0}, new double[] {c1 * r0, y0, s1 * r0},
						new double[] {c1 * r1, y1, s1 * r1}, new double[] {c0 * r1, y1, s0 * r1},
						new double[] {c0 * pn[i][0], pn[i][1], s0 * pn[i][0]}, new double[] {c1 * pn[i][0], pn[i][1], s1 * pn[i][0]},
						new double[] {c1 * pn[i + 1][0], pn[i + 1][1], s1 * pn[i + 1][0]},
						new double[] {c0 * pn[i + 1][0], pn[i + 1][1], s0 * pn[i + 1][0]}, color);
			}
		}
	}

	static double[][] ogive(double y0, double y1, double r, int steps, double sharp) {
		double[][] p = new double[steps + 1][];
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			p[i] = new double[] {y0 + (y1 - y0) * t, r * Math.pow(Math.max(0, 1 - t * t), sharp)};
		}
		return p;
	}

	static double[][] join(double[][]... parts) {
		int total = 0;
		for (double[][] p : parts) {
			total += p.length;
		}
		double[][] out = new double[total][];
		int k = 0;
		for (double[][] p : parts) {
			for (double[] v : p) {
				out[k++] = v;
			}
		}
		return out;
	}

	void tube(double y0, double y1, double r0, double r1, int seg, int color) {
		lathe(new double[][] {{y0, r0}, {y1, r1}}, seg, color);
	}

	void cap(double y, double r, int seg, int color, boolean up) {
		double[] nn = {0, up ? 1 : -1, 0};
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg;
			double a1 = Math.PI * 2 * (i + 1) / seg;
			double[] c = {0, y, 0};
			quad(c, new double[] {Math.cos(a0) * r, y, Math.sin(a0) * r},
					new double[] {Math.cos(a1) * r, y, Math.sin(a1) * r}, c, nn, color);
		}
	}

	void box(double x0, double y0, double z0, double x1, double y1, double z1, int color) {
		double[][] v = {
				{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
				{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
		quad(v[0], v[1], v[2], v[3], new double[] {0, 0, -1}, color);
		quad(v[5], v[4], v[7], v[6], new double[] {0, 0, 1}, color);
		quad(v[4], v[0], v[3], v[7], new double[] {-1, 0, 0}, color);
		quad(v[1], v[5], v[6], v[2], new double[] {1, 0, 0}, color);
		quad(v[3], v[2], v[6], v[7], new double[] {0, 1, 0}, color);
		quad(v[4], v[5], v[1], v[0], new double[] {0, -1, 0}, color);
	}

	void fin(double angle, double y0, double y1, double r, double span, double sweep, double thick, int color) {
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		double px = -s * thick / 2;
		double pz = c * thick / 2;

		double[][] o = {{r, y0}, {r + span, y0 + sweep}, {r + span * 0.92, y1 - sweep * 0.2}, {r, y1}};
		double[][] f = new double[4][];
		double[][] b = new double[4][];
		for (int i = 0; i < 4; i++) {
			f[i] = new double[] {c * o[i][0] + px, o[i][1], s * o[i][0] + pz};
			b[i] = new double[] {c * o[i][0] - px, o[i][1], s * o[i][0] - pz};
		}
		quad(f[0], f[1], f[2], f[3], new double[] {-s, 0, c}, color);
		quad(b[3], b[2], b[1], b[0], new double[] {s, 0, -c}, color);
		quad(f[1], b[1], b[2], f[2], new double[] {c, 0, s}, color);
		quad(f[0], b[0], b[1], f[1], new double[] {c * 0.3, -1, s * 0.3}, color);
		quad(f[2], b[2], b[3], f[3], new double[] {c * 0.3, 1, s * 0.3}, color);
	}

	void rock(double radius, int seed, int dark, int light, int glow, int lat, int lon) {
		double[][][] p = new double[lat + 1][lon + 1][];
		double[][][] nn = new double[lat + 1][lon + 1][];
		for (int i = 0; i <= lat; i++) {
			double th = Math.PI * i / lat;
			for (int j = 0; j <= lon; j++) {
				double ph = Math.PI * 2 * j / lon;
				double nx = Math.sin(th) * Math.cos(ph);
				double ny = Math.cos(th);
				double nz = Math.sin(th) * Math.sin(ph);
				double bump = 1 + 0.14 * Math.sin(3 * nx + seed) * Math.cos(2 * nz + seed * 0.7)
						+ 0.08 * Math.sin(5 * ny + 2 * nx + seed * 1.3) + 0.04 * Math.sin(11 * nz + 7 * ny);
				double rr = radius * bump;
				p[i][j] = new double[] {nx * rr, ny * rr, nz * rr};
				nn[i][j] = new double[] {nx, ny, nz};
			}
		}
		for (int i = 0; i < lat; i++) {
			for (int j = 0; j < lon; j++) {
				int h = (i * 73856093) ^ (j * 19349663) ^ seed;
				float k = ((h >>> 3) & 0xFF) / 255f;
				int color = FxDraw.mix(dark, light, 0.3f + 0.4f * k);
				if (glow != 0 && ((h >>> 11) & 0x1F) < 3) {
					color = glow;
				}
				quadN(p[i][j], p[i][j + 1], p[i + 1][j + 1], p[i + 1][j],
						nn[i][j], nn[i][j + 1], nn[i + 1][j + 1], nn[i + 1][j], color);
			}
		}
	}

	static int shade(int color, float k) {
		int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * k));
		int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * k));
		int b = Math.min(255, Math.round((color & 0xFF) * k));
		return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	void flush(PoseStack pose, SubmitNodeCollector out) {
		if (quads.isEmpty()) {
			return;
		}
		List<float[]> list = List.copyOf(quads);
		out.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, vc) -> {
			for (float[] q : list) {
				int color = Float.floatToRawIntBits(q[24]);
				for (int k = 0; k < 4; k++) {
					vc.addVertex(p, q[k * 3], q[k * 3 + 1], q[k * 3 + 2])
							.setColor(color)
							.setUv(0.5f, 0.5f)
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(LIGHT)
							.setNormal(p, q[12 + k * 3], q[12 + k * 3 + 1], q[12 + k * 3 + 2]);
				}
			}
		});
		quads.clear();
	}
}
