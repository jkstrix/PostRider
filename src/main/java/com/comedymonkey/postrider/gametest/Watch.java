package com.comedymonkey.postrider.gametest;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.Crossing;
import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;
import com.comedymonkey.postrider.content.postman.route.Navigator;
import com.comedymonkey.postrider.post.PendingDelivery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Keeps an eye on the rider carrying one delivery, every tick, and remembers what he got up to. Test-only. */
final class Watch {
	private final Scene scene;
	final PendingDelivery delivery;
	@Nullable
	PostmanEntity rider;
	@Nullable
	DeliveryRun run;
	boolean studiedMap;
	boolean rowed;
	/** How many times he's put out in the barge. */
	int launches;
	private boolean wasOnBarge;
	boolean horseAboard;
	boolean landed;
	/** He (or his horse) stood or swam in water off the barge, and where. */
	@Nullable
	String waded;
	boolean hadHorseBeforeWater;
	boolean hadHorseAfterLanding;
	// Loops and stalls: how many times he's come into each patch of {@link #PATCH} blocks square, and the most of any
	static final int PATCH = 4;
	private final Map<Long, Integer> visits = new HashMap<>();
	private long lastPatch = Long.MIN_VALUE;
	int mostVisits;
	String busiest = "";
	private Vec3 lastPos;
	int stillTicks, longestStill;
	/** The tick he set off riding, and the tick the mail went in the box; -1 until they happen. */
	long setOff = -1, deliveredAt = -1;
	private long ticks;
	@Nullable
	private Vec3 lastSeen;

	Watch(Scene scene, PendingDelivery delivery) {
		this.scene = scene;
		this.delivery = delivery;
		scene.helper.onEachTick(this::look);
	}

	private void look() {
		ticks++;
		PostmanEntity now = scene.rider(delivery);
		if (now == null) {
			if (setOff >= 0 && deliveredAt < 0 && !scene.stillOut(delivery))
				deliveredAt = ticks;
			return;
		}
		rider = now;
		if (now.getRun() != null)
			run = now.getRun();
		lastSeen = scene.helper.relativeVec(now.position());
		DeliveryRun.Stage stage = run == null ? null : run.stage();
		boolean onTheRoad = stage == DeliveryRun.Stage.RIDING || stage == DeliveryRun.Stage.BARGING;
		if (onTheRoad && setOff < 0)
			setOff = ticks;
		if (onTheRoad)
			noteWhereHeIs(now);
		if (now.getActivity() == PostmanEntity.Activity.STUDY_MAP)
			studiedMap = true;
		boolean onBarge = now.getVehicle() instanceof BargeEntity;
		if (onBarge && !wasOnBarge)
			launches++;
		wasOnBarge = onBarge;
		if (onBarge) {
			rowed = true;
			for (Entity passenger : now.getVehicle().getPassengers())
				if (!(passenger instanceof PostmanEntity))
					horseAboard = true;
		} else if (rowed) {
			landed = true;
		}
		if (!rowed && now.getHorse() != null)
			hadHorseBeforeWater = true;
		// Once over, his horse comes when he whistles (he may be at the box and get down again before he's ridden it far)
		if (landed && (now.getHorse() != null || !scene.level.getEntitiesOfClass(PostmanHorse.class, now.getBoundingBox().inflate(8)).isEmpty()))
			hadHorseAfterLanding = true;
		Entity body = now.getVehicle() != null ? now.getVehicle() : now;
		// What's at his feet (or his horse's) now: the "in water" flag lags a tick behind stepping off the barge
		if (!onBarge && waded == null && (feetWet(now) || feetWet(body))) {
			Entity wet = feetWet(now) ? now : body;
			waded = (wet == now ? "rider" : "horse") + " at " + scene.helper.relativeVec(wet.position()) + " " + now.getRun()
				+ ", box " + wet.getBoundingBox() + ", feet in " + scene.level.getBlockState(wet.blockPosition());
		}
	}

