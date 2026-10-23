package com.comedymonkey.postrider.gametest;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.Journey;
import com.comedymonkey.postrider.content.postman.PostmanEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Riders on rivers and shores: where he crosses a river, when he rides round by a bridge or leaps it at a narrows
 * instead, and that a sandbar or an island in his way doesn't catch him. Each is a whole delivery, judged by
 * {@link Trials}.
 * <p>
 * The long-range plan samples four columns in each 8x8 cell (four blocks apart), so it can't see a one-block wall,
 * and the test world outside an arena is open flat ground: a river reaching the arena's edge would look, from afar,
 * as if it could be ridden round outside. So each river here is closed off at both ends by a chasm (a strip of void,
 * down out of the world, too wide for the plan's sampling to miss) behind a stone rim that keeps the water in and
 * the rider out of the chasm. The rim is four blocks thick and thirteen high, so however the scene lines up with the
 * cells one of them samples it, and that cell has a cliff face inside it the plan never crosses (a one-block rim fell
 * between the samples one time in eight, and the plan rode out over it). Rivers are 14 blocks wide and land strips
 * at least 10, so they read as water and land in the plan's cells however the scene happens to line up with them;
 * a bridge is 12 wide for the same reason.
 */
@GameTestHolder(PostRider.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RiverGameTests {
	private static final int DELIVERY_TICKS = 6000;
	private static final String ARENA = "gametest/arena_128x64";
	/** How far in from each end of the arena the ground between the chasms starts (the rim stands on its edges). */
	static final int CHASM = 12;
	/** How thick the rim is: the plan samples columns four apart, so a rim four thick is always seen. */
	static final int RIM = 4;
	static final int X0 = CHASM, X1 = 127 - CHASM, Z0 = 0, Z1 = 63;
	/** The rows the ground inside the rim runs between. */
	static final int INSIDE_Z0 = Z0 + RIM, INSIDE_Z1 = Z1 - RIM;
	/** Rivers run east to west across the arena, between these rows. */
	static final int BANK_SOUTH = 38, BANK_NORTH = 25;
	static final double MIDSTREAM = (BANK_NORTH + BANK_SOUTH + 1) / 2.0;
	/** A crossing longer than this, over a river 14 wide, went along the river rather than over it. */
	static final double SENSIBLE_CROSSING = 26;

	private RiverGameTests() {}

	// ---------------------------------------------------------------- building

	/** Grass between the chasms, the chasms, and the rim: the ground every river scene starts from. */
	private static void banks(Scene scene) {
		scene.ground(X0, Z0, X1, Z1);
		BlockState air = Blocks.AIR.defaultBlockState();
		scene.fill(0, -8, Z0, CHASM - 1, 4, Z1, air);
		scene.fill(128 - CHASM, -8, Z0, 127, 4, Z1, air);
		// The rim is a cliff to the long-range plan (more than twelve high, and thick enough to be sampled whatever
		// the alignment), so it never routes over it and out; it stands on the chasms' inner edges
		BlockState rim = Blocks.STONE.defaultBlockState();
		scene.fill(X0 - RIM + 1, -8, Z0, X0, 13, Z1, rim);
		scene.fill(X1, -8, Z0, X1 + RIM - 1, 13, Z1, rim);
		scene.fill(X0 - RIM + 1, -2, Z0, X1 + RIM - 1, 13, Z0 + RIM - 1, rim);
		scene.fill(X0 - RIM + 1, -2, Z1 - RIM + 1, X1 + RIM - 1, 13, Z1, rim);
	}

	/** Two blocks of water between the rows, from one column to another (inside the rim). */
	private static void river(Scene scene, int x0, int z0, int x1, int z1) {
		scene.water(x0, z0, x1, z1, 2);
	}

	/** Grass level with the banks where there was water (a sandbar, the ground at a narrows). */
	private static void bank(Scene scene, int x0, int z0, int x1, int z1, BlockState top) {
		scene.fill(x0, -1, z0, x1, -1, z1, Blocks.DIRT.defaultBlockState());
		scene.fill(x0, 0, z0, x1, 0, z1, top);
	}

	private record Boxes(BlockPos to, Watch watch) {}

	/** Mailboxes posted between: the near one south of the river facing it, the far one north facing back. */
	private static Boxes post(Scene scene, String what, int nearX, int nearZ, int farX, int farZ) {
		String fromName = Scene.address(what + " A"), toName = Scene.address(what + " B");
		BlockPos from = scene.mailbox(nearX, nearZ, Direction.NORTH, fromName);
		BlockPos to = scene.mailbox(farX, farZ, Direction.SOUTH, toName);
		return new Boxes(to, new Watch(scene, scene.post(from, fromName, toName)));
	}

	// ---------------------------------------------------------------- watching

	/** Where he's being carried: his horse or barge if he's on one, else himself. */
	private static Entity body(PostmanEntity rider) {
		return rider.getVehicle() != null ? rider.getVehicle() : rider;
	}

	/** Notes where the barge was launched and where he stepped ashore each time, in scene coordinates. */
	private static final class Crossings {
		final List<Vec3> launches = new ArrayList<>(), landings = new ArrayList<>();
		private boolean aboard;

		Crossings(Scene scene, Watch watch) {
			scene.helper.onEachTick(() -> {
				PostmanEntity rider = watch.rider;
				if (rider == null || !rider.isAlive())
					return;
				boolean now = rider.getVehicle() instanceof BargeEntity;
				if (now && !aboard)
					launches.add(scene.helper.relativeVec(rider.getVehicle().position()));
				else if (!now && aboard)
					landings.add(scene.helper.relativeVec(rider.position()));
				aboard = now;
			});
		}

		/** Each crossing as "launched at ..., landed at ..., N blocks", for the log and failure messages. */
		String describe() {
			StringBuilder text = new StringBuilder();
			for (int i = 0; i < launches.size(); i++) {
				if (i > 0)
					text.append("; ");
				text.append("launched at ").append(spot(launches.get(i)));
				if (i < landings.size())
					text.append(", landed at ").append(spot(landings.get(i))).append(", ")
						.append(Math.round(Journey.horizontalDistance(launches.get(i), landings.get(i)))).append(" blocks");
				else
					text.append(", still afloat");
			}
			return text.isEmpty() ? "never launched" : text.toString();
		}
	}

	/** The blocks from (x0, z0) to (x1, z1) inclusive. */
	private record Patch(int x0, int z0, int x1, int z1) {}

	/** Follows him on land: where he crossed the river's middle row, and whether he ever stood on a patch of ground. */
	private static final class Ride {
		/** Where (x) he was each time he crossed {@link #MIDSTREAM} on land (on a bridge, or leaping). */
		final List<Double> crossedAt = new ArrayList<>();
		/** Where he was first seen standing on the patch, if ever. */
		Vec3 onPatch;
		private Vec3 last;

		Ride(Scene scene, Watch watch) {
			this(scene, watch, null);
		}

		Ride(Scene scene, Watch watch, @Nullable Patch patch) {
			scene.helper.onEachTick(() -> {
				PostmanEntity rider = watch.rider;
				if (rider == null || !rider.isAlive())
					return;
				if (rider.getVehicle() instanceof BargeEntity) {
					last = null;
					return;
				}
				Vec3 at = scene.helper.relativeVec(body(rider).position());
				if (last != null && crossed(last.z, at.z, MIDSTREAM))
					crossedAt.add(at.x);
				if (onPatch == null && patch != null && within(at.x, at.z, patch.x0(), patch.z0(), patch.x1(), patch.z1()))
					onPatch = at;
				last = at;
			});
		}
	}

	/** Whether going from one coordinate to another crosses a line (ending exactly on it counts). */
	static boolean crossed(double before, double after, double line) {
		return before < line ? after >= line : after < line;
	}

	/** Whether a point is over the blocks from (x0, z0) to (x1, z1) inclusive. */
	static boolean within(double x, double z, int x0, int z0, int x1, int z1) {
		return x >= x0 && x < x1 + 1 && z >= z0 && z < z1 + 1;
	}

	static String spot(Vec3 at) {
		return String.format("%.0f %.0f %.0f", at.x, at.y, at.z);
	}

	// ---------------------------------------------------------------- the river mouth

	/**
	 * A sea down the west side and a river running into it from the east; the near box inland on the south bank near
	 * the mouth, the far box inland on the north bank upstream, so the straight line between them crosses the river
	 * at a slant. There's no bridge and the chasms stop him riding round, so he takes the barge.
	 */
	private static Scene riverMouth(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		banks(scene);
		river(scene, X0 + 1, INSIDE_Z0, 35, INSIDE_Z1);
		river(scene, 36, BANK_NORTH, X1 - 1, BANK_SOUTH);
		return scene;
	}

	@GameTest(skyAccess = true, template = ARENA, batch = "deliver_river_mouth", timeoutTicks = DELIVERY_TICKS)
	public static void heCrossesTheRiverStraightOverNotAlongItOrOutToSea(GameTestHelper helper) {
		Scene scene = riverMouth(helper);
		Boxes boxes = post(scene, "GT Mouth", 44, 50, 84, 12);
		Crossings crossings = new Crossings(scene, boxes.watch());
		Trials.deliver("River mouth", scene, boxes.to(), boxes.watch(), () -> {
			PostRider.LOGGER.info("Trial River mouth: {}", crossings.describe());
			if (crossings.launches.size() != 1 || crossings.landings.size() != 1)
				throw new GameTestAssertException("He should have crossed once: " + crossings.describe());
			Vec3 launch = crossings.launches.get(0), landing = crossings.landings.get(0);
			if (launch.x < 36)
				throw new GameTestAssertException("He put out into the sea rather than the river: " + crossings.describe());
			if (launch.z < MIDSTREAM || landing.z > BANK_NORTH + 1)
				throw new GameTestAssertException("He didn't cross from the south bank to the north: " + crossings.describe());
			if (Journey.horizontalDistance(launch, landing) > SENSIBLE_CROSSING)
				throw new GameTestAssertException("He rowed along the river rather than across it: " + crossings.describe());
		});
	}

	// ---------------------------------------------------------------- the ice field

	/**
	 * A strip of open water and then a band of sea ice right across the arena, with holes of open water in the ice:
	 * an ice field is barge country, so he puts the barge out on the water, rises onto the ice from it without
	 * stopping, slides across (and over the holes), and steps ashore on the far side; never rides the ice.
	 */
	@GameTest(skyAccess = true, template = ARENA, batch = "deliver_ice_field", timeoutTicks = DELIVERY_TICKS)
	public static void heSlidesAcrossAnIceFieldInTheBargeRatherThanRidingIt(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		banks(scene);
		int iceX0 = 48, iceX1 = 79;
		scene.water(iceX0 - 4, INSIDE_Z0, iceX0 - 1, INSIDE_Z1, 2);
		scene.fill(iceX0, 0, INSIDE_Z0, iceX1, 0, INSIDE_Z1, Blocks.ICE.defaultBlockState());
		for (int[] hole : new int[][] {{54, 10}, {62, 30}, {70, 18}, {58, 40}, {74, 50}})
			scene.water(hole[0], hole[1], hole[0] + 2, hole[1] + 2, 2);
		Boxes boxes = post(scene, "GT Ice", 30, 32, 100, 32);
		Crossings crossings = new Crossings(scene, boxes.watch());
		Vec3[] rodeIce = {null};
		boolean[] slid = {false};
		scene.helper.onEachTick(() -> {
			PostmanEntity rider = boxes.watch().rider;
			if (rider == null || !rider.isAlive())
				return;
			Entity body = rider.getVehicle() != null ? rider.getVehicle() : rider;
			Vec3 at = scene.helper.relativeVec(body.position());
			boolean onIce = rider.level().getBlockState(BlockPos.containing(body.position()).below()).is(BlockTags.ICE);
			if (body instanceof BargeEntity barge) {
				if (barge.isOnIce())
					slid[0] = true;
			} else if (onIce && at.x >= iceX0 + 2 && at.x < iceX1 - 1) {
				rodeIce[0] = at;
			}
		});
		Trials.deliver("Ice field", scene, boxes.to(), boxes.watch(), () -> {
			PostRider.LOGGER.info("Trial Ice field: {}", crossings.describe());
			if (rodeIce[0] != null)
				throw new GameTestAssertException("He rode the ice (last seen on it at " + spot(rodeIce[0]) + ") rather than slide across it in the barge");
			if (crossings.launches.size() != 1 || crossings.landings.size() != 1)
				throw new GameTestAssertException("He should have crossed the ice once in the barge: " + crossings.describe());
			if (!slid[0])
				throw new GameTestAssertException("The barge never slid on the ice: " + crossings.describe());
		});
	}

	// ---------------------------------------------------------------- the bridge upstream

	@GameTest(skyAccess = true, template = ARENA, batch = "deliver_river_bridge", timeoutTicks = DELIVERY_TICKS)
	public static void heRidesRoundByTheBridgeUpstreamRatherThanRowing(GameTestHelper helper) {
		Scene scene = riverMouth(helper);
		// A plank bridge well upstream: the way round over it is nowhere near twice as far as rowing
		int bridgeX0 = 96, bridgeX1 = 107;
		scene.fill(bridgeX0, 0, BANK_NORTH, bridgeX1, 0, BANK_SOUTH, Blocks.OAK_PLANKS.defaultBlockState());
		Boxes boxes = post(scene, "GT Bridge", 44, 50, 84, 12);
		Ride ride = new Ride(scene, boxes.watch());
		Trials.deliver("River bridge", scene, boxes.to(), boxes.watch(), () -> {
			if (boxes.watch().rowed)
				throw new GameTestAssertException("He took the barge with a bridge upstream");
			if (ride.crossedAt.size() != 1)
				throw new GameTestAssertException("He crossed the river " + ride.crossedAt.size() + " times, at x " + ride.crossedAt);
			double x = ride.crossedAt.get(0);
			if (x < bridgeX0 || x >= bridgeX1 + 1)
				throw new GameTestAssertException("He crossed the river at x " + x + ", off the bridge");
		});
	}

	// ---------------------------------------------------------------- the sandbar in the bend

	/**
	 * A river that widens into a pool at a bend, the near bank curving back upstream of it, with a sandbar
	 * mid-stream in the pool: dry ground the barge could run its bow onto, which leads nowhere.
	 */
	@GameTest(skyAccess = true, template = ARENA, batch = "deliver_sandbar", timeoutTicks = DELIVERY_TICKS)
	public static void heRowsPastTheSandbarWithoutLandingOnIt(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		banks(scene);
		river(scene, X0 + 1, BANK_NORTH, 59, BANK_SOUTH);
		river(scene, 60, 11, 79, BANK_SOUTH);
		river(scene, 80, 11, X1 - 1, 24);
		int barX0 = 66, barZ0 = 20, barX1 = 70, barZ1 = 24;
		bank(scene, barX0, barZ0, barX1, barZ1, Blocks.SAND.defaultBlockState());
		Boxes boxes = post(scene, "GT Bar", 50, 50, 92, 4);
		Crossings crossings = new Crossings(scene, boxes.watch());
		Ride ride = new Ride(scene, boxes.watch(), new Patch(barX0, barZ0, barX1, barZ1));
		scene.helper.onEachTick(() -> {
			if (ride.onPatch != null)
				throw new GameTestAssertException("He landed on the sandbar, at " + spot(ride.onPatch) + ": " + crossings.describe());
		});
		Trials.deliver("Sandbar bend", scene, boxes.to(), boxes.watch(), () -> {
			PostRider.LOGGER.info("Trial Sandbar bend: {}", crossings.describe());
			if (boxes.watch().launches != 1)
				throw new GameTestAssertException("He put out " + boxes.watch().launches + " times: " + crossings.describe());
		});
	}

	// ---------------------------------------------------------------- the narrows

	/**
	 * A wide river that narrows to a single block, a leap for a horse, well upstream of the straight line between the
	 * boxes: riding up to the narrows and leaping is much shorter than launching the barge where he first meets the
	 * water. The banks at the narrows are sixteen blocks long, so that however the scene falls on the long-range
	 * plan's 8x8 cells one cell lies wholly on them (a shorter bank can be lost between two cells that each sample
	 * the river beside it, and then the plan can only see a river to row across).
	 */
	@GameTest(skyAccess = true, template = ARENA, batch = "deliver_narrows", timeoutTicks = DELIVERY_TICKS)
	public static void heLeapsTheRiverAtItsNarrows(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		banks(scene);
		river(scene, X0 + 1, BANK_NORTH, X1 - 1, BANK_SOUTH);
		int narrowsX0 = 84, narrowsX1 = 99, streamZ = 31;
		bank(scene, narrowsX0, BANK_NORTH, narrowsX1, BANK_SOUTH, Blocks.GRASS_BLOCK.defaultBlockState());
		river(scene, narrowsX0, streamZ, narrowsX1, streamZ);
		Boxes boxes = post(scene, "GT Narrows", 50, 50, 70, 12);
		Ride ride = new Ride(scene, boxes.watch());
		Trials.deliver("Narrows leap", scene, boxes.to(), boxes.watch(), () -> {
			if (boxes.watch().rowed)
				throw new GameTestAssertException("He took the barge rather than leaping the narrows");
			if (ride.crossedAt.size() != 1)
				throw new GameTestAssertException("He crossed the river " + ride.crossedAt.size() + " times, at x " + ride.crossedAt);
			double x = ride.crossedAt.get(0);
			if (x < narrowsX0 - 1 || x >= narrowsX1 + 2)
				throw new GameTestAssertException("He crossed the river at x " + x + ", not at the narrows");
		});
	}

	// ---------------------------------------------------------------- two channels and the island between

	/**
	 * Two channels with a long island between them, right across the way: he has to land on the island, which leads
	 * nowhere, and put out again at once. Putting out again so soon after landing marks the island impassable for the
	 * rest of the trip; this catches that marking (or a plan made afloat) sending him back and forth between the
	 * island and a bank, or leaving him stalled on the island's shore.
	 */
	@GameTest(skyAccess = true, template = ARENA, batch = "deliver_two_channels", timeoutTicks = DELIVERY_TICKS)
	public static void heCrossesTwoChannelsLandingOnTheIslandBetweenJustOnce(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		banks(scene);
		river(scene, X0 + 1, 14, X1 - 1, 27);
		river(scene, X0 + 1, 38, X1 - 1, 51);
		Boxes boxes = post(scene, "GT Channels", 64, 58, 64, 6);
		Crossings crossings = new Crossings(scene, boxes.watch());
		Ride ride = new Ride(scene, boxes.watch(), new Patch(X0 + 1, 28, X1 - 1, 37));
		Trials.deliver("Two channels", scene, boxes.to(), boxes.watch(), () -> {
			PostRider.LOGGER.info("Trial Two channels: {}", crossings.describe());
			if (ride.onPatch == null)
				throw new GameTestAssertException("He never set foot on the island: " + crossings.describe());
			if (boxes.watch().launches != 2)
				throw new GameTestAssertException("He put out " + boxes.watch().launches + " times: " + crossings.describe());
			if (!boxes.watch().horseAboard)
				throw new GameTestAssertException("His horse didn't cross with him");
		});
	}
}
