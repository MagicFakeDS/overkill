package com.example.mymod.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.example.mymod.MyMod;
import com.example.mymod.skill.Skill;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public final class RemoteSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mymod-remote.json");
	private static RemoteSettings instance;

	public boolean breakBlocks = true;
	public boolean fire = false;
	public boolean protectSelf = true;

	public boolean gravity = false;

	public boolean cinema = true;
	public int lastExpanded = 0;

	public int selected = 0;
	public Map<String, float[]> skills = new HashMap<>();

	public static RemoteSettings get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public float[] values(Skill skill) {
		float[] v = skills.computeIfAbsent(skill.id(), id -> new float[] {
				skill.radius().def(),
				skill.damage().def(),
				skill.speed() == null ? 1f : skill.speed().def()
		});
		if (v.length != 3) {
			skills.remove(skill.id());
			return values(skill);
		}
		return v;
	}

	public boolean toggle(int index) {
		return switch (index) {
			case 0 -> breakBlocks;
			case 1 -> fire;
			case 2 -> protectSelf;
			case 3 -> gravity;
			default -> cinema;
		};
	}

	public void flipToggle(int index) {
		switch (index) {
			case 0 -> breakBlocks = !breakBlocks;
			case 1 -> fire = !fire;
			case 2 -> protectSelf = !protectSelf;
			case 3 -> gravity = !gravity;
			default -> cinema = !cinema;
		}
	}

	private static RemoteSettings load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				RemoteSettings loaded = GSON.fromJson(reader, RemoteSettings.class);
				if (loaded != null) {
					if (loaded.skills == null) {
						loaded.skills = new HashMap<>();
					}
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				MyMod.LOGGER.warn("Не удалось прочитать {}, беру настройки по умолчанию", FILE, e);
			}
		}
		return new RemoteSettings();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			MyMod.LOGGER.warn("Не удалось сохранить {}", FILE, e);
		}
	}
}