	/** Counts him into the patch he's in each time he comes into it, and how long he's stood still. */
	private void noteWhereHeIs(PostmanEntity now) {
		Vec3 at = scene.helper.relativeVec(now.getVehicle() != null ? now.getVehicle().position() : now.position());
		long patch = BlockPos.asLong(Math.floorDiv((int) Math.floor(at.x), PATCH), 0, Math.floorDiv((int) Math.floor(at.z), PATCH));
		if (patch != lastPatch) {
			int times = visits.merge(patch, 1, Integer::sum);
			if (times > mostVisits) {
				mostVisits = times;
				busiest = "patch at x " + Math.floorDiv((int) Math.floor(at.x), PATCH) * PATCH + " z " + Math.floorDiv((int) Math.floor(at.z), PATCH) * PATCH;
			}
			lastPatch = patch;
		}
		boolean still = lastPos != null && at.distanceToSqr(lastPos) < 1e-4;
		stillTicks = still ? stillTicks + 1 : 0;
		longestStill = Math.max(longestStill, stillTicks);
		lastPos = at;
	}

	/** Where he was last seen, in test coordinates. */
	String lastSeen() {
		return lastSeen == null ? "nowhere yet" : String.format("%.1f %.1f %.1f", lastSeen.x, lastSeen.y, lastSeen.z);
	}

	/** What the trip took him, for the log. */
	String describe() {
		long road = deliveredAt >= 0 && setOff >= 0 ? deliveredAt - setOff : -1;
		return (road >= 0 ? road + " ticks on the road" : "still out after " + ticks + " ticks")
			+ ", " + launches + " barge launches, " + jumpsFree() + " jumps free (" + unneededJumps() + " not needed), " + (run == null ? 0 : run.waysFollowed()) + " ways followed, busiest patch " + mostVisits + " visits"
			+ (mostVisits > 1 ? " (" + busiest + ")" : "") + ", longest stand " + longestStill + " ticks"
			+ (run == null ? "" : ", route finding " + run.routeStats())
			+ (rider == null ? "" : ", last at " + lastSeen() + ", " + rider.getName().getString() + " " + (run == null ? "" : run.stage()));
	}

	/** The trip's numbers as one line of JSON, for the results file. */
	String json(String trial, boolean passed) {
		long road = deliveredAt >= 0 && setOff >= 0 ? deliveredAt - setOff : -1;
		Navigator.Stats stats = run == null ? new Navigator.Stats(0, 0, 0, 0, 0, 0) : run.routeStats();
		return "{\"trial\":\"" + trial + "\",\"passed\":" + passed + ",\"roadTicks\":" + road + ",\"watchedTicks\":" + ticks
			+ ",\"launches\":" + launches + ",\"jumpsFree\":" + jumpsFree() + ",\"mostVisits\":" + mostVisits + ",\"longestStill\":" + longestStill
			+ ",\"routesAsked\":" + stats.routesAsked() + ",\"routesFound\":" + stats.routesFound() + ",\"routeCells\":" + stats.routeCells()
			+ ",\"stretches\":" + stats.stretches() + ",\"stretchesFailed\":" + stats.stretchesFailed() + ",\"stretchBlocks\":" + stats.stretchBlocks()
			+ ",\"waysFollowed\":" + (run == null ? 0 : run.waysFollowed()) + ",\"unneededJumps\":" + unneededJumps() + ",\"jumps\":[" + (run == null ? "" : run.jumps().stream()
				.map(j -> "{\"stage\":" + quoted(j.stage().name()) + ",\"why\":" + quoted(j.why()) + ",\"needed\":" + !j.wayExisted()
					+ ",\"from\":" + quoted(scene.helper.relativePos(j.from()).toShortString()) + ",\"diagnosis\":" + quoted(j.diagnosis()) + "}")
				.collect(java.util.stream.Collectors.joining(",")))
			+ "],\"waded\":" + (waded != null) + "}";
	}

	private boolean feetWet(Entity entity) {
		return Crossing.isWater(scene.level.getFluidState(BlockPos.containing(entity.getX(), entity.getY() + 0.1, entity.getZ())));
	}

		int jumpsFree() {
		return run == null ? 0 : run.jumpsFree();
	}

	/** Jumps free that a full search from the spot showed weren't needed. */
	int unneededJumps() {
		return run == null ? 0 : (int) run.jumps().stream().filter(DeliveryRun.Jump::wayExisted).count();
	}

	private static String quoted(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
