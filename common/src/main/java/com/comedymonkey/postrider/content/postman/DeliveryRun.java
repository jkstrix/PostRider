package com.comedymonkey.postrider.content.postman;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.advancement.PostEvents;
import com.comedymonkey.postrider.content.lost.LostCause;
import com.comedymonkey.postrider.content.lost.LostParcels;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.postman.route.LevelTerrain;
import com.comedymonkey.postrider.content.postman.route.LocalPlanner;
import com.comedymonkey.postrider.content.postman.route.CoarsePlanner;
import com.comedymonkey.postrider.content.postman.route.Navigator;
import com.comedymonkey.postrider.content.postman.route.TerrainSurvey;
import com.comedymonkey.postrider.post.JourneyNotes;
import com.comedymonkey.postrider.post.Mail;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.post.Notices;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.post.PostOfficeHours;
import com.comedymonkey.postrider.registry.ModBlocks;
import com.comedymonkey.postrider.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * One rider's delivery from start to finish: picking the mail up, calling his horse, riding there (resting at
 * night, freeing himself when stuck, taking the barge over water he can't ride round), walking up to the mailbox and
 * delivering, or leaving a Lost Parcel when it goes wrong.
 */
public class DeliveryRun {
	public enum Stage {
		PICKING_UP,
		/** Looking at his map while he works out the route (before setting off, and when it has to be redone). */
		PLANNING,
		CALLING_HORSE,
		RIDING,
		CAMPING,
		FLEEING,
		BARGING,
		WALKING_TO_BOX,
		DELIVERING,
		DONE
	}

	/** Ticks into the pickup animation when the parcel goes in the satchel, and when it's over. */
	public static final int SATCHEL_FILLS_AT = 32;
	public static final int PICKUP_TICKS = 44;
	/** Ticks into the deliver animation when the mail goes into the box, and when it's over. */
	public static final int MAIL_IN_AT = 26;
	public static final int DELIVER_TICKS = 48;
	/** How long he waits for his horse before setting off on foot. */
	public static final int HORSE_WAIT_TICKS = 80;
	/** How long he tries to walk up to the mailbox before stepping up to it. */
	public static final int WALK_UP_TICKS = 200;
	private static final int AIM_INTERVAL = 40;
	/** The barge's steering is brought up to date this often: it steers for a point only a few blocks ahead. */
	private static final int BARGE_AIM_INTERVAL = 4;
	/** He looks at his map for at least 5 seconds, and sets off with the best route so far after 20. */
	public static final int MAP_MIN_TICKS = 100;
	public static final int MAP_MAX_TICKS = 400;
	/** Once the route is planned, the map stays out this long at most while the ground along it is read and the plan checked (a minute). */
	public static final int MAP_CHECK_TICKS = 1200;
	/** How many times the plan is redone against the real ground before he sets off regardless. */
	public static final int MAP_CHECKS = 2;
	private int mapChecks;
	private boolean checkingMap;
	/** After the pickup he walks this far out from the front of the mailbox to look at his map, for at most this long. */
	public static final double MAP_STAND_OFF = 4.0;
	public static final int STEP_AWAY_TICKS = 60;
	/** He stops to look at his map again at most this often; more re-planning than that goes on as he rides. */
	public static final int MAP_STOP_INTERVAL = 1200;
	/** Run aground this long off the end of his planned water leg, he lands where he is. */
	public static final int AGROUND_TICKS = 60;
	/** On foot he runs as fast as a sprinting player; he only walks the last few steps to the mailbox. */
	public static final double RUN_BLOCKS_PER_SECOND = 5.6;
	public static final double RUN_SPEED = PostmanEntity.moveSpeedFor(RUN_BLOCKS_PER_SECOND, PostmanEntity.BASE_SPEED * PostmanEntity.SPRINT_BOOST);
	/** Where the way goes down he walks (a run skips a step and the fall hurts): a player's walking pace. */
	public static final double WALK_BLOCKS_PER_SECOND = 4.3;
	public static final double WALK_SPEED = PostmanEntity.moveSpeedFor(WALK_BLOCKS_PER_SECOND, PostmanEntity.BASE_SPEED);

	private final PendingDelivery delivery;
	@Nullable
	private final GlobalPos destination;
	private Stage stage = Stage.PICKING_UP;
	private int stageTicks;
	@Nullable
	private ChunkPos loadedChunk;
	private final Journey.Progress progress = new Journey.Progress();
	/** The plan his progress is measured along: redone, the measure starts afresh. */
	@Nullable
	private CoarsePlanner.Plan progressPlan;
	private int aimCooldown;
	/** His route: worked out afresh after a restart, so not saved. */
	private final Navigator navigator = new Navigator();
	private boolean routeStarted;
	/** What he does once he's looked at his map. */
	private Stage afterPlanning = Stage.CALLING_HORSE;
	private long lastMapStop = Long.MIN_VALUE / 2;
	/** Walking a few steps from the mailbox before the map comes out, and where to. */
	private boolean steppingAway;
	@Nullable
	private Vec3 mapSpot;
	/** The route was already being worked out when the map came out (from the end of the pickup). */
	private boolean alreadyPlanning;
	private int aground;
	/** What the mail has been through, noted on it when it's delivered. */
	private boolean crossedWater;
	/** His horse was killed on this trip: he's on foot for the rest of it. */
	private boolean horseLost;
	private int nightsOut;
	/** How many times this trip he's been teleported free when stuck (not saved; for the in-world tests). */
	private int jumpsFree;

	public DeliveryRun(PendingDelivery delivery, @Nullable GlobalPos destination) {
		this.delivery = delivery;
		this.destination = destination;
	}

	/** Plans a run to wherever the address is now; if it no longer exists, the run will end undeliverable. */
	public static DeliveryRun plan(ServerLevel level, PendingDelivery delivery) {
		MailboxDirectory.Entry entry = MailboxDirectory.get(level.getServer()).find(delivery.toName());
		return new DeliveryRun(delivery, entry != null ? entry.pos() : null);
	}

	/** A run picked up again by a rider brought back mid-delivery: he has the mail, so he calls his horse and goes. */
	public static DeliveryRun resume(ServerLevel level, PendingDelivery delivery) {
		DeliveryRun run = plan(level, delivery);
		run.stage = Stage.CALLING_HORSE;
		return run;
	}

	/** Where his campfire is, if he has one going. */
	@Nullable
	public BlockPos campfire() {
		return campfire;
	}

	/** How many times this trip he's been teleported free when stuck. */
	public int jumpsFree() {
		return jumpsFree;
	}

	/** What the route finding has done this trip (for the in-world trials). */
	public Navigator.Stats routeStats() {
		return navigator.stats();
	}

	/** A jump free: where he was stuck and why, where he went, and whether a way through existed from there (then it wasn't needed). */
	public record Jump(Stage stage, String why, BlockPos from, BlockPos to, boolean wayExisted, String diagnosis) {}

	private final List<Jump> jumps = new ArrayList<>();

	/** The jumps free this trip, in order (not saved; for the in-world trials). */
	public List<Jump> jumps() {
		return List.copyOf(jumps);
	}

	/** What the mail has been through so far. */
	public JourneyNotes notes() {
		return new JourneyNotes(crossedWater, nightsOut);
	}

	public PendingDelivery delivery() {
		return delivery;
	}

	public Stage stage() {
		return stage;
	}

	public boolean isOver() {
		return stage == Stage.DONE;
	}

	private void enter(Stage next) {
		stage = next;
		stageTicks = 0;
	}

	// ---------------------------------------------------------------- where things are

	/**
	 * How far from the middle of the mailbox's post he stands, so his hands meet the door: the pickup animation
	 * reaches further than the deliver one.
	 */
	public static final double PICKUP_STAND_OFF = 1.35;
	public static final double DELIVER_STAND_OFF = 1.1;

	/** Where to stand at a mailbox: square in front of its door, this far from the post, on the mailbox's own level. */
	public static Vec3 standingSpot(BlockPos mailbox, Direction facing, double standOff) {
		return Vec3.atBottomCenterOf(mailbox).add(facing.getStepX() * standOff, 0, facing.getStepZ() * standOff);
	}

	public static Vec3 standingSpot(ServerLevel level, BlockPos mailbox, double standOff) {
		return standingSpot(mailbox, facingOf(level, mailbox), standOff);
	}

	/** Which way a mailbox's door faces (north if the mailbox has gone). */
	public static Direction facingOf(ServerLevel level, BlockPos mailbox) {
		BlockState state = level.getBlockState(mailbox);
		return state.getBlock() instanceof MailboxBlock ? state.getValue(MailboxBlock.FACING) : Direction.NORTH;
	}

	/** The way to face to look straight at a point, as a Minecraft yaw. */
	public static float yawToward(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90;
	}

	/** Turns him, body and head, square to the mailbox's door, and keeps him from wandering off it. */
	private static void faceMailbox(PostmanEntity rider, BlockPos mailbox) {
		float yaw = yawToward(rider.position(), Vec3.atBottomCenterOf(mailbox));
		rider.getNavigation().stop();
		rider.setYRot(yaw);
		rider.setYBodyRot(yaw);
		rider.setYHeadRot(yaw);
		rider.getLookControl().setLookAt(Vec3.atCenterOf(mailbox.above()));
	}

	@Nullable
	private Vec3 destinationSpot(ServerLevel level) {
		if (destination == null)
			return null;
		level.getChunkAt(destination.pos());
		return standingSpot(level, destination.pos(), DELIVER_STAND_OFF);
	}

	// ---------------------------------------------------------------- each tick

	public void tick(PostmanEntity rider, ServerLevel level) {
		if (stage == Stage.DONE)
			return;
		navigator.setOwner(rider.getUUID());
		keepChunksLoaded(rider, level);
		logChanges(rider);
		navigator.logTo(message -> logRoute(rider, message));
		navigator.onModeChange(() -> traceUntil = level.getGameTime() + 80);
		if (TRACE && (TRACE_ALL || level.getGameTime() < traceUntil) && level.getGameTime() % 5 == 0)
			logRoute(rider, trace(rider));
		stageTicks++;
		// No address to go to (it went while a vanished rider was being brought back, say): undeliverable, right here
		if (destination == null && stage != Stage.PICKING_UP) {
			fail(rider, level, LostCause.UNDELIVERABLE, noMailbox(rider), null, rider.blockPosition());
			return;
		}
		switch (stage) {
			case PICKING_UP -> pickUp(rider, level);
			case PLANNING -> plan(rider, level);
			case CALLING_HORSE -> callHorse(rider, level);
			case RIDING -> ride(rider, level);
			case CAMPING -> camp(rider, level);
			case FLEEING -> flee(rider, level);
			case BARGING -> barge(rider, level);
			case WALKING_TO_BOX -> walkUp(rider, level);
			case DELIVERING -> deliver(rider, level);
			default -> {}
		}
	}

	@Nullable
	private Stage loggedStage;
	@Nullable
	private CampStep loggedStep;

	/** Notes in the log each time he moves on to the next part of the job, for working out what went wrong. */
	private void logChanges(PostmanEntity rider) {
		CampStep step = stage == Stage.CAMPING ? campStep : null;
		if (stage != loggedStage || step != loggedStep) {
			PostRider.LOGGER.info("Rider {} with mail for {}: {}{}", rider.getName().getString(), delivery.toName(), stage,
				step != null ? " (" + step + ")" : "");
			loggedStage = stage;
			loggedStep = step;
		}
	}

