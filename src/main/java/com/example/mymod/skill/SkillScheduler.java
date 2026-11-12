package com.example.mymod.skill;

import java.util.ArrayList;
import java.util.List;

import com.example.mymod.MyMod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class SkillScheduler {
	private record Task(long runAt, Runnable action) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static long tick;

	private SkillScheduler() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tick++;
			if (TASKS.isEmpty()) {
				return;
			}
			List<Task> due = new ArrayList<>();
			TASKS.removeIf(task -> {
				if (task.runAt() <= tick) {
					due.add(task);
					return true;
				}
				return false;
			});
			for (Task task : due) {
				try {
					task.action().run();
				} catch (RuntimeException e) {
					MyMod.LOGGER.error("Ошибка в отложенном действии навыка", e);
				}
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear());
	}

	public static void schedule(int delayTicks, Runnable action) {
		TASKS.add(new Task(tick + Math.max(0, delayTicks), action));
	}
}
