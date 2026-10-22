package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import com.comedymonkey.postrider.content.postman.GapJump;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * The way through the blocks round a rider, worked out a few thousand steps at a time (A*, slightly greedy). He can
 * be on horseback or on foot, and the plan can have him get down where only someone on foot can go (a ladder, a
 * narrow or low gap) and whistle the horse back once there's room. Water and hazards are never entered, except
 * that on foot he rides bubble columns: up one over soul sand, down a whirlpool over magma (out at the ledge
 * beside its foot).
 * <p>
 * On horseback a step is the corner of the 2x2 blocks the horse stands over (as vanilla paths have it for anything
 * wider than a block), with the horse's middle at the shared corner; on foot it's the block he stands in.
 */
public final class LocalPlanner {
	public enum Status {
		RUNNING,
		/** All the way to the goal. */
		FOUND,
		/** Not all the way, but as close as he can get, and usefully closer than he is. */
		PARTIAL,
		FAILED
	}

	public enum Move {
		START,
		WALK,
		/** A horse's leap over a gap. */
		LEAP,
		/** Up or down a ladder or vines, on foot. */
		CLIMB,
		/** Into and up a bubble column over soul sand, on foot (he's in the water at this step). */
		RISE,
		/** Into and down a whirlpool over magma, on foot (he's in the water at this step). */
		SINK,
		/** He gets down and the horse goes. */
		DISMOUNT,
		/** He whistles the horse back and climbs on. */
		MOUNT
	}

	/** One place on the way, how he's travelling there, and how he got there from the step before. */
	public record Step(int x, int y, int z, boolean mounted, Move move) {
		/** Where the middle of him (or his horse) is at this step. */
		public double centreX() {
			return x + (mounted ? 1.0 : 0.5);
		}

		public double centreZ() {
			return z + (mounted ? 1.0 : 0.5);
		}
	}

	/** Anywhere within {@code radius} blocks of (x, z); and, unless {@code yTolerance} is negative, within that of y. */
	public record Goal(double x, int y, double z, double radius, int yTolerance) {
		public static Goal anyHeight(double x, double z, double radius) {
			return new Goal(x, 0, z, radius, -1);
		}
	}

	/** How much dearer every step is on foot: he runs, but his horse gallops. */
	public static final double FOOT_COST = 1.8;
	public static final double STEP_UP_COST = 0.5;
	public static final double DROP_COST = 0.25;
	public static final double LEAP_COST = 2.0;
	public static final double CLIMB_COST = 1.5;
	/** A block up or down a bubble column: quicker than a ladder, as the water does the work. */
	public static final double COLUMN_COST = 1.0;
	public static final double DISMOUNT_COST = 12.0;
	public static final double MOUNT_COST = 16.0;
	/** Under a roof (a cave, a tunnel) every step costs this much more. */
	public static final double COVERED_COST = 1.15;
	/** How greedy the search is: a little, for speed, at a small cost in how short the way is. */
	public static final double WEIGHT = 1.2;
	public static final int MAX_NODES = 40_000;
	public static final int MOUNTED_DROP = 5;
	public static final int FOOT_DROP = 3;
	/** A plan that doesn't reach the goal is only worth having if it gets him at least this much closer. */
	public static final double MIN_PARTIAL_GAIN = 4.0;
	public static final int MOUNTED_HEIGHT = 3;
	public static final int FOOT_HEIGHT = 2;

	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final double DIAGONAL = Math.sqrt(2);
	private static final int SPAN = 8192;

	private final Terrain terrain;
	private final Goal goal;
	private final boolean mayMount;
	private final int originX, originZ;
	private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
	private final PriorityQueue<Entry> open = new PriorityQueue<>();
	private final Node start;
	private Node best;
	private Node reached;
	private Status status = Status.RUNNING;
	private long order;
	private int expanded;

	public LocalPlanner(Terrain terrain, int x, int y, int z, boolean mounted, Goal goal, boolean mayMount) {
		this.terrain = terrain;
		this.goal = goal;
		this.mayMount = mayMount;
		this.originX = x;
		this.originZ = z;
		start = new Node(x, y, z, mounted);
		start.move = Move.START;
		start.h = toGo(start);
		nodes.put(key(x, y, z, mounted), start);
		best = start;
		push(start);
	}

	public Status status() {
		return status;
	}

	public int expanded() {
		return expanded;
	}

