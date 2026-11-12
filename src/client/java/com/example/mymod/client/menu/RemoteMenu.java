package com.example.mymod.client.menu;

import com.example.mymod.client.RemoteSettings;
import com.example.mymod.item.ModItems;
import com.example.mymod.network.LaunchSkillPayload;
import com.example.mymod.skill.Skill;
import com.example.mymod.skill.Skills;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Quaternionf;

public final class RemoteMenu {
	static final double DISTANCE = 0.25;

	static final float SCALE = 0.0085f * (float) DISTANCE / 2f;

	static final float CLOSE_TIME = 0.22f;

	private static final int KEY_ESCAPE = 256;
	private static final int KEY_H = 'H';

	static final MenuLayout LAYOUT = new MenuLayout();

	private static boolean open;
	private static long openedAt;
	private static long closedAt;
	private static Vec3 forward = new Vec3(0, 0, 1);
	private static Vec3 right = new Vec3(-1, 0, 0);
	private static Vec3 up = new Vec3(0, 1, 0);

	static MenuLayout.Hit hovered = MenuLayout.Hit.NONE;
	static float cursorX;
	static float cursorY;
	static boolean cursorOnPlane;
	static int dragging = -1;
	private static boolean useHeld;

	private RemoteMenu() {
	}

	public static boolean isOpen() {
		return open;
	}

	public static boolean isVisible() {
		return open || closeProgress() < 1f;
	}

	static float secondsSinceOpen() {
		return (System.nanoTime() - openedAt) / 1_000_000_000f;
	}

	static float closeProgress() {
		if (open || closedAt == 0) {
			return open ? 0f : 1f;
		}
		return Mth.clamp((System.nanoTime() - closedAt) / 1_000_000_000f / CLOSE_TIME, 0f, 1f);
	}

	static RemoteSettings settings() {
		return RemoteSettings.get();
	}

	public static void open(LocalPlayer player) {
		float yaw = player.getYRot();
		float pitch = Mth.clamp(player.getXRot(), -12f, 12f);
		forward = viewVector(pitch, yaw);
		right = forward.cross(new Vec3(0, 1, 0)).normalize();
		up = right.cross(forward).normalize();

		open = true;
		openedAt = System.nanoTime();
		hovered = MenuLayout.Hit.NONE;
		dragging = -1;

		LAYOUT.scroll = Math.max(0, settings().selected - 3);
		relayout(Mth.clamp(settings().lastExpanded, -1, Skills.ALL.size() - 1));
		MenuAnim.reset(LAYOUT, settings());
		click(1.4f);
	}

	public static void close() {
		if (!open) {
			return;
		}
		open = false;
		closedAt = System.nanoTime();
		dragging = -1;
		hovered = MenuLayout.Hit.NONE;
		settings().lastExpanded = LAYOUT.expanded;
		settings().save();
	}

	static void relayout(int expanded) {
		LAYOUT.rebuild(Minecraft.getInstance().font, expanded, Component.translatable("gui.mymod.remote.hint"));
	}

	static Vec3 center(Vec3 cameraPos) {
		return cameraPos.add(forward.scale(DISTANCE));
	}

	static Quaternionf rotation() {
		Vec3 back = forward.scale(-1);
		Matrix3f m = new Matrix3f(
				(float) right.x, (float) right.y, (float) right.z,
				(float) up.x, (float) up.y, (float) up.z,
				(float) back.x, (float) back.y, (float) back.z);
		return new Quaternionf().setFromNormalized(m);
	}

	static void updateHover(Vec3 cameraPos, Vec3 look) {
		Vec3 back = forward.scale(-1);
		double denom = look.dot(back);
		MenuLayout.Hit newHover = MenuLayout.Hit.NONE;
		cursorOnPlane = false;
		if (denom < -1e-4) {
			Vec3 c = center(cameraPos);
			double t = c.subtract(cameraPos).dot(back) / denom;
			Vec3 local = cameraPos.add(look.scale(t)).subtract(c);
			cursorX = (float) (local.dot(right) / SCALE) + MenuLayout.PIVOT_X;
			cursorY = (float) (-local.dot(up) / SCALE) + MenuLayout.PIVOT_Y;
			cursorOnPlane = true;
			newHover = LAYOUT.hitTest(cursorX, cursorY);
		}

		if (!newHover.equals(hovered)) {
			if (newHover.kind() == MenuLayout.Kind.ROW || newHover.kind() == MenuLayout.Kind.TOGGLE) {
				Minecraft.getInstance().getSoundManager().play(
						SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.12f));
			}
			hovered = newHover;
		}