	/** Notes a routing decision in the log (rare: a new route, a way that wouldn't plan, a barge, a jump free). */
	private void logRoute(PostmanEntity rider, String message) {
		String toGo = destination != null ? ", " + Math.round(Journey.horizontalDistance(rider.position(), Vec3.atCenterOf(destination.pos()))) + " blocks to go" : "";
		PostRider.LOGGER.info("Rider {} with mail for {}{}: {}", rider.getName().getString(), delivery.toName(), toGo, message);
	}

	/** What he's up to, for /postrider status. */
	public String describe(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destination != null ? Vec3.atCenterOf(destination.pos()) : null;
		String distance = target != null ? (int) Journey.horizontalDistance(rider.position(), target) + " blocks" : "unknown";
		return rider.getName().getString() + ": " + stage + (stage == Stage.CAMPING ? " (" + campStep + ")" : "")
			+ " for " + stageTicks + " ticks, activity " + rider.getActivity() + (rider.isInTent() ? ", in the tent" : "")
			+ ", " + (int) rider.getHealth() + " health and " + rider.getPotions() + " potions"
			+ ", " + distance + " to " + delivery.toName() + ", time of day " + PostOfficeHours.timeOfDay(level.getOverworldClockTime());
	}

	private void keepChunksLoaded(PostmanEntity rider, ServerLevel level) {
		ChunkPos now = rider.chunkPosition();
		if (!now.equals(loadedChunk)) {
			RiderChunks.move(level, rider.getUUID(), loadedChunk, now);
			loadedChunk = now;
		}
	}

	private String noMailbox(PostmanEntity rider) {
		return rider.getName().getString() + " found that " + delivery.toName() + " no longer has a mailbox.";
	}

	private void pickUp(PostmanEntity rider, ServerLevel level) {
		faceMailbox(rider, delivery.from().pos());
		if (stageTicks == 1)
			rider.playGesture(PostmanEntity.Gesture.PICKUP);
		if (stageTicks == SATCHEL_FILLS_AT)
			rider.setCarrying(true);
		if (stageTicks >= PICKUP_TICKS) {
			if (destination == null) {
				fail(rider, level, LostCause.UNDELIVERABLE, noMailbox(rider), null, delivery.from().pos());
				return;
			}
			// The route is worked out from now; he steps away from the box and then gets his map out
			Vec3 target = destinationSpot(level);
			if (target != null) {
				navigator.replan(level, rider.position(), target);
				routeStarted = true;
				alreadyPlanning = true;
			}
			mapSpot = mapSpot(level, delivery.from().pos());
			steppingAway = true;
			afterPlanning = Stage.CALLING_HORSE;
			enter(Stage.PLANNING);
		}
	}

	/**
	 * Where he stands to look at his map after the pickup: a few steps out from the front of the mailbox, or the
	 * nearest spot about that far away he can stand on; null to stay where he is.
	 */
	@Nullable
	private static Vec3 mapSpot(ServerLevel level, BlockPos mailbox) {
		Direction facing = facingOf(level, mailbox);
		LevelTerrain terrain = new LevelTerrain(level);
		Vec3 box = Vec3.atBottomCenterOf(mailbox);
		float ahead = facing.toYRot();
		for (double standOff : new double[] {MAP_STAND_OFF, 3.5, 3.0})
			for (int turn : new int[] {0, 30, -30, 60, -60, 90, -90}) {
				Vec3 way = Vec3.directionFromRotation(0, ahead + turn).scale(standOff);
				int x = Mth.floor(box.x + way.x), z = Mth.floor(box.z + way.z);
				for (int dy : new int[] {0, 1, -1}) {
					int y = mailbox.getY() + dy;
					if (LocalPlanner.stands(terrain, false, x, y, z))
						return new Vec3(x + 0.5, y, z + 0.5);
				}
			}
		return null;
	}

	/**
	 * He gets his map out and works out the route: at least {@link #MAP_MIN_TICKS}, and once it's done (or after
	 * {@link #MAP_MAX_TICKS}, going with the best so far) he puts it away and gets going.
	 */
	private void plan(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destinationSpot(level);
		if (target == null)
			return;
		progress.pause(level.getGameTime());
		navigator.tickPlanning();
		if (steppingAway) {
			// A few steps out from the mailbox, at a walk; the planning time counts while he goes
			if (mapSpot == null || Journey.horizontalDistance(rider.position(), mapSpot) < 0.6 || stageTicks >= STEP_AWAY_TICKS) {
				steppingAway = false;
				stageTicks = 0;
			} else if (stageTicks % 20 == 1) {
				rider.setSprinting(false);
				rider.getNavigation().moveTo(mapSpot.x, mapSpot.y, mapSpot.z, 1.0);
			}
			return;
		}
		rider.getNavigation().stop();
		if (stageTicks == 1) {
			rider.setSprinting(false);
			checkingMap = false;
			if (!alreadyPlanning)
				navigator.replan(level, rider.position(), target);
			alreadyPlanning = false;
			routeStarted = true;
			// On foot, he turns the way his route goes before he looks it over
			if (rider.getVehicle() == null)
				face(rider, yawToward(rider.position(), navigator.heading(rider.position(), target)));
			showMap(rider, true);
		}
		// Planned: the ground along the whole stage is held (loaded, generated if need be) and read, and the plan checked
		// against it; one that doesn't fit is redone, a couple of times at most, before he sets off
		if (!checkingMap && navigator.isPlanned() && navigator.plan() != null) {
			checkingMap = true;
			mapChecks = 0;
			navigator.holdStageAhead(level, rider.position());
			logRoute(rider, "checking the route against the ground: " + navigator.chunksAhead() + " chunks along it to read");
		}
		if (checkingMap && navigator.isPlanned() && stageTicks < MAP_MAX_TICKS + MAP_CHECK_TICKS) {
			if (!navigator.aheadLoaded(level))
				return;
			String misfit = navigator.planMisfit(level);
			if (misfit != null && mapChecks < MAP_CHECKS) {
				mapChecks++;
				logRoute(rider, "the ground along the route isn't as the map had it (" + misfit + "): the route is redone (" + mapChecks + ")");
				navigator.replan(level, rider.position(), target);
				navigator.holdStageAhead(level, rider.position());
				return;
			}
		}
		if (stageTicks >= MAP_MIN_TICKS && (navigator.isPlanned() && (!checkingMap || navigator.aheadLoaded(level) || stageTicks >= MAP_MAX_TICKS + MAP_CHECK_TICKS)
				|| stageTicks >= MAP_MAX_TICKS + MAP_CHECK_TICKS)) {
			if (checkingMap) {
				String misfit = navigator.planMisfit(level);
				logRoute(rider, misfit == null ? "the route checks out against the ground" + (mapChecks > 0 ? " (after " + mapChecks + " redoing)" : "")
					: "the route still doesn't quite match the ground after " + mapChecks + " redoing (" + misfit + "): he goes anyway");
			}
			checkingMap = false;
			navigator.goWithBestSoFar();
			showMap(rider, false);
			enter(afterPlanning);
		}
	}

	/** Up a ladder hand over hand, or drifting in a bubble column, while he's in one; back to himself after. */
	static void showColumn(PostmanEntity rider, @Nullable PostmanEntity.Activity inColumn) {
		PostmanEntity.Activity now = rider.getActivity();
		if (inColumn != null) {
			if (now != inColumn)
				rider.setActivity(inColumn);
		} else if (now == PostmanEntity.Activity.CLIMB || now == PostmanEntity.Activity.DRIFT) {
			rider.setActivity(PostmanEntity.Activity.NONE);
		}
	}

	/** Out comes his map (held open in both hands, the study_map animation), or away it goes. */
	private static void showMap(PostmanEntity rider, boolean out) {
		if (out)
			rider.setActivity(PostmanEntity.Activity.STUDY_MAP);
		else if (rider.getActivity() == PostmanEntity.Activity.STUDY_MAP)
			rider.setActivity(PostmanEntity.Activity.NONE);
	}

	/**
	 * The route has to be worked out again: he stops and looks at his map, then carries on as {@code after}. If he
	 * only looked at it a little while ago, he re-plans as he goes instead.
	 */
	private void stopToReplan(PostmanEntity rider, ServerLevel level, @Nullable Vec3 target, Stage after) {
		long now = level.getGameTime();
		if (now - lastMapStop < MAP_STOP_INTERVAL) {
			if (target != null)
				navigator.replan(level, rider.position(), target);
			enter(after);
			return;
		}
		lastMapStop = now;
		afterPlanning = after;
		steppingAway = false;
		alreadyPlanning = false;
		enter(Stage.PLANNING);
	}

	private void callHorse(PostmanEntity rider, ServerLevel level) {
		if (rider.getHorse() != null) {
			enter(Stage.RIDING);
			return;
		}
		// The barge is just ahead (by his route, or plainly on the straight line): he walks down to it, and calls his
		// horse on the far side
		Vec3 target = destinationSpot(level);
		int nearY = rider.getBlockY();
		if (navigator.waterWithin(rider.position(), Navigator.FOOT_TO_WATER)
				|| target != null && Crossing.waterAhead(rider.position(), navigator.heading(rider.position(), target), Navigator.FOOT_TO_WATER,
					(x, z) -> Crossing.surfaceIn(level, x, z, nearY, Crossing.LAUNCH_DROP))) {
			enter(Stage.RIDING);
			return;
		}
		if (stageTicks == 1) {
			// His horse is gone for the trip: on foot it is
			if (!rider.mayWhistle()) {
				enter(Stage.RIDING);
				return;
			}
			// No room for a horse here (a low roof, a tight spot): he sets off on foot, and whistles at the first place
			// along the way with room for it (the plan gets him back on at the first such spot)
			EntityType<PostmanHorse> type = ModEntities.POSTMAN_HORSE.get();
			if (HorseSpots.find(rider.position(), rider.getYRot(), s -> HorseSpots.fits(level, type, s)) == null) {
				logRoute(rider, "no room for his horse at " + Navigator.spot(rider.position()) + ": sets off on foot");
				enter(Stage.RIDING);
				return;
			}
			rider.whistleForHorse();
		}
		// The horse didn't come after all: he sets off on foot and tries again later
		if (stageTicks >= HORSE_WAIT_TICKS)
			enter(Stage.RIDING);
	}

	private void ride(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destinationSpot(level);
		if (target == null)
			return;
		if (!PostOfficeHours.isOpen(level.getOverworldClockTime()) && !rider.isInWater()) {
			enter(Stage.CAMPING);
			return;
		}
		double distance = Journey.horizontalDistance(rider.position(), target);
		// Not while a ladder or a bubble column has him (he'd be left in it, over the magma at a whirlpool's foot)
		if (Journey.closeEnoughToWalkUp(rider.position(), target) && navigator.columnActivity() == null) {
			rider.dismissHorse();
			rider.setSprinting(false);
			enter(Stage.WALKING_TO_BOX);
			return;
		}
		// Progress is judged with height, so a ramp or a ladder up to the box counts
		if (checkStuck(rider, level, navigator.distanceToGo(rider.position(), target), target))
			return;
		// Back on the road after a restart: the route is worked out again as he rides
		if (!routeStarted) {
			navigator.replan(level, rider.position(), target);
			routeStarted = true;
		}
		if (rider.getHorse() != null)
			jumpGaps(rider, rider.getHorse());
		Navigator.Action action = navigator.tick(rider, level, target);
		showColumn(rider, navigator.columnActivity());
		switch (action) {
			case LAUNCH -> {
				Vec3 toward = navigator.launchAim();
				if (toward != null && launchBarge(rider, level, toward))
					return;
			}
			case REPLAN -> {
				stopToReplan(rider, level, target, Stage.RIDING);
				return;
			}
			default -> {}
		}
		rider.mannerisms().tick(rider, level, distance, navigator.isDetouring());
	}

