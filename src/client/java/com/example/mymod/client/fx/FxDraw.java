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

import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FxDraw {
	static final int WHITE = 0xFFFFFFFF;
	private static final int LIGHT = 0xF000F0;

	private final Vec3 cam;

	final Vec3 right;
	final Vec3 up;

	private final List<float[]> additive = new ArrayList<>();
	private final List<float[]> normal = new ArrayList<>();

	static final Identifier SMOKE = MyMod.id("textures/fx/smoke.png");
	static final Identifier FIRE = MyMod.id("textures/fx/fire.png");

	private final List<float[]> smokeQuads = new ArrayList<>();
	private final List<float[]> fireQuads = new ArrayList<>();

	FxDraw(Vec3 cam, Quaternionf orientation) {
		this.cam = cam;
		Vector3f r = orientation.transform(new Vector3f(1, 0, 0));
		Vector3f u = orientation.transform(new Vector3f(0, 1, 0));
		this.right = new Vec3(r.x, r.y, r.z);
		this.up = new Vec3(u.x, u.y, u.z);
	}

	Vec3 camera() {
		return cam;
	}

	static int alpha(int color, float alpha) {
		int a = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (color & 0x00FFFFFF);
	}

	static int clear(int color) {
		return color & 0x00FFFFFF;
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24)
				| (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd, boolean glow) {
		if (((ca | cb | cc | cd) >>> 24) == 0) {
			return;
		}
		float ax = (float) (a.x - cam.x), ay = (float) (a.y - cam.y), az = (float) (a.z - cam.z);
		float bx = (float) (b.x - cam.x), by = (float) (b.y - cam.y), bz = (float) (b.z - cam.z);
		float cx = (float) (c.x - cam.x), cy = (float) (c.y - cam.y), cz = (float) (c.z - cam.z);
		float dx = (float) (d.x - cam.x), dy = (float) (d.y - cam.y), dz = (float) (d.z - cam.z);

		float ux = bx - ax, uy = by - ay, uz = bz - az;
		float vx = cx - ax, vy = cy - ay, vz = cz - az;
		float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		boolean facing = -(nx * ax + ny * ay + nz * az) >= 0;

		float[] q = facing
				? new float[] {ax, ay, az, Float.intBitsToFloat(ca), bx, by, bz, Float.intBitsToFloat(cb),
						cx, cy, cz, Float.intBitsToFloat(cc), dx, dy, dz, Float.intBitsToFloat(cd)}
				: new float[] {dx, dy, dz, Float.intBitsToFloat(cd), cx, cy, cz, Float.intBitsToFloat(cc),
						bx, by, bz, Float.intBitsToFloat(cb), ax, ay, az, Float.intBitsToFloat(ca)};
		(glow ? additive : normal).add(q);
	}

	void ribbon(Vec3 a, Vec3 b, float wa, float wb, int colorA, int colorB) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1e-8) {
			return;
		}
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 side = dir.cross(mid.subtract(cam));
		if (side.lengthSqr() < 1e-8) {
			side = right;
		}
		side = side.normalize();
		Vec3 sa = side.scale(wa);
		Vec3 sb = side.scale(wb);
		quad(a.add(sa), b.add(sb), b, a, clear(colorA), clear(colorB), colorB, colorA, true);
		quad(a, b, b.subtract(sb), a.subtract(sa), colorA, colorB, clear(colorB), clear(colorA), true);
	}

	void beam(Vec3 a, Vec3 b, float width, int color, float intensity) {
		ribbon(a, b, width * 2.6f, width * 2.6f, alpha(color, 0.30f * intensity), alpha(color, 0.30f * intensity));
		ribbon(a, b, width, width, alpha(color, 0.85f * intensity), alpha(color, 0.85f * intensity));
		ribbon(a, b, width * 0.32f, width * 0.32f, alpha(WHITE, intensity), alpha(WHITE, intensity));
	}

	void disc(Vec3 c, float radius, int center, int edge, boolean glow) {
		disc(c, radius, center, edge, glow, 28);
	}

	void disc(Vec3 c, float radius, int center, int edge, boolean glow, int seg) {
		Vec3 prev = c.add(right.scale(radius));
		for (int i = 1; i <= seg; i++) {
			double ang = Math.PI * 2 * i / seg;
			Vec3 p = c.add(right.scale(Math.cos(ang) * radius)).add(up.scale(Math.sin(ang) * radius));
			quad(c, prev, p, c, center, edge, edge, center, glow);
			prev = p;
		}
	}

	void puff(Vec3 c, float radius, int light, int dark, float alpha, int seed, int seg) {
		if (alpha <= 0.004f || radius <= 0.01f) {
			return;
		}
		alpha = Math.min(1f, alpha);
		float p1 = (seed & 0xFF) / 40f;
		float p2 = ((seed >>> 8) & 0xFF) / 40f;
		float p3 = ((seed >>> 16) & 0xFF) / 40f;
		float inner = 0.6f;
		int centerCol = alpha(mix(dark, light, 0.6f), alpha);

		Vec3 prevMid = null;
		Vec3 prevOut = null;
		int prevCol = 0;
		for (int i = 0; i <= seg; i++) {
			double ang = Math.PI * 2 * i / seg;
			float lump = 1f + 0.13f * (float) Math.sin(3 * ang + p1) + 0.08f * (float) Math.sin(5 * ang + p2)
					+ 0.05f * (float) Math.sin(7 * ang + p3);
			Vec3 dir = right.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));

			float shade = 0.5f + 0.5f * (float) dir.y;
			int col = mix(dark, light, shade);
			Vec3 mid = c.add(dir.scale(radius * inner * lump));
			Vec3 out = c.add(dir.scale(radius * lump));
			if (prevMid != null) {
				int a1 = alpha(prevCol, alpha * 0.95f);
				int a2 = alpha(col, alpha * 0.95f);
				quad(c, prevMid, mid, c, centerCol, a1, a2, centerCol, false);
				quad(prevMid, prevOut, out, mid, a1, clear(prevCol), clear(col), a2, false);
			}
			prevMid = mid;
			prevOut = out;
			prevCol = col;
		}
	}

	void square(Vec3 c, float half, float angle, int color, boolean glow) {
		Vec3 a = right.scale(Math.cos(angle) * half).add(up.scale(Math.sin(angle) * half));
		Vec3 b = right.scale(-Math.sin(angle) * half).add(up.scale(Math.cos(angle) * half));
		quad(c.add(a).add(b), c.subtract(a).add(b), c.subtract(a).subtract(b), c.add(a).subtract(b),
				color, color, color, color, glow);
	}

	void glowBall(Vec3 c, float radius, int color, float intensity) {
		if (radius <= 0.001f || intensity <= 0.002f) {
			return;
		}
		disc(c, radius * 1.8f, alpha(color, 0.35f * intensity), clear(color), true);
		disc(c, radius, alpha(color, 0.9f * intensity), clear(color), true);
		disc(c, radius * 0.45f, alpha(WHITE, intensity), clear(WHITE), true);
	}

	void billboardRing(Vec3 c, float radius, float thickness, int color) {
		ring(c, right, up, radius, thickness, color, 48, 0, 0, 0);
	}

	void ring(Vec3 c, Vec3 u, Vec3 v, float radius, float thickness, int color, int seg,
			int wave, float waveSpeed, float time) {
		if (radius <= 0.001f) {
			return;
		}
		float rin = Math.max(0, radius - thickness);
		float rout = radius + thickness;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg;
			double a1 = Math.PI * 2 * (i + 1) / seg;
			int c0 = color;
			int c1 = color;
			if (wave > 0) {
				c0 = alpha(color, 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(wave * a0 - time * waveSpeed)));
				c1 = alpha(color, 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(wave * a1 - time * waveSpeed)));
			}
			Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
			Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
			Vec3 m0 = c.add(d0.scale(radius));
			Vec3 m1 = c.add(d1.scale(radius));
			quad(c.add(d0.scale(rin)), c.add(d1.scale(rin)), m1, m0, clear(c0), clear(c1), c1, c0, true);
			quad(m0, m1, c.add(d1.scale(rout)), c.add(d0.scale(rout)), c0, c1, clear(c1), clear(c0), true);
		}
	}

	void groundRing(Vec3 c, float radius, float thickness, int color) {
		ring(c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, thickness, color, 64, 0, 0, 0);
	}

	void flatDisc(Vec3 c, Vec3 u, Vec3 v, float r0, float r1, int inner, int outer, int seg,
			int wave, float waveSpeed, float time) {
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg;
			double a1 = Math.PI * 2 * (i + 1) / seg;
			float w0 = 1f;
			float w1 = 1f;
			if (wave > 0) {
				w0 = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(wave * a0 - time * waveSpeed));
				w1 = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(wave * a1 - time * waveSpeed));
			}
			Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
			Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
			quad(c.add(d0.scale(r0)), c.add(d1.scale(r0)), c.add(d1.scale(r1)), c.add(d0.scale(r1)),
					alpha(inner, w0), alpha(inner, w1), alpha(outer, w1), alpha(outer, w0), true);
		}
	}

	void darkDisc(Vec3 c, float radius) {
		int seg = 36;
		int black = 0xFF000000;
		float inner = radius * 0.86f;
		Vec3 prevIn = c.add(right.scale(inner));
		Vec3 prevOut = c.add(right.scale(radius));
		for (int i = 1; i <= seg; i++) {
			double ang = Math.PI * 2 * i / seg;
			Vec3 d = right.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
			Vec3 pin = c.add(d.scale(inner));
			Vec3 pout = c.add(d.scale(radius));
			quad(c, prevIn, pin, c, black, black, black, black, false);
			quad(prevIn, prevOut, pout, pin, black, clear(black), clear(black), black, false);
			prevIn = pin;
			prevOut = pout;
		}
	}

	private static float depth(float[] q) {
		float x = (q[0] + q[4] + q[8] + q[12]) * 0.25f;
		float y = (q[1] + q[5] + q[9] + q[13]) * 0.25f;
		float z = (q[2] + q[6] + q[10] + q[14]) * 0.25f;
		return x * x + y * y + z * z;
	}

	void smoke(Vec3 c, float radius, float rotation, int variant, int light, int dark, float alpha) {
		if (alpha <= 0.01f || radius <= 0.01f) {
			return;
		}
		double dist = c.distanceTo(cam);
		alpha *= (float) Math.max(0, Math.min(1, (dist - radius * 0.35) / (radius * 0.9)));
		if (alpha <= 0.01f) {
			return;
		}
		alpha = Math.min(1f, alpha);
		Vec3 back = c.subtract(cam).normalize().scale(radius * 0.08);
		sprite(smokeQuads, c.add(back), radius * 1.45f, rotation * 0.5f, 6 + (variant & 1), light, dark, alpha * 0.3f, false);
		sprite(smokeQuads, c, radius, rotation, Math.floorMod(variant, 6), light, dark, alpha, false);
	}

	void fire(Vec3 c, float radius, float rotation, int variant, int color, float intensity) {
		if (intensity <= 0.01f || radius <= 0.01f) {
			return;
		}
		double dist = c.distanceTo(cam);
		intensity *= (float) Math.max(0, Math.min(1, (dist - radius * 0.15) / (radius * 0.5)));
		if (intensity <= 0.01f) {
			return;
		}
		sprite(fireQuads, c, radius, rotation, Math.floorMod(variant, 6), color, color, Math.min(1f, intensity), true);
	}

	private void sprite(List<float[]> out, Vec3 c, float r, float rotation, int variant, int top, int bottom,
			float alpha, boolean premultiplied) {
		double cs = Math.cos(rotation);
		double sn = Math.sin(rotation);
		Vec3 ru = right.scale(cs).add(up.scale(sn)).scale(r);
		Vec3 uu = up.scale(cs).subtract(right.scale(sn)).scale(r);
		Vec3[] corners = {c.subtract(ru).subtract(uu), c.add(ru).subtract(uu), c.add(ru).add(uu), c.subtract(ru).add(uu)};
		float u0 = (variant % 4) * 0.25f;
		float v0 = (variant / 4) * 0.5f;
		float[][] uv = {{u0, v0 + 0.5f}, {u0 + 0.25f, v0 + 0.5f}, {u0 + 0.25f, v0}, {u0, v0}};
		float[] q = new float[24];
		for (int k = 0; k < 4; k++) {
			Vec3 p = corners[k];
			float shade = (float) Math.max(0, Math.min(1, 0.5 + (p.y - c.y) / (2 * r)));
			int col = mix(bottom, top, shade);
			int color;
			if (premultiplied) {
				int rr = Math.round(((col >> 16) & 0xFF) * alpha);
				int gg = Math.round(((col >> 8) & 0xFF) * alpha);
				int bb = Math.round((col & 0xFF) * alpha);
				color = (Math.round(alpha * 255) << 24) | (rr << 16) | (gg << 8) | bb;
			} else {
				color = alpha(col | 0xFF000000, alpha);
			}
			q[k * 6] = (float) (p.x - cam.x);
			q[k * 6 + 1] = (float) (p.y - cam.y);
			q[k * 6 + 2] = (float) (p.z - cam.z);
			q[k * 6 + 3] = uv[k][0];
			q[k * 6 + 4] = uv[k][1];
			q[k * 6 + 5] = Float.intBitsToFloat(color);
		}
		out.add(q);
	}

	private static float spriteDepth(float[] q) {
		float x = (q[0] + q[12]) * 0.5f;
		float y = (q[1] + q[13]) * 0.5f;
		float z = (q[2] + q[14]) * 0.5f;
		return x * x + y * y + z * z;
	}

	void flush(PoseStack pose, SubmitNodeCollector out) {
		if (!normal.isEmpty()) {
			normal.sort((q1, q2) -> Float.compare(depth(q2), depth(q1)));
			List<float[]> list = List.copyOf(normal);
			out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> {
				for (float[] q : list) {
					for (int k = 0; k < 4; k++) {
						vc.addVertex(p, q[k * 4], q[k * 4 + 1], q[k * 4 + 2])
								.setColor(Float.floatToRawIntBits(q[k * 4 + 3])).setLight(LIGHT);
					}
				}
			});
		}
		if (!additive.isEmpty()) {
			List<float[]> list = List.copyOf(additive);
			out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
				for (float[] q : list) {
					for (int k = 0; k < 4; k++) {
						vc.addVertex(p, q[k * 4], q[k * 4 + 1], q[k * 4 + 2])
								.setColor(Float.floatToRawIntBits(q[k * 4 + 3]));
					}
				}
			});
		}
		if (!smokeQuads.isEmpty()) {
			smokeQuads.sort((a, b) -> Float.compare(spriteDepth(b), spriteDepth(a)));
			List<float[]> list = List.copyOf(smokeQuads);

			out.submitCustomGeometry(pose, RenderTypes.entityShadow(SMOKE), (p, vc) -> {
				for (float[] q : list) {
					for (int k = 0; k < 4; k++) {
						vc.addVertex(p, q[k * 6], q[k * 6 + 1], q[k * 6 + 2])
								.setColor(Float.floatToRawIntBits(q[k * 6 + 5]))
								.setUv(q[k * 6 + 3], q[k * 6 + 4])
								.setOverlay(OverlayTexture.NO_OVERLAY)
								.setLight(LIGHT)
								.setNormal(p, 0f, 1f, 0f);
					}
				}
			});
		}
		if (!fireQuads.isEmpty()) {
			List<float[]> list = List.copyOf(fireQuads);
			out.submitCustomGeometry(pose, RenderTypes.energySwirl(FIRE, 0f, 0f), (p, vc) -> {
				for (float[] q : list) {
					for (int k = 0; k < 4; k++) {
						vc.addVertex(p, q[k * 6], q[k * 6 + 1], q[k * 6 + 2])
								.setColor(Float.floatToRawIntBits(q[k * 6 + 5]))
								.setUv(q[k * 6 + 3], q[k * 6 + 4])
								.setOverlay(OverlayTexture.NO_OVERLAY)
								.setLight(LIGHT)
								.setNormal(p, 0f, 1f, 0f);
					}
				}
			});
		}
		normal.clear();
		additive.clear();
		smokeQuads.clear();
		fireQuads.clear();
	}
}
