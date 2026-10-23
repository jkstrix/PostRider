package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.Crossing;
import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.content.postman.Journey;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Gets a rider along his route: works out the long-range plan, plans each stretch through the blocks ahead of him,
 * hands the way to his horse (or his own legs) a piece at a time, gets him down and back up on the horse where the
 * plan says, and notices when the plan has gone wrong. Not saved: after a restart it plans again.
 */
public final class Navigator {
	/** Within this far of the end of his plan for the blocks ahead, he starts working out the next one. */
	public static final double RENEW_DISTANCE = 16;
	/** Pushed this far off the way, he plans it again from where he is. */
	public static final double OFF_PATH = 3;
	/** How many steps ahead of him are checked, now and then, to see they're still there to be taken. */
	public static final int CHECK_AHEAD = 8;
	/** A leg on land is done once he's this close to its end. */
	public static final double LEG_DONE = 12;
	public static final double WAYPOINT_RADIUS = 4;
	/**
	 * Heading for a water leg, he makes for the bank: the last dry ground on the way to its first point, give or take
	 * this much. From there the barge can always be launched within reach (the middle of the first water cell can be
	 * well out from the bank, too far to aim for).
	 */
	public static final double SHORE_RADIUS = 2;
	/** How close to the mailbox a plan has to get (he gets down within {@link Journey#DISMOUNT_DISTANCE}). */
	public static final double MAILBOX_RADIUS = 3;
	/** The barge lands once it's within this far of the end of its water leg. */
	public static final double LANDING_NEAR = 24;
	/** If his horse doesn't come where the plan wanted it, he stays on foot this long. */
	public static final int FOOT_ONLY_TICKS = 600;
	/** Stretches that won't plan before the long-range plan is given up as hopeless. */
	public static final int MAX_BLOCKED = 256;
	/** After a stretch fails to plan, he waits this long before trying again. */
	public static final int RETRY_TICKS = 40;
	/** On the water, the way across is planned again this often (drowned move), and he steers for this far along it. */
	public static final int WATER_REPLAN_TICKS = 40;
	public static final double WATER_LOOKAHEAD = 5;
	/** How close to the landing place the barge's way across has to come. */
	public static final double LANDING_RADIUS = 6;
	/** On foot with the barge this close ahead, he stays on foot rather than calling his horse for a few blocks. */
	public static final double FOOT_TO_WATER = 15;
	/** Within this far of the end of his way across the water, he's there: he runs the bow up onto the bank. */
	public static final double END_OF_WATER = 3;
	/** Rowing past drowned, or near an ocean monument, costs this much more a block. */
	public static final double DROWNED_EXTRA = 4, MONUMENT_EXTRA = 30;

	public enum Action {
		/** Carrying on. */
		GOING,
		/** Time to call up the barge: launch towards {@link #launchAim()}. */
		LAUNCH,
		/** The long-range plan has to be done again: he should stop and look at his map. */
		REPLAN
	}

	// The long-range plan
	@Nullable
	private CoarsePlanner coarse;
	@Nullable
	private CoarsePlanner.Plan plan;
	private int leg;
	private final Set<Long> blocked = new HashSet<>();

	// The stretch ahead
	@Nullable
	private LocalPlanner local;
	private boolean localIsFinal;
	@Nullable
	private Vec3 localGoal;
	private List<LocalPlanner.Step> steps = List.of();
	private boolean stepsAreFinal;
	private int segmentStart, segmentEnd;
	private int waitingForHorse;
	private long footOnlyUntil;
	private long retryAt;
	private int checkTimer;
	private boolean detouring;
	@Nullable
	private Vec3 launchAim;
	private final Doors doors = new Doors();
	// The way across the water
	private List<Vec3> waterPath = List.of();
	private boolean waterPathFinal;
	private long waterPlannedAt;

	/** Starts the long-range plan again from here (keeping what it learnt about stretches that couldn't be passed). */
	public void replan(ServerLevel level, Vec3 from, Vec3 target) {
		replan(level, from, target, false);
	}

	/** As above; {@code afloat} on the barge, the plan heads for the nearest land that takes him on. */
	public void replan(ServerLevel level, Vec3 from, Vec3 target, boolean afloat) {
		routesAsked++;
		clearStretch();
		plannedAfloat = afloat;
		landedSincePlanned = false;
		lastHere = from;
		coarse = level.dimensionType().hasCeiling() ? null
			: new CoarsePlanner(TerrainSurvey.of(level), CoarsePlanner.cellOf(from.x), CoarsePlanner.cellOf(from.z),
				CoarsePlanner.cellOf(target.x), CoarsePlanner.cellOf(target.z), blocked, afloat);
		if (coarse == null)
			plan = null;
	}

	/** Whether the long-range plan is done with (finished, or there isn't going to be one). */
	public boolean isPlanned() {
		return coarse == null;
	}

	/** Works on the long-range plan with this tick's share of the planning budget. */
	public void tickPlanning() {
		if (coarse == null)
			return;
		int before = coarse.expanded();
		CoarsePlanner.Status status = coarse.step(PlanningBudget.takeCoarse());
		routeCells += coarse.expanded() - before;
		if (status == CoarsePlanner.Status.DONE) {
			adopt(coarse.plan());
			coarse = null;
		} else if (status == CoarsePlanner.Status.FAILED) {
			coarse = null;
		}
	}

	/** He sets off before planning has finished: goes by the best so far, and keeps planning on the way. */
	public void goWithBestSoFar() {
		if (coarse != null && plan == null)
			adopt(coarse.bestSoFar());
	}

	// How much route finding the trip has taken, for the in-world trials to record
	private int routesAsked, routesFound, stretches, stretchesFailed;
	private long routeCells, stretchBlocks;

	/**
	 * What the route finding has done this trip: long-range routes asked for and found, and the cells they settled;
	 * stretches planned and how many wouldn't plan, and the blocks they looked at.
	 */
	public record Stats(int routesAsked, int routesFound, long routeCells, int stretches, int stretchesFailed, long stretchBlocks) {
		@Override
		public String toString() {
			return routesFound + "/" + routesAsked + " routes (" + routeCells + " cells), " + stretches + " stretches, " + stretchesFailed
				+ " failed (" + stretchBlocks + " blocks)";
		}
	}

	public Stats stats() {
		return new Stats(routesAsked, routesFound, routeCells, stretches, stretchesFailed, stretchBlocks);
	}

	/** Where his routing decisions are noted (the server log), so a strange route can be worked out afterwards. */
	private Consumer<String> log = message -> {};

	public void logTo(Consumer<String> log) {
		this.log = log;
	}

	/** Told each time he gets off or back on his horse (the trials trace him for a while after). */
	private Runnable onModeChange = () -> {};

	public void onModeChange(Runnable hook) {
		this.onModeChange = hook;
	}

	/** Where he was when last seen, the long-range plan being worked out was asked for afloat, and he's landed since. */
	@Nullable
	private Vec3 lastHere;
	private boolean plannedAfloat, landedSincePlanned;

	private void adopt(@Nullable CoarsePlanner.Plan found) {
		if (found != null)
			routesFound++;
		log.accept(found == null ? "no long-range route; heading straight there" : "route planned: " + describe(found));
		plan = found;
		// A plan takes a while to work out: he picks it up where he is now, not where he was when he asked for it
		leg = found == null ? 0 : startingLeg(found.legs(), lastHere, plannedAfloat && landedSincePlanned);
		// What he's following now was planned for the old route: the next stretch follows the new one
		stepsAreFinal = false;
	}

