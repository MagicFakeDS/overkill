package com.example.mymod.client.fx;

import com.example.mymod.fx.Fx;
import com.example.mymod.skill.Cinema;

import net.minecraft.world.phys.Vec3;

final class SpaceScene {
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private static final double ALIGN = Math.PI / 2;
	private static final double DOME = 62;

	private SpaceScene() {
	}

	record Shot(Vec3 pos, Vec3 look) {
	}

	static final Vec3 MISSILE_EARTH = new Vec3(0, -4, 26);
	static final double MISSILE_R = 12;
	static final Vec3 AST_EARTH = new Vec3(14, -3, 40);
	static final double AST_R = 12;
	static final Vec3 AST_MARS = new Vec3(-12, 2, 10);
	static final Vec3 ORB_EARTH = new Vec3(0, -40, 8);
	static final double ORB_R = 30;
	static final Vec3 ORB_SAT = new Vec3(0, 0, 4);

	static Shot camera(Cinema.Story story, float t, float len, Vec3 o) {
		float u = clamp01(t / len);
		switch (story) {
			case MISSILE -> {
				Vec3 rocket = missilePos(u);
				Vec3 cam = lerp(new Vec3(-3, 4, -4), new Vec3(5, 2, 8), smooth(u));
				Vec3 look = lerp(MISSILE_EARTH, rocket == null ? MISSILE_EARTH : rocket, 0.35 + 0.4 * smooth(u));
				return new Shot(o.add(cam), o.add(look));
			}
			case ASTEROID -> {
				Vec3 a = asteroidPos(u);
				Vec3 a2 = asteroidPos(Math.min(1f, u + 0.01f));
				Vec3 dir = a2.subtract(a).lengthSqr() < 1e-6 ? new Vec3(1, 0, 1).normalize() : a2.subtract(a).normalize();
				Vec3 side = dir.cross(UP).normalize();

				Vec3 marsCam = AST_MARS.add(9 - u * 4, 4, -9 + u * 3);
				Vec3 chaseCam = a.subtract(dir.scale(7)).add(0, 2.4, 0).add(side.scale(1.6));
				double k = smooth(clamp01((u - 0.38f) / 0.14f));
				Vec3 cam = lerp(marsCam, chaseCam, k);
				Vec3 look = lerp(a, a.add(dir.scale(12)), k);
				return new Shot(o.add(cam), o.add(look));
			}
			case ORBITAL -> {
				double ang = -0.6 + u * 0.9;
				Vec3 cam = ORB_SAT.add(Math.sin(ang) * 9, 2.5 - u * 1.5, -Math.cos(ang) * 9);
				Vec3 look = lerp(ORB_SAT, ORB_SAT.add(0, -6, 0), smooth(clamp01((u - 0.6f) / 0.3f)));
				return new Shot(o.add(cam), o.add(look));
			}
			default -> {
				return new Shot(o.add(0, 0, -10), o);
			}
		}
	}

	static void render(FxDraw d, MeshDraw mesh, Cinema.Story story, float t, float len, Vec3 o, Vec3 cam, int seed) {
		float u = clamp01(t / len);

		dome(d, mesh, cam, seed);
		Vec3 sunDir = new Vec3(0.6, 0.35, -0.7).normalize();
		d.glowBall(cam.add(sunDir.scale(DOME - 6)), 6f, 0xFFFFF4D0, 1f);
		d.glowBall(cam.add(sunDir.scale(DOME - 6)), 16f, 0xFFFFE8A8, 0.3f);
		switch (story) {
			case MISSILE -> missileStory(d, mesh, t, u, o);
			case ASTEROID -> asteroidStory(d, mesh, t, u, o, seed);
			case ORBITAL -> orbitalStory(d, mesh, t, u, o);
			default -> {
			}
		}
	}

	private static void dome(FxDraw d, MeshDraw mesh, Vec3 o, int seed) {
		mesh.begin(o, UP, ALIGN, 1);
		int lat = 12;
		int lon = 24;
		for (int i = 0; i < lat; i++) {
			double a0 = -Math.PI / 2 + Math.PI * i / lat;
			double a1 = -Math.PI / 2 + Math.PI * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double b0 = Math.PI * 2 * j / lon;
				double b1 = Math.PI * 2 * (j + 1) / lon;
				double[] p0 = sph(a0, b0, DOME);
				double[] p1 = sph(a0, b1, DOME);
				double[] p2 = sph(a1, b1, DOME);
				double[] p3 = sph(a1, b0, DOME);
				double[] in = {-p0[0], -p0[1], -p0[2]};
				mesh.quad(p0, p1, p2, p3, in, 0xFF000000);
			}
		}
		for (int k = 0; k < 360; k++) {
			double a = Math.asin(2 * rnd(seed, k, 1) - 1);
			double b = rnd(seed, k, 2) * Math.PI * 2;
			double[] p = sph(a, b, DOME - 3);
			float size = 0.12f + 0.3f * rnd(seed, k, 3) * rnd(seed, k, 4);
			int color = rnd(seed, k, 5) < 0.2f ? 0xFFFFE0C0 : rnd(seed, k, 5) < 0.35f ? 0xFFC8DCFF : 0xFFFFFFFF;
			d.glowBall(o.add(p[0], p[1], p[2]), size, color, 0.9f);
		}

