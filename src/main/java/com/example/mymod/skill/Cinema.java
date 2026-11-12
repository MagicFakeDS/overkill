package com.example.mymod.skill;

import java.util.Set;

public final class Cinema {
	public enum Story {
		NONE,

		MISSILE,

		ASTEROID,

		ORBITAL,

		CHASE
	}

	private static final Set<String> MISSILE = Set.of(
			"nuke", "tsar", "nuke_rain", "icbm_nuke", "mirv", "icbm_ice", "icbm_hell", "icbm_end", "icbm_glass",
			"crater_field", "doomsday", "hyper_tsar", "nuke_chain", "nuke_ring", "armageddon", "lava_ocean",
			"ice_age", "end_invasion");
	private static final Set<String> ASTEROID = Set.of(
			"meteors", "asteroid", "ice_comet", "meteor_swarm", "moonfall", "planet_killer", "comet_storm",
			"moon_shatter");
	private static final Set<String> ORBITAL = Set.of(
			"orbital", "satellite", "rod", "plasma_column", "orbital_ring", "sun_spear", "tectonic_rift",
			"rod_storm", "orbital_cannon", "laser_grid", "planet_cutter", "death_ray", "world_crack", "hell_gate",
			"final_judgment", "gamma_burst", "sun_drop");
	private static final Set<String> CHASE = Set.of(
			"cruise", "swarm", "thermobaric", "bunker_buster", "seismic", "emp", "aerial_bomb", "carpet_bombing",
			"cluster_bomb", "napalm", "volcano_bomb", "gravity_bomb", "dirty_bomb", "cobalt_bomb", "mega_tsunami",
			"antimatter_rain", "earth_spears", "supervolcano");

	private Cinema() {
	}

	public static Story storyFor(String skillId) {
		if (MISSILE.contains(skillId)) {
			return Story.MISSILE;
		}
		if (ASTEROID.contains(skillId)) {
			return Story.ASTEROID;
		}
		if (ORBITAL.contains(skillId)) {
			return Story.ORBITAL;
		}
		if (CHASE.contains(skillId)) {
			return Story.CHASE;
		}
		return Story.NONE;
	}

	public static int preludeTicks(Story story) {
		return switch (story) {
			case MISSILE -> 120;
			case ASTEROID -> 150;
			case ORBITAL -> 110;
			default -> 0;
		};
	}
}