	/** Works on the plan for up to {@code budget} more places; returns how it stands. */
	public Status step(int budget) {
		while (status == Status.RUNNING && budget-- > 0) {
			Entry entry = open.poll();
			if (entry == null) {
				finish();
				break;
			}
			Node node = entry.node;
			if (node.closed || entry.g != node.g)
				continue;
			node.closed = true;
			expanded++;
			// A way never ends in a bubble column: he'd be left in the water
			boolean afloat = inColumn(node);
			if (!afloat && reaches(node)) {
				reached = node;
				status = Status.FOUND;
				break;
			}
			if (!afloat && (node.h < best.h || node.h == best.h && node.g < best.g))
				best = node;
			expand(node);
			if (nodes.size() > MAX_NODES)
				finish();
		}
		return status;
	}

	/** Runs the plan to the end, however long it takes (for tests, and for very short plans). */
	public Status finishNow() {
		while (status == Status.RUNNING)
			step(Integer.MAX_VALUE);
		return status;
	}

	/** The way from where he is (the first step) to the goal or as near as he can get; empty if there's none. */
	public List<Step> steps() {
		Node last = status == Status.FOUND ? reached : status == Status.PARTIAL ? best : null;
		if (last == null)
			return List.of();
		List<Step> steps = new ArrayList<>();
		for (Node node = last; node != null; node = node.parent)
			steps.add(new Step(node.x, node.y, node.z, node.mounted, node.move));
		Collections.reverse(steps);
		return steps;
	}

	private void finish() {
		status = best != start && best.h <= start.h - MIN_PARTIAL_GAIN ? Status.PARTIAL : Status.FAILED;
	}

	// ---------------------------------------------------------------- where he can be

	/** Whether he (or his horse, on horseback) can stand here, clear of everything, on something solid. */
	public static boolean stands(Terrain terrain, boolean mounted, int x, int y, int z) {
		if (!mounted) {
			Terrain.Kind feet = terrain.kind(x, y, z);
			if (!feet.feetFit() || !terrain.kind(x, y + 1, z).isClear())
				return false;
			Terrain.Kind below = terrain.kind(x, y - 1, z);
			return below == Terrain.Kind.SOLID || below == Terrain.Kind.CLIMB || feet == Terrain.Kind.LOW || feet == Terrain.Kind.CLIMB;
		}
		int support = 0;
		for (int dx = 0; dx <= 1; dx++)
			for (int dz = 0; dz <= 1; dz++) {
				Terrain.Kind feet = terrain.kind(x + dx, y, z + dz);
				if (!feet.feetFit() || !terrain.kind(x + dx, y + 1, z + dz).isClear() || !terrain.kind(x + dx, y + 2, z + dz).isClear())
					return false;
				Terrain.Kind below = terrain.kind(x + dx, y - 1, z + dz);
				if (below == Terrain.Kind.DANGER)
					return false;
				if (below == Terrain.Kind.SOLID || feet == Terrain.Kind.LOW)
					support++;
			}
		return support >= 2;
	}

	/** Whether he (or his horse) would fit here, standing or passing through in the air. */
	static boolean fits(Terrain terrain, boolean mounted, int x, int y, int z) {
		if (!mounted)
			return terrain.kind(x, y, z).feetFit() && terrain.kind(x, y + 1, z).isClear();
		for (int dx = 0; dx <= 1; dx++)
			for (int dz = 0; dz <= 1; dz++)
				if (!terrain.kind(x + dx, y, z + dz).feetFit() || !terrain.kind(x + dx, y + 1, z + dz).isClear()
						|| !terrain.kind(x + dx, y + 2, z + dz).isClear())
					return false;
		return true;
	}

	/** Whether there's room overhead to step up a block from here. */
	private boolean headroom(boolean mounted, int x, int y, int z) {
		int above = y + (mounted ? MOUNTED_HEIGHT : FOOT_HEIGHT);
		if (!mounted)
			return terrain.kind(x, above, z).isClear();
		for (int dx = 0; dx <= 1; dx++)
			for (int dz = 0; dz <= 1; dz++)
				if (!terrain.kind(x + dx, above, z + dz).isClear())
					return false;
		return true;
	}

	// ---------------------------------------------------------------- the ways on from a place

	/** Whether, at this step, he's in the water of a bubble column. */
	private boolean inColumn(Node node) {
		if (node.mounted)
			return false;
		Terrain.Kind in = terrain.kind(node.x, node.y, node.z);
		return in == Terrain.Kind.BUBBLE_UP || in == Terrain.Kind.BUBBLE_DOWN;
	}

	private void expand(Node node) {
		if (inColumn(node)) {
			rideColumn(node, terrain.kind(node.x, node.y, node.z));
			return;
		}
		walks(node);
		if (node.mounted) {
			leaps(node);
			dismount(node);
		} else {
			climbs(node);
			columns(node);
			if (mayMount)
				mount(node);
		}
	}