		for (int k = 0; k < 40; k++) {
			double b = Math.PI * 2 * k / 40;
			double[] p = sph(0.35 * Math.sin(b * 2), b, DOME - 4);
			d.glowBall(o.add(p[0], p[1], p[2]), 7f, 0xFF8090C0, 0.07f);
		}
	}

	private static Vec3 surface(Vec3 center, double r, Vec3 dir) {
		return center.add(dir.normalize().scale(r));
	}

	private static final Vec3 LAUNCH_DIR = new Vec3(-0.5, 0.55, -0.65).normalize();
	private static final Vec3 TARGET_DIR = new Vec3(0.55, 0.5, -0.65).normalize();

	private static Vec3 missilePos(float u) {
		float s = (u - 0.12f) / 0.8f;
		if (s < 0) {
			return null;
		}
		s = Math.min(1f, s);
		Vec3 dir = slerp(LAUNCH_DIR, TARGET_DIR, s);
		double alt = MISSILE_R + 0.3 + 5 * Math.pow(Math.sin(Math.PI * s), 0.8);
		return MISSILE_EARTH.add(dir.scale(alt));
	}

	private static void missileStory(FxDraw d, MeshDraw mesh, float t, float u, Vec3 o) {
		earth(d, mesh, o.add(MISSILE_EARTH), MISSILE_R, t * 0.02);
		moon(mesh, o.add(MISSILE_EARTH).add(-20, 9, 14), 2.6);
		Vec3 launch = o.add(surface(MISSILE_EARTH, MISSILE_R, LAUNCH_DIR));
		Vec3 target = o.add(surface(MISSILE_EARTH, MISSILE_R, TARGET_DIR));
		float pulse = 0.6f + 0.4f * (float) Math.sin(t * 8);
		d.glowBall(target, 0.9f, 0xFFFF3B3B, pulse);
		if (u > 0.1f && u < 0.3f) {
			d.glowBall(launch, 2.2f * (0.3f - u) / 0.2f, 0xFFFFE0A0, 1f);
		}
		Vec3 rel = missilePos(u);
		if (rel == null) {
			return;
		}
		Vec3 pos = o.add(rel);
		Vec3 next = missilePos(Math.min(1f, u + 0.01f));
		Vec3 fwd = next == null || next.subtract(rel).lengthSqr() < 1e-8 ? LAUNCH_DIR : next.subtract(rel).normalize();

		Vec3 prev = null;
		for (int k = 0; k <= 24; k++) {
			Vec3 p = missilePos(Math.max(0.121f, u - k * 0.012f));
			if (p == null) {
				break;
			}
			Vec3 wp = o.add(p);
			if (prev != null) {
				float a = 0.7f * (1f - k / 24f);
				d.ribbon(prev, wp, 0.18f + k * 0.012f, 0.18f + (k + 1) * 0.012f, FxDraw.alpha(0xFFFFE8C8, a),
						FxDraw.alpha(0xFFB0B4C0, a * 0.8f));
			}
			prev = wp;
		}
		mesh.begin(pos, fwd, t * 2, 1.4);
		Models.draw(mesh, Fx.MODEL_ICBM, 0xFFEAEAEA);
		d.glowBall(pos.subtract(fwd.scale(0.75)), 0.55f, 0xFFFFC870, 0.9f + 0.1f * (float) Math.sin(t * 50));
	}

	private static final Vec3[] AST_PATH = {
			new Vec3(-48, 8, -36), new Vec3(-30, 5, -12), AST_MARS.add(5, 3, -4), new Vec3(4, 0, 22),
			AST_EARTH.add(new Vec3(-0.6, 0.3, -0.75).normalize().scale(AST_R * 1.02)), AST_EARTH};

	private static Vec3 asteroidPos(float u) {
		float s = 0.85f * u + 0.15f * u * u;
		int n = AST_PATH.length - 2;
		float f = Math.min(n - 1e-4f, s * n);
		int i = (int) f;
		float k = f - i;
		Vec3 p0 = AST_PATH[Math.max(0, i - 1)];
		Vec3 p1 = AST_PATH[i];
		Vec3 p2 = AST_PATH[i + 1];
		Vec3 p3 = AST_PATH[Math.min(AST_PATH.length - 1, i + 2)];
		return catmull(p0, p1, p2, p3, k);
	}

	private static void asteroidStory(FxDraw d, MeshDraw mesh, float t, float u, Vec3 o, int seed) {
		Vec3 earth = o.add(AST_EARTH);
		earth(d, mesh, earth, AST_R, t * 0.02);
		mars(mesh, o.add(AST_MARS), 4, t * 0.03);
		d.glowBall(o.add(AST_MARS), 4.6f, 0xFFFF9A6A, 0.12f);
		moon(mesh, earth.add(-16, 5, -8), 2.5);
		Vec3 pos = o.add(asteroidPos(u));
		double toEarth = pos.distanceTo(earth) - AST_R;
		float heat = (float) clamp01((float) (1 - toEarth / 9));
		float size = 0.9f;
		mesh.begin(pos, new Vec3(Math.sin(t * 0.7), Math.cos(t * 0.5), Math.sin(t * 0.3 + 1)), t * 0.8, 1);
		mesh.rock(size, seed, 0xFF2A221C, 0xFF5A4A3A, heat > 0.1f ? 0xFFFF8A2A : 0, 10, 16);

		Vec3 back = asteroidPos(Math.max(0f, u - 0.03f));
		Vec3 dir = asteroidPos(u).subtract(back);
		if (dir.lengthSqr() > 1e-6) {
			dir = dir.normalize();
			for (int k = 1; k <= 14; k++) {
				Vec3 p = pos.subtract(dir.scale(k * (0.6 + heat)));
				float a = (1f - k / 14f);
				int col = FxDraw.mix(0xFF9AA4B8, 0xFFFF7A2A, heat);
				d.glowBall(p, size * (0.6f + k * 0.12f) * (1 + heat), col, a * (0.25f + 0.6f * heat));
			}
		}
		if (heat > 0) {
			d.glowBall(pos, size * (1.8f + heat * 2.5f), 0xFFFFB050, heat);
			d.glowBall(pos, size * 1.2f, 0xFFFFF4D0, heat * 0.8f);
		}
	}

	private static void orbitalStory(FxDraw d, MeshDraw mesh, float t, float u, Vec3 o) {
		Vec3 earth = o.add(ORB_EARTH);
		earth(d, mesh, earth, ORB_R, t * 0.01);
		Vec3 sat = o.add(ORB_SAT);
		mesh.begin(sat, new Vec3(0, -1, 0), t * 0.1, 4.5);
		Models.satellite(mesh);
		Vec3 down = earth.subtract(sat).normalize();
		Vec3 ground = earth.subtract(down.scale(ORB_R));
		Vec3 emit = sat.add(down.scale(1.6));
		float charge = clamp01((u - 0.15f) / 0.55f);
		if (charge > 0) {
			float pulse = 0.8f + 0.2f * (float) Math.sin(t * (10 + charge * 30));
			d.glowBall(emit, 0.3f + charge * 1.4f, 0xFF9FE8FF, charge * pulse);
			for (int k = 0; k < 8; k++) {
				double a = t * (2 + charge * 6) + k * Math.PI / 4;
				Vec3 p = emit.add(Math.cos(a) * (2.2 - charge * 1.6), 0.3 * Math.sin(a * 2), Math.sin(a) * (2.2 - charge * 1.6));
				d.glowBall(p, 0.18f, 0xFFDDF4FF, charge);
			}
			d.glowBall(ground, 1.2f + charge, 0xFFFF3B3B, 0.5f + 0.5f * (float) Math.sin(t * 9));
		}
		if (u > 0.72f) {
			float f = clamp01((u - 0.72f) / 0.05f);
			d.beam(emit, ground, 0.5f + 0.6f * f, 0xFF9FE8FF, f);
			d.glowBall(ground, 3f + 4f * f, 0xFFDDF4FF, f);
		}
	}

	private static void earth(FxDraw d, MeshDraw mesh, Vec3 c, double r, double rot) {
		planet(mesh, c, r, rot, 40, 20, (lat, lon) -> {
			double n = Math.sin(3 * lon + 1.3) * Math.cos(2 * lat) + 0.6 * Math.sin(5 * lon - 2 * lat + 0.7)
					+ 0.35 * Math.sin(9 * lon + 4 * lat + 2.1) + 0.25 * Math.cos(13 * lon - 7 * lat);
			if (Math.abs(lat) > 1.22) {
				return 0xFFEFF6FF;
			}
			if (n > 0.38) {
				double dry = Math.sin(7 * lon + 3 * lat) + 0.5 * Math.cos(4 * lon);
				if (Math.abs(lat) < 0.5 && dry > 0.6) {
					return 0xFFC9A95E;
				}
				return n > 0.8 ? 0xFF2F6B2C : 0xFF3E8C3A;
			}
			return n > 0.1 ? 0xFF2A64B8 : 0xFF1D4E9E;
		});

		planet(mesh, c, r * 1.018, rot * 1.6 + 1, 40, 20, (lat, lon) -> {
			double n = Math.sin(6 * lon + 2 * lat + 0.4) * Math.cos(5 * lat - lon) + 0.5 * Math.sin(11 * lon - 6 * lat);
			return n > 0.75 ? 0xFFF2F6FA : 0;
		});
		d.glowBall(c, (float) (r * 1.12), 0xFF6FB4FF, 0.22f);
	}

	private static void mars(MeshDraw mesh, Vec3 c, double r, double rot) {
		planet(mesh, c, r, rot, 24, 12, (lat, lon) -> {
			if (Math.abs(lat) > 1.3) {
				return 0xFFF0E8E0;
			}
			double n = Math.sin(4 * lon + 0.5) * Math.cos(3 * lat) + 0.5 * Math.sin(9 * lon - 4 * lat);
			return n > 0.5 ? 0xFF7A3418 : n > 0 ? 0xFFB5552B : 0xFFC8693A;
		});
	}

	private static void moon(MeshDraw mesh, Vec3 c, double r) {
		planet(mesh, c, r, 0, 20, 10, (lat, lon) -> {
			double n = Math.sin(7 * lon + 1) * Math.cos(6 * lat) + 0.6 * Math.sin(13 * lon - 9 * lat);
			return n > 0.7 ? 0xFF8A8A8E : 0xFFB4B4B8;
		});
	}

	interface Surface {
		int color(double lat, double lon);
	}

	private static void planet(MeshDraw mesh, Vec3 c, double r, double rot, int lon, int lat, Surface surface) {
		mesh.begin(c, UP, ALIGN, 1);
		for (int i = 0; i < lat; i++) {
			double a0 = -Math.PI / 2 + Math.PI * i / lat;
			double a1 = -Math.PI / 2 + Math.PI * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double b0 = Math.PI * 2 * j / lon;
				double b1 = Math.PI * 2 * (j + 1) / lon;
				int color = surface.color((a0 + a1) / 2, (b0 + b1) / 2 + rot);
				if (color == 0) {
					continue;
				}
				double[] n0 = sph(a0, b0, 1);
				double[] n1 = sph(a0, b1, 1);
				double[] n2 = sph(a1, b1, 1);
				double[] n3 = sph(a1, b0, 1);
				mesh.quadN(scale(n0, r), scale(n1, r), scale(n2, r), scale(n3, r), n0, n1, n2, n3, color);
			}
		}
	}

	private static double[] sph(double lat, double lon, double r) {
		return new double[] {Math.cos(lat) * Math.cos(lon) * r, Math.sin(lat) * r, Math.cos(lat) * Math.sin(lon) * r};
	}

	private static double[] scale(double[] v, double k) {
		return new double[] {v[0] * k, v[1] * k, v[2] * k};
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
		return a.add(b.subtract(a).scale(k));
	}

	private static Vec3 slerp(Vec3 a, Vec3 b, double k) {
		double dot = Math.max(-1, Math.min(1, a.dot(b)));
		double th = Math.acos(dot) * k;
		Vec3 rel = b.subtract(a.scale(dot)).normalize();
		return a.scale(Math.cos(th)).add(rel.scale(Math.sin(th)));
	}

	private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return p0.scale(-0.5 * t3 + t2 - 0.5 * t)
				.add(p1.scale(1.5 * t3 - 2.5 * t2 + 1))
				.add(p2.scale(-1.5 * t3 + 2 * t2 + 0.5 * t))
				.add(p3.scale(0.5 * t3 - 0.5 * t2));
	}

	static double smooth(double x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}

	static float clamp01(float x) {
		return Math.max(0f, Math.min(1f, x));
	}

	private static float rnd(int seed, int i, int k) {
		return com.example.mymod.world.SpikeShape.hash(seed, i * 31 + 7, k * 131 + 3);
	}
}
