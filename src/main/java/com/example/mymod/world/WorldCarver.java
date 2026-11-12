package com.example.mymod.world;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongConsumer;

import com.example.mymod.MyMod;
import com.example.mymod.fx.Fx;
import com.example.mymod.fx.ModSounds;
import com.example.mymod.skill.SkillScheduler;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;

public final class WorldCarver {
	private static final long BUDGET_NANOS = 35_000_000L;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final EnumSet<Heightmap.Types> HEIGHTMAPS = EnumSet.of(Heightmap.Types.WORLD_SURFACE,
			Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR);

	private enum Mode { CRATER, SPHERE, CHUNK_SPLIT, SURFACE, FLATTEN, COLUMN, RING, FISSURE, TRENCH, SPIKES, DOME, ISLAND }

	public enum Surface { FREEZE, GLASS, HELL, END, BLIGHT }

	public enum Spire { BASALT, ICE, CRYSTAL, OBSIDIAN, END, STONE }

	private static final List<Job> JOBS = new ArrayList<>();

	private WorldCarver() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (JOBS.isEmpty()) {
				return;
			}
			long deadline = System.nanoTime() + BUDGET_NANOS;
			Iterator<Job> it = JOBS.iterator();
			while (it.hasNext() && System.nanoTime() < deadline) {
				Job job = it.next();
				try {
					if (job.step(deadline)) {
						it.remove();
					}
				} catch (RuntimeException e) {
					MyMod.LOGGER.error("Ошибка при разрушении мира", e);
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> JOBS.clear());
	}

	public static void crater(ServerLevel level, Vec3 center, float radius, float depthRatio, float devastation,
			boolean fire, boolean molten) {
		Job job = new Job(level, Mode.CRATER, center, radius, radius * Math.max(1f, devastation));
		job.depth = radius * depthRatio;
		job.devastation = Math.max(1f, devastation);
		job.fire = fire;
		job.molten = molten;
		JOBS.add(job);
	}

	public static void sphere(ServerLevel level, Vec3 center, float radius) {
		JOBS.add(new Job(level, Mode.SPHERE, center, radius, radius));
	}

	public static void splitChunks(ServerLevel level, BlockPos center, int chunkRadius, float fallChance, long seed,
			LongConsumer onFallen) {
		Job job = new Job(level, Mode.CHUNK_SPLIT, Vec3.atCenterOf(center), chunkRadius * 16f + 8f, chunkRadius * 16f + 8f);
		job.fallChance = fallChance;
		job.seed = seed;
		job.chunkRadius = chunkRadius;
		job.onFallen = onFallen;
		JOBS.add(job);
	}

	public static void transform(ServerLevel level, Vec3 center, float radius, Surface surface, boolean fire) {
		Job job = new Job(level, Mode.SURFACE, center, radius, radius * 1.1f);
		job.surface = surface;
		job.fire = fire;
		JOBS.add(job);
	}

	public static void flatten(ServerLevel level, Vec3 center, float radius) {
		JOBS.add(new Job(level, Mode.FLATTEN, center, radius, radius * 1.06f));
	}

	public static void column(ServerLevel level, Vec3 center, float radius) {
		JOBS.add(new Job(level, Mode.COLUMN, center, radius, radius));
	}

	public static void ring(ServerLevel level, Vec3 center, float radius, float depth) {
		Job job = new Job(level, Mode.RING, center, radius, radius * 1.14f + 4);
		job.depth = depth;
		JOBS.add(job);
	}

	public static void fissures(ServerLevel level, Vec3 center, float radius, float depth, int count, boolean molten) {
		Job job = new Job(level, Mode.FISSURE, center, radius, radius + 8);
		job.depth = depth;
		job.molten = molten;
		job.makeCracks(count);
		JOBS.add(job);
	}

	public static void volcano(ServerLevel level, Vec3 center, float radius, boolean fire) {
		Job job = new Job(level, Mode.CRATER, center, radius, radius * 1.6f);
		job.depth = radius * 0.5f;
		job.devastation = 1.6f;
		job.fire = fire;
		job.molten = true;
		job.fillState = Blocks.LAVA.defaultBlockState();
		JOBS.add(job);
	}

	public static void lake(ServerLevel level, Vec3 center, float radius, float depthRatio) {
		Job job = new Job(level, Mode.CRATER, center, radius, radius * 1.3f);
		job.depth = radius * depthRatio;
		job.devastation = 1.3f;
		job.fillState = Blocks.WATER.defaultBlockState();
		job.fillLevel = 0.03f;
		JOBS.add(job);
	}

	public static void trench(ServerLevel level, Vec3 from, Vec3 to, float width, float depth, boolean molten) {
		double len = Math.hypot(to.x - from.x, to.z - from.z);
		Job job = new Job(level, Mode.TRENCH, from, (float) len, (float) len + width + 4);
		job.width = width;
		job.depth = depth;
		job.molten = molten;
		job.cracks.add(new double[] {from.x, from.z, to.x, to.z, 0, len});
		job.keepChunks(packed -> segmentDistance((int) (packed >> 32) * 16 + 8, (int) packed * 16 + 8,
				from.x, from.z, to.x, to.z) <= width + 14);
		JOBS.add(job);
	}

