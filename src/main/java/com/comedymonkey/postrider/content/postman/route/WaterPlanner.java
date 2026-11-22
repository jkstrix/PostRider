package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * The way across the water, block by block (A*): round the bends of a river or the shore of a lake rather than
 * straight at it, with a block of open water either side of the barge so it doesn't scrape the banks, and dear
 * near danger. Close to where he's landing, it can come right in to the shore.
 */
public final class WaterPlanner {
	/** Places looked at before it settles for the nearest it has got. */
	public static final int MAX_NODES = 8000;
	/** A plan that can't reach its goal is only worth following if it gets him at least this much nearer. */
	public static final double MIN_GAIN = 4;
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final double DIAGONAL = Math.sqrt(2);

	/** Whether a column is open water the barge can float on. */
	@FunctionalInterface
	public interface Water {
		boolean open(int x, int z);
	}

	/** What it costs on top of the distance to row through a column (near drowned, say). */
	@FunctionalInterface
	public interface Danger {
		double extra(int x, int z);
	}

	private WaterPlanner() {}

	/**
	 * The points to row through (block centres) from where the barge is to within {@code radius} of the goal; if it
	 * can't get there, to the nearest place it can get to, as long as that's {@link #MIN_GAIN} nearer. Empty if there's
	 * no better way.
	 */
	public static List<Vec3> plan(Water water, Danger danger, int startX, int startZ, double goalX, double goalZ, double radius) {
		Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Node start = new Node(startX, startZ);
		start.h = toGo(startX, startZ, goalX, goalZ);
		start.f = start.h;
		nodes.put(ChunkPos.pack(startX, startZ), start);
		open.add(start);
		Node best = start, reached = null;
		while (!open.isEmpty() && nodes.size() < MAX_NODES) {
			Node node = open.poll();
			if (node.closed)
				continue;
			node.closed = true;
			if (Math.hypot(node.x + 0.5 - goalX, node.z + 0.5 - goalZ) <= radius) {
				reached = node;
				break;
			}
			if (node.h < best.h)
				best = node;
			for (int[] d : DIRECTIONS) {
				int x = node.x + d[0], z = node.z + d[1];
				if (!floats(water, x, z, goalX, goalZ, radius))
					continue;
				boolean diagonal = d[0] != 0 && d[1] != 0;
				if (diagonal && (!water.open(node.x + d[0], node.z) || !water.open(node.x, node.z + d[1])))
					continue;
				double g = node.g + (diagonal ? DIAGONAL : 1) + danger.extra(x, z) + (tight(water, x, z, goalX, goalZ, radius) ? TIGHT_COST : 0);
				long key = ChunkPos.pack(x, z);
				Node next = nodes.get(key);
				if (next == null) {
					next = new Node(x, z);
					next.h = toGo(x, z, goalX, goalZ);
					nodes.put(key, next);
				} else if (next.closed || g >= next.g) {
					continue;
				}
				next.g = g;
				next.f = g + next.h;
				next.parent = node;
				open.add(next);
			}
		}
		Node last = reached != null ? reached : best.h <= start.h - MIN_GAIN ? best : null;
		if (last == null)
			return List.of();
		List<Vec3> points = new ArrayList<>();
		for (Node node = last; node != null; node = node.parent)
			points.add(new Vec3(node.x + 0.5, 0, node.z + 0.5));
		Collections.reverse(points);
		return points;
	}

	/** What a column costs on top where the water is only just wide enough: a lead the hull fits with nothing to spare catches its edges at every bend. */
	public static final double TIGHT_COST = 3;

	/** Whether the water round a column is only just wide enough: anything but open water within two blocks (away from the landing, where it comes right in). */
	static boolean tight(Water water, int x, int z, double goalX, double goalZ, double radius) {
		if (Math.hypot(x + 0.5 - goalX, z + 0.5 - goalZ) <= radius + 2)
			return false;
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++)
				if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && !water.open(x + dx, z + dz))
					return true;
		return false;
	}

	/**
	 * Whether the barge can be in this column: open water with a block of it all round (the hull is 3 blocks wide),
	 * or just open water close to where it's landing.
	 */
	static boolean floats(Water water, int x, int z, double goalX, double goalZ, double radius) {
		if (!water.open(x, z))
			return false;
		if (Math.hypot(x + 0.5 - goalX, z + 0.5 - goalZ) <= radius + 2)
			return true;
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				if (!water.open(x + dx, z + dz))
					return false;
		return true;
	}

	private static double toGo(int x, int z, double goalX, double goalZ) {
		double dx = Math.abs(x + 0.5 - goalX), dz = Math.abs(z + 0.5 - goalZ);
		return Math.max(dx, dz) + (DIAGONAL - 1) * Math.min(dx, dz);
	}

	private static final class Node {
		final int x, z;
		double g, h, f;
		Node parent;
		boolean closed;

		Node(int x, int z) {
			this.x = x;
			this.z = z;
		}
	}
}