	/** Whether an aim takes him off the straight line to the destination, round water or a hazard. */
	public static boolean isDetour(Vec3 from, Vec3 aim, Vec3 target) {
		Vec3 straight = Journey.nextAim(from, target);
		return Journey.horizontalDistance(aim, straight) > DETOUR_SLACK;
	}

	private static List<Vec3> safeOf(ServerLevel level, List<Vec3> aims) {
		List<Vec3> safe = new ArrayList<>();
		for (Vec3 aim : aims) {
			Vec3 ground = surface(level, aim);
			if (!Hazards.isDangerous(level, BlockPos.containing(ground)))
				safe.add(ground);
		}
		return safe;
	}

	private static Vec3 surface(ServerLevel level, Vec3 aim) {
		BlockPos column = BlockPos.containing(aim);
		return new Vec3(aim.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), aim.z);
	}

	// ---------------------------------------------------------------- the barge

	/** Calls up the barge on the water ahead (towards {@code target}), puts his horse and himself aboard, and pushes off. */
	private boolean launchBarge(PostmanEntity rider, ServerLevel level, Vec3 target) {
		int nearY = rider.getBlockY();
		Vec3 spot = Crossing.launchSpot(rider.position(), target, (x, z) -> Crossing.surfaceIn(level, x, z, nearY, Crossing.LAUNCH_DROP));
		return spot != null && launchBargeAt(rider, level, spot, target);
	}

	/** Calls up the barge on this open water and sets off towards {@code target}. */
	private boolean launchBargeAt(PostmanEntity rider, ServerLevel level, Vec3 spot, Vec3 target) {
		return launchBargeAt(rider, level, spot, target, false);
	}

	/** How long, after putting out from a shore that led nowhere, the barge rows straight away from it before steering for the box. */
	public static final int PUSH_OFF_TICKS_AWAY = 60;
	public static final double PUSH_OFF_DISTANCE = 12;

	/** As above; {@code pushOff} first rows straight out from the shore for a few seconds (out of a cove, say). */
	private boolean launchBargeAt(PostmanEntity rider, ServerLevel level, Vec3 spot, Vec3 target, boolean pushOff) {
		BargeEntity barge = ModEntities.BARGE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
		if (barge == null)
			return false;
		Vec3 shore = rider.position();
		barge.snapTo(spot.x, Crossing.restHeight(level, Mth.floor(spot.x), Mth.floor(spot.z), spot.y), spot.z, yawToward(spot, target), 0);
		level.addFreshEntity(barge);
		PostmanEffects.puff(level, barge);
		PostmanHorse horse = rider.getHorse();
		rider.stopRiding();
		rider.getNavigation().stop();
		rider.setSprinting(false);
		if (horse == null) {
			// On foot (he got down to reach the water's edge, or was walking anyway): the horse crosses with him all
			// the same, appearing aboard (unless it's been killed this trip); one he'd whistled for and that hasn't
			// come yet isn't wanted now
			rider.dismissHorse();
			if (rider.mayWhistle())
				horse = rider.summonHorse(level, barge.position());
		} else {
			PostmanEffects.puff(level, horse);
		}
		if (horse != null)
			horse.startRiding(barge, true, true);
		rider.startRiding(barge, true, true);
		rider.setActivity(PostmanEntity.Activity.ROW);
		crossedWater = true;
		aimCooldown = 0;
		aground = 0;
		Vec3 destination = destinationSpot(level);
		logRoute(rider, "launching the barge at " + Navigator.spot(spot) + " towards " + Navigator.spot(target));
		navigator.launched(level, spot, destination != null ? destination : target);
		if (pushOff) {
			Vec3 out = Journey.horizontalDistance(shore, spot) < 1e-6 ? spot : spot.add(spot.subtract(shore).multiply(1, 0, 1).normalize().scale(PUSH_OFF_DISTANCE));
			barge.snapTo(spot.x, barge.getY(), spot.z, yawToward(spot, out), 0);
			barge.steer(out);
			aimCooldown = PUSH_OFF_TICKS_AWAY;
		}
		enter(Stage.BARGING);
		return true;
	}

	/** Rowing across along his planned water leg (day or night), round ocean monuments and drowned, and ashore at its end. */
	private void barge(PostmanEntity rider, ServerLevel level) {
		if (!(rider.getVehicle() instanceof BargeEntity barge)) {
			// The barge is gone from under him somehow: carry on as best he can
			rider.setActivity(PostmanEntity.Activity.NONE);
			enter(Stage.RIDING);
			return;
		}
		Vec3 target = destinationSpot(level);
		if (target == null)
			return;
		double distance = Journey.horizontalDistance(rider.position(), target);
		// Pushed into something all the same (a bank that gave, a block someone placed): a block back, before it crushes him
		if (rider.isInWall()) {
			logRoute(rider, "the barge backs off a block at " + Navigator.spot(barge.position()) + ": he was being crushed in its bow");
			barge.backOff();
		}
		if (checkStuck(rider, level, navigator.distanceToGo(rider.position(), target), target))
			return;
		if (ashore(level, barge, distance)) {
			if (land(rider, level, barge, target))
				return;
			// Hard against a shore for a while with nowhere to step off (a cliff, a wall): that shore is no landing, and
			// the way across the water is worked out again round it
			if (aground >= AGROUND_TICKS) {
				logRoute(rider, "hard against the shore at " + Navigator.spot(barge.bow()) + " with nowhere to step off: the route is redone round it");
				navigator.blockShore(level, barge.bow());
				navigator.replan(level, barge.position(), target, true);
				aground = 0;
			}
		}
		if (--aimCooldown <= 0) {
			aimCooldown = BARGE_AIM_INTERVAL;
			int nearY = barge.waterLevelY();
			List<Vec3> drowned = level.getEntitiesOfClass(Drowned.class, barge.getBoundingBox().inflate(Journey.stretch() + Crossing.DROWNED_BERTH))
				.stream().map(Entity::position).toList();
			// Along its way across the water, round the bends; with none to be found, the old straight swerves
			Vec3 aim = navigator.bargeAim(level, barge.position(), nearY, target, drowned, level.getGameTime());
			// Caught on an edge with the way across going on, or on a corner by its side: it pulls back onto the line
			// of the way first, rather than push at what it's caught on (or back off and come straight back onto it)
			if (aim != null && (barge.isBlockedAhead() || caughtOnACorner(barge.horizontalCollision, barge.isBlockedAhead()))) {
				Vec3 line = navigator.waterPathNearest(barge.position());
				if (line != null && Journey.horizontalDistance(line, barge.position()) > OFF_THE_LINE)
					aim = line;
			}
			// The hull's side caught on a corner (bumping, with open water ahead of the bow): a block back off it every
			// second or so, and on round it, rather than push at the corner
			if (aim != null && caughtOnACorner(barge.horizontalCollision, barge.isBlockedAhead()) && level.getGameTime() - lastBackOff >= CORNER_BACKOFF_TICKS) {
				lastBackOff = level.getGameTime();
				logRoute(rider, "the barge is caught on a corner at " + Navigator.spot(barge.position()) + ": it backs off a block");
				barge.backOff();
			}
			if (aim == null)
				aim = Crossing.waterAim(barge.position(), target,
					a -> !Hazards.isDangerous(level, BlockPos.containing(surface(level, a))),
					a -> !Double.isNaN(Crossing.surfaceIn(level, Mth.floor(a.x), Mth.floor(a.z), nearY)),
					a -> Crossing.nearAny(a, drowned, Crossing.DROWNED_BERTH) || Crossing.nearMonument(level, a));
			barge.steer(aim != null ? aim : Journey.nextAim(barge.position(), target));
		}
	}

	/**
	 * The height he'd stand at in a column to step ashore there, or unusable: ice at the barge's level isn't stepped
	 * onto (the barge slides over it), unless any shore will do; ice standing higher than the barge can slide onto
	 * is a bank like any other, and he steps onto it.
	 */
	static int floorAshore(ServerLevel level, int x, int z, int nearY, boolean anyShore) {
		int floor = CampSites.floorIn(level, x, z, nearY);
		if (floor == CampSites.UNUSABLE || anyShore)
			return floor;
		return Crossing.isIce(level.getBlockState(new BlockPos(x, floor - 1, z))) && isSlidableIce(floor, nearY) ? CampSites.UNUSABLE : floor;
	}

	/** Whether ice he'd stand on at {@code floor} is within the barge's reach (its top no more than a block over the barge's own level), so the barge slides or climbs onto it instead. */
	public static boolean isSlidableIce(int floor, int nearY) {
		return floor <= nearY + 1 + Crossing.ICE_STEP;
	}

	/** How long after launching before the bank behind it counts as reaching shore. */
	private static final int PUSH_OFF_TICKS = 40;
	/** How often, at most, the barge backs off a corner its hull is caught on. */
	public static final int CORNER_BACKOFF_TICKS = 20;
	private long lastBackOff = Long.MIN_VALUE / 2;

	/** Whether the hull is caught on a corner: bumping against something, with nothing but open water ahead of the bow. */
	public static boolean caughtOnACorner(boolean bumping, boolean blockedAhead) {
		return bumping && !blockedAhead;
	}
	/** Why the barge counted as ashore the last time it did, for the log. */
	private String ashoreWhy = "";
	/** This far off the line of its way across, a barge caught on an edge steers back onto the line before going on. */
	public static final double OFF_THE_LINE = 0.4;

	/**
	 * Whether he can step ashore: the bow is up against land near the end of the water leg (he rows on round anything
	 * before that, unless he's run hard aground), or he's as good as at the mailbox.
	 */
	private boolean ashore(ServerLevel level, BargeEntity barge, double distance) {
		if (distance <= Journey.DISMOUNT_DISTANCE + BargeEntity.BOW_REACH)
			return true;
		if (stageTicks < PUSH_OFF_TICKS)
			return false;
		boolean against = barge.horizontalCollision || barge.isBlockedAhead();
		aground = against ? aground + 1 : 0;
		Vec3 bow = barge.bow();
		boolean land = against || Double.isNaN(Crossing.surfaceIn(level, Mth.floor(bow.x), Mth.floor(bow.z), barge.waterLevelY()));
		boolean nearLanding = navigator.nearLanding(barge.position()), endOfWater = navigator.atEndOfWater(barge.position());
		ashoreWhy = (barge.horizontalCollision ? "bumped" : barge.isBlockedAhead() ? "blocked ahead" : "land at the bow")
			+ (nearLanding ? ", near the landing" : "") + (endOfWater ? ", at the end of the water" : "") + (aground >= AGROUND_TICKS ? ", hard aground" : "")
			+ ", hull at " + String.format("%.2f", barge.getY()) + (barge.isOnIce() ? " on ice" : "");
		return landsHere(land, nearLanding, endOfWater, aground);
	}

	/**
	 * Whether he steps ashore: with land at the bow, once he's near where his plan lands, or at the end of his way
	 * across the water, or after running hard aground for a while.
	 */
	public static boolean landsHere(boolean landAtBow, boolean nearLanding, boolean endOfWater, int aground) {
		return landAtBow && (nearLanding || endOfWater || aground >= AGROUND_TICKS);
	}

	/** On foot this close to the mailbox, he walks the rest rather than calling his horse for a few blocks. */
	public static boolean walksTheRest(double toMailbox) {
		return toMailbox <= Navigator.FOOT_TO_WATER;
	}

	/** Steps ashore by the bow: his horse appears on the bank, he climbs back on, and the barge vanishes. */
	private boolean land(PostmanEntity rider, ServerLevel level, BargeEntity barge, Vec3 target) {
		return land(rider, level, barge, target, Crossing.LANDING_REACH);
	}

	/** Stuck against a bank with nowhere to step off close by, he looks this far for somewhere before being jumped. */
	public static final int STUCK_LANDING_REACH = 12;
	/** A shore he can reach no more blocks of than this leads nowhere: back into the barge. */
	public static final int TINY_SHORE = 64;

	/** Whether a shore, searched from its landing place, is a scrap with no way on: hardly any of it to get about on, and the way not found. */
	public static boolean isScrapOfShore(boolean wayFound, int blocksReached) {
		return !wayFound && blocksReached <= TINY_SHORE;
	}

	/**
	 * Whether he's cut off where he stands: the search from him ran out of ground (not of budget) without finding
	 * the way, and the map has already been redone round the way ahead at least once without that helping.
	 */
	public static boolean isCutOff(boolean wayFound, LocalPlanner.Status status, int strikesOff) {
		return !wayFound && status != LocalPlanner.Status.RUNNING && strikesOff > 0;
	}

	private boolean land(PostmanEntity rider, ServerLevel level, BargeEntity barge, Vec3 target, int reach) {
		return land(rider, level, barge, target, reach, false);
	}

	/** As above; {@code anyShore} when he's stuck afloat, so a shore struck off as leading nowhere will do too. */
	private boolean land(PostmanEntity rider, ServerLevel level, BargeEntity barge, Vec3 target, int reach, boolean anyShore) {
		PostmanHorse horse = null;
		for (Entity passenger : barge.getPassengers())
			if (passenger instanceof PostmanHorse aboard)
				horse = aboard;
		EntityType<?> type = horse != null ? horse.getType() : null;
		// Ice is no landing place while the barge can slide on over it (only when he's stuck afloat and any shore will do)
		BlockPos spot = Crossing.landingSpot(barge.bow(), target, (x, z) -> !anyShore && navigator.isBlockedShore(x, z) ? CampSites.UNUSABLE : floorAshore(level, x, z, barge.waterLevelY(), anyShore), reach,
			(x, z, y) -> passable(level, new BlockPos(x, y, z)) && passable(level, new BlockPos(x, y + 1, z)),
			type == null ? Crossing.Roomy.EVERYWHERE : (x, y, z) -> HorseSpots.find(new Vec3(x + 0.5, y, z + 0.5), yawToward(new Vec3(x + 0.5, y, z + 0.5), target),
				s -> HorseSpots.fits(level, type, s)) != null);
		if (spot == null)
			return false;
		Vec3 ground = Vec3.atBottomCenterOf(spot);
		// A scrap of shore (a sandbar, an islet) isn't stepped onto at all: looked at from the barge, if he could get
		// about on hardly any of it and no way on is found, it's noted as leading nowhere and he rows on (unless
		// he's stuck afloat, when any shore will do)
		if (!anyShore && Journey.horizontalDistance(ground, target) > Journey.DISMOUNT_DISTANCE) {
			Navigator.Probe way = navigator.probeShore(level, spot.getX(), spot.getY(), spot.getZ(), target, JUMP_PROBE_BUDGET);
			if (isScrapOfShore(way.found(), way.expanded())) {
				logRoute(rider, "the shore at " + Navigator.spot(ground) + " is a scrap with no way on (" + way.expanded() + " blocks of it): he rows on");
				navigator.blockShore(level, ground);
				navigator.replan(level, barge.position(), target, true);
				aimCooldown = 0;
				return false;
			}
		}
		float yaw = yawToward(ground, target);
		rider.stopRiding();
		rider.setActivity(PostmanEntity.Activity.NONE);
		rider.snapTo(ground.x, ground.y, ground.z, yaw, 0);
		keepChunksLoaded(rider, level);
		String horseWhere = "";
		if (horse != null) {
			Vec3 room = HorseSpots.find(ground, yaw, s -> HorseSpots.fits(level, type, s));
			horse.stopRiding();
			if (room != null) {
				horse.snapTo(room.x, room.y, room.z, yaw, 0);
				PostmanEffects.puff(level, horse);
				rider.startRiding(horse, true, true);
				horseWhere = ", his horse put down at " + Navigator.spot(room);
			} else {
				// No room for it ashore here (a ledge, a niche under a cliff): put down it would be wedged in the rock. It
				// goes, and he whistles for it at the first place along the way with room
				PostmanEffects.puff(level, horse);
				horse.discard();
				horseWhere = ", no room ashore for his horse: it goes, and comes back where there's room";
			}
		}
		barge.vanish(level);
		aimCooldown = 0;
		logRoute(rider, "stepped ashore at " + Navigator.spot(ground) + horseWhere + " (" + ashoreWhy + ")");
		traceUntil = level.getGameTime() + 80;
		navigator.landed(ground, level.getGameTime());
		// Crossed on foot (he walked down to the water): he calls his horse now he's over, unless the box is close
		if (horse == null && !walksTheRest(Journey.horizontalDistance(ground, target)))
			rider.whistleForHorse();
		enter(Stage.RIDING);
		return true;
	}

	/** Whether a body passes through this block (water included: it's the water's edge he steps over). */
	private static boolean passable(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	// ---------------------------------------------------------------- jumping gaps

	/** He takes off within this far of the edge his plan leaps from. */
	public static final double TAKE_OFF_REACH = 1.2;

	/**
	 * Where his plan leaps a gap, the horse jumps it: from the take-off, straight at the landing. Only there: a hole
	 * beside the way, or one he's turned towards, is never jumped.
	 */
	private void jumpGaps(PostmanEntity rider, PostmanHorse horse) {
		Navigator.Leap leap = navigator.leapAhead(rider);
		if (leap == null || !horse.onGround() || !readyToLeap(horse.position(), leap.takeOff(), leap.landing()))
			return;
		Vec3 across = leap.landing().subtract(leap.takeOff());
		double distance = Math.hypot(across.x, across.z);
		horse.getJumpControl().jump();
		double push = leapPush(distance);
		horse.setDeltaMovement(across.x / distance * push, horse.getDeltaMovement().y, across.z / distance * push);
	}

	/** Whether he's at the take-off: close to it, and not already out over the gap. */
	public static boolean readyToLeap(Vec3 here, Vec3 takeOff, Vec3 landing) {
		double dx = landing.x - takeOff.x, dz = landing.z - takeOff.z;
		double along = ((here.x - takeOff.x) * dx + (here.z - takeOff.z) * dz) / Math.hypot(dx, dz);
		return Journey.horizontalDistance(here, takeOff) <= TAKE_OFF_REACH && along < 0.75;
	}

	/** How hard the horse pushes off to clear a leap of this many blocks, centre to centre. */
	public static double leapPush(double distance) {
		return 0.35 + 0.12 * distance;
	}

	/** How long he hammers in the tent pegs before the tent goes up. */
	public static final int HAMMER_TICKS = 100;
	/** How far behind him the middle of the tent stands, with its open front facing him. */
	public static final double TENT_DISTANCE = 2.2;
	/** The tent runs this far either side of its middle, from the open door to the back wall (18 pixels). */
	public static final double TENT_HALF_LENGTH = 18 / 16.0;
	/** Lying flat he's this long from his soles to the top of his head. */
	public static final double SLEEPER_LENGTH = 2.0;
	/** How far past the door his boots stick out. */
	public static final double BOOTS_OUT = 0.3;
	/** How far an aim can stray from straight ahead before he counts it as a detour (and scratches his head). */
	public static final double DETOUR_SLACK = 3.0;
	/** How long he walks towards his pitch before stepping onto it. */
	public static final int WALK_TO_CAMP_TICKS = 200;
	/** How long he's given to walk to a village bed before he camps instead. */
	public static final int WALK_TO_BED_TICKS = 600;
	/** How near a village bed (either end) counts as there. */
	public static final double AT_BED = 1.6;
	/** How many beds he'll try in an evening (one taken as he got there, the next) before he camps instead. */
	public static final int BED_TRIES = 3;
	/** How long he's given to land after getting down from the saddle, before a way to a bed is looked for. */
	public static final int LANDING_TICKS = 40;
	/** How far he can be pushed off a bed (by a villager, say) before he's put back. */
	public static final double BED_DRIFT = 0.3;

	/**
	 * The evening, step by step: find a pitch, walk to it, hammer in the pegs, play, sleep. In a village with a free bed
	 * he walks to the bed instead, plays beside it, and sleeps in it.
	 */
	public enum CampStep {
		FINDING,
		WALKING,
		TO_BED,
		HAMMERING,
		LIGHTING,
		PLAYING,
		SLEEPING
	}

	private CampStep campStep = CampStep.FINDING;
	private int stepTicks;
	@Nullable
	private CampSites.Site campSite;
	@Nullable
	private UUID tentId;
	/** The village bed he's making for or lying in (its head end), where he stood beside it, and whether he's in it. */
	@Nullable
	private BlockPos bed;
	@Nullable
	private Vec3 bedStand;
	private boolean inBed;
	/** How many beds he's tried this evening; with none to be had, he camps. */
	private int bedTries;

	private void step(CampStep next) {
		campStep = next;
		stepTicks = 0;
	}

	/**
	 * Dusk: he finds a flat pitch with room for the tent, walks to it and hammers in the pegs; the tent goes up behind
	 * him in a puff of smoke and he sits playing the harmonica until monsters come out, then turns in, hidden from all
	 * but the closest mobs. At 6am he packs up and rides on. With nowhere to pitch, he plays and sleeps in the open.
	 */
	private void camp(PostmanEntity rider, ServerLevel level) {
		progress.pause(level.getGameTime());
		navigator.putDoorsBack(level);
		if (stepTicks % 20 == 0)
			tendFire(level);
		if (PostOfficeHours.isOpen(level.getOverworldClockTime())) {
			// Up with the sun: out comes the map and the route is worked out afresh, then he whistles for his horse
			breakCamp(rider, level);
			navigator.clearStretch();
			stopToReplan(rider, level, destinationSpot(level), Stage.CALLING_HORSE);
			return;
		}
		stepTicks++;
		switch (campStep) {
			case FINDING -> {
				rider.getNavigation().stop();
				rider.dismissHorse();
				rider.setSprinting(false);
				BlockPos here = rider.blockPosition();
				// In a village (where villagers live: the game counts a place with a bed, a bell or a workstation a villager has
				// claimed), a bed nobody's in: he sleeps there rather than under canvas
				if (bedTries < BED_TRIES && level.isVillage(here)) {
					// Just down from the saddle he's still dropping, and no way is worked out for anyone off the ground
					if (!rider.onGround() && stepTicks < LANDING_TICKS)
						return;
					bed = bedWithAWayTo(rider, level, here);
					bedTries++;
					if (bed != null) {
						logRoute(rider, "in a village at dusk: a free bed at " + Navigator.spot(Vec3.atCenterOf(bed)) + " for the night");
						step(CampStep.TO_BED);
						return;
					}
					bedTries = BED_TRIES;
				}
				campSite = CampSites.find(here, (x, z) -> CampSites.floorIn(level, x, z, here.getY()));
				if (campSite != null)
					step(CampStep.WALKING);
				else
					startEvening(rider, level);
			}
			case WALKING -> {
				Vec3 seat = campSite.seatPos();
				if (Journey.horizontalDistance(rider.position(), seat) < 0.6 || stepTicks >= WALK_TO_CAMP_TICKS) {
					rider.getNavigation().stop();
					rider.snapTo(seat.x, seat.y, seat.z, campSite.yaw() + 180, 0);
					face(rider, campSite.yaw() + 180);
					rider.setActivity(PostmanEntity.Activity.CAMP_HAMMER);
					step(CampStep.HAMMERING);
				} else if (stepTicks % 20 == 1) {
					rider.getNavigation().moveTo(seat.x, seat.y, seat.z, 1.0);
				}
			}
			case TO_BED -> {
				BlockState state = level.getBlockState(bed);
				if (!VillageBeds.isFreeBedHead(state)) {
					// Taken (or gone) before he got there: another, or his tent
					bed = null;
					step(CampStep.FINDING);
				} else if (nearBed(rider.position(), bed, state)) {
					rider.getNavigation().stop();
					bedStand = rider.position();
					face(rider, VillageBeds.yaw(state.getValue(AbstractBedBlock.FACING)));
					startEvening(rider, level);
				} else if (stepTicks >= WALK_TO_BED_TICKS) {
					logRoute(rider, "couldn't get to the bed at " + Navigator.spot(Vec3.atCenterOf(bed)) + ": camps instead");
					bed = null;
					bedTries = BED_TRIES;
					step(CampStep.FINDING);
				} else if (stepTicks % 20 == 1) {
					rider.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 1.0);
				}
			}
			case HAMMERING -> {
				// Facing the tent as he knocks the pegs in, then he turns round to sit at its door
				face(rider, campSite.yaw() + 180);
				if (stepTicks >= HAMMER_TICKS) {
					pitchTent(rider, level);
					face(rider, campSite.yaw());
					// A camp on ice is a cold one: no fire is lit on ice (it would melt through), and he shivers instead
					coldCamp = isColdCamp(level, campSite);
					if (coldCamp) {
						logRoute(rider, "camped on the ice at " + Navigator.spot(campSite.seatPos()) + ": no fire, and a cold night of it");
						startEvening(rider, level);
					} else {
						step(CampStep.LIGHTING);
					}
				}
			}
			case LIGHTING -> {
				// He kneels in front of his seat and strikes the flint and steel; the third strike catches
				face(rider, campSite.yaw());
				if (stepTicks == 1)
					rider.playGesture(PostmanEntity.Gesture.LIGHT_FIRE);
				if (stepTicks == STRIKES[0] || stepTicks == STRIKES[1] || stepTicks == STRIKES[2])
					level.playSound(null, rider, SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
				if (stepTicks == STRIKES[2])
					lightFire(level);
				if (stepTicks >= LIGHTING_TICKS)
					startEvening(rider, level);
			}
			case PLAYING -> {
				if (PostOfficeHours.isBedtime(level.getOverworldClockTime()))
					turnIn(rider, level);
				else if (shiversNow(coldCamp, stepTicks, rider.isBusy()))
					rider.playGesture(PostmanEntity.Gesture.SHIVER);
			}
			case SLEEPING -> {
				if (inBed)
					stayInBed(rider, level);
			}
		}
	}

	/** The nearest free bed within reach that he has a way to on foot, or null. */
	@Nullable
	private static BlockPos bedWithAWayTo(PostmanEntity rider, ServerLevel level, BlockPos here) {
		List<BlockPos> beds = VillageBeds.freeBedsNear(level, here);
		for (BlockPos candidate : beds.subList(0, Math.min(VillageBeds.CANDIDATES, beds.size()))) {
			Path path = rider.getNavigation().createPath(candidate, 1);
			if (path != null && path.canReach())
				return candidate;
		}
		return null;
	}

	/** Whether he's beside the bed: near either end of it, about level with it. */
	private static boolean nearBed(Vec3 at, BlockPos head, BlockState state) {
		BlockPos foot = VillageBeds.footOf(head, state.getValue(AbstractBedBlock.FACING));
		return Math.abs(at.y - head.getY()) < 1.5
			&& Math.min(Journey.horizontalDistance(at, Vec3.atBottomCenterOf(head)), Journey.horizontalDistance(at, Vec3.atBottomCenterOf(foot))) < AT_BED;
	}

	/** Into the village bed: marked taken, as anyone's is, and flat on his back with his head on the pillow. */
	private boolean lieDown(PostmanEntity rider, ServerLevel level) {
		BlockState state = level.getBlockState(bed);
		if (!VillageBeds.isFreeBedHead(state))
			return false;
		level.setBlock(bed, state.setValue(AbstractBedBlock.OCCUPIED, true), Block.UPDATE_ALL);
		inBed = true;
		putInBed(rider, level, state.getValue(AbstractBedBlock.FACING));
		rider.setActivity(PostmanEntity.Activity.SLEEP);
		return true;
	}

	private void putInBed(PostmanEntity rider, ServerLevel level, Direction facing) {
		Vec3 soles = VillageBeds.soles(bed, facing, VillageBeds.topOf(level, VillageBeds.footOf(bed, facing)));
		float yaw = VillageBeds.yaw(facing);
		rider.snapTo(soles.x, soles.y, soles.z, yaw, 0);
		face(rider, yaw);
	}

	/** Asleep in a village bed: put back if he's been pushed off it; up and out if the bed's gone. */
	private void stayInBed(PostmanEntity rider, ServerLevel level) {
		BlockState state = level.getBlockState(bed);
		if (!VillageBeds.isBed(state) || state.getValue(AbstractBedBlock.PART) != BedPart.HEAD) {
			leaveBed(rider, level);
			rider.setActivity(PostmanEntity.Activity.CAMP_REST);
			return;
		}
		Direction facing = state.getValue(AbstractBedBlock.FACING);
		Vec3 soles = VillageBeds.soles(bed, facing, VillageBeds.topOf(level, VillageBeds.footOf(bed, facing)));
		if (rider.position().distanceTo(soles) > BED_DRIFT)
			putInBed(rider, level, facing);
	}

	/** Out of the village bed (if he's in it), which is free again, and back beside it; the evening's bed forgotten. */
	private void leaveBed(PostmanEntity rider, ServerLevel level) {
		if (inBed) {
			releaseBed(level);
			if (bedStand != null)
				rider.snapTo(bedStand.x, bedStand.y, bedStand.z, rider.getYRot(), 0);
		}
		inBed = false;
		bed = null;
		bedStand = null;
	}

	/** Frees the village bed he's lying in (he's leaving it, or the world). */
	public void releaseBed(ServerLevel level) {
		if (inBed && bed != null && level.isLoaded(bed)) {
			BlockState state = level.getBlockState(bed);
			if (VillageBeds.isBed(state) && state.getValue(AbstractBedBlock.PART) == BedPart.HEAD && state.getValue(AbstractBedBlock.OCCUPIED))
				level.setBlock(bed, state.setValue(AbstractBedBlock.OCCUPIED, false), Block.UPDATE_ALL);
		}
		inBed = false;
	}

	/** The harmonica until monsters come out (sat shivering at a cold camp instead); straight to bed if they already have. */
	private void startEvening(PostmanEntity rider, ServerLevel level) {
		if (PostOfficeHours.isBedtime(level.getOverworldClockTime())) {
			turnIn(rider, level);
			return;
		}
		rider.setActivity(coldCamp ? PostmanEntity.Activity.CAMP_REST : PostmanEntity.Activity.HARMONICA);
		step(CampStep.PLAYING);
	}

	/** How often he shivers at a cold camp, in ticks. */
	public static final int SHIVER_GAP = 5 * 20;
	/** Whether a camp is a cold one: the seat or the fire's spot stands on ice. */
	private boolean coldCamp;

	public static boolean isColdCamp(ServerLevel level, CampSites.Site site) {
		return CampfireBlock.isIce(level.getBlockState(site.seat().below())) || CampfireBlock.isIce(level.getBlockState(CampfireBlock.spotFor(site).below()));
	}

	/** Whether he shivers this tick of the evening: at a cold camp, every few seconds, when nothing else is playing. */
	public static boolean shiversNow(boolean coldCamp, int stepTicks, boolean busy) {
		return coldCamp && !busy && stepTicks % SHIVER_GAP == SHIVER_GAP / 2;
	}

	private static void face(PostmanEntity rider, float yaw) {
		rider.getNavigation().stop();
		rider.setYRot(yaw);
		rider.setYBodyRot(yaw);
		rider.setYHeadRot(yaw);
	}

	/** The tent goes up on the pitch behind him, open end towards him. */
	private void pitchTent(PostmanEntity rider, ServerLevel level) {
		TentEntity tent = campSite == null ? null : ModEntities.TENT.get().create(level, EntitySpawnReason.MOB_SUMMONED);
		if (tent == null)
			return;
		Vec3 spot = campSite.tentPos();
		tent.snapTo(spot.x, spot.y, spot.z, campSite.yaw(), 0);
		tent.setRider(rider.getUUID());
		level.addFreshEntity(tent);
		PostmanEffects.puff(level, tent);
		tentId = tent.getUUID();
	}

	/** Into the tent for the night, flat on his back with his boots out of the door (or, with no tent, dozing where he sits). */
	private void turnIn(PostmanEntity rider, ServerLevel level) {
		nightsOut++;
		if (bed != null) {
			if (lieDown(rider, level)) {
				step(CampStep.SLEEPING);
				return;
			}
			// Taken while he sat by it: he dozes where he sits
			bed = null;
		}
		if (tentId != null && level.getEntity(tentId) instanceof TentEntity tent) {
			Vec3 soles = sleepingSpot(tent.position(), tent.getYRot());
			rider.snapTo(soles.x, soles.y, soles.z, tent.getYRot(), 0);
			face(rider, tent.getYRot());
			rider.setInTent(true);
			rider.setActivity(PostmanEntity.Activity.SLEEP);
		} else {
			rider.setActivity(PostmanEntity.Activity.CAMP_REST);
		}
		step(CampStep.SLEEPING);
	}

	/** Where his soles go when he lies down in a tent: just out of the door, so his head is inside by the back wall. */
	public static Vec3 sleepingSpot(Vec3 tent, float tentYaw) {
		return tent.add(Vec3.directionFromRotation(0, tentYaw).scale(TENT_HALF_LENGTH + BOOTS_OUT));
	}

	/** Ticks into lighting the fire when each strike of the flint lands (the light_fire animation), and when he's done. */
	public static final int[] STRIKES = {16, 28, 40};
	public static final int LIGHTING_TICKS = 60;

	/** The campfire, if he got one going, and the time of day it was lit. */
	@Nullable
	private BlockPos campfire;
	private long fireLitAt;

	private void lightFire(ServerLevel level) {
		BlockPos spot = campSite == null ? null : CampfireBlock.spotFor(campSite);
		if (spot == null || !CampfireBlock.canLightAt(level, spot))
			return;
		campfire = spot;
		fireLitAt = PostOfficeHours.timeOfDay(level.getOverworldClockTime());
		level.setBlock(spot, ModBlocks.CAMPFIRE.get().defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, spot, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
	}

	/** Lets the fire die down as the night goes on. */
	private void tendFire(ServerLevel level) {
		if (campfire == null || !level.isLoaded(campfire))
			return;
		BlockState state = level.getBlockState(campfire);
		if (!(state.getBlock() instanceof CampfireBlock)) {
			campfire = null;
			return;
		}
		int light = CampfireBlock.lightAt(PostOfficeHours.timeOfDay(level.getOverworldClockTime()), fireLitAt);
		if (state.getValue(CampfireBlock.LIGHT) != light)
			level.setBlock(campfire, state.setValue(CampfireBlock.LIGHT, light), Block.UPDATE_ALL);
	}

	/** Puts the fire out as he leaves (in the morning, or running for his life). */
	private void putOutFire(ServerLevel level) {
		if (campfire != null && level.getBlockState(campfire).getBlock() instanceof CampfireBlock)
			level.removeBlock(campfire, false);
		campfire = null;
	}

	private void breakCamp(PostmanEntity rider, ServerLevel level) {
		putOutFire(level);
		// A night's rest: he's up with full hearts
		rider.setHealth(rider.getMaxHealth());
		if (rider.isInTent() && campSite != null) {
			Vec3 seat = campSite.seatPos();
			rider.snapTo(seat.x, seat.y, seat.z, rider.getYRot(), 0);
		}
		rider.setInTent(false);
		leaveBed(rider, level);
		bedTries = 0;
		rider.setActivity(PostmanEntity.Activity.NONE);
		if (tentId != null && level.getEntity(tentId) instanceof TentEntity tent)
			tent.packAway(level);
		tentId = null;
		campSite = null;
		step(CampStep.FINDING);
	}

	// ---------------------------------------------------------------- fleeing

	/** How far he runs from whatever attacked him each time he picks somewhere to run to. */
	private static final int FLEE_DISTANCE = 16;
	/** He keeps running from anything hostile this close. */
	private static final double THREAT_RANGE = 16;

	@Nullable
	private UUID attacker;

	public boolean isHorseLost() {
		return horseLost;
	}

	/** His horse was killed: no whistling brings it back this trip, and the way on is planned on foot. */
	public void horseLost(PostmanEntity rider, ServerLevel level, DamageSource source) {
		if (horseLost)
			return;
		horseLost = true;
		logRoute(rider, "his horse was killed (" + source.getMsgId() + ") at " + Navigator.spot(rider.position()) + ": on foot for the rest of the trip");
		rider.dismissHorse();
		navigator.clearStretch();
	}

	/** Something hurt him: noted in the log (what, where, and how he stands), and if it was someone, dealt with. */
	public void hurt(PostmanEntity rider, ServerLevel level, DamageSource source, float amount) {
		logRoute(rider, "hurt by " + source.getMsgId() + " (" + amount + ") at " + Navigator.spot(rider.position()) + ", health " + rider.getHealth()
			+ " (" + stage + ", " + bodyState(rider.getVehicle() != null ? rider.getVehicle() : rider, level) + ")");
		if (source.getEntity() instanceof LivingEntity by)
			attacked(rider, level, by);
	}

	/** Something hurt him. At camp, he leaves everything and runs for it on foot until daylight. */
	public void attacked(PostmanEntity rider, ServerLevel level, LivingEntity by) {
		if (stage != Stage.CAMPING && stage != Stage.FLEEING)
			return;
		attacker = by.getUUID();
		if (stage == Stage.CAMPING) {
			breakCamp(rider, level);
			enter(Stage.FLEEING);
		}
	}

	private void flee(PostmanEntity rider, ServerLevel level) {
		progress.pause(level.getGameTime());
		if (PostOfficeHours.isOpen(level.getOverworldClockTime())) {
			// Daylight: he's run off his route, so out comes the map before he whistles for his horse
			rider.setSprinting(false);
			attacker = null;
			navigator.clearStretch();
			stopToReplan(rider, level, destinationSpot(level), Stage.CALLING_HORSE);
			return;
		}
		if (stageTicks % 20 != 1 && !rider.getNavigation().isDone())
			return;
		Entity threat = attacker != null ? level.getEntity(attacker) : null;
		if (threat == null || !threat.isAlive() || threat.distanceTo(rider) > THREAT_RANGE * 2)
			threat = level.getNearestEntity(Monster.class, TargetingConditions.forCombat().range(THREAT_RANGE), rider,
				rider.getX(), rider.getY(), rider.getZ(), rider.getBoundingBox().inflate(THREAT_RANGE));
		if (threat == null) {
			rider.setSprinting(false);
			return;
		}
		Vec3 away = DefaultRandomPos.getPosAway(rider, FLEE_DISTANCE, 7, threat.position());
		if (away != null) {
			rider.setSprinting(true);
			rider.getNavigation().moveTo(away.x, away.y, away.z, RUN_SPEED);
		}
	}

	private void walkUp(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destinationSpot(level);
		if (target == null)
			return;
		double distance = Journey.horizontalDistance(rider.position(), target);
		if (distance <= Journey.DELIVER_DISTANCE || stageTicks >= WALK_UP_TICKS) {
			// The last step up to the door, so his hands reach into the box
			rider.getNavigation().stop();
			rider.teleportTo(target.x, target.y, target.z);
			enter(Stage.DELIVERING);
			return;
		}
		if (stageTicks % 20 == 1)
			rider.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
	}

	private void deliver(PostmanEntity rider, ServerLevel level) {
		if (destination == null)
			return;
		BlockPos box = destination.pos();
		faceMailbox(rider, box);
		if (stageTicks == 1)
			rider.playGesture(PostmanEntity.Gesture.DELIVER);
		if (stageTicks == MAIL_IN_AT && !postMail(rider, level, box))
			return;
		if (stageTicks >= DELIVER_TICKS) {
			finish(rider, level);
			rider.vanish();
		}
	}

	/** Puts the mail in the box; if the box is gone, renamed or full, leaves a Lost Parcel instead. */
	private boolean postMail(PostmanEntity rider, ServerLevel level, BlockPos box) {
		MailboxDirectory.Entry entry = MailboxDirectory.get(level.getServer()).find(delivery.toName());
		boolean sameBox = entry != null && entry.pos().equals(destination);
		MailboxBlockEntity mailbox = sameBox && level.getBlockEntity(box) instanceof MailboxBlockEntity found ? found : null;
		String name = rider.getName().getString();
		if (mailbox == null) {
			fail(rider, level, LostCause.UNDELIVERABLE, name + " found no mailbox called " + delivery.toName() + " there any more.", null, box);
			return false;
		}
		long travelTicks = level.getGameTime() - delivery.sentAt();
		ItemStack mail = Mail.wrap(delivery, Mail.day(level.getOverworldClockTime()), travelTicks, new JourneyNotes(crossedWater, nightsOut));
		ItemStack delivered = mail.copy();
		if (!mailbox.deliver(mail)) {
			fail(rider, level, LostCause.UNDELIVERABLE, name + " found the mailbox at " + delivery.toName() + " too full to take it.", null, box);
			return false;
		}
		rider.setCarrying(false);
		Notices.delivered(level.getServer(), delivery, name, entry.owner());
		if (entry.owner() != null && level.getServer().getPlayerList().getPlayer(entry.owner()) instanceof ServerPlayer owner)
			PostEvents.received(owner, List.of(delivered));
		return true;
	}

	// ---------------------------------------------------------------- getting stuck

	/** How often (in ticks) he's checked for being stuck. */
	public static final int STUCK_CHECK_TICKS = 20;
	/** Not moving this long in the barge is being stuck: longer than on land, as a barge pressed against a bank takes a while to back off and turn. */
	public static final int BARGE_STALL_TICKS = 15 * 20;

	private boolean checkStuck(PostmanEntity rider, ServerLevel level, double distance, Vec3 target) {
		if (stageTicks % STUCK_CHECK_TICKS != 0)
			return false;
		long now = level.getGameTime();
		// Standing while the route is worked out again, or for his horse to come to his whistle, isn't being stuck:
		// that time is excused (not the clocks reset: a route redone every few seconds, each time the same, is
		// being stuck, and the clocks must still get there)
		if (navigator.isReplanning() || navigator.isWaitingForHorse()) {
			progress.excuse(STUCK_CHECK_TICKS);
			return false;
		}
		// Progress is how far he has to go along the route: the route redone, it's measured afresh from here
		if (navigator.plan() != progressPlan) {
			progressPlan = navigator.plan();
			progress.rebase(distance, now);
		}
		boolean noProgress = progress.isStuck(distance, now);
		int stallPatience = stage == Stage.BARGING ? BARGE_STALL_TICKS : Journey.Progress.STALL_TICKS;
		boolean stalled = progress.isStalled(rider.getX(), rider.getY(), rider.getZ(), now, stallPatience);
		if (!noProgress && !stalled)
			return false;
		String why = noProgress ? "no nearer in " + Journey.Progress.PATIENCE / 20 + " s" : "not moved in " + stallPatience / 20 + " s";
		if (progress.outOfTries()) {
			fail(rider, level, LostCause.STUCK, rider.getName().getString()
				+ " lost " + rider.possessive() + " way in the wilds and had to turn back.", null, rider.blockPosition());
			return true;
		}
		// Stuck in the barge with land at hand: he steps ashore there rather than being jumped somewhere inland; with
		// no route left at all, even onto a shore struck off as leading nowhere (boxed in by his own strikes, that's
		// the way out)
		if (stage == Stage.BARGING && rider.getVehicle() instanceof BargeEntity barge && land(rider, level, barge, target, STUCK_LANDING_REACH, navigator.plan() == null)) {
			progress.pause(now);
			return true;
		}
		// Stuck at the water's edge: he rows across rather than being jumped over it
		if (stage == Stage.RIDING) {
			Vec3 water = navigator.waterToCross(level, rider, target);
			if (water != null && launchBarge(rider, level, water)) {
				progress.pause(now);
				return true;
			}
		}
		Entity mover = rider.getVehicle() != null ? rider.getVehicle() : rider;
		String stuck = "stuck at " + Navigator.spot(mover.position()) + " (" + stage + ", " + why + "; " + navigator.state() + "; "
			+ (rider.getNavigation().isDone() ? "no path" : "on a path") + "; " + bodyState(mover, level) + ")";
		Vec3 free = null;
		String found = "on the water";
		boolean wayFromHere = false;
		if (stage == Stage.RIDING) {
			// Is there a way through the blocks from where he stands? Then he follows it, and nobody is jumped anywhere
			Navigator.Probe here = navigator.probeFromHere(rider, level, target, LocalPlanner.MAX_NODES);
			wayFromHere = here.found();
			// On a scrap of shore with no way on at all (a ledge under a cliff, an islet): back into the barge, whichever
			// way the water lies, and on to the next bit of coast; this shore is noted as leading nowhere. (Looked at
			// towards the mailbox too: the stretch's goal may be out on the water, with nothing to stand on at it.)
			// Cut off on a bigger piece of ground (an ice floe, an island) the same, once the map has been redone
			// round the way ahead and he's still stuck: all of it searched and no way on, the water is the way
			boolean scrap = !wayFromHere && here.expanded() <= TINY_SHORE
				&& navigator.probeShore(level, Mth.floor(mover.getX()), Mth.floor(mover.getY() + 1e-3), Mth.floor(mover.getZ()), target, JUMP_PROBE_BUDGET).expanded() <= TINY_SHORE;
			if (scrap || isCutOff(wayFromHere, here.status(), strikesOff)) {
				int nearY = rider.getBlockY();
				Vec3 water = Crossing.launchSpotAround(rider.position(), (x, z) -> Crossing.surfaceIn(level, x, z, nearY, Crossing.LAUNCH_DROP));
				if (water != null) {
					logRoute(rider, stuck + (scrap ? "; no way on from this scrap of shore (" : "; cut off here, all of it searched (") + here.expanded() + " blocks to it): back into the barge");
					if (launchBargeAt(rider, level, water, target, true)) {
						progress.pause(now);
						return true;
					}
				}
			}
			if (wayFromHere && !triedFollowingNear(mover.position())) {
				lastFollowSpot = mover.position();
				waysFollowed++;
				// Set square in his block first: wedged against a block's edge he'd not get going however good the way
				// (only onto standing ground: on the edge of a stair a block wide, the middle of his block may be air)
				boolean mounted = rider.getHorse() != null;
				int bx = Mth.floor(mover.getX() - (mounted ? 0.5 : 0)), bz = Mth.floor(mover.getZ() - (mounted ? 0.5 : 0));
				if (LocalPlanner.stands(new LevelTerrain(level), mounted, bx, Mth.floor(mover.getY() + 1e-3), bz))
					mover.setPos(bx + (mounted ? 1.0 : 0.5), mover.getY(), bz + (mounted ? 1.0 : 0.5));
				logRoute(rider, stuck + "; a way through the blocks existed (" + here.steps().size() + " steps, starting " + Navigator.describe(here.steps(), 6)
					+ "): following it rather than jumping");
				traceUntil = now + 60;
				navigator.follow(rider, here, now);
				progress.unstuck(distance, now);
				return true;
			}
			found = wayFromHere ? "a way existed from here but following it didn't work" : "no way through from here (" + here.report() + ")";
			// No way from here at all: the way ahead is struck off the map and the route redone round it, before any
			// thought of jumping (the plan took a cliff for a slope, say; it won't again). Not a try against him.
			if (!wayFromHere && strikesOff < MAX_STRIKES && navigator.strikeOffAhead(level, mover.position(), target)) {
				strikesOff++;
				logRoute(rider, stuck + "; " + found + "; the way ahead is struck off the map and the route redone (" + strikesOff + ")");
				rider.getNavigation().stop();
				navigator.clearStretch();
				progress.pause(now);
				progress.struckOff();
				// The map comes out while the new route is worked out (the route's already asked for), and it's
				// checked against the ground before he sets off again
				lastMapStop = now;
				afterPlanning = Stage.RIDING;
				steppingAway = false;
				alreadyPlanning = true;
				enter(Stage.PLANNING);
				return true;
			}
			// Failing that, the nearest safe spot a way does lead on from: a hop of a few blocks, usually; jumped from here
			// before and stuck here again, further out (the nearest spot led him straight back into the same wedge)
			free = nearestWayOut(rider, level, target, nearestJump(jumpsFromSpot(mover.position())));
		}
		// Stuck on the water with nowhere to step off: the barge is pushed off to open water a little way on towards the
		// box (never put down on land, as a jump would); failing that he leaves it and is jumped ashore like anyone
		if (stage == Stage.BARGING && rider.getVehicle() instanceof BargeEntity barge) {
			int nearY = barge.waterLevelY();
			Vec3 on = barge.position().add(new Vec3(target.x - barge.getX(), 0, target.z - barge.getZ()).normalize().scale(JUMP_MAX / 2));
			Vec3 water = Crossing.launchSpotAround(on, (x, z) -> Crossing.surfaceIn(level, x, z, nearY));
			double hop = water == null ? 0 : Journey.horizontalDistance(water, barge.position());
			if (water != null && hop > 2 && withinJump(barge.position(), water)) {
				logRoute(rider, stuck + "; on the water; the barge is pushed off to open water " + Math.round(hop) + " blocks on, at " + Navigator.spot(water));
				jumps.add(new Jump(stage, why, BlockPos.containing(barge.position()), BlockPos.containing(water), false, "pushed off " + Math.round(hop) + " blocks"));
				PostmanEffects.puff(level, barge);
				barge.snapTo(water.x, Crossing.restHeight(level, Mth.floor(water.x), Mth.floor(water.z), water.y), water.z, yawToward(water, target), 0);
				PostmanEffects.puff(level, barge);
				jumpsFree++;
				aimCooldown = 0;
				progress.unstuck(navigator.distanceToGo(water, target), now);
				return true;
			}
			rider.stopRiding();
			barge.vanish(level);
			mover = rider;
		}
		if (free == null)
			free = freeSpot(level, rider.position(), target, rider.level().canSeeSky(rider.blockPosition()));
		// Nowhere safe to jump to this time (lava all round, say); it still counts as a try
		if (free == null) {
			logRoute(rider, stuck + "; " + found + "; nowhere to jump to");
			progress.unstuck(distance, level.getGameTime());
			return true;
		}
		String diagnosis = found + "; jumped " + Math.round(Journey.horizontalDistance(mover.position(), free)) + " blocks";
		logRoute(rider, stuck + "; " + diagnosis + ", to " + Navigator.spot(free));
		jumps.add(new Jump(stage, why, BlockPos.containing(mover.position()), BlockPos.containing(free), wayFromHere, diagnosis));
		Vec3 jumpedFrom = mover.position();
		PostmanEffects.puff(level, mover);
		mover.teleportTo(free.x, free.y, free.z);
		// His chunks go with him at once: put down in one that isn't ticking, he'd never tick again to move them
		keepChunksLoaded(rider, level);
		jumpsFree++;
		// (Where he last followed a way is kept: put back there, following it again would only fail again, so the next
		// try is a jump, further out)
		jumpsHere = jumpsFromSpot(jumpedFrom);
		lastJumpFrom = jumpedFrom;
		PostmanEffects.puff(level, mover);
		rider.getNavigation().stop();
		aimCooldown = 0;
		progress.unstuck(navigator.distanceToGo(free, target), level.getGameTime());
		rider.mannerisms().prompt(PostmanEntity.Gesture.SCRATCH_HEAD);
		// Somewhere new: on land he gets his map out and works the route out again from here
		navigator.clearStretch();
		if (stage == Stage.RIDING)
			stopToReplan(rider, level, target, Stage.RIDING);
		return true;
	}

	/** With -Dpostrider.trace, a stuck rider's body and path follower are traced for a few seconds afterwards. */
	public static final boolean TRACE = Boolean.getBoolean("postrider.trace");
	/** With -Dpostrider.traceAll as well, he's traced the whole way (a headless trip across a real world). */
	public static final boolean TRACE_ALL = Boolean.getBoolean("postrider.traceAll");
	private long traceUntil;

	private String trace(PostmanEntity rider) {
		Entity body = rider.getVehicle() != null ? rider.getVehicle() : rider;
		var navigation = rider.getNavigation();
		var path = navigation.getPath();
		var control = rider.getMoveControl();
		String next = path == null || path.isDone() ? "none" : path.getNextNodePos().toShortString() + " (" + path.getNextNodeIndex() + "/" + path.getNodeCount() + ")";
		return String.format("trace: at %.2f %.2f %.2f, delta %.3f/%.3f, %s, %s, path next %s, wanted %.1f %.1f %.1f, zza %.2f speed %.2f, %s, %s, activity %s",
			body.getX(), body.getY(), body.getZ(), body.getDeltaMovement().horizontalDistance(), body.getDeltaMovement().y,
			body.onGround() ? "on ground" : "in air", body.horizontalCollision ? "colliding" : "clear", next,
			control.getWantedX(), control.getWantedY(), control.getWantedZ(), (body instanceof net.minecraft.world.entity.LivingEntity l ? l.zza : 0f),
			(body instanceof net.minecraft.world.entity.LivingEntity l2 ? l2.getSpeed() : 0f), rider.isGesturing() ? "gesturing" : "no gesture",
			rider.getVehicle() == null ? (rider.isHorseOnTheWay() ? "on foot, horse on the way" : "on foot") : "on " + rider.getVehicle().getType().toShortString(), rider.getActivity())
			+ (body instanceof BargeEntity barge ? String.format(", barge %s, heading %s, aground %d, %s", barge.isBlockedAhead() ? "blocked ahead" : "open ahead",
				barge.headingSpot(), aground, bodyState(barge, (ServerLevel) rider.level())) + ", " + navigator.state() + ", " + navigator.waterState() : "")
			+ ", stage " + stage + " " + stageTicks + ", progress " + progress.describe(rider.level().getGameTime())
			+ (body instanceof BargeEntity && stageTicks % 100 < 5 ? ", survey " + TerrainSurvey.of((ServerLevel) rider.level()).describe()
				+ Navigator.waterChart((ServerLevel) rider.level(), body.position(), body.getBlockY(), 48, 4) : "");
	}

	/** How the body that's stuck is doing, for the log: on the ground or not, what it's standing in and on, pushing or not. */
	private static String bodyState(Entity mover, ServerLevel level) {
		BlockPos feet = mover.blockPosition();
		return (mover.onGround() ? "on the ground" : "off the ground") + ", motion " + String.format("%.2f", mover.getDeltaMovement().horizontalDistance())
			+ ", " + (mover.horizontalCollision ? "pushing against something" : "not pushing") + ", feet in " + level.getBlockState(feet).getBlock().getName().getString()
			+ " on " + level.getBlockState(feet.below()).getBlock().getName().getString()
			+ (level.noCollision(mover, mover.getBoundingBox()) ? "" : ", WEDGED in a block");
	}

	/** How far out he looks for somewhere to jump to, nearest first; the long jumps below only past all of these. */
	public static final int[] JUMP_RADII = {2, 3, 4, 6, 8, 12, 16};
	/** Places looked at by each search from a spot he might jump to. */
	public static final int JUMP_PROBE_BUDGET = 4000;
	/** Having followed a way found from about here once, he doesn't try that again from within this far. */
	public static final double FOLLOW_AGAIN_DISTANCE = 4.0;

	/** Where he last followed a way found from where he stood, rather than jumping. */
	@Nullable
	private Vec3 lastFollowSpot;
	private int waysFollowed;

	/** How many times this trip he's got unstuck by following a way found from where he stood, with no jump. */
	public int waysFollowed() {
		return waysFollowed;
	}

	private boolean triedFollowingNear(Vec3 here) {
		return lastFollowSpot != null && Journey.horizontalDistance(lastFollowSpot, here) < FOLLOW_AGAIN_DISTANCE;
	}

	/** Spots to try round a place, nearest first: at each radius, eight ways round (sixteen further out). */
	public static List<int[]> jumpOffsets() {
		List<int[]> offsets = new ArrayList<>();
		for (int radius : JUMP_RADII) {
			int ways = radius >= 8 ? 16 : 8;
			for (int i = 0; i < ways; i++) {
				double angle = 2 * Math.PI * i / ways;
				offsets.add(new int[] {(int) Math.round(radius * Math.cos(angle)), (int) Math.round(radius * Math.sin(angle))});
			}
		}
		return offsets;
	}

	/**
	 * The nearest safe spot round him that a way through the blocks leads on from (each is searched before he's
	 * put there); null if none of the usual spots has one.
	 */
	@Nullable
	/** A spot a jump lands on must have at least this much ground to be got about on, or it's a pinnacle or an islet. */
	public static final int OPEN_GROUND = 100;

	/** Stuck again within this far of where he was last jumped from is being stuck at the same spot. */
	public static final double SAME_SPOT = 4.0;
	/** The nearest a jump goes, by how many times he's already been jumped from the same spot: then further each time. */
	public static final int[] ESCALATING_JUMPS = {0, 6, 12};
	/** Where he was last jumped from, and how many jumps in a row that was from about there. */
	@Nullable
	private Vec3 lastJumpFrom;
	private int jumpsHere;

	/** How many times he's already been jumped from about this spot, in a row (0 for a new spot). */
	private int jumpsFromSpot(Vec3 here) {
		return lastJumpFrom != null && Journey.horizontalDistance(lastJumpFrom, here) < SAME_SPOT ? jumpsHere + 1 : 0;
	}

	/** The nearest a jump may land, having been jumped from the same spot this many times already. */
	public static int nearestJump(int jumpsFromHere) {
		return ESCALATING_JUMPS[Math.min(jumpsFromHere, ESCALATING_JUMPS.length - 1)];
	}

	private Vec3 nearestWayOut(PostmanEntity rider, ServerLevel level, Vec3 target, int nearest) {
		Entity body = rider.getVehicle() != null ? rider.getVehicle() : rider;
		boolean mounted = rider.getHorse() != null;
		LevelTerrain terrain = new LevelTerrain(level);
		int fromY = Mth.floor(body.getY() + 1e-3);
		// Failing a spot with a way on, the most open ground about (never a pinnacle, a ledge or an islet to be stuck on again)
		Vec3 openest = null;
		int mostOpen = OPEN_GROUND - 1;
		for (int[] offset : jumpOffsets()) {
			if (Math.hypot(offset[0], offset[1]) < nearest - 0.5)
				continue;
			double cx = body.getX() + offset[0], cz = body.getZ() + offset[1];
			int x = mounted ? Mth.floor(cx - 0.5) : Mth.floor(cx), z = mounted ? Mth.floor(cz - 0.5) : Mth.floor(cz);
			for (int y = fromY + 3; y >= fromY - 6; y--) {
				if (!LocalPlanner.stands(terrain, mounted, x, y, z))
					continue;
				if (!withinJump(body.position(), new Vec3(x + (mounted ? 1.0 : 0.5), y, z + (mounted ? 1.0 : 0.5))))
					break;
				Navigator.Probe probe = navigator.probe(level, x, y, z, mounted, rider.mayWhistle(), target, JUMP_PROBE_BUDGET);
				Vec3 spot = new Vec3(x + (mounted ? 1.0 : 0.5), y, z + (mounted ? 1.0 : 0.5));
				if (probe.found())
					return spot;
				if (probe.expanded() > mostOpen) {
					mostOpen = probe.expanded();
					openest = spot;
				}
				break;
			}
		}
		return openest;
	}

	/**
	 * The long jumps, when nothing nearer will do. Out of a cave: straight up to the surface; otherwise the best of
	 * {@link Journey#escapes} (a stretch ahead, a shorter hop, anywhere round about), on top of whatever is there.
	 * Never onto water, lava, fire and the like: null if there's nowhere safe at all.
	 */
	/** A spot to be jumped to is taken near his own level if there is one: a mountain top leads only to falls. */
	public static final int JUMP_LEVEL = 8;
	/**
	 * He's never jumped further than this, up and down included: within the chunks that tick round him, and no great
	 * leap across the map. A spot further off isn't used; with none nearer, the attempt finds nowhere to jump to.
	 */
	public static final double JUMP_MAX = 16;

	/** Whether a jump from one spot to another stays within {@link #JUMP_MAX}, counting the climb or the drop. */
	public static boolean withinJump(Vec3 from, Vec3 to) {
		return from.distanceTo(to) <= JUMP_MAX;
	}
	/** How many times a trip the way ahead is struck off the map and the route redone, before he's jumped instead. */
	public static final int MAX_STRIKES = 24;
	private int strikesOff;

	@Nullable

	static Vec3 freeSpot(ServerLevel level, Vec3 from, Vec3 target, boolean underOpenSky) {
		List<Vec3> safe = underOpenSky ? safeOf(level, Journey.escapes(from, target)) : List.of(surface(level, from));
		Vec3 first = null;
		for (Vec3 aim : safe) {
			if (Journey.horizontalDistance(from, aim) > JUMP_MAX)
				continue;
			BlockPos column = BlockPos.containing(aim);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
			if (Hazards.isDangerous(level, feet))
				continue;
			Vec3 spot = new Vec3(column.getX() + 0.5, y, column.getZ() + 0.5);
			if (!withinJump(from, spot))
				continue;
			if (Math.abs(y - from.y) <= JUMP_LEVEL)
				return spot;
			if (first == null)
				first = spot;
		}
		if (first == null && !underOpenSky)
			return freeSpot(level, from, target, true);
		return first;
	}

	// ---------------------------------------------------------------- ending

	/** He gives up and turns back, as if he'd got hopelessly stuck (for testing failures). */
	public void giveUp(PostmanEntity rider, ServerLevel level) {
		if (stage != Stage.DONE)
			fail(rider, level, LostCause.STUCK, rider.getName().getString() + " lost " + rider.possessive() + " way in the wilds and had to turn back.",
				null, rider.blockPosition());
	}

	/** He was killed on the road. */
	public void killed(PostmanEntity rider, ServerLevel level, DamageSource source) {
		Entity killer = source.getEntity();
		UUID killerId = killer instanceof Player ? killer.getUUID() : null;
		String killerName = killer != null ? killer.getName().getString() : "";
		String reason = source.getLocalizedDeathMessage(rider).getString() + ".";
		fail(rider, level, LostCause.KILLED, reason, killerId, killerName, rider.blockPosition());
	}

	private void fail(PostmanEntity rider, ServerLevel level, LostCause cause, String reason, @Nullable UUID killer, BlockPos near) {
		fail(rider, level, cause, reason, killer, "", near);
	}

	private void fail(PostmanEntity rider, ServerLevel level, LostCause cause, String reason, @Nullable UUID killer,
			String killerName, BlockPos near) {
		logRoute(rider, "the delivery is lost (" + cause + ") at " + Navigator.spot(rider.position()) + ": " + reason);
		MailboxDirectory.Entry entry = MailboxDirectory.get(level.getServer()).find(delivery.toName());
		UUID recipient = entry != null ? entry.owner() : null;
		putOutFire(level);
		BlockPos at = LostParcels.place(level, near, delivery, cause, recipient, killer, killerName);
		Notices.failed(level.getServer(), delivery, reason, at != null ? at : near, at != null, Mail.day(level.getOverworldClockTime()));
		rider.setCarrying(false);
		finish(rider, level);
		if (cause != LostCause.KILLED)
			rider.vanish();
	}

	/** His mail has gone with another rider: he lets go of his chunks and packs up any camp, without a word. */
	public void withdraw(PostmanEntity rider, ServerLevel level) {
		putOutFire(level);
		if (tentId != null && level.getEntity(tentId) instanceof TentEntity tent)
			tent.packAway(level);
		enter(Stage.DONE);
		navigator.releaseAhead(level);
		if (loadedChunk != null)
			RiderChunks.release(level, rider.getUUID(), loadedChunk);
		loadedChunk = null;
	}

	private void finish(PostmanEntity rider, ServerLevel level) {
		enter(Stage.DONE);
		navigator.putDoorsBack(level);
		navigator.releaseAhead(level);
		PostOffice.get(level.getServer()).finish(delivery.id());
		if (loadedChunk != null)
			RiderChunks.release(level, rider.getUUID(), loadedChunk);
		loadedChunk = null;
	}

	// ---------------------------------------------------------------- saving

	public CompoundTag save(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		PendingDelivery.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), delivery).result()
			.ifPresent(d -> tag.put("delivery", d));
		if (destination != null)
			GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, destination).result().ifPresent(d -> tag.put("destination", d));
		tag.putString("stage", stage.name());
		tag.putInt("stage_ticks", stageTicks);
		tag.putString("after_planning", afterPlanning.name());
		tag.putBoolean("crossed_water", crossedWater);
		tag.putBoolean("horse_lost", horseLost);
		tag.putInt("nights_out", nightsOut);
		if (loadedChunk != null)
			tag.putLong("loaded_chunk", loadedChunk.pack());
		tag.putString("camp_step", campStep.name());
		tag.putInt("step_ticks", stepTicks);
		if (campSite != null) {
			tag.putLong("camp_seat", campSite.seat().asLong());
			tag.putString("camp_facing", campSite.facing().getName());
		}
		if (tentId != null)
			tag.store("tent", UUIDUtil.CODEC, tentId);
		if (campfire != null) {
			tag.putLong("campfire", campfire.asLong());
			tag.putLong("fire_lit_at", fireLitAt);
		}
		if (coldCamp)
			tag.putBoolean("cold_camp", true);
		if (bed != null)
			tag.putLong("bed", bed.asLong());
		if (bedStand != null) {
			tag.putDouble("bed_stand_x", bedStand.x);
			tag.putDouble("bed_stand_y", bedStand.y);
			tag.putDouble("bed_stand_z", bedStand.z);
		}
		if (inBed)
			tag.putBoolean("in_bed", true);
		if (bedTries > 0)
			tag.putInt("bed_tries", bedTries);
		if (attacker != null)
			tag.store("attacker", UUIDUtil.CODEC, attacker);
		return tag;
	}

	@Nullable
	public static DeliveryRun load(CompoundTag tag, HolderLookup.Provider registries) {
		PendingDelivery delivery = PendingDelivery.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("delivery"))
			.result().orElse(null);
		if (delivery == null)
			return null;
		GlobalPos destination = tag.contains("destination")
			? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("destination")).result().orElse(null) : null;
		DeliveryRun run = new DeliveryRun(delivery, destination);
		try {
			run.stage = Stage.valueOf(tag.getStringOr("stage", ""));
		} catch (IllegalArgumentException e) {
			run.stage = Stage.RIDING;
		}
		run.stageTicks = tag.getIntOr("stage_ticks", 0);
		try {
			run.afterPlanning = Stage.valueOf(tag.getStringOr("after_planning", ""));
		} catch (IllegalArgumentException e) {
			run.afterPlanning = Stage.CALLING_HORSE;
		}
		// The map comes out again and the route is worked out afresh
		if (run.stage == Stage.PLANNING)
			run.stageTicks = 0;
		run.crossedWater = tag.getBooleanOr("crossed_water", false);
		run.horseLost = tag.getBooleanOr("horse_lost", false);
		run.nightsOut = tag.getIntOr("nights_out", 0);
		if (tag.contains("loaded_chunk"))
			run.loadedChunk = ChunkPos.unpack(tag.getLongOr("loaded_chunk", 0));
		try {
			run.campStep = CampStep.valueOf(tag.getStringOr("camp_step", ""));
		} catch (IllegalArgumentException e) {
			run.campStep = CampStep.FINDING;
		}
		run.stepTicks = tag.getIntOr("step_ticks", 0);
		Direction facing = Direction.byName(tag.getStringOr("camp_facing", ""));
		if (tag.contains("camp_seat") && facing != null)
			run.campSite = new CampSites.Site(BlockPos.of(tag.getLongOr("camp_seat", 0)), facing);
		// A pitch that didn't survive saving: start the evening over
		if (run.campSite == null && (run.campStep == CampStep.WALKING || run.campStep == CampStep.HAMMERING))
			run.campStep = CampStep.FINDING;
		run.tentId = tag.read("tent", UUIDUtil.CODEC).orElse(null);
		if (tag.contains("campfire")) {
			run.campfire = BlockPos.of(tag.getLongOr("campfire", 0));
			run.fireLitAt = tag.getLongOr("fire_lit_at", 0);
		}
		run.coldCamp = tag.getBooleanOr("cold_camp", false);
		if (tag.contains("bed"))
			run.bed = BlockPos.of(tag.getLongOr("bed", 0));
		if (tag.contains("bed_stand_x"))
			run.bedStand = new Vec3(tag.getDoubleOr("bed_stand_x", 0), tag.getDoubleOr("bed_stand_y", 0), tag.getDoubleOr("bed_stand_z", 0));
		run.inBed = run.bed != null && tag.getBooleanOr("in_bed", false);
		run.bedTries = tag.getIntOr("bed_tries", 0);
		// A bed that didn't survive saving: start the evening over
		if (run.bed == null && run.campStep == CampStep.TO_BED)
			run.campStep = CampStep.FINDING;
		run.attacker = tag.read("attacker", UUIDUtil.CODEC).orElse(null);
		return run;
	}
}