	@Nullable
	public CoarsePlanner.Plan plan() {
		return plan;
	}

	/** Roughly which way his route sets off from here: along the plan (or the best so far), else straight there. */
	public Vec3 heading(Vec3 here, Vec3 target) {
		CoarsePlanner.Plan known = plan != null ? plan : coarse != null ? coarse.bestSoFar() : null;
		if (known != null && !known.legs().isEmpty()) {
			Vec3 point = along(known.legs().get(plan != null ? Math.min(leg, known.legs().size() - 1) : 0).points(), here, 16);
			if (Journey.horizontalDistance(here, point) > 2)
				return point;
		}
		return target;
	}

	/** Forgets the stretch ahead (he's been moved: teleported free, camped, fled). */
	public void clearStretch() {
		local = null;
		steps = List.of();
		stepsAreFinal = false;
		waitingForHorse = 0;
		columnEnd = -1;
		leavingColumn = false;
	}

	public boolean isDetouring() {
		return detouring;
	}

	@Nullable
	public Vec3 launchAim() {
		return launchAim;
	}

	// ---------------------------------------------------------------- each tick on land

	public Action tick(PostmanEntity rider, ServerLevel level, Vec3 target) {
		lastHere = rider.position();
		tendDoors(rider, level);
		tickPlanning();
		long now = level.getGameTime();
		Vec3 here = rider.position();
		advanceLegs(here);
		CoarsePlanner.Leg current = currentLeg();
		if (current != null && current.kind() == CoarsePlanner.LegKind.WATER && now % 10 == 0) {
			// At the shore he calls up the barge, once there's open water towards the first stretch of it
			Vec3 water = current.points().get(0);
			if (launchable(level, rider, water)) {
				launchAim = water;
				return Action.LAUNCH;
			}
		}
		if (waitingForHorse > 0) {
			if (rider.getHorse() != null) {
				waitingForHorse = 0;
				startSegment(rider);
			} else if (--waitingForHorse == 0) {
				footOnlyUntil = now + FOOT_ONLY_TICKS;
				clearStretch();
			}
			return Action.GOING;
		}
		if (local != null) {
			Action action = workOnStretch(rider, level, target, now);
			if (action != Action.GOING)
				return action;
		} else if (now >= retryAt && wantsStretch(rider, level, now)) {
			startStretch(rider, level, target, now);
		}
		skipPassed(rider);
		boolean descending = descendingAhead(rider);
		rider.setDescending(descending);
		// On foot likewise: a walk going down, a run on the flat
		if (rider.getVehicle() == null && !steps.isEmpty() && !rider.getNavigation().isDone()) {
			rider.setSprinting(!descending);
			rider.getNavigation().setSpeedModifier(descending ? DeliveryRun.WALK_SPEED : DeliveryRun.RUN_SPEED);
		}
		nudgeIntoClimb(rider, level);
		if (tickColumn(rider, level))
			return Action.GOING;
		follow(rider);
		return Action.GOING;
	}

	// ---------------------------------------------------------------- doors

	/** Opens any door among the next few steps on foot, and shuts (or leaves open) each one as it was once he's through. */
	private void tendDoors(PostmanEntity rider, ServerLevel level) {
		Path path = rider.getNavigation().getPath();
		int next = !steps.isEmpty() && rider.getVehicle() == null && path != null && !path.isDone() ? segmentStart + path.getNextNodeIndex() : -1;
		doors.tick(level, rider, steps, next, segmentEnd);
	}

	/** Every door he's opened goes back as he found it (he's leaving the road: delivered, camping, lost). */
	public void putDoorsBack(ServerLevel level) {
		doors.putAllBack(level);
	}

	/** How many doors he's opened and not yet put back. */
	public int doorsOpen() {
		return doors.open();
	}

	// ---------------------------------------------------------------- up and down a ladder or a bubble column

	/** The last step of the run of column steps he's riding (a ladder or a bubble column), or -1. */
	private int columnEnd = -1;
	/** What he's doing while he rides it: climbing a ladder, or drifting in a bubble column. */
	@Nullable
	private PostmanEntity.Activity columnKind;

	/** How he's moving while in a column (climbing, or drifting), or null when he isn't in one. */
	@Nullable
	public PostmanEntity.Activity columnActivity() {
		return columnEnd >= 0 ? columnKind : null;
	}
	private int columnTicks;
	/** Thrown up out of a rising column, or nearly at the foot of a whirlpool: being pushed across onto the ledge beside it. */
	private boolean leavingColumn;
	/** This close (in blocks) to the foot of a whirlpool he's pushed out, so his feet never touch the magma under it. */
	public static final double FOOT_OF_WHIRLPOOL = 1.5;
	/** He's pulled this far off the column's middle: knocked out of it. */
	public static final double COLUMN_OFF = 0.8;
	/** How hard he's pushed, a tick, from the top of a rising column onto the ledge beside it (blocks a tick). */
	public static final double PUSH_OUT = 0.3;
	/** Longer than this up or down one column means something's wrong. */
	public static final int COLUMN_TICKS = 400;

	/** Whether a step is one of a column: a ladder, or a bubble column, straight up or down. */
	public static boolean isColumn(LocalPlanner.Move move) {
		return move == LocalPlanner.Move.CLIMB || move == LocalPlanner.Move.RISE || move == LocalPlanner.Move.SINK;
	}

	/** The index of the last step, from {@code from} on, of column steps all in the same column; {@code from} itself if it isn't one. */
	public static int columnRunEnd(List<LocalPlanner.Step> steps, int from) {
		if (!isColumn(steps.get(from).move()))
			return from;
		int end = from;
		while (end + 1 < steps.size() && isColumn(steps.get(end + 1).move()) && steps.get(end + 1).x() == steps.get(from).x()
				&& steps.get(end + 1).z() == steps.get(from).z())
			end++;
		return end;
	}

