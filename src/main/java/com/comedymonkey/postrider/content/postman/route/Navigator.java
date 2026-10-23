package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.Crossing;
import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.content.postman.Journey;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;
import com.comedymonkey.postrider.content.postman.PostmanHorseChunks;

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
	/** In tight water (a lead between bergs, a channel just wide enough) it steers for a point only this far along, or the hull cuts the bends and catches on the corners. */
	public static final double TIGHT_LOOKAHEAD = 2.5;
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
	/** The cells of shore that led nowhere: the barge's way across the water keeps clear of the water in them. */
	private final Set<Long> noShore = new HashSet<>();
	/** The strikes were forgiven for the plan now being worked out (so a second failure stands). */
	private boolean strikesForgivenForThisPlan, shoresForgivenForThisPlan;
	private int strikesForgiven;
	@Nullable
	private Vec3 lastTarget;
	@Nullable
	private ServerLevel lastLevel;

	/** Forgives the cells struck off this trip (bar shores that led nowhere), once per failed plan; false if there were none to forgive, or they just were. */
	private boolean forgiveStrikes() {
		if (lastTarget == null || lastLevel == null)
			return false;
		if (!strikesForgivenForThisPlan) {
			strikesForgivenForThisPlan = true;
			int before = blocked.size();
			blocked.retainAll(noShore);
			strikesForgiven = before - blocked.size();
			if (strikesForgiven > 0)
				return true;
		}
		// Afloat with the shores struck off round him and still no route: the barge is boxed in by its own strikes
		// (an inlet in sea ice whose shore was taken for a scrap), so they go too, and he lands where he can
		if (plannedAfloat && !shoresForgivenForThisPlan && forgiveShores(blocked, noShore, noLandings)) {
			shoresForgivenForThisPlan = true;
			strikesForgiven = 0;
			return true;
		}
		return false;
	}

	/** Forgets every shore that led nowhere; false if there were none to forget. */
	static boolean forgiveShores(Set<Long> blocked, Set<Long> noShore, List<Vec3> noLandings) {
		if (noShore.isEmpty() && noLandings.isEmpty())
			return false;
		blocked.removeAll(noShore);
		noShore.clear();
		noLandings.clear();
		return true;
	}

	/** A route redone takes a while; after this long he goes by the best of it so far. */
	public static final int REPLAN_PATIENCE = 1200;
	private int replanTicks;
	/** The plan he has was struck off (the way ahead on it found impassable): no stretch follows it while the new one is worked out. */
	private boolean planStruck;

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
		replanTicks = 0;
		lastTarget = target;
		lastLevel = level;
		clearStretch();
		plannedAfloat = afloat;
		landedSincePlanned = false;
		lastHere = from;
		// A long trip is planned a stage at a time, to the nearest land to a point a stage along the straight line
		Vec3 goal = target;
		stageTarget = null;
		if (Journey.horizontalDistance(from, target) > STAGE) {
			stageTarget = nearestLand(TerrainSurvey.of(level), stagePoint(from, target, STAGE));
			goal = stageTarget;
			log.accept("a long trip: the route is planned as far as " + spot(goal) + " for now");
		}
		coarse = level.dimensionType().hasCeiling() ? null
			: new CoarsePlanner(TerrainSurvey.of(level), CoarsePlanner.cellOf(from.x), CoarsePlanner.cellOf(from.z),
				CoarsePlanner.cellOf(goal.x), CoarsePlanner.cellOf(goal.z), blocked, afloat);
		if (coarse == null)
			plan = null;
	}

	/** Whether the long-range plan is done with (finished, or there isn't going to be one). */
	public boolean isPlanned() {
		return coarse == null;
	}

	/** Whether a long-range plan is still being worked out (standing still meanwhile isn't being stuck). */
	public boolean isReplanning() {
		return coarse != null;
	}

	/** Whether he's whistled for his horse and is waiting for it to come (standing still meanwhile isn't being stuck). */
	public boolean isWaitingForHorse() {
		return waitingForHorse > 0;
	}

	/** Whether a stretch may be planned now: not along a plan that's been struck off while its replacement is worked out. */
	static boolean mayPlanStretch(boolean planStruck, boolean replanning) {
		return !(planStruck && replanning);
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
			if (forgiveStrikes()) {
				// No route at all, with cells struck off: more likely the strikes have walled him in than the world
				// has, so they're forgiven (bar shores that led nowhere) and the map is tried afresh, once
				log.accept(strikesForgiven > 0 ? "no long-range route with " + strikesForgiven + " cells struck off: they're forgiven and the map is tried afresh"
					: "no long-range route afloat with the shores round him struck off: they're forgiven too, and he lands where he can");
				replan(lastLevel, lastHere, lastTarget, plannedAfloat);
				return;
			}
			// No route at all from here: the old plan isn't kept to, he heads straight there and the stretches find
			// what they can
			adopt(null);
		} else if (++replanTicks > REPLAN_PATIENCE) {
			log.accept("the route is taking too long to work out: he goes by the best of it so far");
			adopt(coarse.bestSoFar());
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
	// A long trip is planned a stage at a time: this stage's end, or null when the plan runs to the box
	@Nullable
	private Vec3 stageTarget;
	/** A trip further than this is planned in stages this long, each to the nearest land to a point that far along the straight line. */
	public static final double STAGE = 1000;
	/** This near the end of a stage, the next is planned from where he is. */
	public static final double STAGE_REACHED = 48;
	/** How many cells each way round a stage point are looked at for land to make for. */
	public static final int STAGE_LAND_REACH = 6;
	// The chunks along the plan ahead of him, kept loaded (and so generated) so the map ahead is the real ground
	@Nullable
	private UUID owner;
	private final Set<Long> ahead = new HashSet<>();
	/** How far along the plan ahead of him chunks are kept loaded, and how wide (chunks each side of the way). */
	public static final double AHEAD_BLOCKS = 400;
	public static final int AHEAD_WIDTH = 1;
	/** How often (ticks) the plan is checked against the ground as it's now known, and the chunks ahead are moved on. */
	public static final int CHECK_INTERVAL = 100;
	/** The plan isn't redone for the ground not matching it more often than this (ticks): the map fills in as he goes. */
	public static final int REDO_INTERVAL = 600;
	private long lastRedo = Long.MIN_VALUE;

	/** Whose chunks the ones ahead are held as (the rider's), so they're let go with his if he's ever lost. */
	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	/** The point {@code stage} blocks along the straight line from here to the target (the target, if that's nearer). */
	public static Vec3 stagePoint(Vec3 from, Vec3 target, double stage) {
		double distance = Journey.horizontalDistance(from, target);
		if (distance <= stage)
			return target;
		return from.add(new Vec3(target.x - from.x, 0, target.z - from.z).scale(stage / distance));
	}

	/** The middle of the nearest cell of land (as far as it's known) to a point, within {@link #STAGE_LAND_REACH} cells; the point itself if none. */
	public static Vec3 nearestLand(CoarsePlanner.Cells cells, Vec3 point) {
		int cx = CoarsePlanner.cellOf(point.x), cz = CoarsePlanner.cellOf(point.z);
		for (int radius = 0; radius <= STAGE_LAND_REACH; radius++)
			for (int dx = -radius; dx <= radius; dx++)
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius)
						continue;
					CoarsePlanner.Cell cell = cells.cell(cx + dx, cz + dz);
					if (cell != null && !cell.isVoid() && !cell.water())
						return new Vec3((cx + dx) * CoarsePlanner.CELL + CoarsePlanner.CELL / 2.0, point.y, (cz + dz) * CoarsePlanner.CELL + CoarsePlanner.CELL / 2.0);
				}
		return point;
	}

	/**
	 * Whether the plan's legs from {@code fromLeg} on still fit the ground as it's now known: no land leg over what
	 * has turned out to be water or the void, no water leg over what's turned out to be dry land. Unknown cells
	 * are given the benefit of the doubt.
	 */
	public static boolean legsFit(List<CoarsePlanner.Leg> legs, int fromLeg, CoarsePlanner.Cells cells) {
		return legsFit(legs, fromLeg, cells, null, Double.MAX_VALUE);
	}

	/** More than this much height within one cell of a land leg (its highest sample over its lowest) is a cliff, not ground to ride over. */
	public static final int STEEP_WITHIN = CoarsePlanner.CLIFF_WITHIN;
	/** More than this many blocks up or down from one cell of a land leg to the next is a step no horse or man makes. */
	public static final int STEEP_BETWEEN = CoarsePlanner.MAX_RISE;

	/**
	 * As above, looking only at the plan within {@code reach} blocks along it from {@code here} (what's loaded and so
	 * really known). A land leg over ground too steep to ride (a cliff within a cell, or a step of more than
	 * {@link #STEEP_BETWEEN} from one cell to the next) doesn't fit either, the plan being made the same way; the
	 * cells at either end (where he stands, and the mailbox's) may be as steep as they like.
	 */
	public static boolean legsFit(List<CoarsePlanner.Leg> legs, int fromLeg, CoarsePlanner.Cells cells, @Nullable Vec3 here, double reach) {
		return misfit(legs, fromLeg, cells, here, reach) == null;
	}

	/** Where and how the plan's legs don't fit the ground (see {@link #legsFit}), for the log; null if they fit. */
	@Nullable
	public static String misfit(List<CoarsePlanner.Leg> legs, int fromLeg, CoarsePlanner.Cells cells, @Nullable Vec3 here, double reach) {
		return misfit(legs, fromLeg, cells, here, reach, false);
	}

	/**
	 * As above; {@code wetGoal} if the plan's goal is a stage point rather than the mailbox, which may turn out to be
	 * water (a lake the map didn't show): he'd arrive there by barge and plan the next stage afloat, so it's no misfit,
	 * and redoing the route couldn't move it anyway.
	 */
	@Nullable
	public static String misfit(List<CoarsePlanner.Leg> legs, int fromLeg, CoarsePlanner.Cells cells, @Nullable Vec3 here, double reach, boolean wetGoal) {
		int first = Math.max(0, fromLeg);
		long startCell = first < legs.size() ? cellKeyOf(legs.get(first).points().get(0)) : 0;
		long goalCell = !legs.isEmpty() ? cellKeyOf(legs.get(legs.size() - 1).points().get(legs.get(legs.size() - 1).points().size() - 1)) : 0;
		for (int i = first; i < legs.size(); i++) {
			CoarsePlanner.Leg leg = legs.get(i);
			boolean land = leg.kind() == CoarsePlanner.LegKind.LAND;
			String which = (land ? "land" : "water") + " leg " + (i + 1) + "/" + legs.size();
			CoarsePlanner.Cell previous = null;
			for (Vec3 point : sampled(leg.points(), CoarsePlanner.CELL)) {
				if (here != null && Journey.horizontalDistance(here, point) > reach)
					continue;
				int cx = CoarsePlanner.cellOf(point.x), cz = CoarsePlanner.cellOf(point.z);
				CoarsePlanner.Cell cell = cells.cell(cx, cz);
				if (cell == null) {
					previous = null;
					continue;
				}
				String at = " at " + Mth.floor(point.x) + " " + Mth.floor(point.z);
				if (cell.isVoid())
					return which + " over the void" + at;
				long key = CoarsePlanner.key(cx, cz);
				boolean anEnd = !legs.isEmpty() && (key == startCell || key == goalCell);
				String wrong = null;
				// (A land leg over water at the start is where he is, afloat or on ice: nothing to redo)
				if (land && cell.water() && key != startCell && !(wetGoal && key == goalCell))
					wrong = "over water";
				else if (land && !anEnd && cell.rough() > STEEP_WITHIN)
					wrong = "over a cliff face " + cell.rough() + " high inside a cell";
				else if (!land && !cell.water() && !cell.shore())
					wrong = "over dry land";
				// A leg's line is drawn between the cells its way went through, so at a bend it clips the corner of a
				// cell beside the way: only a cell with no fitting ground next to it is a real mismatch
				if (wrong != null && !fittingBeside(cells, cx, cz, leg.kind()))
					return which + " " + wrong + at;
				if (land && previous != null && cell != previous && Math.abs(cell.height() - previous.height()) > STEEP_BETWEEN)
					return which + " over a step of " + Math.abs(cell.height() - previous.height()) + " from one cell to the next (" + previous.height() + " to " + cell.height() + ")" + at;
				previous = cell;
			}
		}
		return null;
	}

	/** Whether any of the eight cells round one would do for a leg of this kind (land for a land leg, water or shore for a water one). */
	static boolean fittingBeside(CoarsePlanner.Cells cells, int cx, int cz, CoarsePlanner.LegKind kind) {
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0)
					continue;
				CoarsePlanner.Cell cell = cells.cell(cx + dx, cz + dz);
				if (cell == null || cell.isVoid())
					continue;
				if (kind == CoarsePlanner.LegKind.LAND ? !cell.water() && cell.rough() <= STEEP_WITHIN : cell.water() || cell.shore())
					return true;
			}
		return false;
	}

	/** The points of a line, and points every {@code step} blocks along it between them. */
	static List<Vec3> sampled(List<Vec3> line, double step) {
		List<Vec3> points = new ArrayList<>();
		for (int i = 0; i < line.size(); i++) {
			if (i > 0) {
				Vec3 a = line.get(i - 1), b = line.get(i);
				int steps = (int) Math.ceil(Journey.horizontalDistance(a, b) / step);
				for (int s = 1; s < steps; s++)
					points.add(a.lerp(b, (double) s / steps));
			}
			points.add(line.get(i));
		}
		return points;
	}

	/** Points every {@code step} blocks along the plan ahead of {@code here}, up to {@code distance} along it. */
	public static List<Vec3> pointsAhead(List<CoarsePlanner.Leg> legs, int fromLeg, Vec3 here, double distance, double step) {
		List<Vec3> line = new ArrayList<>();
		for (int i = Math.max(0, fromLeg); i < legs.size(); i++)
			line.addAll(legs.get(i).points());
		List<Vec3> points = new ArrayList<>();
		if (line.isEmpty())
			return points;
		for (double d = 0; d <= distance; d += step) {
			Vec3 point = along(line, here, d);
			if (!points.isEmpty() && Journey.horizontalDistance(points.get(points.size() - 1), point) < 1e-6)
				break;
			points.add(point);
		}
		return points;
	}

	/** Holds the chunks along the whole of this stage of the plan, to be read before he sets off. */
	public void holdStageAhead(ServerLevel level, Vec3 here) {
		keepAheadLoaded(level, here, STAGE);
	}

	/** Whether every chunk held ahead is loaded now (and so its real ground is in the survey, or about to be). */
	public boolean aheadLoaded(ServerLevel level) {
		for (long chunk : ahead) {
			ChunkPos pos = new ChunkPos(chunk);
			if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null)
				return false;
		}
		return true;
	}

	/** Whether the plan, as it stands, fits the ground as the survey now has it. */
	public boolean planFits(ServerLevel level) {
		return planMisfit(level) == null;
	}

	/** Where and how the plan doesn't fit the ground as the survey now has it, for the log; null if it fits (or there's no plan). */
	@Nullable
	public String planMisfit(ServerLevel level) {
		return plan == null ? null : misfit(plan.legs(), leg, TerrainSurvey.of(level), null, Double.MAX_VALUE, stageTarget != null);
	}

	/** How many chunks are held ahead, for the log. */
	public int chunksAhead() {
		return ahead.size();
	}

	/** Keeps the chunks along the plan ahead loaded (and so generated), a window moved on as he goes. */
	private void keepAheadLoaded(ServerLevel level, Vec3 here) {
		keepAheadLoaded(level, here, AHEAD_BLOCKS);
	}

	private void keepAheadLoaded(ServerLevel level, Vec3 here, double distance) {
		Set<Long> wanted = new HashSet<>();
		if (owner != null && plan != null)
			for (Vec3 point : pointsAhead(plan.legs(), leg, here, distance, CoarsePlanner.CELL))
				for (int dx = -AHEAD_WIDTH; dx <= AHEAD_WIDTH; dx++)
					for (int dz = -AHEAD_WIDTH; dz <= AHEAD_WIDTH; dz++)
						wanted.add(ChunkPos.asLong((Mth.floor(point.x) >> 4) + dx, (Mth.floor(point.z) >> 4) + dz));
		for (long chunk : new ArrayList<>(ahead))
			if (!wanted.contains(chunk)) {
				PostmanHorseChunks.hold(level, owner, chunk, false);
				ahead.remove(chunk);
			}
		for (long chunk : wanted)
			if (ahead.add(chunk))
				PostmanHorseChunks.hold(level, owner, chunk, true);
	}

	/** Lets go of the chunks held ahead (the trip is over, or the plan is gone). */
	public void releaseAhead(ServerLevel level) {
		for (long chunk : ahead)
			PostmanHorseChunks.hold(level, owner, chunk, false);
		ahead.clear();
	}

	private void adopt(@Nullable CoarsePlanner.Plan found) {
		if (found != null)
			routesFound++;
		log.accept(found == null ? "no long-range route; heading straight there" : "route planned: " + describe(found));
		plan = found;
		planStruck = false;
		strikesForgivenForThisPlan = false;
		shoresForgivenForThisPlan = false;
		// A plan takes a while to work out: he picks it up where he is now, not where he was when he asked for it
		leg = found == null ? 0 : startingLeg(found.legs(), lastHere, plannedAfloat && landedSincePlanned);
		// What he's following now was planned for the old route: the next stretch follows the new one
		stepsAreFinal = false;
	}

	@Nullable
	public CoarsePlanner.Plan plan() {
		return plan;
	}

	/**
	 * How far he has to go from here: to the nearest point of the way ahead, along it to its end, and on to the
	 * target (straight there if there's no plan), and half the height still to climb or drop to it. Measured this way
	 * a detour round a hill, which takes him further from the box as the crow flies, still counts as getting on, and
	 * so does winding up a stair to a box on a tower, which gets him no nearer across.
	 */
	public double distanceToGo(Vec3 here, Vec3 target) {
		return plan == null ? Journey.horizontalDistance(here, target) + CLIMB_COUNTS * Math.abs(here.y - target.y) : alongToGo(plan.legs(), leg, here, target);
	}

	/** How much of the height still to climb or drop to the box counts towards how far he has to go. */
	public static final double CLIMB_COUNTS = 0.5;

	static double alongToGo(List<CoarsePlanner.Leg> legs, int fromLeg, Vec3 here, Vec3 target) {
		return alongAcross(legs, fromLeg, here, target) + CLIMB_COUNTS * Math.abs(here.y - target.y);
	}

	private static double alongAcross(List<CoarsePlanner.Leg> legs, int fromLeg, Vec3 here, Vec3 target) {
		List<Vec3> line = new ArrayList<>();
		for (int i = Math.max(0, fromLeg); i < legs.size(); i++)
			line.addAll(legs.get(i).points());
		if (line.isEmpty())
			return Journey.horizontalDistance(here, target);
		Nearest nearest = nearestOn(line, here);
		Vec3 from = line.size() == 1 ? line.get(0) : line.get(nearest.segment()).lerp(line.get(nearest.segment() + 1), nearest.t());
		double total = Journey.horizontalDistance(here, from);
		for (int i = nearest.segment() + 1; i < line.size(); i++) {
			total += Journey.horizontalDistance(from, line.get(i));
			from = line.get(i);
		}
		return total + Journey.horizontalDistance(from, target);
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

	/**
	 * Now and then: the next stage is planned once this one's end is near, the plan is checked against the ground as
	 * it's now known (and redone if it no longer fits), and the chunks ahead along it are moved on. True if a new
	 * plan was asked for.
	 */
	private boolean checkPlanAhead(ServerLevel level, Vec3 here, Vec3 target, long now) {
		if (coarse != null)
			return false;
		if (stageTarget != null && Journey.horizontalDistance(here, stageTarget) <= STAGE_REACHED) {
			log.accept("at the end of this stage of the trip: the next is planned from here");
			replan(level, here, target, plannedAfloat);
			return true;
		}
		if (now % CHECK_INTERVAL != 0)
			return false;
		if (plan != null && now - lastRedo >= REDO_INTERVAL && !legsFit(plan.legs(), leg, TerrainSurvey.of(level), here, AHEAD_BLOCKS)) {
			log.accept("the ground ahead isn't as the map had it: the route is redone");
			lastRedo = now;
			// He keeps to the way he's on while the new route is worked out
			List<LocalPlanner.Step> keep = steps;
			boolean keepFinal = stepsAreFinal;
			int keepStart = segmentStart, keepEnd = segmentEnd;
			replan(level, here, target, plannedAfloat);
			steps = keep;
			stepsAreFinal = keepFinal;
			segmentStart = keepStart;
			segmentEnd = keepEnd;
			return false;
		}
		keepAheadLoaded(level, here);
		return false;
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
		if (checkPlanAhead(level, here, target, now))
			return Action.GOING;
		CoarsePlanner.Leg current = currentLeg();
		if (current != null && current.kind() == CoarsePlanner.LegKind.WATER && now % 10 == 0 && ashoreLongEnough(now, landedAt)) {
			// At the shore he calls up the barge, once there's open water towards the first stretch of it (never the
			// instant he's stepped ashore: across an islet he'd be back in the barge in the tick he landed)
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
		nudgeOffTheLip(rider);
		if (tickColumn(rider, level))
			return Action.GOING;
		follow(rider);
		return Action.GOING;
	}

	/** Standing this near, across, the edge the way drops off is standing on its lip. */
	public static final double LIP_REACH = 0.7;
	/** How hard he's nudged off a lip, a tick. */
	public static final double LIP_PUSH = 0.12;

	/**
	 * Whether he's standing on the lip of a drop of two or more that the way goes straight down: the path follower
	 * never counts a step that far under its feet as reached, and with nowhere to go across it stands there.
	 */
	public static boolean onTheLip(double bodyY, boolean onGround, int stepY, double across) {
		return onGround && stepY <= bodyY - 2 && across <= LIP_REACH;
	}

	/** On the lip of a drop the way goes straight down, on foot, he's nudged off it the way the step goes. */
	private void nudgeOffTheLip(PostmanEntity rider) {
		Path path = rider.getNavigation().getPath();
		if (steps.isEmpty() || path == null || path.isDone() || rider.getVehicle() != null || columnEnd >= 0)
			return;
		int next = segmentStart + path.getNextNodeIndex();
		if (next <= 0 || next > segmentEnd || next >= steps.size() || isColumn(steps.get(next).move()))
			return;
		LocalPlanner.Step step = steps.get(next), before = steps.get(next - 1);
		if (!onTheLip(rider.getY(), rider.onGround(), step.y(), Math.hypot(step.centreX() - rider.getX(), step.centreZ() - rider.getZ())))
			return;
		double dx = step.centreX() - before.centreX(), dz = step.centreZ() - before.centreZ(), length = Math.hypot(dx, dz);
		if (length < 1e-6)
			return;
		rider.setDeltaMovement(dx / length * LIP_PUSH, rider.getDeltaMovement().y, dz / length * LIP_PUSH);
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
	public static final double FOOT_OF_WHIRLPOOL = 2.5;
	/** He's pulled this far off the column's middle: knocked out of it. */
	public static final double COLUMN_OFF = 0.8;
	/** How hard he's pushed, a tick, from the top of a rising column onto the ledge beside it (blocks a tick). */
	public static final double PUSH_OUT = 0.4;
	/** Longer than this up or down one column means something's wrong. */
	public static final int COLUMN_TICKS = 400;

	/**
	 * How high up a column he climbs: level with its last step, or, where the way goes on up onto a ledge above the
	 * last rung (or vine), to the top of that block, as at the top of a ladder; stepping off level with the rung he'd
	 * step into the air beside the ledge and fall.
	 */
	public static double columnTop(List<LocalPlanner.Step> steps, int columnEnd) {
		LocalPlanner.Step end = steps.get(columnEnd);
		return columnEnd + 1 < steps.size() && steps.get(columnEnd + 1).y() > end.y() ? end.y() + 1 : end.y();
	}

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
			// Climbing he jumps to go up; at the foot of a vine that hangs to the water he swims up into it first
			if (up && (rider.onClimbable() || rider.isInWater()))
				rider.getJumpControl().jump();
			boolean there = up ? rider.getY() >= columnTop(steps, columnEnd) - 0.05 : rider.getY() <= end.y() + 0.05 && (rider.onGround() || rider.onClimbable());
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
			if (!passed(body.getX(), body.getY(), body.getZ(), a.centreX(), a.y(), a.centreZ(), b.centreX(), b.centreZ())
					|| !mayPassOnTo(body.getY(), b.y()))
				break;
			path.advance();
		}
	}

	/** A step may be skipped only if the one after it is within a block of his level: skipped on a climb, the horse bounced at a two-block face; on a descent, two drops became one fall. */
	public static final double PASS_ON_LEVEL = 1.1;

	public static boolean mayPassOnTo(double bodyY, int nextY) {
		return Math.abs(nextY - bodyY) <= PASS_ON_LEVEL;
	}

	/** The nearest point of a line to somewhere: which segment, and how far along it (0 to 1). */
	record Nearest(int segment, double t) {}

	static Nearest nearestOn(List<Vec3> points, Vec3 here) {
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
		return new Nearest(segment, t);
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
		if (status == LocalPlanner.Status.PARTIAL) {
			// Not all the way: taken only as far as the first drop he couldn't climb back up, and if that's nowhere
			// it's no way at all
			found = upToTheFirstDrop(found);
			if (found.size() < 2) {
				stretchesFailed++;
				retryAt = now + RETRY_TICKS;
				return stretchFailed(rider, level, target);
			}
		}
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
	 * A stretch that doesn't reach its goal (the best the search could do) is taken only as far as its first drop of
	 * more than a block: a basin got down into on a guess has no way back up it, and no way out but a jump. Once
	 * nearer, the next search either reaches the goal through it or goes another way.
	 */
	static List<LocalPlanner.Step> upToTheFirstDrop(List<LocalPlanner.Step> steps) {
		for (int i = 1; i < steps.size(); i++)
			if (steps.get(i - 1).y() - steps.get(i).y() > 1)
				return steps.subList(0, i);
		return steps;
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
				// The whole way from here to the stretch's goal is struck off, not just its last cell: a cliff the plan
				// took for a slope is not got round a cell at a time
				int struck = 0;
				for (long cell : strikeBand(rider.position(), localGoal))
					if (blocked.add(cell))
						struck++;
				log.accept("the way from here to " + spot(localGoal) + " is struck off the map: " + struck + " cells new, " + blocked.size() + " struck this trip");
				planStruck = true;
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
		return Crossing.launchSpot(rider.position(), toward, (x, z) -> Crossing.surfaceIn(level, x, z, nearY, Crossing.LAUNCH_DROP)) != null;
	}

	/** Whether it's time to plan the next stretch: nothing to follow, near the end of it, off it, or it's gone bad. */
	private boolean wantsStretch(PostmanEntity rider, ServerLevel level, long now) {
		if (!mayPlanStretch(planStruck, coarse != null))
			return false;
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
				&& stalledShort(stepsAreFinal, Journey.horizontalDistance(here, new Vec3(last.centreX(), here.y, last.centreZ())),
					localGoal != null ? Journey.horizontalDistance(here, localGoal) : 0))
			return true;
		if (offPath(here))
			return true;
		return blockedAhead(level, navigation);
	}

	/**
	 * Whether, his path follower having stopped, he's short of where this stretch ends: any stretch on the way, or
	 * the last one if he's still well off the end of it or further from the mailbox than he gets down from his horse
	 * (the way to the box ending a few blocks short of it, he'd stand there).
	 */
	public static boolean stalledShort(boolean toMailbox, double fromEnd, double fromBox) {
		return !toMailbox || fromEnd > MAILBOX_RADIUS + 1 || fromBox > Journey.DISMOUNT_DISTANCE;
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

	/**
	 * How much ground there is to get about on from a spot, on foot: a search from it towards the mailbox itself
	 * (not the stretch's goal, which from a shore is a point out on the water, with nothing to stand on at it),
	 * looking at up to {@code budget} places. A scrap of shore runs out of places long before the budget does.
	 */
	public Probe probeShore(ServerLevel level, int x, int y, int z, Vec3 target, int budget) {
		return probeShore(new LevelTerrain(level), x, y, z, target, budget);
	}

	/** As above, on any terrain. */
	public static Probe probeShore(Terrain terrain, int x, int y, int z, Vec3 target, int budget) {
		LocalPlanner search = new LocalPlanner(terrain, x, y, z, false, LocalPlanner.Goal.anyHeight(target.x, target.z, MAILBOX_RADIUS), false);
		LocalPlanner.Status status = search.step(budget);
		while (status == LocalPlanner.Status.RUNNING && search.expanded() < budget)
			status = search.step(budget - search.expanded());
		return new Probe(status, search.steps(), search.expanded(), true, search.report());
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
			Vec3 bank = shore(here, water, (x, z) -> !level.isLoaded(new BlockPos(x, nearY, z)) || !Double.isNaN(Crossing.surfaceIn(level, x, z, nearY, Crossing.LAUNCH_DROP)));
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

	/** Whether the horse is on the ground and not rising or falling, so he can get down from it. */
	public static boolean settledToDismount(boolean onGround, double verticalSpeed) {
		return onGround && Math.abs(verticalSpeed) < 0.2;
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
		// Never off the horse while it's in the air (landing a leap, say): got off over a gap he'd fall into it
		if (change.move() == LocalPlanner.Move.DISMOUNT && !settledToDismount(body.onGround(), body.getDeltaMovement().y))
			return;
		segmentStart = segmentEnd + 1;
		segmentEnd = segmentEnd(steps, segmentStart);
		if (change.move() == LocalPlanner.Move.DISMOUNT) {
			log.accept("gets down from his horse at " + spot(rider.position()) + " for " + describe(steps.subList(segmentStart, Math.min(steps.size(), segmentStart + 4)), 4));
			onModeChange.run();
			rider.dismissHorse();
			startSegment(rider);
		} else if (change.move() == LocalPlanner.Move.MOUNT) {
			onModeChange.run();
			if (rider.whistleForHorse()) {
				log.accept("whistles for his horse at " + spot(rider.position()));
				waitingForHorse = DeliveryRun.HORSE_WAIT_TICKS;
			} else {
				// No room for the horse here: on foot a while longer, and whistles where there is
				log.accept("no room for his horse at " + spot(rider.position()) + ": on foot a while longer");
				footOnlyUntil = rider.level().getGameTime() + FOOT_ONLY_TICKS;
				clearStretch();
			}
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
				if (cell != null && isShoreTo(cell, at.y))
					shore.add(CoarsePlanner.key(cx + dx, cz + dz));
			}
		return shore;
	}

	/**
	 * Whether a cell is shore to a barge at this height: land, or an ice field standing more than a block above it
	 * (water to the map, but a step the barge can't slide up onto, so a wall to it like any bank).
	 */
	public static boolean isShoreTo(CoarsePlanner.Cell cell, double bargeY) {
		return !cell.water() || cell.ice() && cell.height() > Math.floor(bargeY) + 1 + Crossing.ICE_STEP;
	}

	/** Ashore again: on to the leg after the water. */
	/** How long he's ashore at least before putting out again (a second): a landing and a launch in one tick is a blink across an islet. */
	public static final int ASHORE_TICKS = 20;

	public static boolean ashoreLongEnough(long now, long landedAt) {
		return now - landedAt >= ASHORE_TICKS;
	}

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
		checkPlanAhead(level, here, target, now);
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
		if (waterPath.isEmpty()) {
			noWayAcross(level, here, goal, target);
			return null;
		}
		// A way across that only gets as far as the next block or two (the water the plan expected isn't there, or is
		// closed off) is no way: the old straight swerves take over
		if (!waterPathFinal && Journey.horizontalDistance(here, waterPath.get(waterPath.size() - 1)) <= WATER_LOOKAHEAD / 2) {
			waterPath = List.of();
			noWayAcross(level, here, goal, target);
			return null;
		}
		waterFails = 0;
		boolean tight = WaterPlanner.tight(water(level, nearY), Mth.floor(here.x), Mth.floor(here.z), goal.x, goal.z, radius);
		Vec3 point = steerFor(waterPath, waterPathFinal, goal, here, tight);
		return new Vec3(point.x, here.y, point.z);
	}

	/** This many times running with no way across the water towards where the water leg goes, the water ahead is struck off. */
	public static final int WATER_FAILS_TO_STRIKE = 3;
	private int waterFails;

	/**
	 * No way across the water towards where the water leg goes (a strip of land the cells can't see closes it, say):
	 * found so a few times running, the water ahead is struck off the map like a stretch of land that fails, and the
	 * route redone round it; rowing at it and bumping the bank, with the route redone round that shore, only brought
	 * him straight back.
	 */
	private void noWayAcross(ServerLevel level, Vec3 here, Vec3 goal, Vec3 target) {
		if (coarse != null || plan == null || ++waterFails < WATER_FAILS_TO_STRIKE)
			return;
		waterFails = 0;
		int struck = 0;
		for (long cell : strikeBand(here, goal))
			if (blocked.add(cell))
				struck++;
		if (struck == 0)
			return;
		log.accept("no way across the water from " + spot(here) + " towards " + spot(goal) + ": the water ahead is struck off the map (" + struck
			+ " cells new, " + blocked.size() + " struck this trip) and the route redone round it");
		planStruck = true;
		replan(level, here, target, true);
	}

	/** The nearest point of the way across the water to the barge, to pull back onto when it's caught an edge; null without a way. */
	@Nullable
	public Vec3 waterPathNearest(Vec3 here) {
		if (waterPath.size() < 2)
			return null;
		Nearest nearest = nearestOn(waterPath, here);
		Vec3 point = waterPath.get(nearest.segment()).lerp(waterPath.get(nearest.segment() + 1), nearest.t());
		return new Vec3(point.x, here.y, point.z);
	}

	/**
	 * Where to steer along a way across the water: a few blocks on along it; at the end of the last of it, on at the
	 * landing place itself, so the bow runs up onto the bank rather than the barge drifting to a stop short of it.
	 */
	public static Vec3 steerFor(List<Vec3> path, boolean landing, Vec3 goal, Vec3 here) {
		return steerFor(path, landing, goal, here, false);
	}

	/** As above; in {@code tight} water, for a point only {@link #TIGHT_LOOKAHEAD} along, so the hull follows the bends rather than cut them. */
	public static Vec3 steerFor(List<Vec3> path, boolean landing, Vec3 goal, Vec3 here, boolean tight) {
		if (landing && Journey.horizontalDistance(here, path.get(path.size() - 1)) <= WATER_LOOKAHEAD)
			return goal;
		return along(path, here, tight ? TIGHT_LOOKAHEAD : WATER_LOOKAHEAD);
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

	private static long cellKeyOf(Vec3 point) {
		return CoarsePlanner.key(CoarsePlanner.cellOf(point.x), CoarsePlanner.cellOf(point.z));
	}

	/** The cell a spot is in and the eight round it: never struck off, or the plan could never leave where he stands. */
	static Set<Long> cellsRound(Vec3 here) {
		Set<Long> cells = new HashSet<>();
		int cx = CoarsePlanner.cellOf(here.x), cz = CoarsePlanner.cellOf(here.z);
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				cells.add(CoarsePlanner.key(cx + dx, cz + dz));
		return cells;
	}

	/** The cells to strike off for a way from here to a goal that can't be got along: the band to it, bar the cells round where he stands. */
	static List<Long> strikeBand(Vec3 here, Vec3 goal) {
		Set<Long> keep = cellsRound(here);
		List<Long> band = new ArrayList<>();
		for (long cell : cellsBetween(here, goal))
			if (!keep.contains(cell))
				band.add(cell);
		return band;
	}

	/**
	 * The long-range plan's cells on the straight line from one spot to another, both ends included, and the cells
	 * either side of the line (a cliff or a ridge the way ran into is struck off as a band, not a thread, or the next
	 * plan tries it one cell over).
	 */
	public static List<Long> cellsBetween(Vec3 from, Vec3 to) {
		List<Long> cells = new ArrayList<>();
		double distance = Journey.horizontalDistance(from, to);
		int steps = Math.max(1, (int) Math.ceil(distance / CoarsePlanner.CELL));
		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.lerp(to, (double) i / steps);
			int cx = CoarsePlanner.cellOf(point.x), cz = CoarsePlanner.cellOf(point.z);
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++) {
					long cell = CoarsePlanner.key(cx + dx, cz + dz);
					if (!cells.contains(cell))
						cells.add(cell);
				}
		}
		return cells;
	}

	/**
	 * Stuck with no way at all from where he stands towards the stretch's goal: the cells from here to it are struck
	 * off the map (bar his own) and the route redone round them. False if they were all struck off already (the plan
	 * has nothing new to go on), or there's no stretch goal to strike off.
	 */
	public boolean strikeOffAhead(ServerLevel level, Vec3 here, Vec3 target) {
		if (localGoal == null)
			return false;
		boolean struck = false;
		for (long cell : strikeBand(here, localGoal))
			if (blocked.add(cell))
				struck = true;
		if (!struck) {
			// Those were struck off already and he's still stuck here: the ground round the goal goes too
			Set<Long> keep = cellsRound(here);
			int gx = CoarsePlanner.cellOf(localGoal.x), gz = CoarsePlanner.cellOf(localGoal.z);
			for (int dx = -2; dx <= 2; dx++)
				for (int dz = -2; dz <= 2; dz++) {
					long cell = CoarsePlanner.key(gx + dx, gz + dz);
					if (!keep.contains(cell) && blocked.add(cell))
						struck = true;
				}
		}
		if (!struck)
			return false;
		planStruck = true;
		replan(level, here, target, plannedAfloat);
		return true;
	}

	/** Notes the shore round a spot as leading nowhere (the barge hard against it with nowhere to step off), for the plan to avoid. */
	public void blockShore(ServerLevel level, Vec3 at) {
		for (long cell : shoreAround(at, TerrainSurvey.of(level))) {
			blocked.add(cell);
			noShore.add(cell);
		}
		noLandings.add(at);
	}

	/** Whether a column is in a cell, or by a spot, he's found leads nowhere (a shore he put out from again straight away). */
	public boolean isBlockedShore(int x, int z) {
		return blocked.contains(CoarsePlanner.key(CoarsePlanner.cellOf(x), CoarsePlanner.cellOf(z))) || nearNoLanding(noLandings, x, z);
	}

	/**
	 * Open water in loaded chunks, each column looked at once per plan. The water in a cell of shore that led nowhere
	 * is kept clear of (the water by a scrap of such shore is not: he puts out again from it, and must row away).
	 * Cells struck off the long-range map for the plan's sake are not: the water in them is still rowed through, or
	 * each strike would box the barge in a little more until no way across was left.
	 */
	private WaterPlanner.Water water(ServerLevel level, int nearY) {
		Long2ByteOpenHashMap known = new Long2ByteOpenHashMap();
		return (x, z) -> {
			long key = ChunkPos.asLong(x, z);
			byte open = known.getOrDefault(key, (byte) -1);
			if (open < 0) {
				boolean blockedCell = noShore.contains(CoarsePlanner.key(CoarsePlanner.cellOf(x), CoarsePlanner.cellOf(z)));
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
		Nearest nearest = nearestOn(points, here);
		int segment = nearest.segment();
		double left = ahead;
		Vec3 from = points.get(segment).lerp(points.get(segment + 1), nearest.t());
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
