package com.example.mymod.client.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

final class Canvas {
	private static final int LIGHT = 0xF000F0;
	private static final float LAYER_STEP = 0.5f;

	private static final float FEATHER = 0.6f;

	private final PoseStack pose;
	private final SubmitNodeCollector out;
	private final Font font;

	private final List<float[]> quads = new ArrayList<>();
	private final Map<Identifier, List<float[]>> textured = new LinkedHashMap<>();

	private float gcx;
	private float gcy;
	private float gs = 1f;
	private float gdx;
	private float gdy;
	private float gdz;
	private float galpha = 1f;
	private float globalAlpha = 1f;

	Canvas(PoseStack pose, SubmitNodeCollector out, Font font) {
		this.pose = pose;
		this.out = out;
		this.font = font;
	}

	Font font() {
		return font;
	}

	static float z(int layer) {
		return layer * LAYER_STEP;
	}

	void group(float cx, float cy, float scale, float dx, float dy, float dz, float alpha) {
		gcx = cx;
		gcy = cy;
		gs = scale;
		gdx = dx;
		gdy = dy;
		gdz = dz;
		galpha = Math.max(0f, Math.min(1f, alpha));
	}

	void endGroup() {
		group(0, 0, 1f, 0, 0, 0, 1f);
	}

	void setGlobalAlpha(float alpha) {
		globalAlpha = Math.max(0f, Math.min(1f, alpha));
	}

	private float tx(float x) {
		return gcx + (x - gcx) * gs + gdx;
	}

	private float ty(float y) {
		return gcy + (y - gcy) * gs + gdy;
	}

	private float alpha() {
		return galpha * globalAlpha;
	}

	private int col(int color) {
		return MenuAnim.withAlpha(color, alpha());
	}