	/**
	 * A ladder or a bubble column is not for the path follower: pushed sideways on a ladder a mob climbs up, whichever
	 * way the path goes. So from the first column step he's held square in the column with no path, the ladder (he
	 * jumps to climb, slides to descend) or the water takes him, and the path is handed back at the other end.
	 * True while he's being taken.
	 */
	private boolean tickColumn(PostmanEntity rider, ServerLevel level) {
		if (columnEnd < 0) {
			Path path = rider.getNavigation().getPath();
			if (steps.isEmpty() || path == null || path.isDone() || rider.getVehicle() != null)
				return false;
			int next = segmentStart + path.getNextNodeIndex();
			if (next < 0 || next > segmentEnd || next >= steps.size() || !isColumn(steps.get(next).move()))
				return false;
			LocalPlanner.Step step = steps.get(next);
			if (Math.hypot(rider.getX() - (step.x() + 0.5), rider.getZ() - (step.z() + 0.5)) > SHAFT_NUDGE)
				return false;
			columnEnd = Math.min(segmentEnd, columnRunEnd(steps, next));
			columnTicks = 0;
			rider.getNavigation().stop();
			columnKind = new LevelTerrain(level).kind(step.x(), step.y(), step.z()) == Terrain.Kind.CLIMB ? PostmanEntity.Activity.CLIMB
				: PostmanEntity.Activity.DRIFT;
			log.accept((steps.get(columnEnd).y() < step.y() ? "down" : "up") + " a column from " + spot(rider.position()) + " to " + steps.get(columnEnd).y());
		}
		LocalPlanner.Step end = steps.get(columnEnd);
		double cx = end.x() + 0.5, cz = end.z() + 0.5;
		if (++columnTicks > COLUMN_TICKS || !leavingColumn && Math.hypot(rider.getX() - cx, rider.getZ() - cz) > COLUMN_OFF) {
			log.accept("knocked out of the column at " + spot(rider.position()) + ": planning again");
			columnEnd = -1;
			leavingColumn = false;
			clearStretch();
			return false;
		}
		int resume = columnEnd + 1;
		if (!leavingColumn) {
			rider.setPos(cx, rider.getY(), cz);
			boolean up = end.y() > rider.getY();
			if (up && rider.onClimbable())
				rider.getJumpControl().jump();
			boolean there = up ? rider.getY() >= end.y() - 0.05 : rider.getY() <= end.y() + 0.05 && (rider.onGround() || rider.onClimbable());
			// The water of a rising column throws him up and would hold him bobbing there, and the magma at the foot
			// of a whirlpool burns: from the top of the one, and just above the foot of the other, he's pushed across
			// onto the ledge beside it, which the next step is
			boolean nearTheFoot = !up && rider.getY() <= end.y() + FOOT_OF_WHIRLPOOL;
			if (columnKind == PostmanEntity.Activity.DRIFT && resume <= segmentEnd && (nearTheFoot || there && up)) {
				leavingColumn = true;
				return true;
			}
			if (!there)
				return true;
		} else {
			LocalPlanner.Step ledge = steps.get(resume);
			boolean onTheLedge = Math.hypot(rider.getX() - ledge.centreX(), rider.getZ() - ledge.centreZ()) < 0.5
				|| rider.onGround() && !rider.isInWater() && Math.hypot(rider.getX() - cx, rider.getZ() - cz) > 0.6;
			if (!onTheLedge) {
				Vec3 push = pushOut(rider.getX(), rider.getZ(), ledge);
				rider.setDeltaMovement(push.x, rider.getDeltaMovement().y, push.z);
				return true;
			}
			leavingColumn = false;
		}
		// At the other end: on along the rest of the way
		columnEnd = -1;
		if (resume > segmentEnd)
			return false;
		segmentStart = resume;
		startSegment(rider);
		return true;
	}

	/** The push, a tick, from here towards the middle of a step beside him (at most {@link #PUSH_OUT}, less when it's close). */
	public static Vec3 pushOut(double x, double z, LocalPlanner.Step ledge) {
		double dx = ledge.centreX() - x, dz = ledge.centreZ() - z;
		double distance = Math.hypot(dx, dz);
		if (distance < 1e-6)
			return Vec3.ZERO;
		double push = Math.min(PUSH_OUT, distance);
		return new Vec3(dx / distance * push, 0, dz / distance * push);
	}

	/** Coming up to a ladder or a bubble column, he's set this far from square in it at most before he's put square. */
	public static final double CLIMB_NUDGE = 1.2;

	/** A step straight down his own column (a shaft) from this close to its middle: he's set square over it. */
	public static final double SHAFT_NUDGE = 0.6;

	/**
	 * A ladder (or a bubble column) is only climbed from inside its block, and a ladder has a thin solid side he can
	 * push against for ever; and a step straight down a shaft needs him square over it, or a sliver of his feet on
	 * the rim holds him up while the path follower moves on to the steps at the bottom. On foot, heading for either,
	 * he's set square into the block.
	 */
	private void nudgeIntoClimb(PostmanEntity rider, ServerLevel level) {
		Path path = rider.getNavigation().getPath();
		if (steps.isEmpty() || path == null || path.isDone() || rider.getVehicle() != null)
			return;
		int next = segmentStart + path.getNextNodeIndex();
		if (next < 0 || next > segmentEnd || next >= steps.size())
			return;
		LocalPlanner.Step step = steps.get(next);
		double cx = step.x() + 0.5, cz = step.z() + 0.5;
		double off = Math.hypot(rider.getX() - cx, rider.getZ() - cz);
		boolean climb = climbable(new LevelTerrain(level).kind(step.x(), step.y(), step.z()));
		boolean down = step.y() < Mth.floor(rider.getY() + 1e-3);
		if (off <= 0.05)
			return;
		if (climb && rider.horizontalCollision && off <= CLIMB_NUDGE && Math.abs(rider.getY() - step.y()) <= 1.5
				|| (climb || down) && off <= SHAFT_NUDGE && rider.onGround())
			rider.setPos(cx, rider.getY(), cz);
	}

	static boolean climbable(Terrain.Kind kind) {
		return kind == Terrain.Kind.CLIMB || kind == Terrain.Kind.BUBBLE_UP || kind == Terrain.Kind.BUBBLE_DOWN;
	}

	/** A leap in the plan: where he takes off and where he lands (the middles of his horse at each). */
	public record Leap(Vec3 takeOff, Vec3 landing) {}

	/** The leap he's heading into on his horse, if the next step of the way is one; else null. */
	@Nullable
	public Leap leapAhead(PostmanEntity rider) {
		Path path = rider.getNavigation().getPath();
		if (steps.isEmpty() || path == null || path.isDone() || rider.getHorse() == null)
			return null;
		int next = segmentStart + path.getNextNodeIndex();
		if (next <= segmentStart || next > segmentEnd || steps.get(next).move() != LocalPlanner.Move.LEAP)
			return null;
		LocalPlanner.Step from = steps.get(next - 1), to = steps.get(next);
		return new Leap(new Vec3(from.centreX(), from.y(), from.centreZ()), new Vec3(to.centreX(), to.y(), to.centreZ()));
	}

	/**
	 * Moves the path on past steps he's already gone by (a leap carries him past where it lands), so he never turns
	 * back to touch them.
	 */
	private void skipPassed(PostmanEntity rider) {
		Path path = rider.getNavigation().getPath();
		if (steps.isEmpty() || path == null || path.isDone())
			return;
		Entity body = rider.getVehicle() != null ? rider.getVehicle() : rider;
		while (path.getNextNodeIndex() + 1 < path.getNodeCount()) {
			int next = segmentStart + path.getNextNodeIndex();
			if (next + 1 > segmentEnd)
				break;
			LocalPlanner.Step a = steps.get(next), b = steps.get(next + 1);
			if (!passed(body.getX(), body.getY(), body.getZ(), a.centreX(), a.y(), a.centreZ(), b.centreX(), b.centreZ()))
				break;
			path.advance();
		}
	}

	/** He's this far from a step's level, or from the line of the way, at most, to count as having passed it. */
	public static final double PASS_NEAR = 2.0;

	/**
	 * Whether, at (x, y, z), he's gone past step a on the way to step b: on its level (the ground over a tunnel the
	 * way runs through is not the way), beside the line of the way, beyond a in the direction of b, and nearer b
	 * than a is.
	 */
	public static boolean passed(double x, double y, double z, double ax, double ay, double az, double bx, double bz) {
		if (Math.abs(y - ay) > PASS_NEAR)
			return false;
		double dx = bx - ax, dz = bz - az;
		double length = Math.hypot(dx, dz);
		if (length < 1e-6 || (x - ax) * dx + (z - az) * dz <= 0)
			return false;
		double across = Math.abs((x - ax) * dz - (z - az) * dx) / length;
		return across <= PASS_NEAR && Math.hypot(bx - x, bz - z) < length;
	}

