package com.example.mymod.client.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.example.mymod.skill.Skill;
import com.example.mymod.skill.Skills;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class MenuLayout {
	public static final int LEFT_W = 146;
	public static final int GAP = 12;
	public static final int RIGHT_W = 218;
	public static final int TOTAL_W = LEFT_W + GAP + RIGHT_W;
	public static final int TOP = 22;
	public static final int CARD_H = 142;
	public static final int TOGGLE_INDENT = 10;
	public static final int TOGGLE_H = 15;
	public static final int TOGGLE_GAP = 3;
	public static final int ROW_H = 22;
	public static final int ROW_GAP = 3;
	public static final int SLIDER_H = 12;
	public static final int SLIDER_LABEL_W = 52;
	public static final int SLIDER_VALUE_W = 34;
	public static final int HINT_H = 14;

	public static final int VISIBLE_ROWS = 8;
	public static final int ROW_STEP = ROW_H + ROW_GAP;
	public static final float LIST_TOP = TOP;
	public static final float LIST_BOTTOM = TOP + VISIBLE_ROWS * ROW_STEP - ROW_GAP;

	public static final float PIVOT_X = TOTAL_W / 2f;
	public static final float PIVOT_Y = 148f;

	public enum Kind { NONE, PANEL, ROW, TOGGLE, SLIDER }

	public record Hit(Kind kind, int index) {
		public static final Hit NONE = new Hit(Kind.NONE, -1);
		public static final Hit PANEL = new Hit(Kind.PANEL, -1);
	}

	public record Rect(float x, float y, float w, float h) {
		public boolean contains(float mx, float my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		public float right() {
			return x + w;
		}

		public float bottom() {
			return y + h;
		}
	}

	public Rect card;
	public final Rect[] toggles = new Rect[5];
	public final List<Rect> rows = new ArrayList<>();
	public Rect details;
	public final Rect[] sliders = new Rect[3];
	public List<FormattedCharSequence> detailLines = List.of();
	public Rect hint;
	public int expanded = -1;

	public int scroll;

	public int maxScroll() {
		return Math.max(0, rows.size() - VISIBLE_ROWS);
	}

	public Rect rowAt(int i, float scrollRows) {
		Rect r = rows.get(i);
		return new Rect(r.x(), r.y() - scrollRows * ROW_STEP, r.w(), r.h());
	}

	public boolean rowVisible(int i) {
		Rect r = rowAt(i, scroll);
		return r.y() >= LIST_TOP - 0.5f && r.bottom() <= LIST_BOTTOM + 0.5f;
	}

	public void rebuild(Font font, int expanded, Component hintText) {
		this.expanded = expanded;
		card = new Rect(0, TOP, LEFT_W, CARD_H);
		float ty = card.bottom() + 6;
		for (int i = 0; i < toggles.length; i++) {
			toggles[i] = new Rect(TOGGLE_INDENT, ty, LEFT_W - TOGGLE_INDENT, TOGGLE_H);
			ty += TOGGLE_H + TOGGLE_GAP;
		}
		float leftBottom = ty - TOGGLE_GAP;

		float rx = LEFT_W + GAP;
		rows.clear();
		details = null;
		Arrays.fill(sliders, null);
		detailLines = List.of();

		for (int i = 0; i < Skills.ALL.size(); i++) {
			rows.add(new Rect(rx, TOP + i * ROW_STEP, RIGHT_W, ROW_H));
		}
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		float rightBottom = Math.min(LIST_BOTTOM, TOP + rows.size() * ROW_STEP - ROW_GAP);

		if (expanded >= 0 && expanded < Skills.ALL.size()) {
			Skill skill = Skills.ALL.get(expanded);
			float dy = rightBottom + 6;
			detailLines = font.split(Component.translatable(skill.descKey()), RIGHT_W - 14);
			int sliderCount = skill.speed() == null ? 2 : 3;
			float sliderTop = dy + 5 + detailLines.size() * 9 + 3;
			for (int k = 0; k < sliderCount; k++) {
				sliders[k] = new Rect(rx + 7, sliderTop + k * SLIDER_H, RIGHT_W - 14, SLIDER_H);
			}
			float dh = (sliderTop - dy) + sliderCount * SLIDER_H + 4;
			details = new Rect(rx, dy, RIGHT_W, dh);
			rightBottom = details.bottom();
		}

		float hintW = font.width(hintText) + 16;
		hint = new Rect((TOTAL_W - hintW) / 2f, Math.max(leftBottom, rightBottom) + 10, hintW, HINT_H);
	}

	public Hit hitTest(float mx, float my) {
		for (int k = 0; k < sliders.length; k++) {
			if (sliders[k] != null && sliders[k].contains(mx, my)) {
				return new Hit(Kind.SLIDER, k);
			}
		}
		for (int i = 0; i < toggles.length; i++) {
			if (toggles[i].contains(mx, my)) {
				return new Hit(Kind.TOGGLE, i);
			}
		}
		for (int i = 0; i < rows.size(); i++) {
			if (rowVisible(i) && rowAt(i, scroll).contains(mx, my)) {
				return new Hit(Kind.ROW, i);
			}
		}
		if (card.contains(mx, my) || (details != null && details.contains(mx, my)) || hint.contains(mx, my)) {
			return Hit.PANEL;
		}
		return Hit.NONE;
	}

	public float trackStart(int k) {
		return sliders[k].x() + SLIDER_LABEL_W;
	}

	public float trackEnd(int k) {
		return sliders[k].right() - SLIDER_VALUE_W;
	}
}