		if (dragging >= 0) {
			if (Minecraft.getInstance().options.keyAttack.isDown() && cursorOnPlane) {
				setSliderFromCursor(dragging);
			} else if (!Minecraft.getInstance().options.keyAttack.isDown()) {
				dragging = -1;
				settings().save();
			}
		}
	}

	static Vec3 viewVector(float pitchDeg, float yawDeg) {
		float pitch = pitchDeg * Mth.DEG_TO_RAD;
		float yaw = -yawDeg * Mth.DEG_TO_RAD;
		float cosPitch = Mth.cos(pitch);
		return new Vec3(Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
	}

	public static void tick(Minecraft mc) {
		if (!mc.options.keyUse.isDown()) {
			useHeld = false;
		}
		if (!open) {
			return;
		}
		if (mc.player == null || !mc.player.isAlive() || mc.screen != null
				|| !mc.player.getMainHandItem().is(ModItems.REMOTE)) {
			close();
		}
	}

	public static boolean onAttack() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		if (open) {
			switch (hovered.kind()) {
				case ROW -> select(hovered.index());
				case TOGGLE -> {
					settings().flipToggle(hovered.index());
					settings().save();
					if (hovered.index() == 3) {
						ClientPlayNetworking.send(new com.example.mymod.network.GravityPayload(settings().gravity));
					}
					click(1.0f);
				}
				case SLIDER -> {
					dragging = hovered.index();
					setSliderFromCursor(dragging);
				}
				case PANEL -> {
				}
				case NONE -> close();
			}
			return true;
		}
		if (mc.player.getMainHandItem().is(ModItems.REMOTE)) {
			launchSelected(mc);
			return true;
		}
		return false;
	}

	public static boolean blocksContinueAttack() {
		Minecraft mc = Minecraft.getInstance();
		return open || (mc.player != null && mc.player.getMainHandItem().is(ModItems.REMOTE));
	}

	public static boolean onUse() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		if (open) {
			if (!useHeld) {
				useHeld = true;
				if (hovered.kind() == MenuLayout.Kind.ROW) {
					int i = hovered.index();
					relayout(LAYOUT.expanded == i ? -1 : i);
					click(1.2f);
				} else if (hovered.kind() == MenuLayout.Kind.NONE) {
					close();
				}
			}
			return true;
		}
		if (mc.player.getMainHandItem().is(ModItems.REMOTE) && mc.screen == null) {
			if (!useHeld) {
				useHeld = true;
				open(mc.player);
			}
			return true;
		}
		return false;
	}

	public static boolean onKey(int key, int action) {
		Minecraft mc = Minecraft.getInstance();
		if (!open || mc.screen != null || action == 0) {
			return false;
		}
		if (key == KEY_ESCAPE || key == KEY_H) {
			close();
			return true;
		}
		for (int i = 0; i < Skills.ALL.size(); i++) {
			if (key == Character.toUpperCase(Skills.ALL.get(i).key())) {
				if (action == 1) {
					select(i);
				}
				return true;
			}
		}
		return false;
	}

	public static boolean onScroll(double amount) {
		if (!open) {
			return false;
		}

		boolean overList = cursorOnPlane
				&& cursorX >= MenuLayout.LEFT_W + MenuLayout.GAP && cursorX <= MenuLayout.TOTAL_W + 8
				&& cursorY >= MenuLayout.LIST_TOP && cursorY <= MenuLayout.LIST_BOTTOM;
		if (overList && amount != 0) {
			int before = LAYOUT.scroll;
			LAYOUT.scroll = Mth.clamp(LAYOUT.scroll - 2 * (int) Math.signum(amount), 0, LAYOUT.maxScroll());
			if (LAYOUT.scroll != before) {
				click(1.6f);
			}
			return true;
		}
		if (hovered.kind() == MenuLayout.Kind.SLIDER && amount != 0) {
			int k = hovered.index();
			Skill skill = Skills.ALL.get(LAYOUT.expanded);
			Skill.Range range = range(skill, k);
			float[] values = settings().values(skill);
			values[k] = range.clamp(snap(k, values[k] + step(k) * Math.signum((float) amount)));
			settings().save();
			click(1.8f);
		}
		return true;
	}

	private static void select(int index) {
		settings().selected = index;
		settings().save();
		MenuAnim.flash(index);
		close();
		click(1.0f);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			Component name = Component.translatable(Skills.ALL.get(index).nameKey());
			mc.player.sendOverlayMessage(Component.translatable("message.mymod.remote.selected", name));
		}
	}

	private static void launchSelected(Minecraft mc) {
		if (!ClientPlayNetworking.canSend(LaunchSkillPayload.TYPE)) {
			mc.player.sendOverlayMessage(Component.translatable("message.mymod.remote.no_server_mod"));
			return;
		}
		int index = Mth.clamp(settings().selected, 0, Skills.ALL.size() - 1);
		Skill skill = Skills.ALL.get(index);
		float[] v = settings().values(skill);
		RemoteSettings s = settings();
		ClientPlayNetworking.send(new LaunchSkillPayload(index, v[0], v[1], v[2], s.breakBlocks, s.fire, s.protectSelf,
				s.gravity, s.cinema));
		mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
	}

	private static void setSliderFromCursor(int k) {
		if (LAYOUT.expanded < 0 || LAYOUT.sliders[k] == null) {
			return;
		}
		Skill skill = Skills.ALL.get(LAYOUT.expanded);
		Skill.Range range = range(skill, k);
		float x1 = LAYOUT.trackStart(k);
		float x2 = LAYOUT.trackEnd(k);
		float frac = Mth.clamp((cursorX - x1) / (x2 - x1), 0f, 1f);
		float raw = range.min() + frac * (range.max() - range.min());
		settings().values(skill)[k] = range.clamp(snap(k, raw));
	}

	static Skill.Range range(Skill skill, int k) {
		return switch (k) {
			case 0 -> skill.radius();
			case 1 -> skill.damage();
			default -> skill.speed();
		};
	}

	private static float step(int k) {
		return k == 0 ? 0.5f : (k == 1 ? 0.1f : 0.05f);
	}

	private static float snap(int k, float value) {
		float step = step(k);
		return Math.round(value / step) * step;
	}

	private static void click(float pitch) {
		Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.6f));
	}
}