	private Action workOnStretch(PostmanEntity rider, ServerLevel level, Vec3 target, long now) {
		int budget = PlanningBudget.takeLocal();
		int before = local.expanded();
		LocalPlanner.Status status = local.step(budget);
		PlanningBudget.giveBackLocal(budget - (local.expanded() - before));
		stretchBlocks += local.expanded() - before;
		if (status == LocalPlanner.Status.RUNNING)
			return Action.GOING;
		stretches++;
		if (status == LocalPlanner.Status.FAILED) {
			stretchesFailed++;
			local = null;
			retryAt = now + RETRY_TICKS;
			return stretchFailed(rider, level, target);
		}
		List<LocalPlanner.Step> found = local.steps();
		local = null;
		// Planned before he got into a ladder or a bubble column: it's taking him along, and he plans again at its end
		if (columnEnd >= 0)
			return Action.GOING;
		stepsAreFinal = localIsFinal && status == LocalPlanner.Status.FOUND;
		if (found.size() < 2) {
			steps = List.of();
			return Action.GOING;
		}
		steps = found;
		// However short the new stretch, the next isn't planned straight away
		retryAt = now + 20;
		segmentStart = 0;
		segmentEnd = segmentEnd(steps, 0);
		Vec3 ahead = new Vec3(steps.get(steps.size() - 1).centreX(), rider.getY(), steps.get(steps.size() - 1).centreZ());
		detouring = DeliveryRun.isDetour(rider.position(), ahead, target);
		startSegment(rider);
		return Action.GOING;
	}

	/** No way through the blocks ahead: the barge, the plan redone round it, or carry on (see {@link #whenStretchFails}). */
	private Action stretchFailed(PostmanEntity rider, ServerLevel level, Vec3 target) {
		Vec3 water = waterToCross(level, rider, target);
		boolean hasPlan = plan != null && localGoal != null;
		CoarsePlanner.Leg current = currentLeg();
		boolean toWater = current != null && current.kind() == CoarsePlanner.LegKind.WATER;
		Failed what = whenStretchFails(localIsFinal, water != null, hasPlan, toWater, coarse != null, blocked.size());
		log.accept("no way through the blocks from " + spot(rider.position()) + " towards " + (localGoal != null ? spot(localGoal) : "the mailbox")
			+ ": " + what.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
		switch (what) {
			case BARGE -> {
				// Where he couldn't get to still goes down as impassable, so the route worked out afloat doesn't
				// bring him straight back to the same dead end
				// (not the water of a water leg: that's where he's going, not what stopped him)
				if (hasPlan && !localIsFinal && !toWater)
					blocked.add(CoarsePlanner.key(CoarsePlanner.cellOf(localGoal.x), CoarsePlanner.cellOf(localGoal.z)));
				launchAim = water;
				return Action.LAUNCH;
			}
			case REPLAN -> {
				blocked.add(CoarsePlanner.key(CoarsePlanner.cellOf(localGoal.x), CoarsePlanner.cellOf(localGoal.z)));
				return Action.REPLAN;
			}
			case DROP_PLAN -> plan = null;
			default -> {}
		}
		return Action.GOING;
	}

	/** Where he's got to with his route, for the log when something goes wrong. */
	public String state() {
		CoarsePlanner.Leg current = currentLeg();
		return "leg " + (plan == null ? "-" : (leg + 1) + "/" + plan.legs().size() + " " + (current == null ? "" : current.kind()))
			+ (coarse != null ? ", re-planning the route" : "")
			+ ", stretch " + (local != null ? "being planned" : steps.size() + " steps" + (stepsAreFinal ? " to the mailbox" : ""))
			+ (localGoal != null ? " towards " + spot(localGoal) : "")
			+ (waitingForHorse > 0 ? ", waiting for his horse" : "");
	}

	/** A plan in a few words: each leg, land or barge, and how long it is. */
	public static String describe(CoarsePlanner.Plan plan) {
		StringBuilder text = new StringBuilder();
		for (CoarsePlanner.Leg leg : plan.legs()) {
			double length = 0;
			for (int i = 1; i < leg.points().size(); i++)
				length += Journey.horizontalDistance(leg.points().get(i - 1), leg.points().get(i));
			if (!text.isEmpty())
				text.append(", then ");
			text.append(leg.kind() == CoarsePlanner.LegKind.WATER ? "barge " : "land ").append(Math.round(length)).append(" blocks");
		}
		return text.toString();
	}

	/** The first few steps of a way, for the log: each move and where it lands. */
	public static String describe(List<LocalPlanner.Step> steps, int atMost) {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < Math.min(atMost, steps.size()); i++) {
			LocalPlanner.Step step = steps.get(i);
			if (i > 0)
				text.append(", ");
			text.append(step.move().name().toLowerCase(java.util.Locale.ROOT)).append(step.mounted() ? " (mounted) " : " ")
				.append(step.x()).append(' ').append(step.y()).append(' ').append(step.z());
		}
		return text.toString();
	}

	/** A place as whole block coordinates, for the log. */
	public static String spot(Vec3 at) {
		return Mth.floor(at.x) + " " + Mth.floor(at.y) + " " + Mth.floor(at.z);
	}

	/** What he does when there's no way through the blocks ahead. */
	public enum Failed {
		/** Carries on as he is (getting unstuck sorts it out if it comes to that). */
		CARRY_ON,
		/** Water's in the way, open enough to launch on: he takes the barge. */
		BARGE,
		/** Marks that part of the long-range plan impassable and works it out again. */
		REPLAN,
		/** Too much of the plan has proved impassable: he goes without one. */
		DROP_PLAN
	}

	/**
	 * When the stretch ahead won't plan: water he can launch onto means the barge, whatever the long-range plan
	 * thought (a river narrower than its cells can slip through it as land); otherwise the plan is redone round the
	 * blockage, unless it was the mailbox he couldn't reach or the water at the end of a land leg.
	 */
	public static Failed whenStretchFails(boolean toMailbox, boolean waterToLaunchOn, boolean hasPlan, boolean toWater, boolean planning,
			int blockedSoFar) {
		if (waterToLaunchOn)
			return Failed.BARGE;
		// Heading down to a water leg and no nearer: the water is what's in the way, so it's no use marking that part
		// of the plan blocked; he tries again in a moment. Likewise while a new long-range plan is still being worked
		// out (it can take a while): starting it over each time would mean it never finishes
		if (toMailbox || !hasPlan || toWater || planning)
			return Failed.CARRY_ON;
		return blockedSoFar + 1 > MAX_BLOCKED ? Failed.DROP_PLAN : Failed.REPLAN;
	}

	/**
	 * Open water close ahead, room enough to launch the barge on, towards where this stretch was going or else the
	 * mailbox: the way to row; null if there's none.
	 */
	@Nullable
	public Vec3 waterToCross(ServerLevel level, PostmanEntity rider, Vec3 target) {
		if (localGoal != null && launchable(level, rider, localGoal))
			return localGoal;
		return launchable(level, rider, target) ? target : null;
	}

	private static boolean launchable(ServerLevel level, PostmanEntity rider, Vec3 toward) {
		int nearY = rider.getBlockY();
		return Crossing.launchSpot(rider.position(), toward, (x, z) -> Crossing.surfaceIn(level, x, z, nearY)) != null;
	}

