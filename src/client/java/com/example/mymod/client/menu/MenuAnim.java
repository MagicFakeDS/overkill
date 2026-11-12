package com.example.mymod.client.menu;

import java.util.Arrays;

import com.example.mymod.client.RemoteSettings;
import com.example.mymod.skill.Skill;
import com.example.mymod.skill.Skills;

final class MenuAnim {
	private static final int MAX = 512;

	private static long lastFrame;

	static float dt;

	static final float[] rowHover = new float[MAX];
	static final float[] rowY = new float[MAX];
	static final float[] rowFlash = new float[MAX];
	static final float[] toggleHover = new float[5];
	static final float[] toggleKnob = new float[5];
	static final float[] sliderShown = new float[3];
	static float detailsAppear;
	static int detailsFor = -2;
	static float cursorActive;

	static float scrollAnim;

	private MenuAnim() {
	}

	static void beginFrame() {
		long now = System.nanoTime();
		dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
	}

	static float approach(float current, float target, float speed) {
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	static void reset(MenuLayout layout, RemoteSettings settings) {
		Arrays.fill(rowHover, 0f);
		Arrays.fill(toggleHover, 0f);
		for (int i = 0; i < layout.rows.size() && i < MAX; i++) {
			rowY[i] = layout.rows.get(i).y();
		}
		for (int i = 0; i < toggleKnob.length; i++) {
			toggleKnob[i] = settings.toggle(i) ? 1f : 0f;
		}
		detailsFor = layout.expanded;
		detailsAppear = 0f;
		syncSliders(layout, settings);
		cursorActive = 0f;
		scrollAnim = layout.scroll;
	}

	static void flash(int row) {
		if (row >= 0 && row < MAX) {
			rowFlash[row] = 1f;
		}
	}

	static void syncSliders(MenuLayout layout, RemoteSettings settings) {
		if (layout.expanded >= 0) {
			Skill skill = Skills.ALL.get(layout.expanded);
			float[] v = settings.values(skill);
			System.arraycopy(v, 0, sliderShown, 0, 3);
		}
	}

	static void update(MenuLayout layout, RemoteSettings settings, MenuLayout.Hit hovered, boolean interactive) {
		for (int i = 0; i < layout.rows.size() && i < MAX; i++) {
			boolean hover = interactive && hovered.kind() == MenuLayout.Kind.ROW && hovered.index() == i;
			rowHover[i] = approach(rowHover[i], hover ? 1f : 0f, 16f);
			rowY[i] = approach(rowY[i], layout.rows.get(i).y(), 14f);
			rowFlash[i] = approach(rowFlash[i], 0f, 5f);
		}
		for (int i = 0; i < toggleKnob.length; i++) {
			boolean hover = interactive && hovered.kind() == MenuLayout.Kind.TOGGLE && hovered.index() == i;
			toggleHover[i] = approach(toggleHover[i], hover ? 1f : 0f, 16f);
			toggleKnob[i] = approach(toggleKnob[i], settings.toggle(i) ? 1f : 0f, 18f);
		}

		if (detailsFor != layout.expanded) {
			detailsFor = layout.expanded;
			detailsAppear = 0f;
			syncSliders(layout, settings);
		}
		detailsAppear = approach(detailsAppear, 1f, 12f);

		if (layout.expanded >= 0) {
			float[] v = settings.values(Skills.ALL.get(layout.expanded));
			for (int k = 0; k < 3; k++) {
				sliderShown[k] = approach(sliderShown[k], v[k], 22f);
			}
		}

		scrollAnim = approach(scrollAnim, layout.scroll, 16f);

		boolean active = interactive && hovered.kind() != MenuLayout.Kind.NONE && hovered.kind() != MenuLayout.Kind.PANEL;
		cursorActive = approach(cursorActive, active ? 1f : 0f, 18f);
	}

	static float easeOutBack(float x) {
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float t = x - 1f;
		return 1f + c3 * t * t * t + c1 * t * t;
	}

	static float easeOutCubic(float x) {
		float t = 1f - x;
		return 1f - t * t * t;
	}

	static float easeInCubic(float x) {
		return x * x * x;
	}

	static float stagger(float time, float delay, float duration) {
		return Math.max(0f, Math.min(1f, (time - delay) / duration));
	}

	static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24)
				| (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	static int withAlpha(int color, float alpha) {
		int a = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (color & 0x00FFFFFF);
	}
}
