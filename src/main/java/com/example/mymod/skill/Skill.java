package com.example.mymod.skill;

import com.example.mymod.network.LaunchSkillPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record Skill(String id, char key, String icon, Range radius, Range damage, Range speed, Action action) {
	@FunctionalInterface
	public interface Action {
		void launch(ServerPlayer player, Vec3 target, LaunchSkillPayload params);
	}

	public record Range(float min, float max, float def) {
		public float clamp(float value) {
			if (Float.isNaN(value)) {
				return def;
			}
			return Math.max(min, Math.min(max, value));
		}
	}

	public String nameKey() {
		return "skill.mymod." + id;
	}

	public String typeKey() {
		return nameKey() + ".type";
	}

	public String descKey() {
		return nameKey() + ".desc";
	}

	public LaunchSkillPayload sanitize(LaunchSkillPayload p) {
		return new LaunchSkillPayload(
				p.skill(),
				radius.clamp(p.radius()),
				damage.clamp(p.damage()),
				speed == null ? 1.0f : speed.clamp(p.speed()),
				p.breakBlocks(),
				p.fire(),
				p.protectSelf(),
				p.gravity(),
				p.cinema());
	}
}