	void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
			int ca, int cb, int cc, int cd, int layer) {
		if (alpha() <= 0.003f) {
			return;
		}
		quads.add(new float[] {
				tx(ax), ty(ay), tx(bx), ty(by), tx(cx), ty(cy), tx(dx), ty(dy), z(layer) + gdz,
				Float.intBitsToFloat(col(ca)), Float.intBitsToFloat(col(cb)),
				Float.intBitsToFloat(col(cc)), Float.intBitsToFloat(col(cd))
		});
	}

	void rect(float x1, float y1, float x2, float y2, int color, int layer) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		quad(x1, y1, x2, y1, x2, y2, x1, y2, color, color, color, color, layer);
	}

	private static int segments(float r) {
		return Math.max(3, Math.min(12, (int) Math.ceil(r * 1.4f)));
	}

	void roundRect(float x, float y, float w, float h, float r, int color, int layer) {
		fillRoundRect(x, y, w, h, r, color, layer, true);
	}

	void shadow(float x, float y, float w, float h, float r, int color, float size, int layer) {
		fillRoundRect(x, y, w, h, r, color, layer, false);
		glow(x, y, w, h, r, color, size, layer);
	}

	private void fillRoundRect(float x, float y, float w, float h, float r, int color, int layer, boolean feather) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
		if (r < 0.05f) {
			rect(x, y, x + w, y + h, color, layer);
			return;
		}
		int n = segments(r);
		double step = Math.PI / 2 / n;

		for (int j = 0; j < n; j++) {
			double a0 = step * j;
			double a1 = step * (j + 1);
			float s0 = (float) Math.sin(a0);
			float s1 = (float) Math.sin(a1);
			float c0 = (float) Math.cos(a0);
			float c1 = (float) Math.cos(a1);

			float ty0 = y + r - r * c0;
			float ty1 = y + r - r * c1;
			float tl0 = x + r - r * s0;
			float tl1 = x + r - r * s1;
			float tr0 = x + w - r + r * s0;
			float tr1 = x + w - r + r * s1;
			quad(tl0, ty0, tr0, ty0, tr1, ty1, tl1, ty1, color, color, color, color, layer);

			float by0 = y + h - r + r * c0;
			float by1 = y + h - r + r * c1;
			quad(tl1, by1, tr1, by1, tr0, by0, tl0, by0, color, color, color, color, layer);
		}
		rect(x, y + r, x + w, y + h - r, color, layer);

		if (feather) {
			edge(x, y, w, h, r, color, color & 0x00FFFFFF, FEATHER, layer);
		}
	}

	void edge(float x, float y, float w, float h, float r, int inner, int outer, float size, int layer) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
		float[] pts = perimeter(x, y, w, h, r);
		int count = pts.length / 4;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			float px = pts[i * 4];
			float py = pts[i * 4 + 1];
			float nx = pts[i * 4 + 2];
			float ny = pts[i * 4 + 3];
			float qx = pts[j * 4];
			float qy = pts[j * 4 + 1];
			float mx = pts[j * 4 + 2];
			float my = pts[j * 4 + 3];
			quad(px, py, qx, qy, qx + mx * size, qy + my * size, px + nx * size, py + ny * size,
					inner, inner, outer, outer, layer);
		}
	}

	void glow(float x, float y, float w, float h, float r, int color, float size, int layer) {
		edge(x, y, w, h, r, color, color & 0x00FFFFFF, size, layer);
	}

	private static float[] perimeter(float x, float y, float w, float h, float r) {
		int n = r < 0.05f ? 1 : segments(r);
		float[][] corners = {
				{x + r, y + r, (float) Math.PI},
				{x + w - r, y + r, (float) (Math.PI * 1.5)},
				{x + w - r, y + h - r, 0f},
				{x + r, y + h - r, (float) (Math.PI * 0.5)}
		};
		float[] pts = new float[4 * (n + 1) * 4];
		int k = 0;
		for (float[] c : corners) {
			for (int j = 0; j <= n; j++) {
				double a = c[2] + Math.PI / 2 * j / n;
				float dx = (float) Math.cos(a);
				float dy = (float) Math.sin(a);
				pts[k++] = c[0] + dx * r;
				pts[k++] = c[1] + dy * r;
				pts[k++] = dx;
				pts[k++] = dy;
			}
		}
		return pts;
	}

	void ring(float cx, float cy, float radius, float thickness, int color, int layer) {
		int segments = 40;
		float inner = radius - thickness;
		int clear = color & 0x00FFFFFF;
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			quad(cx + c0 * inner, cy + s0 * inner, cx + c0 * radius, cy + s0 * radius,
					cx + c1 * radius, cy + s1 * radius, cx + c1 * inner, cy + s1 * inner,
					color, color, color, color, layer);
			float out = radius + FEATHER;
			quad(cx + c0 * radius, cy + s0 * radius, cx + c0 * out, cy + s0 * out,
					cx + c1 * out, cy + s1 * out, cx + c1 * radius, cy + s1 * radius,
					color, clear, clear, color, layer);
			float in = inner - FEATHER;
			quad(cx + c0 * in, cy + s0 * in, cx + c0 * inner, cy + s0 * inner,
					cx + c1 * inner, cy + s1 * inner, cx + c1 * in, cy + s1 * in,
					clear, color, color, clear, layer);
		}
	}

	void image(Identifier texture, float x, float y, float w, float h, int layer) {
		if (alpha() <= 0.003f) {
			return;
		}
		textured.computeIfAbsent(texture, t -> new ArrayList<>()).add(new float[] {
				tx(x), ty(y), tx(x + w), ty(y + h), z(layer) + gdz, Float.intBitsToFloat(col(0xFFFFFFFF))
		});
	}

	void text(FormattedCharSequence text, float x, float y, int color, int layer, float scale, boolean shadow) {
		int c = col(color);
		if ((c >>> 24) < 26) {
			return;
		}
		pose.pushPose();
		pose.translate(tx(x), ty(y), z(layer) + gdz);
		float sc = scale * gs;
		if (sc != 1f) {
			pose.scale(sc, sc, 1f);
		}
		out.submitText(pose, 0, 0, text, shadow, Font.DisplayMode.NORMAL, LIGHT, c, 0, 0);
		pose.popPose();
	}

	void text(String text, float x, float y, int color, int layer) {
		text(Component.literal(text).getVisualOrderText(), x, y, color, layer, 1f, false);
	}

	void smallText(String text, float x, float y, int color, int layer) {
		text(Component.literal(text).getVisualOrderText(), x, y, color, layer, 0.75f, false);
	}

	int width(String text) {
		return font.width(text);
	}

	void spacedText(String s, float cx, float y, int color, int layer, float spacing, boolean shadow) {
		float total = 0;
		for (int i = 0; i < s.length(); i++) {
			total += font.width(String.valueOf(s.charAt(i))) + spacing;
		}
		float x = cx - (total - spacing) / 2f;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			text(Component.literal(ch).getVisualOrderText(), x, y, color, layer, 1f, shadow);
			x += font.width(ch) + spacing;
		}
	}

	void flush() {
		if (!quads.isEmpty()) {
			List<float[]> list = List.copyOf(quads);
			out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> {
				for (float[] q : list) {
					emitColorQuad(p, vc, q);
				}
			});
		}
		for (Map.Entry<Identifier, List<float[]>> entry : textured.entrySet()) {
			List<float[]> list = List.copyOf(entry.getValue());
			out.submitCustomGeometry(pose, RenderTypes.text(entry.getKey()), (p, vc) -> {
				for (float[] q : list) {
					emitTexturedQuad(p, vc, q[0], q[1], q[2], q[3], q[4], Float.floatToRawIntBits(q[5]));
				}
			});
		}
		quads.clear();
		textured.clear();
	}

	private static void emitColorQuad(PoseStack.Pose p, VertexConsumer vc, float[] q) {
		float z = q[8];
		int ca = Float.floatToRawIntBits(q[9]);
		int cb = Float.floatToRawIntBits(q[10]);
		int cc = Float.floatToRawIntBits(q[11]);
		int cd = Float.floatToRawIntBits(q[12]);

		vc.addVertex(p, q[0], q[1], z).setColor(ca).setLight(LIGHT);
		vc.addVertex(p, q[6], q[7], z).setColor(cd).setLight(LIGHT);
		vc.addVertex(p, q[4], q[5], z).setColor(cc).setLight(LIGHT);
		vc.addVertex(p, q[2], q[3], z).setColor(cb).setLight(LIGHT);

		vc.addVertex(p, q[0], q[1], z).setColor(ca).setLight(LIGHT);
		vc.addVertex(p, q[2], q[3], z).setColor(cb).setLight(LIGHT);
		vc.addVertex(p, q[4], q[5], z).setColor(cc).setLight(LIGHT);
		vc.addVertex(p, q[6], q[7], z).setColor(cd).setLight(LIGHT);
	}

	private static void emitTexturedQuad(PoseStack.Pose p, VertexConsumer vc,
			float x1, float y1, float x2, float y2, float z, int white) {
		vc.addVertex(p, x1, y1, z).setColor(white).setUv(0, 0).setLight(LIGHT);
		vc.addVertex(p, x1, y2, z).setColor(white).setUv(0, 1).setLight(LIGHT);
		vc.addVertex(p, x2, y2, z).setColor(white).setUv(1, 1).setLight(LIGHT);
		vc.addVertex(p, x2, y1, z).setColor(white).setUv(1, 0).setLight(LIGHT);

		vc.addVertex(p, x1, y1, z).setColor(white).setUv(0, 0).setLight(LIGHT);
		vc.addVertex(p, x2, y1, z).setColor(white).setUv(1, 0).setLight(LIGHT);
		vc.addVertex(p, x2, y2, z).setColor(white).setUv(1, 1).setLight(LIGHT);
		vc.addVertex(p, x1, y2, z).setColor(white).setUv(0, 1).setLight(LIGHT);
	}
}
