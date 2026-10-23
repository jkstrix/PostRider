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
 * <p>
 * A goal at a fixed height (a mailbox) is searched for backwards first, from the goal towards him, with every move
 * turned round: a mailbox in a room is found by the room's few ways out, where a search from him towards it would
 * spend its budget on the open ground outside the room's walls, which looks nearly as close. If that doesn't reach
 * him, the search from him runs as usual.
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
	public static final double FOOT_COST = 2.0;
	public static final double STEP_UP_COST = 0.5;
	public static final double DROP_COST = 0.25;
	public static final double LEAP_COST = 2.0;
	public static final double CLIMB_COST = 1.5;
	/** A block up or down a bubble column: quicker than a ladder, as the water does the work. */
	public static final double COLUMN_COST = 1.0;
	public static final double DISMOUNT_COST = 12.0;
	public static final double MOUNT_COST = 10.0;
	/** Opening a door and going through it, on foot: a moment at the handle. */
	public static final double DOOR_COST = 2.0;
	/** Under a roof (a cave, a tunnel) every step costs this much more. */
	public static final double COVERED_COST = 1.15;
	/** How greedy the search is: a little, for speed, at a small cost in how short the way is. */
	public static final double WEIGHT = 1.2;
	public static final int MAX_NODES = 40_000;
	/** Places the search backwards from a fixed goal settles before the search from him takes over. */
	public static final int BACKWARD_NODES = 16_000;
	/** Only a goal this near (a mailbox, not a waypoint) is worth searching backwards for. */
	public static final double BACKWARD_RADIUS = 8;
	/** The furthest down a step may go, in the saddle or on foot: any further and the fall hurts them both. */
	public static final int MOUNTED_DROP = 3;
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
	// The search backwards from the goal, while it runs: its own places, and the one that reached where he stands
	private boolean backward;
	private final Long2ObjectOpenHashMap<Node> back = new Long2ObjectOpenHashMap<>();
	private final PriorityQueue<Entry> openBack = new PriorityQueue<>();
	private Node reachedBack;
	private int expandedBack, seeds;
	private Node nearestBack;

	/** What the search did, for the log when it finds nothing. */
	public String report() {
		String backwards = seeds == 0 ? "no search back from the goal (nowhere to stand in it)"
			: "back from the goal: " + seeds + " places to start, " + expandedBack + " settled"
				+ (nearestBack == null ? "" : ", got within " + Math.round(nearestBack.h) + " of him at " + nearestBack.x + " " + nearestBack.y + " " + nearestBack.z + (nearestBack.mounted ? " mounted" : " on foot"));
		return backwards + "; from him: " + (expanded - expandedBack) + " settled, " + nodes.size() + " seen";
	}

	public LocalPlanner(Terrain terrain, int x, int y, int z, boolean mounted, Goal goal, boolean mayMount) {
		this.terrain = terrain;
		this.goal = goal;
		this.mayMount = mayMount;
		this.originX = x;
		this.originZ = z;
		start = new Node(x, y, z, mounted);
		start.move = Move.START;
		start.far = blocksToGo(start);
		start.h = atLeast(start.far, start.mounted);
		nodes.put(key(x, y, z, mounted), start);
		best = start;
		push(start);
		backward = goal.yTolerance() >= 0 && goal.radius() <= BACKWARD_RADIUS && seedBackward();
	}

	public Status status() {
		return status;
	}

	public int expanded() {
		return expanded;
	}

	/** Works on the plan for up to {@code budget} more places; returns how it stands. */
	public Status step(int budget) {
		if (backward)
			budget = stepBackward(budget);
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
			if (!afloat && (node.far < best.far || node.far == best.far && node.g < best.g))
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
		if (reachedBack != null) {
			// Found from the goal's end: the chain runs from where he stands towards the goal, each place noting the
			// move on to the next
			List<Step> steps = new ArrayList<>();
			steps.add(new Step(reachedBack.x, reachedBack.y, reachedBack.z, reachedBack.mounted, Move.START));
			for (Node node = reachedBack; node.parent != null; node = node.parent)
				steps.add(new Step(node.parent.x, node.parent.y, node.parent.z, node.parent.mounted, node.move));
			return steps;
		}
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
		status = best != start && best.far <= start.far - MIN_PARTIAL_GAIN ? Status.PARTIAL : Status.FAILED;
	}

	// ---------------------------------------------------------------- backwards from the goal

	/** Every place in the goal he could stand, as the beginnings of the search backwards; false if there's none. */
	private boolean seedBackward() {
		int reach = (int) Math.ceil(goal.radius());
		for (int x = (int) Math.floor(goal.x()) - reach - 1; x <= Math.floor(goal.x()) + reach; x++)
			for (int z = (int) Math.floor(goal.z()) - reach - 1; z <= Math.floor(goal.z()) + reach; z++)
				for (int y = goal.y() - goal.yTolerance(); y <= goal.y() + goal.yTolerance(); y++)
					for (boolean mounted : new boolean[] {false, true}) {
						if (mounted && !mayMount && !start.mounted)
							continue;
						Node node = new Node(x, y, z, mounted);
						if (blocksToGo(node) > 0 || !stands(terrain, mounted, x, y, z))
							continue;
						node.h = toStart(node);
						back.put(key(x, y, z, mounted), node);
						openBack.add(new Entry(node, 0, WEIGHT * node.h, node.h, order++));
						seeds++;
					}
		return !openBack.isEmpty();
	}

	/** Works the search backwards for up to {@code budget} places; what's left of the budget when it's done with. */
	private int stepBackward(int budget) {
		while (budget > 0) {
			Entry entry = openBack.poll();
			if (entry == null || expandedBack >= BACKWARD_NODES) {
				backward = false;
				return budget;
			}
			Node node = entry.node;
			if (node.closed || entry.g != node.g)
				continue;
			node.closed = true;
			expanded++;
			expandedBack++;
			budget--;
			if (nearestBack == null || node.h < nearestBack.h)
				nearestBack = node;
			if (node.x == start.x && node.y == start.y && node.z == start.z && node.mounted == start.mounted) {
				reachedBack = node;
				status = Status.FOUND;
				backward = false;
				return 0;
			}
			expandBackward(node);
		}
		return 0;
	}

	private double toStart(Node node) {
		double dx = (node.x + (node.mounted ? 1.0 : 0.5)) - (start.x + (start.mounted ? 1.0 : 0.5));
		double dz = (node.z + (node.mounted ? 1.0 : 0.5)) - (start.z + (start.mounted ? 1.0 : 0.5));
		return atLeast(Math.sqrt(dx * dx + dz * dz), node.mounted);
	}

	/**
	 * The least this many blocks can cost from a place: a block each on horseback; on foot, walking them, or
	 * whistling the horse and riding them if he may.
	 */
	private double atLeast(double blocks, boolean mounted) {
		if (mounted)
			return blocks;
		return mayMount ? Math.min(blocks * FOOT_COST, MOUNT_COST + blocks) : blocks * FOOT_COST;
	}

	/** The places he could have come to {@code to} from, each with the move that brings him on from it. */
	private void expandBackward(Node to) {
		Terrain.Kind at = to.mounted ? Terrain.Kind.OPEN : terrain.kind(to.x, to.y, to.z);
		if (at == Terrain.Kind.BUBBLE_UP || at == Terrain.Kind.BUBBLE_DOWN) {
			// In a column: from the cell before it in the column, or, at its start, from the ledge beside it
			int dy = at == Terrain.Kind.BUBBLE_UP ? 1 : -1;
			if (terrain.kind(to.x, to.y - dy, to.z) == at)
				before(to, to.x, to.y - dy, to.z, false, dy > 0 ? Move.RISE : Move.SINK, COLUMN_COST);
			for (int i = 0; i < 4; i++) {
				int x = to.x + DIRECTIONS[i][0], z = to.z + DIRECTIONS[i][1];
				if (at == Terrain.Kind.BUBBLE_UP && (terrain.kind(to.x, to.y + 1, to.z) == at || terrain.kind(to.x, to.y + 1, to.z).isClear())
						&& stands(terrain, false, x, to.y, z))
					before(to, x, to.y, z, false, Move.RISE, COLUMN_COST);
				if (at == Terrain.Kind.BUBBLE_DOWN) {
					if (stands(terrain, false, x, to.y, z))
						before(to, x, to.y, z, false, Move.SINK, COLUMN_COST);
					if (terrain.kind(to.x, to.y + 1, to.z).isClear() && stands(terrain, false, x, to.y + 1, z))
						before(to, x, to.y + 1, z, false, Move.SINK, COLUMN_COST + DROP_COST);
				}
			}
			return;
		}
		boolean m = to.mounted;
		for (int[] d : DIRECTIONS) {
			boolean diagonal = d[0] != 0 && d[1] != 0;
			int px = to.x - d[0], pz = to.z - d[1];
			double length = diagonal ? DIAGONAL : 1.0;
			// A walk along, or a step up, or a drop down to here
			if (standsSquarely(terrain, m, to.x, to.y, to.z)) {
				if (stands(terrain, m, px, to.y, pz) && cornersClear(m, px, to.y, pz, d))
					before(to, px, to.y, pz, m, Move.WALK, length);
				if (!diagonal && stands(terrain, m, px, to.y - 1, pz) && headroom(m, px, to.y - 1, pz))
					before(to, px, to.y - 1, pz, m, Move.WALK, length + STEP_UP_COST);
			}
			int maxDrop = m ? MOUNTED_DROP : FOOT_DROP;
			int[] outs = m && !diagonal ? new int[] {1, 2} : new int[] {1};
			for (int out : outs) {
				int fx = to.x - d[0] * out, fz = to.z - d[1] * out;
				for (int drop = 1; drop <= maxDrop; drop++) {
					if (!m && diagonal && drop > 1)
						break;
					int fy = to.y + drop;
					if (!stands(terrain, m, fx, fy, fz) || !cornersClear(m, fx, fy, fz, d))
						continue;
					if (!(m ? landsOn(terrain, to.x, to.y, to.z, d[0], d[1]) : stands(terrain, m, to.x, to.y, to.z)))
						continue;
					if (!fits(terrain, m, fx + d[0], fy, fz + d[1]) || out == 2 && !fits(terrain, m, to.x, fy, to.z))
						continue;
					boolean clearDown = true;
					for (int y = fy - 1; y > to.y; y--)
						if (!fits(terrain, m, to.x, y, to.z) || stands(terrain, m, to.x, y, to.z)) {
							clearDown = false;
							break;
						}
					if (clearDown)
						before(to, fx, fy, fz, m, Move.WALK, length * out + DROP_COST * drop);
				}
			}
		}
		if (m) {
			// A leap to here, from across a gap
			for (int i = 0; i < 4; i++) {
				int dx = DIRECTIONS[i][0], dz = DIRECTIONS[i][1];
				for (int across = 2; across <= GapJump.MAX_GAP + 1; across++) {
					int fx = to.x - dx * across, fz = to.z - dz * across;
					for (int fy : new int[] {to.y, to.y + 1, to.y - 1}) {
						if (!landsOn(terrain, to.x, to.y, to.z, dx, dz) || !stands(terrain, true, fx, fy, fz))
							continue;
						if (fy == to.y - 1 && !headroom(true, to.x, fy, to.z))
							continue;
						boolean gap = true;
						for (int k = 1; k < across && gap; k++) {
							int gx = fx + dx * k, gz = fz + dz * k;
							gap = !standsSquarely(terrain, true, gx, fy, gz) && !standsSquarely(terrain, true, gx, fy - 1, gz) && fits(terrain, true, gx, fy, gz);
						}
						if (gap)
							before(to, fx, fy, fz, true, Move.LEAP, across + LEAP_COST);
					}
				}
			}
			// Got on here, from beside the horse
			if (mayMount && standsSquarely(terrain, true, to.x, to.y, to.z))
				for (int dx = 0; dx <= 1; dx++)
					for (int dz = 0; dz <= 1; dz++)
						if (stands(terrain, false, to.x + dx, to.y, to.z + dz))
							before(to, to.x + dx, to.y, to.z + dz, false, Move.MOUNT, 0);
		} else {
			// A ladder, up or down
			if (at == Terrain.Kind.CLIMB && terrain.kind(to.x, to.y + 1, to.z).isClear() && terrain.kind(to.x, to.y - 1, to.z).isClear())
				before(to, to.x, to.y - 1, to.z, false, Move.CLIMB, CLIMB_COST);
			if (at == Terrain.Kind.CLIMB)
				before(to, to.x, to.y + 1, to.z, false, Move.CLIMB, CLIMB_COST);
			// Out of a column beside here, at its end
			for (int i = 0; i < 4; i++) {
				int x = to.x + DIRECTIONS[i][0], z = to.z + DIRECTIONS[i][1];
				for (int fy : new int[] {to.y, to.y - 1}) {
					Terrain.Kind column = terrain.kind(x, fy, z);
					if (column != Terrain.Kind.BUBBLE_UP && column != Terrain.Kind.BUBBLE_DOWN)
						continue;
					int dy = column == Terrain.Kind.BUBBLE_UP ? 1 : -1;
					if (terrain.kind(x, fy + dy, z) == column)
						continue;
					boolean thrownOut = column == Terrain.Kind.BUBBLE_UP && terrain.kind(x, fy + 1, z).isClear() && terrain.kind(x, fy + 2, z).isClear();
					if (fy == to.y && stands(terrain, false, to.x, to.y, to.z))
						before(to, x, fy, z, false, Move.WALK, 1.0);
					else if (fy == to.y - 1 && thrownOut && stands(terrain, false, to.x, to.y, to.z))
						before(to, x, fy, z, false, Move.WALK, 1.0 + STEP_UP_COST);
				}
			}
			// Got down here, off the horse
			if (stands(terrain, false, to.x, to.y, to.z))
				for (int dx = 0; dx <= 1; dx++)
					for (int dz = 0; dz <= 1; dz++)
						if (stands(terrain, true, to.x - dx, to.y, to.z - dz))
							before(to, to.x - dx, to.y, to.z - dz, true, Move.DISMOUNT, 0);
		}
	}

	/** Whether a diagonal step from here in direction d cuts no corner (both sides clear); always, straight on. */
	private boolean cornersClear(boolean mounted, int x, int y, int z, int[] d) {
		if (d[0] == 0 || d[1] == 0)
			return true;
		return fits(terrain, mounted, x + d[0], y, z) && fits(terrain, mounted, x, y, z + d[1]);
	}

	/** Considers that he came to {@code to} from here by this move (costed as the move on to {@code to} is). */
	private void before(Node to, int x, int y, int z, boolean mounted, Move move, double length) {
		if (Math.abs(x - originX) >= SPAN || Math.abs(z - originZ) >= SPAN)
			return;
		double g = to.g + cost(move, length, to.mounted, to.x, to.y, to.z);
		long key = key(x, y, z, mounted);
		Node node = back.get(key);
		if (node == null) {
			node = new Node(x, y, z, mounted);
			node.h = toStart(node);
			back.put(key, node);
		} else if (node.closed || g >= node.g) {
			return;
		}
		node.g = g;
		node.parent = to;
		node.move = move;
		openBack.add(new Entry(node, node.g, node.g + WEIGHT * node.h, node.h, order++));
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
				if (!feet.horseFeetFit() || !terrain.kind(x + dx, y + 1, z + dz).horseClear() || !terrain.kind(x + dx, y + 2, z + dz).horseClear())
					return false;
				Terrain.Kind below = terrain.kind(x + dx, y - 1, z + dz);
				if (below == Terrain.Kind.DANGER)
					return false;
				if (below == Terrain.Kind.SOLID || feet == Terrain.Kind.LOW)
					support++;
			}
		// Half the horse on something is enough to land on (a leap or a drop ends with its front half on the far
		// side, and it stands); walking along, every corner needs ground (see standsSquarely)
		return support >= 2;
	}

	/**
	 * As {@link #stands}, but on horseback with ground under every corner: the way he walks along, so he never hugs
	 * the edge of a bridge or a cliff with half the horse over the drop. A corner over a one-block step down (the
	 * tread he's stepping up from, a log he's stepping onto) still counts: that's how a horse climbs a step.
	 */
	public static boolean standsSquarely(Terrain terrain, boolean mounted, int x, int y, int z) {
		if (!stands(terrain, mounted, x, y, z))
			return false;
		if (!mounted)
			return true;
		for (int dx = 0; dx <= 1; dx++)
			for (int dz = 0; dz <= 1; dz++)
				if (!underfoot(terrain, x + dx, y, z + dz))
					return false;
		return true;
	}

	/** Whether a column has ground close enough under a foot at y: a block, or a one-block step down. */
	private static boolean underfoot(Terrain terrain, int x, int y, int z) {
		return terrain.kind(x, y, z) == Terrain.Kind.LOW || terrain.kind(x, y - 1, z) == Terrain.Kind.SOLID
			|| terrain.kind(x, y - 1, z).isClear() && terrain.kind(x, y - 2, z) == Terrain.Kind.SOLID;
	}

	/**
	 * Whether a horse coming in from the direction (dx, dz) can land at a node: it stands there, and both corners at
	 * its front have ground under them, so a leap or a drop never ends up along a one-block bridge or ledge.
	 */
	public static boolean landsOn(Terrain terrain, int x, int y, int z, int dx, int dz) {
		if (!stands(terrain, true, x, y, z))
			return false;
		int fx = dx > 0 ? x + 1 : x, fz = dz > 0 ? z + 1 : z;
		if (dx != 0)
			return underfoot(terrain, fx, y, z) && underfoot(terrain, fx, y, z + 1);
		return underfoot(terrain, x, y, fz) && underfoot(terrain, x + 1, y, fz);
	}

	/** Whether he (or his horse) would fit here, standing or passing through in the air. */
	static boolean fits(Terrain terrain, boolean mounted, int x, int y, int z) {
		if (!mounted)
			return terrain.kind(x, y, z).feetFit() && terrain.kind(x, y + 1, z).isClear();
		for (int dx = 0; dx <= 1; dx++)
			for (int dz = 0; dz <= 1; dz++)
				if (!terrain.kind(x + dx, y, z + dz).horseFeetFit() || !terrain.kind(x + dx, y + 1, z + dz).horseClear()
						|| !terrain.kind(x + dx, y + 2, z + dz).horseClear())
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
				if (!terrain.kind(x + dx, above, z + dz).horseClear())
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
			if (standsSquarely(terrain, m, x, from.y, z)) {
				add(from, x, from.y, z, m, Move.WALK, length);
				continue;
			}
			if (!diagonal && standsSquarely(terrain, m, x, from.y + 1, z) && headroom(m, from.x, from.y, from.z)) {
				add(from, x, from.y + 1, z, m, Move.WALK, length + STEP_UP_COST);
				continue;
			}
			if (!fits(terrain, m, x, from.y, z))
				continue;
			int maxDrop = m ? MOUNTED_DROP : FOOT_DROP;
			// Off an edge: on foot he lands a block on; a horse (two long) can't land with its back half in the cliff
			// face, so straight off an edge it lands two blocks on, clear of it
			int[] outs = m && !diagonal ? new int[] {1, 2} : new int[] {1};
			for (int out : outs) {
				int lx = from.x + d[0] * out, lz = from.z + d[1] * out;
				if (out == 2 && !fits(terrain, m, lx, from.y, lz))
					break;
				boolean landed = false;
				for (int drop = 1; drop <= maxDrop; drop++) {
					// On foot, more than a block down is dropped straight, never corner to corner: into a hole a block
					// wide (a shaft, a steep stair cut into the ground) he'd wedge on the corners
					if (!m && diagonal && drop > 1)
						break;
					int y = from.y - drop;
					if (m ? landsOn(terrain, lx, y, lz, d[0], d[1]) : stands(terrain, m, lx, y, lz)) {
						add(from, lx, y, lz, m, Move.WALK, length * out + DROP_COST * drop);
						landed = true;
						break;
					}
					if (!fits(terrain, m, lx, y, lz))
						break;
				}
				if (landed)
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
					if (landsOn(terrain, x, from.y, z, dx, dz))
						landing = from.y;
					else if (landsOn(terrain, x, from.y - 1, z, dx, dz))
						landing = from.y - 1;
					else if (landsOn(terrain, x, from.y + 1, z, dx, dz) && headroom(true, x, from.y, z))
						landing = from.y + 1;
					if (landing != Integer.MIN_VALUE) {
						add(from, x, landing, z, true, Move.LEAP, across + LEAP_COST);
						break;
					}
				}
				// Over the gap: nothing to walk along near his level (or he'd just walk), and nothing to hit
				boolean gap = !standsSquarely(terrain, true, x, from.y, z) && !standsSquarely(terrain, true, x, from.y - 1, z)
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
				if (standsSquarely(terrain, true, from.x + dx, from.y, from.z + dz)) {
					add(from, from.x + dx, from.y, from.z + dz, true, Move.MOUNT, 0);
					return;
				}
	}

	/** What a move to a place costs: {@code length} is in blocks, before what the move and mode add. */
	private double cost(Move move, double length, boolean mounted, int x, int y, int z) {
		if (move == Move.DISMOUNT)
			return DISMOUNT_COST;
		if (move == Move.MOUNT)
			return MOUNT_COST;
		double cost = length * (mounted ? 1.0 : FOOT_COST);
		if (!mounted && terrain.kind(x, y, z) == Terrain.Kind.DOOR)
			cost += DOOR_COST;
		return terrain.covered(x, y, z) ? cost * COVERED_COST : cost;
	}

	/** Considers getting to a place this way: {@code length} is in blocks, before what the move and mode add. */
	private void add(Node from, int x, int y, int z, boolean mounted, Move move, double length) {
		if (Math.abs(x - originX) >= SPAN || Math.abs(z - originZ) >= SPAN)
			return;
		double g = from.g + cost(move, length, mounted, x, y, z);
		long key = key(x, y, z, mounted);
		Node node = nodes.get(key);
		if (node == null) {
			node = new Node(x, y, z, mounted);
			node.far = blocksToGo(node);
			node.h = atLeast(node.far, node.mounted);
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

	/** How many blocks there are still to go to the goal from a place. */
	private double blocksToGo(Node node) {
		double dx = (node.x + (node.mounted ? 1.0 : 0.5)) - goal.x(), dz = (node.z + (node.mounted ? 1.0 : 0.5)) - goal.z();
		return Math.max(0, Math.sqrt(dx * dx + dz * dz) - goal.radius());
	}

	private boolean reaches(Node node) {
		return node.far <= 0 && (goal.yTolerance() < 0 || Math.abs(node.y - goal.y()) <= goal.yTolerance());
	}

	private long key(int x, int y, int z, boolean mounted) {
		long dx = x - originX + SPAN, dz = z - originZ + SPAN, ly = (y + 2048) & 0xFFF;
		return dx << 27 | dz << 13 | ly << 1 | (mounted ? 1 : 0);
	}

	private static final class Node {
		final int x, y, z;
		final boolean mounted;
		/** The cost so far, the least cost to go (for ordering), and the blocks to go (for how far he'd get). */
		double g, h, far;
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
