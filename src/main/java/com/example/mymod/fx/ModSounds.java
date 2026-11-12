package com.example.mymod.fx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.mymod.MyMod;
import com.example.mymod.skill.SkillScheduler;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class ModSounds {
	public static final Holder<SoundEvent> EXPLOSION_SMALL = register("explosion.small");
	public static final Holder<SoundEvent> EXPLOSION_MEDIUM = register("explosion.medium");
	public static final Holder<SoundEvent> EXPLOSION_BIG = register("explosion.big");

	public static final Holder<SoundEvent> RUMBLE = register("explosion.rumble");

	public static final Holder<SoundEvent> EXPLOSION_FAR = register("explosion.far");
	public static final Holder<SoundEvent> NUKE = register("nuke");
	public static final Holder<SoundEvent> TSAR = register("tsar");
	public static final Holder<SoundEvent> SONIC_BOOM = register("sonic_boom");
	public static final Holder<SoundEvent> SATELLITE_CHARGE = register("satellite.charge");
	public static final Holder<SoundEvent> SATELLITE_FIRE = register("satellite.fire");
	public static final Holder<SoundEvent> BOMB_WHISTLE = register("bomb.whistle");
	public static final Holder<SoundEvent> ASTEROID_ENTRY = register("asteroid.entry");
	public static final Holder<SoundEvent> ANTIMATTER = register("antimatter");
	public static final Holder<SoundEvent> CHUNK_CUT = register("chunk.cut");
	public static final Holder<SoundEvent> CHUNK_COLLAPSE = register("chunk.collapse");
	public static final Holder<SoundEvent> ROCKET_LAUNCH = register("rocket.launch");
	public static final Holder<SoundEvent> FLYBY = register("rocket.flyby");
	public static final Holder<SoundEvent> FREEZE = register("freeze");
	public static final Holder<SoundEvent> FIRE_ROAR = register("fire.roar");
	public static final Holder<SoundEvent> GEIGER = register("geiger");
	public static final Holder<SoundEvent> QUAKE = register("quake");
	public static final Holder<SoundEvent> PLASMA = register("plasma");

	public static final Holder<SoundEvent> INCOMING = register("incoming");

	public static final int INCOMING_TICKS = 26;

	private static final Holder<SoundEvent> L_THUNDER = register("layer.thunder");
	private static final Holder<SoundEvent> L_SONIC = register("layer.sonic");
	private static final Holder<SoundEvent> L_CRACK = register("layer.crack");
	private static final Holder<SoundEvent> L_RUBBLE = register("layer.rubble");
	private static final Holder<SoundEvent> L_FIRE = register("layer.fire");
	private static final Holder<SoundEvent> L_GHAST = register("layer.ghast");
	private static final Holder<SoundEvent> L_WHOOSH = register("layer.whoosh");
	private static final Holder<SoundEvent> L_SNOW = register("layer.snow");
	private static final Holder<SoundEvent> L_AMETHYST = register("layer.amethyst");
	private static final Holder<SoundEvent> L_BEACON = register("layer.beacon");
	private static final Holder<SoundEvent> L_PORTAL = register("layer.portal");
	private static final Holder<SoundEvent> L_TRIDENT = register("layer.trident");
	private static final Holder<SoundEvent> L_BOOM = register("layer.boom");
	private static final Holder<SoundEvent> L_RIPTIDE = register("layer.riptide");

	public record Layer(Holder<SoundEvent> sound, float volume, int delay) {
	}

	private static final Map<Holder<SoundEvent>, List<Layer>> LAYERS = new HashMap<>();

	static {
		LAYERS.put(EXPLOSION_MEDIUM, List.of(new Layer(L_RUBBLE, 0.5f, 8)));
		LAYERS.put(EXPLOSION_BIG, List.of(new Layer(L_THUNDER, 0.8f, 0), new Layer(L_CRACK, 0.7f, 0),
				new Layer(L_RUBBLE, 0.8f, 10)));
		LAYERS.put(NUKE, List.of(new Layer(L_SONIC, 1f, 0), new Layer(L_THUNDER, 1f, 0), new Layer(L_BOOM, 0.8f, 4),
				new Layer(L_THUNDER, 0.7f, 24), new Layer(L_RUBBLE, 0.8f, 18)));
		LAYERS.put(TSAR, List.of(new Layer(L_SONIC, 1f, 0), new Layer(L_THUNDER, 1f, 0), new Layer(L_BOOM, 1f, 3),
				new Layer(L_THUNDER, 0.8f, 20), new Layer(L_THUNDER, 0.6f, 45), new Layer(L_RUBBLE, 1f, 25)));
		LAYERS.put(SONIC_BOOM, List.of(new Layer(L_TRIDENT, 0.8f, 0), new Layer(L_THUNDER, 0.6f, 2)));
		LAYERS.put(ROCKET_LAUNCH, List.of(new Layer(L_GHAST, 0.8f, 0), new Layer(L_FIRE, 0.8f, 0),
				new Layer(L_WHOOSH, 0.6f, 6)));
		LAYERS.put(FREEZE, List.of(new Layer(L_SNOW, 1f, 0), new Layer(L_AMETHYST, 0.8f, 2), new Layer(L_BOOM, 0.5f, 0)));
		LAYERS.put(FIRE_ROAR, List.of(new Layer(L_GHAST, 0.8f, 0), new Layer(L_WHOOSH, 0.7f, 0), new Layer(L_FIRE, 0.8f, 10)));
		LAYERS.put(QUAKE, List.of(new Layer(L_THUNDER, 1f, 0), new Layer(L_RUBBLE, 1f, 8), new Layer(L_THUNDER, 0.7f, 30)));
		LAYERS.put(PLASMA, List.of(new Layer(L_BEACON, 0.8f, 0), new Layer(L_TRIDENT, 0.6f, 0)));
		LAYERS.put(ANTIMATTER, List.of(new Layer(L_PORTAL, 0.8f, 0), new Layer(L_BEACON, 0.6f, 10)));
		LAYERS.put(ASTEROID_ENTRY, List.of(new Layer(L_WHOOSH, 0.9f, 0), new Layer(L_FIRE, 0.8f, 0),
				new Layer(L_GHAST, 0.6f, 15)));
		LAYERS.put(CHUNK_CUT, List.of(new Layer(L_BEACON, 0.8f, 0), new Layer(L_THUNDER, 0.6f, 4)));
		LAYERS.put(CHUNK_COLLAPSE, List.of(new Layer(L_THUNDER, 0.8f, 0), new Layer(L_RUBBLE, 1f, 4)));
		LAYERS.put(SATELLITE_CHARGE, List.of(new Layer(L_BEACON, 0.7f, 0)));
		LAYERS.put(SATELLITE_FIRE, List.of(new Layer(L_BOOM, 0.6f, 0)));
		LAYERS.put(INCOMING, List.of(new Layer(L_RIPTIDE, 0.7f, 6), new Layer(L_WHOOSH, 0.6f, 14)));
	}

	private ModSounds() {
	}

	private static Holder<SoundEvent> register(String name) {
		Identifier id = MyMod.id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static List<Layer> layers(Holder<SoundEvent> sound) {
		return LAYERS.getOrDefault(sound, List.of());
	}

	public static void play(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, volume, pitch);
		playLayers(level, pos, sound, volume, pitch);
	}

	public static void playLayers(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
		for (Layer layer : layers(sound)) {
			Runnable run = () -> level.playSound(null, pos.x, pos.y, pos.z, layer.sound(), SoundSource.BLOCKS,
					volume * layer.volume(), pitch);
			if (layer.delay() <= 0) {
				run.run();
			} else {
				SkillScheduler.schedule(layer.delay(), run);
			}
		}
	}

	public static void incoming(ServerLevel level, Vec3 target, int impactTicks, float volume) {
		SkillScheduler.schedule(Math.max(0, impactTicks - INCOMING_TICKS),
				() -> play(level, target.add(0, 6, 0), INCOMING, volume, 0.9f + level.getRandom().nextFloat() * 0.2f));
	}

	public static Holder<SoundEvent> forPower(float power) {
		if (power < 2.5f) {
			return EXPLOSION_SMALL;
		}
		if (power < 6f) {
			return EXPLOSION_MEDIUM;
		}
		return EXPLOSION_BIG;
	}

	public static void initialize() {
	}
}