	/** Whether it's time to plan the next stretch: nothing to follow, near the end of it, off it, or it's gone bad. */
	private boolean wantsStretch(PostmanEntity rider, ServerLevel level, long now) {
		if (steps.isEmpty())
			return true;
		// Being taken up or down a column: the path is stopped on purpose
		if (columnEnd >= 0)
			return false;
		LocalPlanner.Step last = steps.get(steps.size() - 1);
		Vec3 here = rider.position();
		if (!stepsAreFinal && Journey.horizontalDistance(here, new Vec3(last.centreX(), here.y, last.centreZ())) < RENEW_DISTANCE)
			return true;
		if (++checkTimer % 20 != 0)
			return false;
		PathNavigation navigation = rider.getNavigation();
		// His horse (or his legs) gave up following the way before the end of it, a snag on a corner, say: plan it
		// again from here, the last stretch to the mailbox too, rather than standing there
		if (navigation.isDone() && segmentEnd >= steps.size() - 1
				&& stalledShort(stepsAreFinal, Journey.horizontalDistance(here, new Vec3(last.centreX(), here.y, last.centreZ()))))
			return true;
		if (offPath(here))
			return true;
		return blockedAhead(level, navigation);
	}

	/**
	 * Whether, his path follower having stopped, he's short of where this stretch ends: any stretch on the way, or
	 * the last one if he's still well off the mailbox.
	 */
	public static boolean stalledShort(boolean toMailbox, double fromEnd) {
		return !toMailbox || fromEnd > MAILBOX_RADIUS + 1;
	}

	private boolean offPath(Vec3 here) {
		double nearest = Double.MAX_VALUE;
		for (int i = segmentStart; i <= segmentEnd; i++) {
			LocalPlanner.Step step = steps.get(i);
			nearest = Math.min(nearest, Journey.horizontalDistance(here, new Vec3(step.centreX(), here.y, step.centreZ())));
		}
		return nearest > OFF_PATH;
	}

	private boolean blockedAhead(ServerLevel level, PathNavigation navigation) {
		Path path = navigation.getPath();
		int next = path == null ? 0 : path.getNextNodeIndex();
		LevelTerrain terrain = new LevelTerrain(level);
		for (int i = segmentStart + next; i <= Math.min(segmentEnd, segmentStart + next + CHECK_AHEAD); i++) {
			LocalPlanner.Step step = steps.get(i);
			// (a step in a bubble column is in the water, not standing)
			if (step.move() != LocalPlanner.Move.LEAP && step.move() != LocalPlanner.Move.START
					&& step.move() != LocalPlanner.Move.RISE && step.move() != LocalPlanner.Move.SINK
					&& !LocalPlanner.stands(terrain, step.mounted(), step.x(), step.y(), step.z()))
				return true;
		}
		return false;
	}

	/** What a search from a spot found: the way (empty if none), how much was looked at, and whether it was to the mailbox. */
	public record Probe(LocalPlanner.Status status, List<LocalPlanner.Step> steps, int expanded, boolean toMailbox, String report) {
		public boolean found() {
			return status == LocalPlanner.Status.FOUND;
		}
	}

	/**
	 * A search there and then (not spread over ticks) from a spot to where the stretch was going, or the mailbox,
	 * looking at up to {@code budget} places: for getting unstuck, and for the in-world trials.
	 */
	public Probe probe(ServerLevel level, int x, int y, int z, boolean mounted, boolean mayMount, Vec3 target, int budget) {
		boolean toMailbox = localGoal == null || localIsFinal;
		LocalPlanner.Goal goal = toMailbox ? new LocalPlanner.Goal(target.x, Mth.floor(target.y), target.z, MAILBOX_RADIUS, 3)
			: LocalPlanner.Goal.anyHeight(localGoal.x, localGoal.z, WAYPOINT_RADIUS);
		LocalPlanner search = new LocalPlanner(new LevelTerrain(level), x, y, z, mounted, goal, mayMount);
		LocalPlanner.Status status = search.step(budget);
		while (status == LocalPlanner.Status.RUNNING && search.expanded() < budget)
			status = search.step(budget - search.expanded());
		return new Probe(status, search.steps(), search.expanded(), toMailbox, search.report());
	}

	/** As {@link #probe}, from where he (or his horse) stands now. */
	public Probe probeFromHere(PostmanEntity rider, ServerLevel level, Vec3 target, int budget) {
		Entity body = rider.getVehicle() instanceof PostmanHorse horse ? horse : rider;
		boolean mounted = body != rider;
		int x = mounted ? Mth.floor(body.getX() - 0.5) : Mth.floor(body.getX());
		int z = mounted ? Mth.floor(body.getZ() - 0.5) : Mth.floor(body.getZ());
		return probe(level, x, Mth.floor(body.getY() + 1e-3), z, mounted, rider.mayWhistle(), target, budget);
	}

	/** Takes the way a probe found from where he is as the stretch to follow now. */
	public void follow(PostmanEntity rider, Probe way, long now) {
		local = null;
		columnEnd = -1;
		leavingColumn = false;
		steps = way.steps();
		stepsAreFinal = way.toMailbox() && way.found();
		segmentStart = 0;
		segmentEnd = segmentEnd(steps, 0);
		retryAt = now + 20;
		startSegment(rider);
	}

	private void startStretch(PostmanEntity rider, ServerLevel level, Vec3 target, long now) {
		Entity body = rider.getVehicle() instanceof PostmanHorse horse ? horse : rider;
		boolean mounted = body != rider;
		int x = mounted ? Mth.floor(body.getX() - 0.5) : Mth.floor(body.getX());
		int z = mounted ? Mth.floor(body.getZ() - 0.5) : Mth.floor(body.getZ());
		int y = Mth.floor(body.getY() + 1e-3);
		LocalPlanner.Goal goal = goalFrom(level, rider.position(), target);
		// On foot with the barge just ahead, he walks down to it rather than calling his horse for the last few blocks
		boolean mayMount = rider.mayWhistle() && now >= footOnlyUntil && (mounted || !waterWithin(rider.position(), FOOT_TO_WATER));
		local = new LocalPlanner(new LevelTerrain(level), x, y, z, mounted, goal, mayMount);
	}

	/** Where the next stretch aims: about one stretch on along the plan (or straight on without one), or the mailbox. */
	private LocalPlanner.Goal goalFrom(ServerLevel level, Vec3 here, Vec3 target) {
		double ahead = Journey.stretch() - 8;
		CoarsePlanner.Leg current = currentLeg();
		localIsFinal = false;
		if (current == null) {
			if (Journey.horizontalDistance(here, target) <= ahead)
				return mailbox(target);
			Vec3 aim = Journey.nextAim(here, target);
			Vec3 point = here.add(aim.subtract(here).normalize().scale(ahead));
			localGoal = point;
			return LocalPlanner.Goal.anyHeight(point.x, point.z, WAYPOINT_RADIUS);
		}
		if (current.kind() == CoarsePlanner.LegKind.WATER) {
			Vec3 water = current.points().get(0);
			localGoal = water;
			int nearY = Mth.floor(here.y);
			Vec3 bank = shore(here, water, (x, z) -> !level.isLoaded(new BlockPos(x, nearY, z)) || !Double.isNaN(Crossing.surfaceIn(level, x, z, nearY)));
			return LocalPlanner.Goal.anyHeight(bank.x, bank.z, SHORE_RADIUS);
		}
		boolean lastLeg = leg >= plan.legs().size() - 1;
		Vec3 point = along(current.points(), here, ahead);
		if (Journey.horizontalDistance(here, target) <= ahead)
			return mailbox(target);
		if (lastLeg && Journey.horizontalDistance(point, current.last()) < 1) {
			// At the end of the plan: the box, if the plan gets there; a plan that could only get so far (the best
			// so far of one still being worked out) is carried straight on from its end
			if (endsAtTheBox(current.last(), target, ahead))
				return mailbox(target);
			Vec3 aim = Journey.nextAim(here, target);
			point = here.add(aim.subtract(here).normalize().scale(ahead));
		}
		localGoal = point;
		return LocalPlanner.Goal.anyHeight(point.x, point.z, WAYPOINT_RADIUS);
	}

