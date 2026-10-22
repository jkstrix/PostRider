package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		clearStretch();
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
		CoarsePlanner.Status status = coarse.step(PlanningBudget.takeCoarse());
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

	private void adopt(@Nullable CoarsePlanner.Plan found) {
		plan = found;
		leg = 0;
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
		follow(rider);
		return Action.GOING;
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
			if (!passed(body.getX(), body.getZ(), a.centreX(), a.centreZ(), b.centreX(), b.centreZ()))
				break;
			path.advance();
		}
	}

	/**
	 * Whether, at (x, z), he's gone past step a on the way to step b: beyond it in the direction of b, and nearer b
	 * than a is.
	 */
	public static boolean passed(double x, double z, double ax, double az, double bx, double bz) {
		double dx = bx - ax, dz = bz - az;
		if ((x - ax) * dx + (z - az) * dz <= 0)
			return false;
		return Math.hypot(bx - x, bz - z) < Math.hypot(dx, dz);
	}

	private Action workOnStretch(PostmanEntity rider, ServerLevel level, Vec3 target, long now) {
		int budget = PlanningBudget.takeLocal();
		int before = local.expanded();
		LocalPlanner.Status status = local.step(budget);
		PlanningBudget.giveBackLocal(budget - (local.expanded() - before));
		if (status == LocalPlanner.Status.RUNNING)
			return Action.GOING;
		if (status == LocalPlanner.Status.FAILED) {
			local = null;
			retryAt = now + RETRY_TICKS;
			return stretchFailed(rider, level, target);
		}
		List<LocalPlanner.Step> found = local.steps();
		stepsAreFinal = localIsFinal && status == LocalPlanner.Status.FOUND;
		local = null;
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

	/**
	 * No way through the blocks ahead. With a long-range plan, that part of it is marked impassable and the plan done
	 * again; without one, if it's water in the way, it's the barge.
	 */
	private Action stretchFailed(PostmanEntity rider, ServerLevel level, Vec3 target) {
		// Can't get to the mailbox itself: that's for getting unstuck to sort out
		if (localIsFinal)
			return Action.GOING;
		if (plan != null && localGoal != null) {
			int cx = CoarsePlanner.cellOf(localGoal.x), cz = CoarsePlanner.cellOf(localGoal.z);
			blocked.add(CoarsePlanner.key(cx, cz));
			if (blocked.size() > MAX_BLOCKED) {
				plan = null;
				return Action.GOING;
			}
			return Action.REPLAN;
		}
		if (launchable(level, rider, target)) {
			launchAim = target;
			return Action.LAUNCH;
		}
		return Action.GOING;
	}

	private static boolean launchable(ServerLevel level, PostmanEntity rider, Vec3 toward) {
		int nearY = rider.getBlockY();
		return Crossing.launchSpot(rider.position(), toward, (x, z) -> Crossing.surfaceIn(level, x, z, nearY)) != null;
	}

	/** Whether it's time to plan the next stretch: nothing to follow, near the end of it, off it, or it's gone bad. */
	private boolean wantsStretch(PostmanEntity rider, ServerLevel level, long now) {
		if (steps.isEmpty())
			return true;
		LocalPlanner.Step last = steps.get(steps.size() - 1);
		Vec3 here = rider.position();
		if (!stepsAreFinal && Journey.horizontalDistance(here, new Vec3(last.centreX(), here.y, last.centreZ())) < RENEW_DISTANCE)
			return true;
		if (++checkTimer % 20 != 0)
			return false;
		PathNavigation navigation = rider.getNavigation();
		if (navigation.isDone() && segmentEnd >= steps.size() - 1 && !stepsAreFinal)
			return true;
		if (offPath(here))
			return true;
		return blockedAhead(level, navigation);
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
			if (step.move() != LocalPlanner.Move.LEAP && step.move() != LocalPlanner.Move.START
					&& !LocalPlanner.stands(terrain, step.mounted(), step.x(), step.y(), step.z()))
				return true;
		}
		return false;
	}

	private void startStretch(PostmanEntity rider, ServerLevel level, Vec3 target, long now) {
		Entity body = rider.getVehicle() instanceof PostmanHorse horse ? horse : rider;
		boolean mounted = body != rider;
		int x = mounted ? Mth.floor(body.getX() - 0.5) : Mth.floor(body.getX());
		int z = mounted ? Mth.floor(body.getZ() - 0.5) : Mth.floor(body.getZ());
		int y = Mth.floor(body.getY() + 1e-3);
		LocalPlanner.Goal goal = goalFrom(rider.position(), target);
		// On foot with the barge just ahead, he walks down to it rather than calling his horse for the last few blocks
		boolean mayMount = now >= footOnlyUntil && (mounted || !waterWithin(rider.position(), FOOT_TO_WATER));
		local = new LocalPlanner(new LevelTerrain(level), x, y, z, mounted, goal, mayMount);
	}

	/** Where the next stretch aims: about one stretch on along the plan (or straight on without one), or the mailbox. */
	private LocalPlanner.Goal goalFrom(Vec3 here, Vec3 target) {
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
			return LocalPlanner.Goal.anyHeight(water.x, water.z, WAYPOINT_RADIUS + 2);
		}
		boolean lastLeg = leg >= plan.legs().size() - 1;
		Vec3 point = along(current.points(), here, ahead);
		if (lastLeg && (Journey.horizontalDistance(point, current.last()) < 1 || Journey.horizontalDistance(here, target) <= ahead))
			return mailbox(target);
		localGoal = point;
		return LocalPlanner.Goal.anyHeight(point.x, point.z, WAYPOINT_RADIUS);
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
		path.setNextNodeIndex(firstToHeadFor(steps, segmentStart, segmentEnd, body.getX(), body.getZ()) - segmentStart);
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
			rider.dismissHorse();
			startSegment(rider);
		} else if (change.move() == LocalPlanner.Move.MOUNT) {
			rider.whistleForHorse();
			waitingForHorse = DeliveryRun.HORSE_WAIT_TICKS;
		} else {
			startSegment(rider);
		}
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

	/** The step to head for first: the nearest of the first few, or the one after if he's already past it. */
	public static int firstToHeadFor(List<LocalPlanner.Step> steps, int from, int to, double x, double z) {
		int nearest = from;
		double best = Double.MAX_VALUE;
		for (int i = from; i <= Math.min(to, from + 6); i++) {
			double d = Math.hypot(steps.get(i).centreX() - x, steps.get(i).centreZ() - z);
			if (d < best) {
				best = d;
				nearest = i;
			}
		}
		if (nearest < to) {
			LocalPlanner.Step a = steps.get(nearest), b = steps.get(nearest + 1);
			double toNext = Math.hypot(b.centreX() - x, b.centreZ() - z);
			if (toNext < Math.hypot(b.centreX() - a.centreX(), b.centreZ() - a.centreZ()))
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
		CoarsePlanner.Leg current = currentLeg();
		if (current != null && current.kind() == CoarsePlanner.LegKind.LAND && leg + 1 < plan.legs().size()
				&& plan.legs().get(leg + 1).kind() == CoarsePlanner.LegKind.WATER)
			leg++;
		replan(level, from, target, true);
		waterPath = List.of();
	}

	/** Ashore again: on to the leg after the water. */
	public void landed() {
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
		if (current != null && current.kind() == CoarsePlanner.LegKind.WATER) {
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

	/** Whether the barge has come to the end of its way across the water to where he lands. */
	public boolean atEndOfWater(Vec3 here) {
		return waterPathFinal && !waterPath.isEmpty() && Journey.horizontalDistance(here, waterPath.get(waterPath.size() - 1)) <= END_OF_WATER;
	}

	/** Whether his route takes to the water within this far of here (or he's on a water leg already). */
	public boolean waterWithin(Vec3 here, double within) {
		return plan != null && launchWithin(plan.legs(), leg, here, within);
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

	/** Open water in loaded chunks, each column looked at once per plan. */
	private static WaterPlanner.Water water(ServerLevel level, int nearY) {
		Long2ByteOpenHashMap known = new Long2ByteOpenHashMap();
		return (x, z) -> {
			long key = ChunkPos.asLong(x, z);
			byte open = known.getOrDefault(key, (byte) -1);
			if (open < 0) {
				open = (byte) (level.isLoaded(new BlockPos(x, nearY, z)) && !Double.isNaN(Crossing.surfaceIn(level, x, z, nearY)) ? 1 : 0);
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
