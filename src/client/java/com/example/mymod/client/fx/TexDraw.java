package com.example.mymod.client.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

final class TexDraw {
	private static final int LIGHT = 0xF000F0;

	private final Vec3 cam;

	private final Map<RenderType, List<float[]>> quads = new LinkedHashMap<>();

	TexDraw(Vec3 cam) {
		this.cam = cam;
	}

	void quad(RenderType type, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float[] u, float[] v, int c0, int c1, int c2, int c3) {
		float[] q = new float[24];
		Vec3[] p = {p0, p1, p2, p3};
		int[] c = {c0, c1, c2, c3};
		for (int k = 0; k < 4; k++) {
			q[k * 6] = (float) (p[k].x - cam.x);
			q[k * 6 + 1] = (float) (p[k].y - cam.y);
			q[k * 6 + 2] = (float) (p[k].z - cam.z);
			q[k * 6 + 3] = u[k];
			q[k * 6 + 4] = v[k];
			q[k * 6 + 5] = Float.intBitsToFloat(c[k]);
		}
		quads.computeIfAbsent(type, t -> new ArrayList<>()).add(q);
	}

	void flush(PoseStack pose, SubmitNodeCollector out) {
		for (Map.Entry<RenderType, List<float[]>> entry : quads.entrySet()) {
			List<float[]> list = List.copyOf(entry.getValue());
			out.submitCustomGeometry(pose, entry.getKey(), (p, vc) -> {
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
		quads.clear();
	}
}