	/** Whether a plan ending here ends at the box: within a stretch of it, so the last stretch can be aimed at the box itself. */
	public static boolean endsAtTheBox(Vec3 end, Vec3 target, double ahead) {
		return Journey.horizontalDistance(end, target) <= ahead;
	}

	private LocalPlanner.Goal mailbox(Vec3 target) {
		localIsFinal = true;
		localGoal = target;
		return new LocalPlanner.Goal(target.x, Mth.floor(target.y), target.z, MAILBOX_RADIUS, 3);
	}

	// ---------------------------------------------------------------- following

	/** Hands the current piece of the way (all on horseback, or all on foot) to his horse or his own legs. */
	private void startSegment(PostmanEntity rider) {
		if (steps.isEmpty())
			return;
		boolean mounted = steps.get(segmentStart).mounted();
		if (mounted != (rider.getHorse() != null)) {
			// The plan expected him on his horse (or off it) and he isn't: plan again from how he is
			clearStretch();
			return;
		}
		Path path = toPath(steps, segmentStart, segmentEnd, stepsAreFinal && segmentEnd == steps.size() - 1);
		Entity body = mounted ? rider.getHorse() : rider;
		path.setNextNodeIndex(firstToHeadFor(steps, segmentStart, segmentEnd, body.getX(), body.getY(), body.getZ()) - segmentStart);
		rider.setSprinting(!mounted);
		rider.getNavigation().moveTo(path, mounted ? 1.0 : DeliveryRun.RUN_SPEED);
	}

	/** At the end of a piece of the way: on to the next, getting down from the horse or calling it back first. */
	private void follow(PostmanEntity rider) {
		if (steps.isEmpty() || !rider.getNavigation().isDone() || segmentEnd >= steps.size() - 1)
			return;
		LocalPlanner.Step end = steps.get(segmentEnd);
		Entity body = rider.getVehicle() != null ? rider.getVehicle() : rider;
		if (Journey.horizontalDistance(body.position(), new Vec3(end.centreX(), body.getY(), end.centreZ())) > 2) {
			clearStretch();
			return;
		}
		LocalPlanner.Step change = steps.get(segmentEnd + 1);
		segmentStart = segmentEnd + 1;
		segmentEnd = segmentEnd(steps, segmentStart);
		if (change.move() == LocalPlanner.Move.DISMOUNT) {
			log.accept("gets down from his horse at " + spot(rider.position()) + " for " + describe(steps.subList(segmentStart, Math.min(steps.size(), segmentStart + 4)), 4));
			onModeChange.run();
			rider.dismissHorse();
			startSegment(rider);
		} else if (change.move() == LocalPlanner.Move.MOUNT) {
			log.accept("whistles for his horse at " + spot(rider.position()));
			onModeChange.run();
			rider.whistleForHorse();
			waitingForHorse = DeliveryRun.HORSE_WAIT_TICKS;
		} else {
			startSegment(rider);
		}
	}

	/** How many steps ahead of the one he's heading for are looked at for a way down. */
	public static final int DESCENT_LOOKAHEAD = 3;

	/** Whether the way goes down among steps {@code from} to {@code to} (each against the one before it, the first against {@code fromY}). */
	public static boolean descends(List<LocalPlanner.Step> steps, int from, int to, int fromY) {
		int y = fromY;
		for (int i = Math.max(0, from); i <= Math.min(to, steps.size() - 1); i++) {
			if (steps.get(i).y() < y)
				return true;
			y = steps.get(i).y();
		}
		return false;
	}

	/** Whether the next few steps of the way he's on go down. */
	private boolean descendingAhead(PostmanEntity rider) {
		Path path = rider.getNavigation().getPath();
		if (steps.isEmpty() || path == null || path.isDone())
			return false;
		int next = segmentStart + path.getNextNodeIndex();
		Entity body = rider.getVehicle() != null ? rider.getVehicle() : rider;
		return descends(steps, next, Math.min(segmentEnd, next + DESCENT_LOOKAHEAD), Mth.floor(body.getY() + 1e-3));
	}

	/** The last step of the piece of the way starting here that's all on horseback or all on foot. */
	static int segmentEnd(List<LocalPlanner.Step> steps, int start) {
		int end = start;
		while (end + 1 < steps.size() && steps.get(end + 1).mounted() == steps.get(start).mounted())
			end++;
		return end;
	}

	/** A vanilla path through these steps, which his horse's (or his own) navigation can follow. */
	public static Path toPath(List<LocalPlanner.Step> steps, int from, int to, boolean reaches) {
		List<Node> nodes = new ArrayList<>();
		for (int i = from; i <= to; i++) {
			LocalPlanner.Step step = steps.get(i);
			Node node = new Node(step.x(), step.y(), step.z());
			node.type = PathType.WALKABLE;
			nodes.add(node);
		}
		LocalPlanner.Step last = steps.get(to);
		return new Path(nodes, new BlockPos(last.x(), last.y(), last.z()), reaches);
	}

	/** The step to head for first: the nearest of the first few on his level, or the one after if he's already past it. */
	public static int firstToHeadFor(List<LocalPlanner.Step> steps, int from, int to, double x, double y, double z) {
		int nearest = from;
		double best = Double.MAX_VALUE;
		for (int i = from; i <= Math.min(to, from + 6); i++) {
			if (Math.abs(steps.get(i).y() - y) > PASS_NEAR)
				continue;
			double d = Math.hypot(steps.get(i).centreX() - x, steps.get(i).centreZ() - z);
			if (d < best) {
				best = d;
				nearest = i;
			}
		}
		if (nearest < to) {
			LocalPlanner.Step a = steps.get(nearest), b = steps.get(nearest + 1);
			double toNext = Math.hypot(b.centreX() - x, b.centreZ() - z);
			if (Math.abs(b.y() - y) <= PASS_NEAR && toNext < Math.hypot(b.centreX() - a.centreX(), b.centreZ() - a.centreZ()))
				nearest++;
		}
		return nearest;
	}

	// ---------------------------------------------------------------- the legs of the long-range plan

	@Nullable
	private CoarsePlanner.Leg currentLeg() {
		return plan == null || leg >= plan.legs().size() ? null : plan.legs().get(leg);
	}

	private void advanceLegs(Vec3 here) {
		while (plan != null && leg < plan.legs().size() - 1) {
			CoarsePlanner.Leg current = plan.legs().get(leg);
			if (current.kind() != CoarsePlanner.LegKind.LAND || Journey.horizontalDistance(here, current.last()) > LEG_DONE)
				break;
			leg++;
		}
	}

	/**
	 * The barge has been launched: he's on the water leg now, and from out on the water he works out afresh where the
	 * nearest land is that takes him on to the mailbox (going by the old plan until he has).
	 */
	public void launched(ServerLevel level, Vec3 from, Vec3 target) {
		// Putting out again soon after landing, close by: that shore led nowhere, so he doesn't land there again
		long now = level.getGameTime();
		if (lastLanding != null && landedBackSoon(lastLanding, landedAt, from, now)) {
			for (long cell : shoreAround(lastLanding, TerrainSurvey.of(level)))
				blocked.add(cell);
			// A scrap of shore in a cell the plan counts as water is no cell to block: the spot itself is remembered
			noLandings.add(lastLanding);
			log.accept("the shore at " + spot(lastLanding) + " led nowhere; he won't land there again");
		}
		// Nor is the shore he's putting out from any use to land on again
		for (long cell : shoreAround(from, TerrainSurvey.of(level)))
			blocked.add(cell);
		CoarsePlanner.Leg current = currentLeg();
		if (current != null && current.kind() == CoarsePlanner.LegKind.LAND && leg + 1 < plan.legs().size()
				&& plan.legs().get(leg + 1).kind() == CoarsePlanner.LegKind.WATER)
			leg++;
		replan(level, from, target, true);
		waterPath = List.of();
	}

