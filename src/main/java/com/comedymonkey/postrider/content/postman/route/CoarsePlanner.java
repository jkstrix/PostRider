package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.Crossing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * The long way: a plan for the whole trip over a grid of 8x8-block cells, worked out a few thousand cells at a time
 * (A*). He plans twice, once with the barge allowed on water and once over land only, and rides round unless that's
 * more than twice as long as rowing (or there's no way round at all). With the barge, he rows as little as he can:
 * across to the nearest land that takes him on, rather than along the water. The plan comes out as legs, on land
 * and on the water, each a short list of points to head for.
 */
public final class CoarsePlanner {
	/** The side of a cell, in blocks. */
	public static final int CELL = 8;
	/** Rowing costs this much per block, on top of what launching and landing cost. */
	public static final double WATER_COST = 1.1;
	/**
	 * How much dearer rowing is made while choosing the way with the barge, so he lands as soon as the land will take
	 * him on (whether the barge is worth it at all is judged on {@link #WATER_COST}).
	 */
	public static final double ROWING_RELUCTANCE = 4.0;
	public static final double LAUNCH_COST = 24;
	public static final double LANDING_COST = 16;
	/**
	 * More than this many blocks up or down from one cell to the next (straight or corner to corner) is a cliff, or a
	 * chasm's wall, not a slope anyone rides or walks: not crossed at all. Anything less is left to the local plan,
	 * which sees what the cells can't: a bridge a block wide over a chasm, a ramp cut into a cliff.
	 */
	public static final int MAX_RISE = 12;
	/**
	 * More than this much height inside one cell (its highest sampled column over its lowest) is a cliff face, not
	 * ground to ride over: never crossed, unless it's the cell the mailbox stands in (or the one he stands in).
	 */
	public static final int CLIFF_WITHIN = 8;
	/** Half-water ground (a marsh, a river bank) is slow going. */
	public static final double SHORE_COST = 1.25;
	public static final int MAX_NODES = 60_000;
	/** While he waits for terrain, the planner asks after this many more places on its list so they come together. */
	private static final int PREFETCH = 32;
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final double DIAGONAL = Math.sqrt(2);

	/** What a cell is like, from four columns sampled in it. */
	public record Cell(int height, int liquid, int rough, boolean isVoid) {
		/** Mostly water (or lava): crossed only by barge. */
		public boolean water() {
			return !isVoid && liquid >= 3;
		}

		public boolean shore() {
			return !isVoid && liquid == 2;
		}

		/**
		 * A cell from its sampled columns: each one's floor (the first height above solid ground) and top (the first
		 * height above any water on it). A column whose floor is at the bottom of the world has no ground at all.
		 */
		public static Cell of(int[] floors, int[] tops, int minY) {
			int liquid = 0, voids = 0, land = 0;
			int floorSum = 0, topSum = 0, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
			for (int i = 0; i < floors.length; i++) {
				if (floors[i] <= minY) {
					voids++;
					continue;
				}
				land++;
				floorSum += floors[i];
				low = Math.min(low, floors[i]);
				high = Math.max(high, floors[i]);
				if (tops[i] > floors[i]) {
					liquid++;
					topSum += tops[i];
				}
			}
			if (land == 0 || voids >= 3)
				return new Cell(minY, 0, 0, true);
			int height = liquid >= 3 ? topSum / liquid : floorSum / land;
			return new Cell(height, liquid, high - low, false);
		}
	}

	/** Where the planner gets cells from. Null means the cell isn't known yet: it's being looked up, try again later. */
	@FunctionalInterface
	public interface Cells {
		@Nullable
		Cell cell(int cx, int cz);
	}

	public enum Status {
		RUNNING,
		/** Waiting for terrain to be surveyed. */
		WAITING,
		DONE,
		FAILED
	}

	public enum LegKind {
		LAND,
		WATER
	}

	public record Leg(LegKind kind, List<Vec3> points) {
		public Vec3 last() {
			return points.get(points.size() - 1);
		}
	}

	public record Plan(List<Leg> legs, double cost) {
		public boolean usesBarge() {
			return legs.stream().anyMatch(leg -> leg.kind() == LegKind.WATER);
		}
	}

	private final Cells cells;
	private final int startX, startZ, goalX, goalZ;
	private final Set<Long> blocked;
	/** He starts on the barge: the water he's on costs nothing to launch onto. */
	private final boolean afloat;
	private Search search;
	@Nullable
	private Plan bargePlan;
	@Nullable
	private Plan plan;
	private Status status = Status.RUNNING;
	/** How many cells the searches have settled so far (a measure of the work a plan took). */
	private int expanded;

	public int expanded() {
		return expanded;
	}

	/** A plan between two cells, never through any of the blocked ones (cell keys, as {@link #key}). */
	public CoarsePlanner(Cells cells, int startX, int startZ, int goalX, int goalZ, Set<Long> blocked) {
		this(cells, startX, startZ, goalX, goalZ, blocked, false);
	}

	/** As above, starting {@code afloat} on the barge: the way to the nearest land that takes him on to the goal. */
	public CoarsePlanner(Cells cells, int startX, int startZ, int goalX, int goalZ, Set<Long> blocked, boolean afloat) {
		this.cells = cells;
		this.afloat = afloat;
		this.startX = startX;
		this.startZ = startZ;
		this.goalX = goalX;
		this.goalZ = goalZ;
		this.blocked = blocked;
		search = new Search(true, -1);
	}

	public static int cellOf(double blockCoordinate) {
		return Math.floorDiv((int) Math.floor(blockCoordinate), CELL);
	}

	public static long key(int cx, int cz) {
		return ChunkPos.asLong(cx, cz);
	}

	public Status status() {
		return status;
	}

	@Nullable
	public Plan plan() {
		return plan;
	}

	/**
	 * The best to go on while planning goes on: the finished plan, else the plan with the barge if that's done, else
	 * the way to wherever the search has got closest to the goal. Null if there's nothing useful yet.
	 */
	@Nullable
	public Plan bestSoFar() {
		if (plan != null)
			return plan;
		if (bargePlan != null)
			return bargePlan;
		Node best = search.best;
		return best == null || best == search.start ? null : planFrom(best);
	}

	/** Works on the plan for up to {@code budget} more cells; returns how it stands. */
	public Status step(int budget) {
		if (status == Status.DONE || status == Status.FAILED)
			return status;
		Status result = search.step(budget);
		if (result == Status.DONE) {
			Plan found = planFrom(search.reached);
			if (search.barge && found.usesBarge()) {
				// Rowing works; now, is there a way round that's no more than twice as long?
				bargePlan = found;
				search = new Search(false, Crossing.DETOUR_FACTOR * found.cost());
				status = Status.RUNNING;
			} else {
				plan = found;
				status = Status.DONE;
			}
		} else if (result == Status.FAILED) {
			plan = bargePlan;
			status = plan != null ? Status.DONE : Status.FAILED;
		} else {
			status = result;
		}
		return status;
	}

	/** Runs the plan as far as it can go now: to the end, or until it has to wait for terrain. */
	public Status finishNow() {
		step(Integer.MAX_VALUE);
		while (status == Status.RUNNING)
			step(Integer.MAX_VALUE);
		return status;
	}

	// ---------------------------------------------------------------- what moving costs

	/**
	 * What it costs to go from one cell to a neighbour {@code length} blocks away, rowing at {@code rowing} a block;
	 * negative if he can't.
	 */
	private double cost(Cell from, Cell to, double length, boolean barge, double rowing, boolean toGoal, boolean fromStart) {
		if (to.isVoid())
			return -1;
		// Standing at the start on a wet cell is still standing on land, unless he's afloat there; and afloat, the
		// start is water whatever its columns say (an inlet in sea ice reads as land, and he's in the barge in it)
		boolean fromWater = fromStart ? afloat : from.water(), toWater = to.water() && !toGoal;
		if (toWater) {
			if (!barge)
				return -1;
			return length * rowing + (fromWater ? 0 : LAUNCH_COST);
		}
		int rise = Math.abs(to.height() - from.height());
		// A cliff isn't ridden up, and nor is it landed on from the water: a shore that leads nowhere
		if (rise > MAX_RISE || (!toGoal && to.rough() > CLIFF_WITHIN))
			return -1;
		if (fromWater)
			return length * rowing + LANDING_COST;
		double slope = rise / length;
		double steep = slope <= 0.25 ? slope : 0.25 + (slope - 0.25) * 6;
		double rough = Math.min(to.rough(), 16) / 32.0;
		return length * (1 + steep + rough) * (to.shore() ? SHORE_COST : 1);
	}

	private double toGo(int cx, int cz) {
		int dx = Math.abs(cx - goalX), dz = Math.abs(cz - goalZ);
		return CELL * (Math.max(dx, dz) + (DIAGONAL - 1) * Math.min(dx, dz));
	}

	// ---------------------------------------------------------------- turning cells into legs

	private Plan planFrom(Node last) {
		List<Node> path = new ArrayList<>();
		for (Node node = last; node != null; node = node.parent)
			path.add(node);
		Collections.reverse(path);
		List<Leg> legs = new ArrayList<>();
		List<Node> run = new ArrayList<>();
		LegKind kind = null;
		for (int i = 0; i < path.size(); i++) {
			Node node = path.get(i);
			boolean ends = i == 0 && !afloat || i == path.size() - 1;
			LegKind here = (node.cell.water() || i == 0 && afloat) && !ends ? LegKind.WATER : LegKind.LAND;
			if (kind != null && here != kind) {
				legs.add(new Leg(kind, pull(run, kind)));
				run = new ArrayList<>();
			}
			kind = here;
			run.add(node);
		}
		if (!run.isEmpty())
			legs.add(new Leg(kind, pull(run, kind)));
		return new Plan(legs, effort(path));
	}

	/** What the way really costs, with rowing at its true price (the search made it dearer so he'd row less). */
	private double effort(List<Node> path) {
		double total = 0;
		for (int i = 1; i < path.size(); i++) {
			Node a = path.get(i - 1), b = path.get(i);
			boolean diagonal = a.x != b.x && a.z != b.z;
			total += cost(a.cell, b.cell, diagonal ? CELL * DIAGONAL : CELL, true, WATER_COST, i == path.size() - 1, i == 1);
		}
		return total;
	}

	/** The points of a leg with the corners smoothed off: straight on wherever the way between is the same going. */
	private List<Vec3> pull(List<Node> run, LegKind kind) {
		List<Vec3> points = new ArrayList<>();
		int from = 0;
		points.add(centre(run.get(0)));
		while (from < run.size() - 1) {
			int to = from + 1;
			for (int ahead = run.size() - 1; ahead > from + 1; ahead--)
				if (straightOn(run.get(from), run.get(ahead), kind)) {
					to = ahead;
					break;
				}
			points.add(centre(run.get(to)));
			from = to;
		}
		return points;
	}

	/** Whether every cell on the straight line between two is open going of this kind, without big steps. */
	private boolean straightOn(Node a, Node b, LegKind kind) {
		double ax = a.x + 0.5, az = a.z + 0.5, bx = b.x + 0.5, bz = b.z + 0.5;
		int samples = (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 4);
		Cell previous = a.cell;
		for (int i = 1; i < samples; i++) {
			double t = (double) i / samples;
			int cx = (int) Math.floor(ax + (bx - ax) * t), cz = (int) Math.floor(az + (bz - az) * t);
			Cell cell = cells.cell(cx, cz);
			if (cell == null || cell.isVoid() || blocked.contains(key(cx, cz)) || cell.water() != (kind == LegKind.WATER))
				return false;
			if (kind == LegKind.LAND && Math.abs(cell.height() - previous.height()) > CELL / 2)
				return false;
			previous = cell;
		}
		return true;
	}

	private static Vec3 centre(Node node) {
		return new Vec3(node.x * CELL + CELL / 2.0, node.cell.height(), node.z * CELL + CELL / 2.0);
	}

	// ---------------------------------------------------------------- the search

	private final class Search {
		final boolean barge;
		/** Give up once nothing left can come in under this cost (negative: no limit). */
		final double cap;
		final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
		final PriorityQueue<Entry> open = new PriorityQueue<>();
		Node start, best, reached;
		long order;

		Search(boolean barge, double cap) {
			this.barge = barge;
			this.cap = cap;
		}

		Status step(int budget) {
			if (start == null) {
				Cell cell = cells.cell(startX, startZ);
				if (cell == null)
					return Status.WAITING;
				start = new Node(startX, startZ, cell);
				start.h = toGo(startX, startZ);
				nodes.put(key(startX, startZ), start);
				best = start;
				push(start);
			}
			while (budget-- > 0) {
				Entry entry = open.poll();
				if (entry == null)
					return Status.FAILED;
				Node node = entry.node;
				if (node.closed || entry.g != node.g)
					continue;
				if (cap >= 0 && node.g + node.h > cap)
					return Status.FAILED;
				if (node.x == goalX && node.z == goalZ) {
					reached = node;
					return Status.DONE;
				}
				if (!neighboursKnown(node)) {
					open.add(entry);
					askAhead();
					return Status.WAITING;
				}
				node.closed = true;
				expanded++;
				if (node.h < best.h)
					best = node;
				expand(node);
				if (nodes.size() > MAX_NODES)
					return Status.FAILED;
			}
			return Status.RUNNING;
		}

		boolean neighboursKnown(Node node) {
			boolean known = true;
			for (int[] d : DIRECTIONS)
				if (cells.cell(node.x + d[0], node.z + d[1]) == null)
					known = false;
			return known;
		}

		/** Asks after the cells round the next few places on the list, so they're surveyed together. */
		void askAhead() {
			Iterator<Entry> entries = open.iterator();
			for (int i = 0; i < PREFETCH && entries.hasNext(); i++) {
				Node node = entries.next().node;
				for (int[] d : DIRECTIONS)
					cells.cell(node.x + d[0], node.z + d[1]);
			}
		}

		void expand(Node from) {
			for (int[] d : DIRECTIONS) {
				int x = from.x + d[0], z = from.z + d[1];
				if (isBlocked(x, z))
					continue;
				boolean diagonal = d[0] != 0 && d[1] != 0;
				// No squeezing between two cells he couldn't cross, nor past a cliff on the corner (he passes over it)
				if (diagonal && (impassable(from.x + d[0], from.z) || impassable(from.x, from.z + d[1])
						|| cliffBeside(from.cell, from.x + d[0], from.z) || cliffBeside(from.cell, from.x, from.z + d[1])))
					continue;
				Cell cell = cells.cell(x, z);
				double step = cost(from.cell, cell, diagonal ? CELL * DIAGONAL : CELL, barge, ROWING_RELUCTANCE, x == goalX && z == goalZ, from == start);
				if (step < 0)
					continue;
				double g = from.g + step;
				long key = key(x, z);
				Node node = nodes.get(key);
				if (node == null) {
					node = new Node(x, z, cell);
					node.h = toGo(x, z);
					nodes.put(key, node);
				} else if (node.closed || g >= node.g) {
					continue;
				}
				node.g = g;
				node.parent = from;
				push(node);
			}
		}

		boolean impassable(int x, int z) {
			Cell cell = cells.cell(x, z);
			return cell == null || cell.isVoid() || isBlocked(x, z) || !barge && cell.water();
		}

		/** Whether the cell on the corner of a diagonal move is a cliff from where he is: too big a step, or a cliff face inside it. */
		boolean cliffBeside(Cell from, int x, int z) {
			Cell corner = cells.cell(x, z);
			return corner != null && !corner.isVoid() && !corner.water()
				&& (Math.abs(corner.height() - from.height()) > MAX_RISE || corner.rough() > CLIFF_WITHIN);
		}

		/**
		 * Whether a cell was found impassable this trip; never the goal, or the plan could never end, and on land
		 * never one next to the start, or it could never begin (he's there: what's struck off is what lies beyond).
		 * Afloat, only the start itself is spared: the shore struck off round the barge is the shore he can't land
		 * on, and the way begins over the water.
		 */
		boolean isBlocked(int x, int z) {
			if (x == goalX && z == goalZ)
				return false;
			boolean nearStart = afloat ? x == startX && z == startZ : Math.abs(x - startX) <= 1 && Math.abs(z - startZ) <= 1;
			return !nearStart && blocked.contains(key(x, z));
		}

		void push(Node node) {
			open.add(new Entry(node, node.g, node.g + node.h * (barge ? 1.1 : 1.0), order++));
		}
	}

	private static final class Node {
		final int x, z;
		final Cell cell;
		double g, h;
		Node parent;
		boolean closed;

		Node(int x, int z, Cell cell) {
			this.x = x;
			this.z = z;
			this.cell = cell;
		}
	}

	private record Entry(Node node, double g, double f, long order) implements Comparable<Entry> {
		@Override
		public int compareTo(Entry other) {
			int c = Double.compare(f, other.f);
			return c != 0 ? c : Long.compare(order, other.order);
		}
	}
}