	public static void spikes(ServerLevel level, Vec3 center, float radius, int count, float height, float base,
			Spire spire) {
		java.util.Random r = new java.util.Random(level.getRandom().nextLong());
		for (int i = 0; i < count; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = radius * Math.sqrt(r.nextDouble());
			double k = 1 - 0.45 * d / radius;
			float lean = (float) (r.nextGaussian() * 0.08);
			double la = r.nextDouble() * Math.PI * 2;
			int delay = (int) (d / 3) + r.nextInt(6);
			spike(level, center.x + Math.cos(a) * d, center.z + Math.sin(a) * d,
					(float) (base * (0.6 + 0.6 * r.nextDouble()) * k), (float) (height * (0.45 + 0.55 * r.nextDouble()) * k),
					(float) (Math.cos(la) * lean), (float) (Math.sin(la) * lean), false, spire, delay);
		}
	}

	public static void spike(ServerLevel level, double x, double z, float base, float height, float leanX, float leanZ,
			boolean pillar, Spire spire, int delay) {
		SkillScheduler.schedule(delay, () -> {
			int gy = SpikeShape.ground(level, Mth.floor(x), Mth.floor(z));
			SpikeShape shape = SpikeShape.build(level, x, z, gy, base, height, leanX, leanZ, pillar, spire);
			if (shape.top == Integer.MIN_VALUE) {
				return;
			}
			int pre = 8;
			int rise = shape.riseTicks();
			Fx.spikeRise(level, shape, pre, rise);
			SkillScheduler.schedule(pre, () -> spikeSound(level, shape));
			SkillScheduler.schedule(pre + rise, () -> {
				Job job = new Job(level, Mode.SPIKES, new Vec3(x, gy, z), 1,
						(float) Math.hypot(shape.sizeX, shape.sizeZ) + 2);
				job.shape = shape;
				JOBS.add(0, job);
			});
		});
	}

	private static void spikeSound(ServerLevel level, SpikeShape s) {
		float vol = Mth.clamp(s.height / 12f, 1.5f, 6f);
		float pitch = 0.8f + level.getRandom().nextFloat() * 0.4f;
		SoundEvent crack = switch (s.spire) {
			case ICE -> SoundEvents.GLASS_BREAK;
			case CRYSTAL -> SoundEvents.AMETHYST_BLOCK_BREAK;
			default -> SoundEvents.BASALT_BREAK;
		};
		level.playSound(null, s.x, s.gy + 1, s.z, crack, SoundSource.BLOCKS, vol, pitch * 0.6f);
		ModSounds.play(level, new Vec3(s.x, s.gy + 1, s.z), ModSounds.QUAKE, vol * 0.5f, 1.3f + pitch * 0.3f);
	}

	public static void dome(ServerLevel level, Vec3 center, float radius, float thickness, BlockState a, BlockState b) {
		int stages = 12;
		int grow = Mth.clamp(Math.round(radius * 0.8f), 20, 80);
		Fx.domeGrow(level, center, radius, grow / 20f, 0xFFB080FF);
		for (int k = 0; k < stages; k++) {
			int kk = k;
			SkillScheduler.schedule(Math.round(grow * (k + 1) / (float) stages), () -> {
				Job job = new Job(level, Mode.DOME, center, radius, radius);
				job.width = thickness;
				job.shellA = a;
				job.shellB = b;
				job.yLimit = (int) Math.floor(center.y - 6 + (radius + 6) * (kk + 1) / (double) stages);
				JOBS.add(0, job);
			});
		}
	}

	public static void island(ServerLevel level, double x, double z, float radius, int lift, int pre, int rise) {
		int gy = SpikeShape.ground(level, Mth.floor(x), Mth.floor(z));
		IslandShape shape = new IslandShape(x, gy, z, radius, level.getRandom().nextInt());
		IslandData data = new IslandData(shape.size);
		Vec3 c = new Vec3(x, gy, z);

		Fx.islandRise(level, shape, lift, pre, rise);
		liftEntities(level, shape, lift, pre, rise);
		Job cutJob = new Job(level, Mode.ISLAND, c, radius, radius * 1.3f + 2);
		cutJob.island = shape;
		cutJob.islandData = data;
		JOBS.add(0, cutJob);
		ModSounds.play(level, c, ModSounds.QUAKE, Mth.clamp(radius / 4f, 3f, 12f), 0.7f);
		SkillScheduler.schedule(pre, () -> {
			level.playSound(null, x, gy, z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, Mth.clamp(radius / 4f, 3f, 12f), 0.5f);
			ModSounds.play(level, c, ModSounds.CHUNK_COLLAPSE, Mth.clamp(radius / 5f, 2f, 8f), 0.6f);
		});
		SkillScheduler.schedule(pre + rise, () -> {
			Job put = new Job(level, Mode.ISLAND, c.add(0, lift, 0), radius, radius * 1.3f + 2);
			put.island = shape;
			put.islandData = data;
			put.paste = true;
			put.lift = lift;
			JOBS.add(0, put);
		});
	}