	/** Where and when he last stepped ashore. */
	@Nullable
	private Vec3 lastLanding;
	private long landedAt;
	/** Putting out again this soon after landing, this close by, means the shore led nowhere. */
	public static final int LANDED_BACK_TICKS = 1200;
	public static final double LANDED_BACK_DISTANCE = 32;
	/** Shores he's found lead nowhere, each kept clear of to this many blocks (a scrap of shore is smaller than a cell). */
	private final List<Vec3> noLandings = new ArrayList<>();
	public static final double NO_LANDING_RADIUS = 12;

	/** Whether a column is within {@link #NO_LANDING_RADIUS} of a shore noted as leading nowhere. */
	public static boolean nearNoLanding(List<Vec3> noLandings, int x, int z) {
		for (Vec3 shore : noLandings)
			if (Journey.horizontalDistance(shore, new Vec3(x + 0.5, shore.y, z + 0.5)) <= NO_LANDING_RADIUS)
				return true;
		return false;
	}

	/** Whether he's putting out again so soon after landing, so near where he landed, that the shore led nowhere. */
	public static boolean landedBackSoon(Vec3 landing, long landedAt, Vec3 launch, long now) {
		return now - landedAt <= LANDED_BACK_TICKS && Journey.horizontalDistance(landing, launch) <= LANDED_BACK_DISTANCE;
	}