	private void walks(Node from) {
		boolean m = from.mounted;
		for (int[] d : DIRECTIONS) {
			boolean diagonal = d[0] != 0 && d[1] != 0;
			// No cutting corners: both sides of a diagonal step must be clear
			if (diagonal && (!fits(terrain, m, from.x + d[0], from.y, from.z) || !fits(terrain, m, from.x, from.y, from.z + d[1])))
				continue;
			int x = from.x + d[0], z = from.z + d[1];
			double length = diagonal ? DIAGONAL : 1.0;
			if (stands(terrain, m, x, from.y, z)) {
				add(from, x, from.y, z, m, Move.WALK, length);
				continue;
			}
			if (!diagonal && stands(terrain, m, x, from.y + 1, z) && headroom(m, from.x, from.y, from.z)) {
				add(from, x, from.y + 1, z, m, Move.WALK, length + STEP_UP_COST);
				continue;
			}
			if (!fits(terrain, m, x, from.y, z))
				continue;
			int maxDrop = m ? MOUNTED_DROP : FOOT_DROP;
			for (int drop = 1; drop <= maxDrop; drop++) {
				int y = from.y - drop;
				if (stands(terrain, m, x, y, z)) {
					add(from, x, y, z, m, Move.WALK, length + DROP_COST * drop);
					break;
				}
				if (!fits(terrain, m, x, y, z))
					break;
			}
		}
	}

	/** A galloping leap over a gap: no ground near his level on the way, safe ground at about his level beyond. */
	private void leaps(Node from) {
		for (int i = 0; i < 4; i++) {
			int dx = DIRECTIONS[i][0], dz = DIRECTIONS[i][1];
			for (int across = 1; across <= GapJump.MAX_GAP + 1; across++) {
				int x = from.x + dx * across, z = from.z + dz * across;
				if (across >= 2) {
					int landing = Integer.MIN_VALUE;
					if (stands(terrain, true, x, from.y, z))
						landing = from.y;
					else if (stands(terrain, true, x, from.y - 1, z))
						landing = from.y - 1;
					else if (stands(terrain, true, x, from.y + 1, z) && headroom(true, x, from.y, z))
						landing = from.y + 1;
					if (landing != Integer.MIN_VALUE) {
						add(from, x, landing, z, true, Move.LEAP, across + LEAP_COST);
						break;
					}
				}
				// Over the gap: nothing to stand on near his level (or he'd just walk), and nothing to hit
				boolean gap = !stands(terrain, true, x, from.y, z) && !stands(terrain, true, x, from.y - 1, z)
					&& fits(terrain, true, x, from.y, z);
				if (!gap)
					break;
			}
		}
	}

	private void climbs(Node from) {
		if (terrain.kind(from.x, from.y + 1, from.z) == Terrain.Kind.CLIMB && terrain.kind(from.x, from.y + 2, from.z).isClear()
				&& terrain.kind(from.x, from.y, from.z).isClear())
			add(from, from.x, from.y + 1, from.z, false, Move.CLIMB, CLIMB_COST);
		if (terrain.kind(from.x, from.y - 1, from.z) == Terrain.Kind.CLIMB)
			add(from, from.x, from.y - 1, from.z, false, Move.CLIMB, CLIMB_COST);
	}

	/**
	 * Bubble columns beside him, on foot (his horse can't be in one): into one rising from soul sand at any height
	 * of it, or into a whirlpool at the top of it, stepping down into the water from a ledge level with its top.
	 */
	private void columns(Node from) {
		for (int i = 0; i < 4; i++) {
			int x = from.x + DIRECTIONS[i][0], z = from.z + DIRECTIONS[i][1];
			Terrain.Kind beside = terrain.kind(x, from.y, z), above = terrain.kind(x, from.y + 1, z);
			if (beside == Terrain.Kind.BUBBLE_UP && (above == Terrain.Kind.BUBBLE_UP || above.isClear()))
				add(from, x, from.y, z, false, Move.RISE, COLUMN_COST);
			else if (beside == Terrain.Kind.BUBBLE_DOWN)
				add(from, x, from.y, z, false, Move.SINK, COLUMN_COST);
			else if (beside.isClear() && terrain.kind(x, from.y - 1, z) == Terrain.Kind.BUBBLE_DOWN)
				add(from, x, from.y - 1, z, false, Move.SINK, COLUMN_COST + DROP_COST);
		}
	}