	private static void liftEntities(ServerLevel level, IslandShape s, int lift, int pre, int rise) {
		AABB box = new AABB(s.minX, s.gy - s.depth - 2, s.minZ, s.minX + s.size, s.gy + 48, s.minZ + s.size);
		List<Entity> list = level.getEntities((Entity) null, box,
				e -> !e.isSpectator() && s.inside(Mth.floor(e.getX()), Mth.floor(e.getZ())));
		if (list.isEmpty()) {
			return;
		}
		for (int t = 0; t < pre + rise; t++) {
			int tt = t;
			SkillScheduler.schedule(t, () -> {
				double v = tt < pre ? 0
						: lift * (IslandShape.ease((tt + 1 - pre) / (double) rise) - IslandShape.ease((tt - pre) / (double) rise));
				for (Entity e : list) {
					if (!e.isAlive()) {
						continue;
					}
					Vec3 m = e.getDeltaMovement();
					e.setDeltaMovement(m.x * 0.6, v, m.z * 0.6);
					e.hurtMarked = true;
					e.resetFallDistance();
				}
			});
		}
		SkillScheduler.schedule(pre + rise, () -> {
			for (Entity e : list) {
				e.resetFallDistance();
				if (e instanceof LivingEntity living) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
				}
			}
		});
	}

	private static final class IslandData {
		final BlockState[][] states;
		final int[] bottoms;

		IslandData(int size) {
			states = new BlockState[size * size][];
			bottoms = new int[size * size];
		}
	}

	private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
		double ex = bx - ax;
		double ez = bz - az;
		double len2 = ex * ex + ez * ez;
		double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * ex + (pz - az) * ez) / len2));
		double qx = ax + ex * t - px;
		double qz = az + ez * t - pz;
		return Math.sqrt(qx * qx + qz * qz);
	}

	public static boolean isFallen(int cx, int cz, long seed, float chance) {
		long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (double) (1L << 24) < chance;
	}

	private static final class Job {
		final ServerLevel level;
		final Mode mode;
		final Vec3 c;
		final float radius;
		float depth;
		float devastation = 1f;
		boolean fire;
		boolean molten;
		float fallChance;
		long seed;
		int chunkRadius;
		LongConsumer onFallen;
		Surface surface = Surface.FREEZE;
		BlockState fillState;
		float fillLevel = 0.35f;
		double width;
		SpikeShape shape;
		IslandShape island;
		IslandData islandData;
		boolean paste;
		int lift;
		int yLimit = Integer.MAX_VALUE;
		BlockState shellA = Blocks.OBSIDIAN.defaultBlockState();
		BlockState shellB = Blocks.OBSIDIAN.defaultBlockState();
		final List<double[]> cracks = new ArrayList<>();
		long[] chunks;
		int next;
		final float n1;
		final float n2;
		final float n3;
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Job(ServerLevel level, Mode mode, Vec3 c, float radius, float outer) {
			this.level = level;
			this.mode = mode;
			this.c = c;
			this.radius = radius;
			this.n1 = level.getRandom().nextFloat() * 6.28f;
			this.n2 = level.getRandom().nextFloat() * 6.28f;
			this.n3 = level.getRandom().nextFloat() * 6.28f;

			int ccx = (int) Math.floor(c.x) >> 4;
			int ccz = (int) Math.floor(c.z) >> 4;
			int rc = (int) Math.ceil(outer / 16f) + 1;
			List<long[]> list = new ArrayList<>();
			for (int dx = -rc; dx <= rc; dx++) {
				for (int dz = -rc; dz <= rc; dz++) {
					int cx = ccx + dx;
					int cz = ccz + dz;
					double nx = Math.max(cx * 16, Math.min(c.x, cx * 16 + 16)) - c.x;
					double nz = Math.max(cz * 16, Math.min(c.z, cz * 16 + 16)) - c.z;
					if (mode != Mode.CHUNK_SPLIT && nx * nx + nz * nz > outer * outer) {
						continue;
					}
					if (mode == Mode.CHUNK_SPLIT && (Math.abs(dx) > chunkRadiusFor(outer) || Math.abs(dz) > chunkRadiusFor(outer))) {
						continue;
					}
					double ex = cx * 16 + 8 - c.x;
					double ez = cz * 16 + 8 - c.z;
					list.add(new long[] {(long) (ex * ex + ez * ez), ((long) cx << 32) | (cz & 0xFFFFFFFFL)});
				}
			}
			list.sort((a, b) -> Long.compare(a[0], b[0]));
			chunks = new long[list.size()];
			for (int i = 0; i < chunks.length; i++) {
				chunks[i] = list.get(i)[1];
			}
		}

		void keepChunks(java.util.function.LongPredicate keep) {
			chunks = java.util.Arrays.stream(chunks).filter(keep).toArray();
		}

		void makeCracks(int count) {
			java.util.Random r = new java.util.Random(level.getRandom().nextLong());
			for (int i = 0; i < count; i++) {
				double ang = Math.PI * 2 * i / count + r.nextGaussian() * 0.3;
				double x = c.x;
				double z = c.z;
				double s = 0;
				double step = Math.max(4, radius / 14);
				while (s < radius) {
					ang += r.nextGaussian() * 0.35;
					double nx = x + Math.cos(ang) * step;
					double nz = z + Math.sin(ang) * step;
					cracks.add(new double[] {x, z, nx, nz, s, s + step});
					x = nx;
					z = nz;
					s += step;

					if (r.nextFloat() < 0.12 && s < radius * 0.7) {
						double ba = ang + (r.nextBoolean() ? 0.8 : -0.8);
						double bx = x;
						double bz = z;
						for (double bs = s; bs < Math.min(radius, s + radius * 0.35); bs += step) {
							ba += r.nextGaussian() * 0.35;
							double ex = bx + Math.cos(ba) * step;
							double ez = bz + Math.sin(ba) * step;
							cracks.add(new double[] {bx, bz, ex, ez, bs, bs + step});
							bx = ex;
							bz = ez;
						}
					}
				}
			}
		}

		private static int chunkRadiusFor(float outer) {
			return Math.round((outer - 8f) / 16f);
		}

		boolean step(long deadline) {
			while (next < chunks.length) {
				if (System.nanoTime() > deadline) {
					return false;
				}
				long packed = chunks[next++];
				processChunk((int) (packed >> 32), (int) packed);
			}
			return true;
		}

		private void processChunk(int cx, int cz) {
			ServerChunkCache cache = level.getChunkSource();
			LevelChunk chunk = cache.getChunkNow(cx, cz);
			if (chunk == null) {
				return;
			}
			LevelLightEngine light = cache.getLightEngine();
			boolean fallen = mode == Mode.CHUNK_SPLIT && isFallen(cx, cz, seed, fallChance);
			if (fallen && onFallen != null) {
				onFallen.accept(((long) cx << 32) | (cz & 0xFFFFFFFFL));
			}
			boolean changed = false;
			int minY = level.getMinY();

			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					int x = (cx << 4) + lx;
					int z = (cz << 4) + lz;
					int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
					if (top < minY && mode != Mode.SPIKES && !(mode == Mode.ISLAND && paste)) {
						continue;
					}
					changed |= switch (mode) {
						case CRATER -> crater(chunk, light, cache, lx, lz, x, z, top, minY);
						case SPHERE -> sphere(chunk, light, cache, lx, lz, x, z, top, minY);
						case CHUNK_SPLIT -> (fallen || lx == 0 || lx == 15 || lz == 0 || lz == 15)
								&& clear(chunk, light, cache, lx, lz, x, z, top, minY, true);
						case SURFACE -> surface(chunk, light, cache, lx, lz, x, z, top, minY);
						case FLATTEN -> flatten(chunk, light, cache, lx, lz, x, z, top, minY);
						case COLUMN -> column(chunk, light, cache, lx, lz, x, z, top, minY);
						case RING -> ring(chunk, light, cache, lx, lz, x, z, top, minY);
						case FISSURE -> fissure(chunk, light, cache, lx, lz, x, z, top, minY);
						case TRENCH -> trench(chunk, light, cache, lx, lz, x, z, top, minY);
						case SPIKES -> shape(chunk, light, cache, lx, lz, x, z, top);
						case ISLAND -> paste ? islandPaste(chunk, light, cache, lx, lz, x, z)
								: islandCut(chunk, light, cache, lx, lz, x, z, top, minY);
						case DOME -> dome(chunk, light, cache, lx, lz, x, z, top, minY);
					};
				}
			}
			if (changed) {
				Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
				chunk.markUnsaved();
			}
		}

		private boolean crater(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double dx = x + 0.5 - c.x;
			double dz = z + 0.5 - c.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			double ang = Math.atan2(dz, dx);
			double edge = 1 + 0.07 * Math.sin(5 * ang + n1) + 0.05 * Math.sin(9 * ang + n2) + 0.03 * Math.sin(17 * ang + n3);
			double nd = d / (radius * edge);
			float rnd = hash01(x, z);

			if (nd <= 1) {
				double depthCol = depth * Math.pow(1 - nd * nd, 0.55);
				int floorY = Math.max(minY, (int) Math.floor(c.y - depthCol));
				boolean changed = clearRange(chunk, cache, lx, lz, x, z, floorY + 1, top, false);

				BlockState floor;
				if (fillState != null && fillState.is(Blocks.WATER)) {
					floor = rnd < 0.5 ? Blocks.SAND.defaultBlockState()
							: rnd < 0.8 ? Blocks.GRAVEL.defaultBlockState() : Blocks.CLAY.defaultBlockState();
				} else if (molten && nd < 0.12 && rnd < 0.10) {
					floor = Blocks.LAVA.defaultBlockState();
				} else if (molten && nd < 0.35 && rnd < 0.35) {
					floor = Blocks.MAGMA_BLOCK.defaultBlockState();
				} else if (rnd < 0.45) {
					floor = Blocks.BLACKSTONE.defaultBlockState();
				} else if (rnd < 0.7) {
					floor = Blocks.BASALT.defaultBlockState();
				} else if (rnd < 0.82) {
					floor = Blocks.SMOOTH_BASALT.defaultBlockState();
				} else if (rnd < 0.93) {
					floor = Blocks.OBSIDIAN.defaultBlockState();
				} else {
					floor = Blocks.CRYING_OBSIDIAN.defaultBlockState();
				}
				if (floorY > minY && !get(chunk, lx, floorY, lz).isAir()) {
					changed |= set(chunk, cache, lx, floorY, lz, x, z, floor);
				}
				if (fillState != null) {
					int fillTop = (int) Math.floor(c.y - Math.max(1.5, depth * fillLevel));
					for (int y = floorY + 1; y <= fillTop && y <= level.getMaxY(); y++) {
						changed |= set(chunk, cache, lx, y, lz, x, z, fillState);
					}
				}
				if (fire && rnd > 0.96 && floorY + 1 <= level.getMaxY()) {
					changed |= set(chunk, cache, lx, floorY + 1, lz, x, z, Blocks.FIRE.defaultBlockState());
				}
				if (changed) {
					relight(light, x, z, floorY, top);
				}
				return changed;
			}
			if (nd > devastation) {
				return false;
			}

			double t = (nd - 1) / (devastation - 1);
			boolean changed = false;
			int bottom = Math.max(minY, top - 48);
			for (int y = top; y >= bottom; y--) {
				BlockState state = get(chunk, lx, y, lz);
				if (state.isAir()) {
					continue;
				}
				boolean natural = state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
						|| state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.SNOW)
						&& state.isSolid();
				boolean vegetation = state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
						|| state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || !state.isSolid();
				if (vegetation || (!natural && t < 0.45)) {
					changed |= set(chunk, cache, lx, y, lz, x, z, AIR);
					continue;
				}
				if (!natural) {
					break;
				}

				BlockState burnt;
				if (t < 0.3 && rnd < 0.5) {
					burnt = rnd < 0.25 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
				} else if (state.is(BlockTags.DIRT)) {
					burnt = Blocks.COARSE_DIRT.defaultBlockState();
				} else {
					burnt = state;
				}
				if (burnt != state) {
					changed |= set(chunk, cache, lx, y, lz, x, z, burnt);
				}
				if (fire && y + 1 <= level.getMaxY() && rnd > 0.55 + 0.4 * t) {
					changed |= set(chunk, cache, lx, y + 1, lz, x, z, Blocks.FIRE.defaultBlockState());
				}
				if (changed) {
					relight(light, x, z, y, top);
				}
				break;
			}
			return changed;
		}

		private boolean sphere(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double dx = x + 0.5 - c.x;
			double dz = z + 0.5 - c.z;
			double d2 = dx * dx + dz * dz;
			double r2 = (double) radius * radius;
			if (d2 > r2) {
				return false;
			}
			double h = Math.sqrt(r2 - d2);
			int y0 = Math.max(minY, (int) Math.floor(c.y - h));
			int y1 = Math.min(top, (int) Math.ceil(c.y + h));
			boolean changed = clearRange(chunk, cache, lx, lz, x, z, y0, y1, true);
			if (changed) {
				relight(light, x, z, y0, y1);
			}
			return changed;
		}

		private boolean clear(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY, boolean bedrock) {
			boolean changed = clearRange(chunk, cache, lx, lz, x, z, minY, top, bedrock);
			if (changed) {
				relight(light, x, z, minY, top);
			}
			return changed;
		}

		private boolean surface(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double dx = x + 0.5 - c.x;
			double dz = z + 0.5 - c.z;
			double ang = Math.atan2(dz, dx);
			double edge = 1 + 0.08 * Math.sin(5 * ang + n1) + 0.05 * Math.sin(11 * ang + n2);
			double nd = Math.sqrt(dx * dx + dz * dz) / (radius * edge);
			if (nd > 1) {
				return false;
			}
			float rnd = hash01(x, z);
			boolean changed = false;
			int bottom = Math.max(minY, top - 40);
			for (int y = top; y >= bottom; y--) {
				BlockState state = get(chunk, lx, y, lz);
				if (state.isAir()) {
					continue;
				}
				boolean leaves = state.is(BlockTags.LEAVES);
				boolean logs = state.is(BlockTags.LOGS);
				boolean water = state.is(Blocks.WATER);
				boolean lava = state.is(Blocks.LAVA);
				boolean natural = state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
						|| state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.SNOW) && state.isSolid();
				boolean plant = !leaves && !logs && !water && !lava && (state.is(BlockTags.REPLACEABLE)
						|| state.is(BlockTags.FLOWERS) || !state.isSolid());
				BlockState to = null;
				boolean stop = false;
				switch (surface) {
					case FREEZE -> {
						if (leaves) {
							to = rnd < 0.5 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
						} else if (logs) {
							to = Blocks.PACKED_ICE.defaultBlockState();
						} else if (water) {
							to = Blocks.ICE.defaultBlockState();
							stop = y < top - 3;
						} else if (lava) {
							to = Blocks.OBSIDIAN.defaultBlockState();
							stop = true;
						} else if (plant) {
							to = AIR;
						} else if (natural) {
							to = nd < 0.25 && rnd < 0.4 ? Blocks.BLUE_ICE.defaultBlockState()
									: state.is(BlockTags.BASE_STONE_OVERWORLD) ? Blocks.PACKED_ICE.defaultBlockState()
									: Blocks.SNOW_BLOCK.defaultBlockState();
							stop = true;
						} else {
							stop = true;
						}
					}
					case GLASS -> {
						if (leaves || logs || plant) {
							to = AIR;
						} else if (water) {
							to = AIR;
						} else if (natural || lava) {
							to = rnd < 0.15 ? Blocks.OBSIDIAN.defaultBlockState()
									: rnd < 0.35 ? Blocks.TINTED_GLASS.defaultBlockState() : Blocks.GLASS.defaultBlockState();

							for (int k = 1; k <= 2 && y - k >= minY; k++) {
								if (!get(chunk, lx, y - k, lz).isAir()) {
									changed |= set(chunk, cache, lx, y - k, lz, x, z, Blocks.GLASS.defaultBlockState());
								}
							}
							stop = true;
						} else {
							to = rnd < 0.5 ? Blocks.GLASS.defaultBlockState() : AIR;
						}
					}
					case HELL -> {
						if (leaves || plant) {
							to = AIR;
						} else if (logs) {
							to = Blocks.BASALT.defaultBlockState();
						} else if (water) {
							to = AIR;
						} else if (natural) {
							to = rnd < 0.04 ? Blocks.LAVA.defaultBlockState()
									: rnd < 0.14 ? Blocks.MAGMA_BLOCK.defaultBlockState()
									: rnd < 0.3 ? Blocks.SOUL_SOIL.defaultBlockState()
									: rnd < 0.36 ? Blocks.SOUL_SAND.defaultBlockState()
									: Blocks.NETHERRACK.defaultBlockState();
							if (fire && rnd > 0.82 && y + 1 <= level.getMaxY()) {
								changed |= set(chunk, cache, lx, y + 1, lz, x, z, Blocks.FIRE.defaultBlockState());
							}
							stop = true;
						} else {
							stop = true;
						}
					}
					case END -> {
						if (leaves || logs || plant || water) {
							to = AIR;
						} else if (natural || lava) {
							to = Blocks.END_STONE.defaultBlockState();
							if (y - 1 >= minY && !get(chunk, lx, y - 1, lz).isAir()) {
								changed |= set(chunk, cache, lx, y - 1, lz, x, z, Blocks.END_STONE.defaultBlockState());
							}

							if (rnd > 0.996) {
								int h = 8 + (int) (hash01(z, x) * 26);
								for (int k = 1; k <= h && y + k <= level.getMaxY(); k++) {
									changed |= set(chunk, cache, lx, y + k, lz, x, z, Blocks.OBSIDIAN.defaultBlockState());
								}
							}
							stop = true;
						} else {
							stop = true;
						}
					}
					case BLIGHT -> {
						if (leaves || plant) {
							to = AIR;
						} else if (natural) {
							if (state.is(BlockTags.DIRT)) {
								to = rnd < 0.45 ? Blocks.MYCELIUM.defaultBlockState()
										: rnd < 0.75 ? Blocks.PODZOL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
								if (rnd > 0.9 && y + 1 <= level.getMaxY()) {
									changed |= set(chunk, cache, lx, y + 1, lz, x, z, Blocks.DEAD_BUSH.defaultBlockState());
								}
							}
							stop = true;
						} else {
							stop = true;
						}
					}
				}
				if (to != null) {
					changed |= set(chunk, cache, lx, y, lz, x, z, to);
				}
				if (stop) {
					break;
				}
			}
			if (changed) {
				relight(light, x, z, bottom, top);
			}
			return changed;
		}

		private boolean flatten(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double dx = x + 0.5 - c.x;
			double dz = z + 0.5 - c.z;
			double ang = Math.atan2(dz, dx);
			double edge = 1 + 0.05 * Math.sin(7 * ang + n1);
			if (Math.sqrt(dx * dx + dz * dz) > radius * edge) {
				return false;
			}
			int floor = (int) Math.floor(c.y);
			if (top <= floor) {
				return false;
			}
			boolean changed = clearRange(chunk, cache, lx, lz, x, z, floor + 1, top, true);
			if (changed) {
				relight(light, x, z, floor, top);
			}
			return changed;
		}

		private boolean column(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double dx = x + 0.5 - c.x;
			double dz = z + 0.5 - c.z;
			if (dx * dx + dz * dz > (double) radius * radius) {
				return false;
			}
			return clear(chunk, light, cache, lx, lz, x, z, top, minY, true);
		}

		private boolean ring(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double dx = x + 0.5 - c.x;
			double dz = z + 0.5 - c.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			double w = Math.max(4, radius * 0.12);
			double k = (d - radius) / w;
			if (Math.abs(k) > 1) {
				return false;
			}
			double depthCol = depth * Math.sqrt(1 - k * k);
			int floorY = Math.max(minY, (int) Math.floor(c.y - depthCol));
			boolean changed = clearRange(chunk, cache, lx, lz, x, z, floorY + 1, top, false);
			if (floorY > minY && !get(chunk, lx, floorY, lz).isAir()) {
				float rnd = hash01(x, z);
				changed |= set(chunk, cache, lx, floorY, lz, x, z,
						rnd < 0.5 ? Blocks.BLACKSTONE.defaultBlockState() : rnd < 0.8 ? Blocks.BASALT.defaultBlockState()
								: Blocks.MAGMA_BLOCK.defaultBlockState());
			}
			if (changed) {
				relight(light, x, z, floorY, top);
			}
			return changed;
		}

		private boolean fissure(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double px = x + 0.5;
			double pz = z + 0.5;
			double best = Double.MAX_VALUE;
			double bestS = 0;
			for (double[] seg : cracks) {
				double ex = seg[2] - seg[0];
				double ez = seg[3] - seg[1];
				double len2 = ex * ex + ez * ez;
				double tt = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - seg[0]) * ex + (pz - seg[1]) * ez) / len2));
				double qx = seg[0] + ex * tt - px;
				double qz = seg[1] + ez * tt - pz;
				double dist = Math.sqrt(qx * qx + qz * qz);
				if (dist < best) {
					best = dist;
					bestS = seg[4] + tt * (seg[5] - seg[4]);
				}
			}
			double s = bestS / radius;
			double width = 1.5 + (radius * 0.035) * (1 - s);
			if (best > width) {
				return false;
			}
			double depthCol = depth * (1 - 0.6 * s) * (1 - best / width * 0.5);
			int floorY = Math.max(minY, (int) Math.floor(Math.min(top, c.y) - depthCol));
			boolean changed = clearRange(chunk, cache, lx, lz, x, z, floorY + 1, top, false);
			if (molten && floorY > minY && hash01(x, z) < 0.25) {
				changed |= set(chunk, cache, lx, floorY, lz, x, z, Blocks.LAVA.defaultBlockState());
			}
			if (changed) {
				relight(light, x, z, floorY, top);
			}
			return changed;
		}

		private boolean trench(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double[] s = cracks.get(0);
			double px = x + 0.5;
			double pz = z + 0.5;
			double ex = s[2] - s[0];
			double ez = s[3] - s[1];
			double len2 = ex * ex + ez * ez;
			double tt = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - s[0]) * ex + (pz - s[1]) * ez) / len2));
			double qx = s[0] + ex * tt - px;
			double qz = s[1] + ez * tt - pz;
			double dist = Math.sqrt(qx * qx + qz * qz);
			double len = Math.sqrt(len2);
			double along = tt * len;
			double w = width * (1 + 0.10 * Math.sin(along * 0.11 + n1) + 0.06 * Math.sin(along * 0.37 + n2));
			w *= Math.min(1, Math.min(along, len - along) / Math.max(4, width) + 0.35);
			if (dist > w + 1.6) {
				return false;
			}
			if (dist > w) {
				if (!molten || top <= minY) {
					return false;
				}
				BlockState state = get(chunk, lx, top, lz);
				if (state.isAir() || !state.isSolid()) {
					return false;
				}
				boolean changed = set(chunk, cache, lx, top, lz, x, z,
						hash01(x, z) < 0.6 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
				if (changed) {
					relight(light, x, z, top, top);
				}
				return changed;
			}
			boolean full = depth <= 0;
			int floorY = full ? minY
					: Math.max(minY, (int) Math.floor(Math.min(top, c.y) - depth * Math.sqrt(1 - (dist / w) * (dist / w))));
			boolean changed = clearRange(chunk, cache, lx, lz, x, z, full ? minY : floorY + 1, top, full);
			if (!full && floorY > minY && !get(chunk, lx, floorY, lz).isAir()) {
				float rnd = hash01(x, z);
				changed |= set(chunk, cache, lx, floorY, lz, x, z,
						molten && rnd < 0.2 ? Blocks.LAVA.defaultBlockState()
								: molten && rnd < 0.45 ? Blocks.MAGMA_BLOCK.defaultBlockState()
								: rnd < 0.75 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState());
			}
			if (changed) {
				relight(light, x, z, full ? minY : floorY, top);
			}
			return changed;
		}

		private boolean shape(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top) {
			int i = shape.index(x, z);
			if (i < 0 || shape.lo[i] > shape.hi[i]) {
				return false;
			}
			boolean changed = false;
			for (int y = shape.lo[i]; y <= shape.hi[i]; y++) {
				changed |= set(chunk, cache, lx, y, lz, x, z, shape.block(x, y, z));
			}
			if (changed) {
				relight(light, x, z, shape.lo[i], Math.max(top, shape.hi[i]));
			}
			return changed;
		}

		private boolean islandCut(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			if (!island.inside(x, z)) {
				return false;
			}
			int b = Math.max(minY + 1, island.bottom(x, z));
			if (top < b) {
				return false;
			}
			int i = island.index(x, z);
			BlockState[] arr = new BlockState[top - b + 1];
			for (int y = b; y <= top; y++) {
				BlockState st = get(chunk, lx, y, lz);
				boolean keep = !st.hasBlockEntity() && st.getDestroySpeed(level, pos.set(x, y, z)) >= 0;
				arr[y - b] = keep ? st : AIR;
			}
			islandData.states[i] = arr;
			islandData.bottoms[i] = b;
			boolean changed = clearRange(chunk, cache, lx, lz, x, z, b, top, false);
			if (changed) {
				relight(light, x, z, b, top);
			}
			return changed;
		}

		private boolean islandPaste(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z) {
			int i = island.index(x, z);
			if (i < 0 || islandData.states[i] == null) {
				return false;
			}
			BlockState[] arr = islandData.states[i];
			int b = islandData.bottoms[i] + lift;
			boolean changed = false;
			for (int k = 0; k < arr.length; k++) {
				if (!arr[k].isAir()) {
					changed |= set(chunk, cache, lx, b + k, lz, x, z, arr[k]);
				}
			}
			if (changed) {
				relight(light, x, z, b - 1, b + arr.length);
			}
			return changed;
		}

		private boolean dome(LevelChunk chunk, LevelLightEngine light, ServerChunkCache cache,
				int lx, int lz, int x, int z, int top, int minY) {
			double dx = x + 0.5 - c.x;
			double dz = z + 0.5 - c.z;
			double d2 = dx * dx + dz * dz;
			double ro = radius;
			double ri = Math.max(0, radius - width);
			if (d2 > ro * ro) {
				return false;
			}
			double ho = Math.sqrt(ro * ro - d2);
			int y1 = Math.min(Math.min(level.getMaxY(), yLimit), (int) Math.floor(c.y + ho));
			int y0 = d2 < ri * ri ? (int) Math.ceil(c.y + Math.sqrt(ri * ri - d2)) : Math.min((int) Math.floor(c.y) - 6, top);
			boolean changed = false;
			for (int y = Math.max(minY, y0); y <= y1; y++) {
				BlockState st = get(chunk, lx, y, lz);
				if (!st.isAir() && !st.is(BlockTags.REPLACEABLE) && !st.is(BlockTags.LEAVES)) {
					continue;
				}
				changed |= set(chunk, cache, lx, y, lz, x, z, hash01(x + y * 31, z - y * 17) < 0.25 ? shellB : shellA);
			}
			if (changed) {
				relight(light, x, z, Math.max(minY, y0), y1);
			}
			return changed;
		}

		private boolean clearRange(LevelChunk chunk, ServerChunkCache cache, int lx, int lz, int x, int z,
				int y0, int y1, boolean bedrock) {
			boolean changed = false;
			for (int y = y1; y >= y0; y--) {
				BlockState state = get(chunk, lx, y, lz);
				if (state.isAir()) {
					continue;
				}
				if (!bedrock && state.getDestroySpeed(level, pos.set(x, y, z)) < 0) {
					continue;
				}
				changed |= set(chunk, cache, lx, y, lz, x, z, AIR);
			}
			if (changed) {
				GravityEngine.check(level, x, y1 + 1, z);
			}
			return changed;
		}

		private BlockState get(LevelChunk chunk, int lx, int y, int lz) {
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
			return section.getBlockState(lx, y & 15, lz);
		}

		private boolean set(LevelChunk chunk, ServerChunkCache cache, int lx, int y, int lz, int x, int z, BlockState state) {
			if (y < level.getMinY() || y > level.getMaxY()) {
				return false;
			}
			int index = chunk.getSectionIndex(y);
			LevelChunkSection section = chunk.getSection(index);
			BlockState old = section.getBlockState(lx, y & 15, lz);
			if (old == state) {
				return false;
			}
			pos.set(x, y, z);
			if (old.hasBlockEntity()) {
				level.removeBlockEntity(pos);
			}
			boolean wasEmpty = section.hasOnlyAir();
			section.setBlockState(lx, y & 15, lz, state, false);
			boolean nowEmpty = section.hasOnlyAir();
			if (wasEmpty != nowEmpty) {
				cache.getLightEngine().updateSectionStatus(SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index)), nowEmpty);
			}
			cache.blockChanged(pos);
			return true;
		}

		private void relight(LevelLightEngine light, int x, int z, int bottom, int top) {
			light.checkBlock(pos.set(x, top, z));
			light.checkBlock(pos.set(x, Math.max(bottom, level.getMinY()), z));
			light.checkBlock(pos.set(x, (top + bottom) / 2, z));
		}

		private float hash01(int x, int z) {
			int h = x * 0x9E3779B1 ^ z * 0x85EBCA77 ^ (int) seed;
			h ^= h >>> 15;
			h *= 0x2C1B3C6D;
			h ^= h >>> 12;
			return (h >>> 8) / (float) (1 << 24);
		}
	}
}