	/**
	 * The long-range plan's cells of shore round a place: the one it's in and those next to it that are land (never
	 * the water, which is what he leaves by).
	 */
	public static List<Long> shoreAround(Vec3 at, CoarsePlanner.Cells cells) {
		int cx = CoarsePlanner.cellOf(at.x), cz = CoarsePlanner.cellOf(at.z);
		List<Long> shore = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++) {
				CoarsePlanner.Cell cell = cells.cell(cx + dx, cz + dz);
				if (cell != null && !cell.water())
					shore.add(CoarsePlanner.key(cx + dx, cz + dz));
			}
		return shore;
	}

	/** Ashore again: on to the leg after the water. */
	public void landed(Vec3 at, long now) {
		lastLanding = at;
		landedAt = now;
		landedSincePlanned = true;
		CoarsePlanner.Leg current = currentLeg();
		if (current != null && current.kind() == CoarsePlanner.LegKind.WATER)
			leg++;
		clearStretch();
		waterPath = List.of();
	}

	/**
	 * Where to steer the barge now: a few blocks on along its way across the water, round the bends and clear of
	 * the banks, to the landing place of the water leg (or towards the mailbox without one). Null if no way across
	 * the water ahead can be found.
	 */
	@Nullable
	public Vec3 bargeAim(ServerLevel level, Vec3 here, int nearY, Vec3 target, List<Vec3> drowned, long now) {
		lastHere = here;
		tickPlanning();
		CoarsePlanner.Leg current = currentLeg();
		// Launched from a cell that counts as land: the water leg starts here
		if (current != null && current.kind() == CoarsePlanner.LegKind.LAND && leg + 1 < plan.legs().size()
				&& plan.legs().get(leg + 1).kind() == CoarsePlanner.LegKind.WATER && Journey.horizontalDistance(here, current.last()) <= LANDING_NEAR) {
			leg++;
			current = currentLeg();
		}
		Vec3 goal;
		double radius;
		boolean landing;
		double stretch = Journey.stretch();
		if (coarse != null) {
			// Still working the route out (the sea ahead being surveyed, a chunk at a time): the plan so far ends
			// where what's known ends, which may be mid-ocean, and it changes as the survey comes in; so until it's
			// done he rows straight for the box, round what's in the way, and the plan catches up with him
			landing = false;
			goal = here.add(new Vec3(target.x - here.x, 0, target.z - here.z).normalize().scale(stretch));
			radius = WAYPOINT_RADIUS + 2;
		} else if (current != null && current.kind() == CoarsePlanner.LegKind.WATER) {
			Vec3 ashore = leg + 1 < plan.legs().size() ? plan.legs().get(leg + 1).points().get(0) : current.last();
			landing = Journey.horizontalDistance(here, ashore) <= stretch;
			if (landing) {
				goal = ashore;
				radius = LANDING_RADIUS;
			} else {
				List<Vec3> line = new ArrayList<>(current.points());
				line.add(ashore);
				goal = along(line, here, stretch);
				radius = WAYPOINT_RADIUS + 2;
			}
		} else {
			landing = Journey.horizontalDistance(here, target) <= stretch;
			goal = landing ? target : here.add(new Vec3(target.x - here.x, 0, target.z - here.z).normalize().scale(stretch));
			radius = landing ? LANDING_RADIUS : WAYPOINT_RADIUS + 2;
		}
		if (waterPathStale(here, now)) {
			waterPath = WaterPlanner.plan(water(level, nearY), danger(level, drowned), Mth.floor(here.x), Mth.floor(here.z), goal.x, goal.z, radius);
			waterPathFinal = landing;
			waterPlannedAt = now;
		}
		if (waterPath.isEmpty())
			return null;
		// A way across that only gets as far as the next block or two (the water the plan expected isn't there, or is
		// closed off) is no way: the old straight swerves take over
		if (!waterPathFinal && Journey.horizontalDistance(here, waterPath.get(waterPath.size() - 1)) <= WATER_LOOKAHEAD / 2) {
			waterPath = List.of();
			return null;
		}
		Vec3 point = steerFor(waterPath, waterPathFinal, goal, here);
		return new Vec3(point.x, here.y, point.z);
	}

	/**
	 * Where to steer along a way across the water: a few blocks on along it; at the end of the last of it, on at the
	 * landing place itself, so the bow runs up onto the bank rather than the barge drifting to a stop short of it.
	 */
	public static Vec3 steerFor(List<Vec3> path, boolean landing, Vec3 goal, Vec3 here) {
		if (landing && Journey.horizontalDistance(here, path.get(path.size() - 1)) <= WATER_LOOKAHEAD)
			return goal;
		return along(path, here, WATER_LOOKAHEAD);
	}

	/**
	 * A chart of the water round a spot for the trace, {@code reach} columns each way, a row a line, north at the
	 * top: {@code ~} open water, {@code #} anything else, {@code ?} not loaded, {@code @} the spot.
	 */
	public static String waterChart(ServerLevel level, Vec3 here, int nearY, int reach, int step) {
		StringBuilder chart = new StringBuilder();
		int cx = Mth.floor(here.x), cz = Mth.floor(here.z);
		for (int z = cz - reach; z <= cz + reach; z += step) {
			chart.append('\n');
			for (int x = cx - reach; x <= cx + reach; x += step) {
				if (Math.abs(x - cx) < step && Math.abs(z - cz) < step)
					chart.append('@');
				else if (!level.isLoaded(new BlockPos(x, nearY, z)))
					chart.append('?');
				else
					chart.append(Double.isNaN(Crossing.surfaceIn(level, x, z, nearY)) ? '#' : '~');
			}
		}
		return chart.toString();
	}

	/** The way across the water as it stands, for the trace. */
	public String waterState() {
		if (waterPath.isEmpty())
			return "no water path";
		Vec3 end = waterPath.get(waterPath.size() - 1);
		return "water path of " + waterPath.size() + " to " + Math.round(end.x) + " " + Math.round(end.z) + (waterPathFinal ? " (final)" : "")
			+ ", planned " + waterPlannedAt;
	}

	/** Whether a column is water, for finding the bank. */
	@FunctionalInterface
	public interface Wet {
		boolean water(int x, int z);
	}

	/**
	 * The bank on the way from here to a point out on the water: the last dry column before the water, going from
	 * here towards it (here itself if it's all water from the start, the point if it's all dry).
	 */
	public static Vec3 shore(Vec3 here, Vec3 water, Wet wet) {
		double distance = Journey.horizontalDistance(here, water);
		if (distance < 1e-6)
			return water;
		double dx = (water.x - here.x) / distance, dz = (water.z - here.z) / distance;
		Vec3 dry = here;
		for (double along = 0; along <= distance; along += 0.5) {
			double x = here.x + dx * along, z = here.z + dz * along;
			if (wet.water(Mth.floor(x), Mth.floor(z)))
				return dry;
			dry = new Vec3(Math.floor(x) + 0.5, here.y, Math.floor(z) + 0.5);
		}
		return water;
	}

	/** Whether the barge has come to the end of its way across the water to where he lands. */
	public boolean atEndOfWater(Vec3 here) {
		return waterPathFinal && !waterPath.isEmpty() && Journey.horizontalDistance(here, waterPath.get(waterPath.size() - 1)) <= END_OF_WATER;
	}

	/** Whether his route takes to the water within this far of here (or he's on a water leg already). */
	public boolean waterWithin(Vec3 here, double within) {
		return plan != null && launchWithin(plan.legs(), leg, here, within);
	}

	/**
	 * The leg of a newly made plan to pick it up at: the one he's nearest now (the later one if it's a tie, as where
	 * two meet he's done with the first), and never the water a plan made afloat starts with once he's landed.
	 */
	public static int startingLeg(List<CoarsePlanner.Leg> legs, @Nullable Vec3 here, boolean ashoreSince) {
		int start = ashoreSince && legs.size() > 1 && legs.get(0).kind() == CoarsePlanner.LegKind.WATER ? 1 : 0;
		if (here == null)
			return start;
		int best = start;
		double bestDistance = Double.MAX_VALUE;
		for (int i = start; i < legs.size(); i++) {
			double distance = Journey.horizontalDistance(here, along(legs.get(i).points(), here, 0));
			if (distance <= bestDistance + 1e-6) {
				best = i;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Whether, on this leg of these, the route takes to the water within {@code within} of here. */
	public static boolean launchWithin(List<CoarsePlanner.Leg> legs, int leg, Vec3 here, double within) {
		if (leg >= legs.size())
			return false;
		CoarsePlanner.Leg current = legs.get(leg);
		if (current.kind() == CoarsePlanner.LegKind.WATER)
			return true;
		return leg + 1 < legs.size() && legs.get(leg + 1).kind() == CoarsePlanner.LegKind.WATER
			&& Journey.horizontalDistance(here, current.last()) <= within;
	}

	private boolean waterPathStale(Vec3 here, long now) {
		if (waterPath.isEmpty() || now - waterPlannedAt >= WATER_REPLAN_TICKS)
			return true;
		Vec3 end = waterPath.get(waterPath.size() - 1);
		if (!waterPathFinal && Journey.horizontalDistance(here, end) < RENEW_DISTANCE)
			return true;
		Vec3 nearest = along(waterPath, here, 0);
		return Journey.horizontalDistance(here, nearest) > OFF_PATH + 1;
	}

	/** Whether a column is in a cell, or by a spot, he's found leads nowhere (a shore he put out from again straight away). */
	public boolean isBlockedShore(int x, int z) {
		return blocked.contains(CoarsePlanner.key(CoarsePlanner.cellOf(x), CoarsePlanner.cellOf(z))) || nearNoLanding(noLandings, x, z);
	}

	/**
	 * Open water in loaded chunks, each column looked at once per plan. The water in a cell of shore that led nowhere
	 * is kept clear of (the water by a scrap of such shore is not: he puts out again from it, and must row away).
	 */
	private WaterPlanner.Water water(ServerLevel level, int nearY) {
		Long2ByteOpenHashMap known = new Long2ByteOpenHashMap();
		return (x, z) -> {
			long key = ChunkPos.asLong(x, z);
			byte open = known.getOrDefault(key, (byte) -1);
			if (open < 0) {
				boolean blockedCell = blocked.contains(CoarsePlanner.key(CoarsePlanner.cellOf(x), CoarsePlanner.cellOf(z)));
				open = (byte) (level.isLoaded(new BlockPos(x, nearY, z)) && !blockedCell && !Double.isNaN(Crossing.surfaceIn(level, x, z, nearY)) ? 1 : 0);
				known.put(key, open);
			}
			return open == 1;
		};
	}

	/** Rowing near drowned costs more, and near an ocean monument (checked a chunk at a time) a lot more. */
	private static WaterPlanner.Danger danger(ServerLevel level, List<Vec3> drowned) {
		Long2ByteOpenHashMap monuments = new Long2ByteOpenHashMap();
		return (x, z) -> {
			Vec3 spot = new Vec3(x + 0.5, 0, z + 0.5);
			double extra = Crossing.nearAny(spot, drowned, Crossing.DROWNED_BERTH) ? DROWNED_EXTRA : 0;
			long chunk = ChunkPos.asLong(x >> 4, z >> 4);
			byte near = monuments.getOrDefault(chunk, (byte) -1);
			if (near < 0) {
				near = (byte) (Crossing.nearMonument(level, new Vec3((x & ~15) + 8, 0, (z & ~15) + 8)) ? 1 : 0);
				monuments.put(chunk, near);
			}
			return near == 1 ? extra + MONUMENT_EXTRA : extra;
		};
	}

	/** Whether the barge is near enough the end of its water leg to land (always, without one). */
	public boolean nearLanding(Vec3 here) {
		CoarsePlanner.Leg current = currentLeg();
		return current == null || current.kind() != CoarsePlanner.LegKind.WATER
			|| Journey.horizontalDistance(here, current.last()) <= LANDING_NEAR;
	}

	/**
	 * The point {@code ahead} blocks on along a line of points from the place on it nearest to {@code here}; the end of
	 * the line if it's nearer than that.
	 */
	public static Vec3 along(List<Vec3> points, Vec3 here, double ahead) {
		if (points.size() == 1)
			return points.get(0);
		int segment = 0;
		double t = 0, best = Double.MAX_VALUE;
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3 a = points.get(i), b = points.get(i + 1);
			double dx = b.x - a.x, dz = b.z - a.z, length2 = dx * dx + dz * dz;
			double s = length2 < 1e-9 ? 0 : Mth.clamp(((here.x - a.x) * dx + (here.z - a.z) * dz) / length2, 0, 1);
			double px = a.x + dx * s, pz = a.z + dz * s;
			double d = (here.x - px) * (here.x - px) + (here.z - pz) * (here.z - pz);
			if (d < best) {
				best = d;
				segment = i;
				t = s;
			}
		}
		double left = ahead;
		Vec3 from = points.get(segment).lerp(points.get(segment + 1), t);
		for (int i = segment + 1; i < points.size(); i++) {
			Vec3 to = points.get(i);
			double length = Journey.horizontalDistance(from, to);
			if (length >= left)
				return from.lerp(to, left / length);
			left -= length;
			from = to;
		}
		return points.get(points.size() - 1);
	}
}
