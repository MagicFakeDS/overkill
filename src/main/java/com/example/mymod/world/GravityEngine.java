package com.example.mymod.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class GravityEngine {
	public static volatile boolean enabled = false;

	private static final int SUPPORT = 4;

	private static final int MAX_RUN = 48;

	private static final int MAX_FALLING = 700;
	private static final int MAX_QUEUE = 600_000;
	private static final long BUDGET_NANOS = 6_000_000L;

	private static final Map<ServerLevel, Queue> QUEUES = new IdentityHashMap<>();
	private static final List<FallingBlockEntity> FALLING = new ArrayList<>();

	private static final class Queue {
		final LongArrayFIFOQueue fifo = new LongArrayFIFOQueue();
		final LongOpenHashSet set = new LongOpenHashSet();
	}

	private GravityEngine() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUES.clear();
			FALLING.clear();
		});
	}

	public static void check(ServerLevel level, int x, int y, int z) {
		if (!enabled) {
			return;
		}
		Queue q = QUEUES.computeIfAbsent(level, l -> new Queue());
		if (q.set.size() >= MAX_QUEUE) {
			return;
		}
		long key = BlockPos.asLong(x, y, z);
		if (q.set.add(key)) {
			q.fifo.enqueue(key);
		}
	}

	public static void afterExplosion(ServerLevel level, List<BlockPos> destroyed) {
		if (!enabled) {
			return;
		}
		for (BlockPos p : destroyed) {
			check(level, p.getX(), p.getY() + 1, p.getZ());
			check(level, p.getX() + 1, p.getY(), p.getZ());
			check(level, p.getX() - 1, p.getY(), p.getZ());
			check(level, p.getX(), p.getY(), p.getZ() + 1);
			check(level, p.getX(), p.getY(), p.getZ() - 1);
		}
	}

	private static void tick() {
		if (!FALLING.isEmpty()) {
			FALLING.removeIf(e -> e.isRemoved());
		}
		if (QUEUES.isEmpty()) {
			return;
		}
		long deadline = System.nanoTime() + BUDGET_NANOS;
		for (Map.Entry<ServerLevel, Queue> entry : QUEUES.entrySet()) {
			ServerLevel level = entry.getKey();
			Queue q = entry.getValue();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			while (!q.fifo.isEmpty() && FALLING.size() < MAX_FALLING && System.nanoTime() < deadline) {
				long key = q.fifo.dequeueLong();
				q.set.remove(key);
				pos.set(key);
				process(level, pos.immutable());
			}
		}
	}

	private static void process(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof LiquidBlock) {
			level.scheduleTick(pos, state.getFluidState().getType(), 4);
			return;
		}
		if (!canFall(level, pos, state) || supports(level, pos.below()) || heldSideways(level, pos)) {
			return;
		}

		List<BlockPos> run = new ArrayList<>();
		for (int k = 0; k < MAX_RUN; k++) {
			BlockPos p = pos.above(k);
			BlockState s = level.getBlockState(p);
			if (!canFall(level, p, s)) {
				break;
			}
			run.add(p);
		}
		for (BlockPos p : run) {
			BlockState s = level.getBlockState(p);
			FallingBlockEntity block = FallingBlockEntity.fall(level, p, s);
			block.dropItem = false;
			FALLING.add(block);
		}

		for (BlockPos p : run) {
			check(level, p.getX() + 1, p.getY(), p.getZ());
			check(level, p.getX() - 1, p.getY(), p.getZ());
			check(level, p.getX(), p.getY(), p.getZ() + 1);
			check(level, p.getX(), p.getY(), p.getZ() - 1);
		}
		if (!run.isEmpty()) {
			BlockPos top = run.get(run.size() - 1);
			check(level, top.getX(), top.getY() + 1, top.getZ());
		}
	}

	private static boolean canFall(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
			return false;
		}
		if (state.getDestroySpeed(level, pos) < 0) {
			return false;
		}
		return !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.NETHER_PORTAL)
				&& !state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_GATEWAY) && !state.is(Blocks.MOVING_PISTON)
				&& !state.is(Blocks.PISTON_HEAD);
	}

	private static boolean supports(ServerLevel level, BlockPos below) {
		BlockState b = level.getBlockState(below);
		return !b.isAir() && b.getFluidState().isEmpty() && !b.canBeReplaced();
	}

	private static boolean heldSideways(ServerLevel level, BlockPos start) {
		int size = SUPPORT * 2 + 1;
		boolean[] seen = new boolean[size * size];
		int[] qx = new int[size * size];
		int[] qz = new int[size * size];
		int[] qd = new int[size * size];
		int head = 0;
		int tail = 0;
		qx[tail] = 0;
		qz[tail] = 0;
		qd[tail++] = 0;
		seen[SUPPORT * size + SUPPORT] = true;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (head < tail) {
			int cx = qx[head];
			int cz = qz[head];
			int cd = qd[head++];
			if (cd > 0 && supports(level, p.set(start.getX() + cx, start.getY() - 1, start.getZ() + cz))) {
				return true;
			}
			if (cd >= SUPPORT) {
				continue;
			}
			for (int[] d : dirs) {
				int nx = cx + d[0];
				int nz = cz + d[1];
				int i = (nz + SUPPORT) * size + (nx + SUPPORT);
				if (Math.abs(nx) > SUPPORT || Math.abs(nz) > SUPPORT || seen[i]) {
					continue;
				}
				seen[i] = true;
				BlockState s = level.getBlockState(p.set(start.getX() + nx, start.getY(), start.getZ() + nz));
				if (s.isAir() || s.getBlock() instanceof LiquidBlock || s.canBeReplaced()) {
					continue;
				}
				qx[tail] = nx;
				qz[tail] = nz;
				qd[tail++] = cd + 1;
			}
		}
		return false;
	}
}
