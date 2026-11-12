package com.example.mymod.client.menu;

import java.util.List;
import java.util.Locale;

import com.example.mymod.MyMod;
import com.example.mymod.client.RemoteSettings;
import com.example.mymod.client.menu.MenuLayout.Rect;
import com.example.mymod.skill.Skill;
import com.example.mymod.skill.Skills;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class MenuRenderer {
	private static final int PANEL = 0xEEFFFFFF;
	private static final int PANEL_HOVER = 0xFFFFFFFF;
	private static final int TEXT = 0xFF26262B;
	private static final int SUBTEXT = 0xFF8C8C96;
	private static final int ACCENT = 0xFFF2A531;
	private static final int RED = 0xFFE5484D;
	private static final int TRACK = 0xFFDCDCE2;
	private static final int BORDER = 0xFFB8B8C0;
	private static final int ICON_BG = 0xFF33333D;
	private static final int PILL = 0xFFEFEFF3;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int LINE = 0xC0FFFFFF;
	private static final int SHADOW = 0x38000000;
	private static final int GLOW = 0xD0F2A531;
	private static final int FLASH = 0xFFFFD27A;

	private static final float R_CARD = 12;
	private static final float R_ROW = 8;

	private static final int L_SHADOW = -1;
	private static final int L_OUTLINE = 0;
	private static final int L_PANEL = 1;
	private static final int L_INNER = 2;
	private static final int L_FILL = 3;
	private static final int L_TEXT = 4;
	private static final int L_KNOB = 5;
	private static final int L_CURSOR = 7;

	private static final Identifier REMOTE_TEXTURE = MyMod.id("textures/item/remote.png");

	private static final String[] TOGGLE_KEYS = {
			"gui.mymod.remote.break_blocks",
			"gui.mymod.remote.fire",
			"gui.mymod.remote.protect_self",
			"gui.mymod.remote.gravity",
			"gui.mymod.remote.cinema"
	};
	private static final String[] SLIDER_KEYS = {
			"gui.mymod.remote.radius",
			"gui.mymod.remote.damage",
			"gui.mymod.remote.speed"
	};

	private MenuRenderer() {
	}

	public static void render(LevelRenderContext context) {
		if (!RemoteMenu.isVisible()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || camera.pos == null) {
			return;
		}
		MenuAnim.beginFrame();
		boolean open = RemoteMenu.isOpen();
		Vec3 cameraPos = camera.pos;
		if (open) {
			RemoteMenu.updateHover(cameraPos, RemoteMenu.viewVector(camera.xRot, camera.yRot));
		}
		MenuAnim.update(RemoteMenu.LAYOUT, RemoteMenu.settings(), RemoteMenu.hovered, open);

		float closing = MenuAnim.easeOutCubic(RemoteMenu.closeProgress());

		PoseStack pose = context.poseStack();
		pose.pushPose();
		Vec3 c = RemoteMenu.center(cameraPos);
		pose.translate((float) (c.x - cameraPos.x), (float) (c.y - cameraPos.y), (float) (c.z - cameraPos.z));
		pose.mulPose(RemoteMenu.rotation());
		float s = RemoteMenu.SCALE * (1f - 0.06f * closing);
		pose.scale(s, -s, s);
		pose.translate(-MenuLayout.PIVOT_X, -MenuLayout.PIVOT_Y, -12f * closing);

		Canvas cv = new Canvas(pose, context.submitNodeCollector(), Minecraft.getInstance().font);
		cv.setGlobalAlpha(1f - closing);
		draw(cv, RemoteMenu.secondsSinceOpen(), open);
		cv.flush();
		pose.popPose();
	}

	private static void draw(Canvas cv, float t, boolean open) {
		MenuLayout layout = RemoteMenu.LAYOUT;
		RemoteSettings settings = RemoteMenu.settings();

		drawTitle(cv, t);
		drawCard(cv, layout.card, t);
		drawToggles(cv, layout, settings, t);
		drawRows(cv, layout, settings, t);
		drawDetails(cv, layout, settings, t);
		drawHint(cv, layout.hint, t);
		if (open) {
			drawCursor(cv);
		}
	}

	private static void panel(Canvas cv, float x, float y, float w, float h, float r, float hover, float flash) {
		cv.shadow(x, y + 1.5f + 1.5f * hover, w, h, r, SHADOW, 6 + 3 * hover, L_SHADOW);
		if (hover > 0.01f) {
			int border = MenuAnim.withAlpha(ACCENT, hover);
			cv.edge(x, y, w, h, r, border, border, 1.1f, L_OUTLINE);
			cv.glow(x - 1.1f, y - 1.1f, w + 2.2f, h + 2.2f, r + 1.1f, MenuAnim.withAlpha(GLOW, hover), 5, L_OUTLINE);
		}
		if (flash > 0.01f) {
			cv.glow(x - 1, y - 1, w + 2, h + 2, r + 1, MenuAnim.withAlpha(FLASH, flash), 12 * flash, L_OUTLINE);
		}
		int fill = MenuAnim.lerpColor(PANEL, PANEL_HOVER, hover);
		if (flash > 0.01f) {
			fill = MenuAnim.lerpColor(fill, 0xFFFFF4DC, flash);
		}
		cv.roundRect(x, y, w, h, r, fill, L_PANEL);
	}

	private static void drawTitle(Canvas cv, float t) {
		float e = MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0f, 0.35f));
		float cx = MenuLayout.TOTAL_W / 2f;
		cv.group(cx, 6, 1f, 0, -(1 - e) * 6, 0, e);

		String title = Component.translatable("gui.mymod.remote.title").getString().toUpperCase(Locale.ROOT);
		cv.spacedText(title, cx, 0, WHITE, L_TEXT, 3, true);
		float lineW = 32 * e;
		cv.roundRect(cx - lineW / 2, 12, lineW, 0.8f, 0.4f, LINE, L_INNER);
		float pulse = 0.65f + 0.35f * (float) Math.sin(t * 3.0);
		cv.roundRect(cx - 1.6f, 14.5f, 3.2f, 3.2f, 1.6f, MenuAnim.withAlpha(WHITE, pulse), L_INNER);
		cv.endGroup();
	}

	private static void drawCard(Canvas cv, Rect card, float t) {
		float e = MenuAnim.easeOutBack(MenuAnim.stagger(t, 0.03f, 0.42f));
		float a = MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0.03f, 0.25f));
		float cx = card.x() + card.w() / 2f;
		cv.group(cx, card.y() + card.h() / 2f, 0.9f + 0.1f * e, -(1 - e) * 10, (1 - e) * 6, -(1 - e) * 14, a);

		panel(cv, card.x(), card.y(), card.w(), card.h(), R_CARD, 0f, 0f);
		cv.roundRect(cx - 18, card.y() + 3, 36, 2.4f, 1.2f, RED, L_INNER);

		String name = Component.translatable("item.mymod.remote").getString().toUpperCase(Locale.ROOT);
		cv.spacedText(name, cx, card.y() + 10, TEXT, L_TEXT, 2, false);
		cv.roundRect(card.x() + 14, card.y() + 22, card.w() - 28, 0.7f, 0.35f, 0xFFE6E6EA, L_INNER);

		float bob = (float) Math.sin(t * 2.2) * 1.6f;
		cv.roundRect(cx - 27, card.y() + 27, 54, 50, 10, 0xFFF3F3F6, L_INNER);
		cv.image(REMOTE_TEXTURE, cx - 22, card.y() + 30 + bob, 44, 44, L_FILL);

		List<FormattedCharSequence> lines = cv.font().split(Component.translatable("gui.mymod.remote.desc"), (int) card.w() - 18);
		float ly = card.y() + 82;
		for (int i = 0; i < Math.min(4, lines.size()); i++) {
			FormattedCharSequence line = lines.get(i);
			cv.text(line, cx - cv.font().width(line) / 2f, ly, SUBTEXT, L_TEXT, 1f, false);
			ly += 9;
		}

		String pill = Component.translatable("gui.mymod.remote.skills", Skills.ALL.size()).getString();
		float pw = cv.width(pill) + 14;
		cv.roundRect(cx - pw / 2, card.bottom() - 17, pw, 12, 6, PILL, L_INNER);
		cv.text(pill, cx - cv.width(pill) / 2f, card.bottom() - 15, SUBTEXT, L_TEXT);
		cv.endGroup();
	}

	private static void drawToggles(Canvas cv, MenuLayout layout, RemoteSettings settings, float t) {
		Rect last = layout.toggles[layout.toggles.length - 1];
		float lx = 4;
		float le = MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0.12f, 0.35f));
		float lineH = (last.y() + last.h() / 2f - layout.card.bottom() - 2) * le;
		cv.group(0, 0, 1f, 0, 0, 0, le);
		cv.roundRect(lx - 0.4f, layout.card.bottom() + 2, 0.8f, lineH, 0.4f, LINE, L_PANEL);
		cv.endGroup();

		for (int i = 0; i < layout.toggles.length; i++) {
			Rect r = layout.toggles[i];
			float e = MenuAnim.easeOutBack(MenuAnim.stagger(t, 0.14f + 0.05f * i, 0.4f));
			float a = MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0.14f + 0.05f * i, 0.25f));
			float h = MenuAnim.toggleHover[i];
			float k = MenuAnim.toggleKnob[i];
			float midY = r.y() + r.h() / 2f;

			cv.group(0, 0, 1f, 0, 0, 0, a);
			cv.roundRect(lx, midY - 0.4f, (r.x() - lx - 1) * a, 0.8f, 0.4f, LINE, L_PANEL);

			cv.group(r.x() + r.w() / 2f, midY, (0.92f + 0.08f * e) * (1f + 0.03f * h), -(1 - e) * 14, 0, 4 * h, a);
			panel(cv, r.x(), r.y(), r.w(), r.h(), r.h() / 2f, h, 0f);
			cv.text(Component.translatable(TOGGLE_KEYS[i]).getString(), r.x() + 8, r.y() + 4, TEXT, L_TEXT);

			float sx = r.right() - 25;
			float sy = r.y() + 3;
			cv.roundRect(sx, sy, 20, 9, 4.5f, MenuAnim.lerpColor(TRACK, ACCENT, k), L_INNER);
			cv.roundRect(sx + 1 + 11 * k, sy + 1, 7, 7, 3.5f, WHITE, L_FILL);
			cv.endGroup();
		}
	}

	private static void drawRows(Canvas cv, MenuLayout layout, RemoteSettings settings, float t) {
		float scroll = MenuAnim.scrollAnim;
		for (int i = 0; i < layout.rows.size(); i++) {
			Rect r = layout.rowAt(i, scroll);

			float fadeTop = Mth.clamp((r.y() - MenuLayout.LIST_TOP + 14) / 14f, 0f, 1f);
			float fadeBottom = Mth.clamp((MenuLayout.LIST_BOTTOM - r.bottom() + 14) / 14f, 0f, 1f);
			float vis = Math.min(fadeTop, fadeBottom);
			if (vis <= 0.01f) {
				continue;
			}
			Skill skill = Skills.ALL.get(i);
			int slot = Math.max(0, i - Math.round(scroll));
			float e = MenuAnim.easeOutBack(MenuAnim.stagger(t, 0.08f + 0.05f * slot, 0.42f));
			float a = MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0.08f + 0.05f * slot, 0.25f)) * vis;
			float h = MenuAnim.rowHover[i];
			float flash = MenuAnim.rowFlash[i];

			cv.group(r.x() + r.w() / 2f, r.y() + r.h() / 2f, (0.94f + 0.06f * e) * (1f + 0.025f * h),
					(1 - e) * 18, 0, 5 * h, a);

			panel(cv, r.x(), r.y(), r.w(), r.h(), R_ROW, h, flash);
			if (settings.selected == i) {
				cv.roundRect(r.x() + 1.2f, r.y() + 6, 2.2f, r.h() - 12, 1.1f, RED, L_INNER);
			}

			cv.roundRect(r.x() + 4, r.y() + 2, 18, 18, 5.5f, ICON_BG, L_INNER);
			float isz = 14 * (1f + 0.14f * h);
			cv.image(MyMod.id("textures/gui/skill/" + skill.icon() + ".png"),
					r.x() + 13 - isz / 2, r.y() + 11 - isz / 2, isz, isz, L_FILL);

			String name = "№" + (i + 1) + " · " + Component.translatable(skill.nameKey()).getString();
			cv.text(name, r.x() + 27, r.y() + 3, TEXT, L_TEXT);
			cv.smallText(Component.translatable(skill.typeKey()).getString(), r.x() + 27, r.y() + 13, SUBTEXT, L_TEXT);

			if (skill.key() != ' ') {
				float kx = r.right() - 18;
				float ky = r.y() + 5;
				cv.roundRect(kx, ky, 12, 12, 4, MenuAnim.lerpColor(BORDER, ACCENT, h), L_INNER);
				cv.roundRect(kx + 0.8f, ky + 0.8f, 10.4f, 10.4f, 3.4f, WHITE, L_FILL);
				String key = String.valueOf(skill.key());
				cv.text(key, kx + 6f - cv.width(key) / 2f + 0.5f, ky + 2f, TEXT, L_TEXT);
			}
			cv.endGroup();
		}

		int max = layout.maxScroll();
		if (max > 0) {
			float x = MenuLayout.LEFT_W + MenuLayout.GAP + MenuLayout.RIGHT_W + 4;
			float top = MenuLayout.LIST_TOP;
			float height = MenuLayout.LIST_BOTTOM - MenuLayout.LIST_TOP;
			float thumbH = height * MenuLayout.VISIBLE_ROWS / layout.rows.size();
			float thumbY = top + (height - thumbH) * Mth.clamp(scroll / max, 0f, 1f);
			float e = MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0.2f, 0.3f));
			cv.group(0, 0, 1f, 0, 0, 0, e);
			cv.roundRect(x, top, 2.4f, height, 1.2f, 0x50FFFFFF, L_PANEL);
			cv.roundRect(x, thumbY, 2.4f, thumbH, 1.2f, 0xE8FFFFFF, L_INNER);
			cv.endGroup();
		}
	}

	private static void drawDetails(Canvas cv, MenuLayout layout, RemoteSettings settings, float t) {
		Rect d = layout.details;
		if (d == null) {
			return;
		}
		int ex = layout.expanded;
		Skill skill = Skills.ALL.get(ex);
		float appear = MenuAnim.easeOutCubic(MenuAnim.detailsAppear)
				* MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0.22f, 0.3f));
		cv.group(d.x() + d.w() / 2f, d.y(), 0.96f + 0.04f * appear, 0, -(1 - appear) * 8, 0, appear);

		panel(cv, d.x(), d.y(), d.w(), d.h(), R_ROW, 0f, 0f);

		float ly = d.y() + 5;
		for (FormattedCharSequence line : layout.detailLines) {
			cv.text(line, d.x() + 7, ly, SUBTEXT, L_TEXT, 1f, false);
			ly += 9;
		}

		float[] values = settings.values(skill);
		MenuLayout.Hit hovered = RemoteMenu.hovered;
		for (int k = 0; k < layout.sliders.length; k++) {
			Rect r = layout.sliders[k];
			if (r == null) {
				continue;
			}
			boolean active = (hovered.kind() == MenuLayout.Kind.SLIDER && hovered.index() == k) || RemoteMenu.dragging == k;
			Skill.Range range = RemoteMenu.range(skill, k);
			float frac = Mth.clamp((MenuAnim.sliderShown[k] - range.min()) / (range.max() - range.min()), 0f, 1f);

			cv.text(Component.translatable(SLIDER_KEYS[k]).getString(), r.x(), r.y() + 2, active ? TEXT : SUBTEXT, L_TEXT);

			float x1 = layout.trackStart(k);
			float x2 = layout.trackEnd(k);
			float ty = r.y() + 5;
			float fx = x1 + frac * (x2 - x1);
			cv.roundRect(x1, ty, x2 - x1, 2.4f, 1.2f, TRACK, L_INNER);
			cv.roundRect(x1, ty, Math.max(2.4f, fx - x1), 2.4f, 1.2f, RED, L_FILL);
			float knob = active ? 9f : 8f;
			if (active) {
				cv.glow(fx - knob / 2, ty + 1.2f - knob / 2, knob, knob, knob / 2, GLOW, 3, L_FILL);
			}
			cv.roundRect(fx - knob / 2, ty + 1.2f - knob / 2, knob, knob, knob / 2, active ? ACCENT : BORDER, L_FILL);
			float inner = knob - 1.8f;
			cv.roundRect(fx - inner / 2, ty + 1.2f - inner / 2, inner, inner, inner / 2, WHITE, L_KNOB);

			String value = formatValue(k, values[k]);
			cv.text(value, r.right() - cv.width(value), r.y() + 2, TEXT, L_TEXT);
		}
		cv.endGroup();
	}

	private static void drawHint(Canvas cv, Rect hint, float t) {
		float e = MenuAnim.easeOutCubic(MenuAnim.stagger(t, 0.3f, 0.35f));
		cv.group(hint.x() + hint.w() / 2f, hint.y(), 1f, 0, (1 - e) * 8, 0, e);
		String text = Component.translatable("gui.mymod.remote.hint").getString();
		cv.shadow(hint.x(), hint.y() + 1, hint.w(), hint.h(), hint.h() / 2f, SHADOW, 5, L_SHADOW);
		cv.roundRect(hint.x(), hint.y(), hint.w(), hint.h(), hint.h() / 2f, 0xD8FFFFFF, L_PANEL);
		cv.text(text, hint.x() + (hint.w() - cv.width(text)) / 2f, hint.y() + 3, SUBTEXT, L_TEXT);
		cv.endGroup();
	}

	private static void drawCursor(Canvas cv) {
		if (!RemoteMenu.cursorOnPlane) {
			return;
		}
		float x = RemoteMenu.cursorX;
		float y = RemoteMenu.cursorY;
		if (x < -40 || x > MenuLayout.TOTAL_W + 40 || y < -40 || y > 320) {
			return;
		}
		float a = MenuAnim.cursorActive;
		cv.ring(x, y, 5f + 1.8f * a, 1.1f, MenuAnim.lerpColor(0xE0FFFFFF, ACCENT, a), L_CURSOR);
	}

	private static String formatValue(int k, float value) {
		return switch (k) {
			case 0 -> String.format(Locale.ROOT, "%.1f", value);
			case 1 -> String.format(Locale.ROOT, "×%.1f", value);
			default -> String.format(Locale.ROOT, "×%.2f", value);
		};
	}
}