	/**
	 * In a bubble column: carried on up (or down) it a block at a time, and out onto a ledge beside the end of it.
	 * A rising column throws him up out of the water, so there he can also step up onto a ledge a block higher; at
	 * the foot of a whirlpool he steps straight off the magma onto the ledge beside it, never up.
	 */
	private void rideColumn(Node from, Terrain.Kind column) {
		int dy = column == Terrain.Kind.BUBBLE_UP ? 1 : -1;
		if (terrain.kind(from.x, from.y + dy, from.z) == column) {
			add(from, from.x, from.y + dy, from.z, false, dy > 0 ? Move.RISE : Move.SINK, COLUMN_COST);
			return;
		}
		boolean thrownOut = column == Terrain.Kind.BUBBLE_UP && terrain.kind(from.x, from.y + 1, from.z).isClear()
			&& terrain.kind(from.x, from.y + 2, from.z).isClear();
		for (int i = 0; i < 4; i++) {
			int x = from.x + DIRECTIONS[i][0], z = from.z + DIRECTIONS[i][1];
			if (stands(terrain, false, x, from.y, z))
				add(from, x, from.y, z, false, Move.WALK, 1.0);
			else if (thrownOut && stands(terrain, false, x, from.y + 1, z))
				add(from, x, from.y + 1, z, false, Move.WALK, 1.0 + STEP_UP_COST);
		}
	}

	private void dismount(Node from) {
		for (int dx = 0; dx <= 1; dx++)
			for (int dz = 0; dz <= 1; dz++)
				if (stands(terrain, false, from.x + dx, from.y, from.z + dz)) {
					add(from, from.x + dx, from.y, from.z + dz, false, Move.DISMOUNT, 0);
					return;
				}
	}

	private void mount(Node from) {
		for (int dx = -1; dx <= 0; dx++)
			for (int dz = -1; dz <= 0; dz++)
				if (stands(terrain, true, from.x + dx, from.y, from.z + dz)) {
					add(from, from.x + dx, from.y, from.z + dz, true, Move.MOUNT, 0);
					return;
				}
	}

	/** Considers getting to a place this way: {@code length} is in blocks, before what the move and mode add. */
	private void add(Node from, int x, int y, int z, boolean mounted, Move move, double length) {
		if (Math.abs(x - originX) >= SPAN || Math.abs(z - originZ) >= SPAN)
			return;
		double cost;
		if (move == Move.DISMOUNT)
			cost = DISMOUNT_COST;
		else if (move == Move.MOUNT)
			cost = MOUNT_COST;
		else {
			cost = length * (mounted ? 1.0 : FOOT_COST);
			if (terrain.covered(x, y, z))
				cost *= COVERED_COST;
		}
		double g = from.g + cost;
		long key = key(x, y, z, mounted);
		Node node = nodes.get(key);
		if (node == null) {
			node = new Node(x, y, z, mounted);
			node.h = toGo(node);
			nodes.put(key, node);
		} else if (node.closed || g >= node.g) {
			return;
		}
		node.g = g;
		node.parent = from;
		node.move = move;
		push(node);
	}

	private void push(Node node) {
		open.add(new Entry(node, node.g, node.g + WEIGHT * node.h, node.h, order++));
	}

	private double toGo(Node node) {
		double dx = (node.x + (node.mounted ? 1.0 : 0.5)) - goal.x(), dz = (node.z + (node.mounted ? 1.0 : 0.5)) - goal.z();
		return Math.max(0, Math.sqrt(dx * dx + dz * dz) - goal.radius());
	}

	private boolean reaches(Node node) {
		return node.h <= 0 && (goal.yTolerance() < 0 || Math.abs(node.y - goal.y()) <= goal.yTolerance());
	}

	private long key(int x, int y, int z, boolean mounted) {
		long dx = x - originX + SPAN, dz = z - originZ + SPAN, ly = (y + 2048) & 0xFFF;
		return dx << 27 | dz << 13 | ly << 1 | (mounted ? 1 : 0);
	}

	private static final class Node {
		final int x, y, z;
		final boolean mounted;
		double g, h;
		Node parent;
		Move move;
		boolean closed;

		Node(int x, int y, int z, boolean mounted) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.mounted = mounted;
		}
	}

	private record Entry(Node node, double g, double f, double h, long order) implements Comparable<Entry> {
		@Override
		public int compareTo(Entry other) {
			int c = Double.compare(f, other.f);
			if (c == 0)
				c = Double.compare(h, other.h);
			return c != 0 ? c : Long.compare(order, other.order);
		}
	}
}
