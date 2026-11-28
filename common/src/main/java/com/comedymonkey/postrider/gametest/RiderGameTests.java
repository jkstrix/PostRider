package com.comedymonkey.postrider.gametest;

import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.Crossing;
import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;
import com.comedymonkey.postrider.content.postman.TentEntity;
import com.comedymonkey.postrider.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Riders in a real world: each test builds a small scene, sets a rider (or a barge) going and watches what he does.
 * Every test has sky access: a barrier roof over it would be all a rider's long-range plan could see.
 * Run them with {@code ./gradlew runGameTestServer}. Each is in its own batch, so they run one at a time and riders
 * never meet.
 */
public final class RiderGameTests {
	/** Long enough for him to study his map (up to 20 s), whistle, ride a few dozen blocks, row and deliver. */
	private static final int DELIVERY_TICKS = 3600;

	private RiderGameTests() {}

	// ---------------------------------------------------------------- the barge on its own

	/** A rider aboard a barge on a river one block deep, steered for its far end. */
	private static BargeEntity launch(Scene scene, double x, double z) {
		BargeEntity barge = ModEntities.BARGE.get().create(scene.level, EntitySpawnReason.STRUCTURE);
		if (barge == null)
			throw scene.helper.assertionException(Component.literal("No barge"));
		Vec3 spot = scene.at(x, 0, z);
		barge.snapTo(spot.x, BargeEntity.floatHeight(scene.surface()), spot.z, -90, 0);
		scene.level.addFreshEntity(barge);
		PostmanEntity rider = PostmanEntity.appear(ModEntities.POSTMAN.get(), scene.level, spot.x, barge.getY() + 1, spot.z, -90, Roster.ALL.get(0));
		if (rider == null)
			throw scene.helper.assertionException(Component.literal("No rider"));
		rider.startRiding(barge, true, true);
		rider.setActivity(PostmanEntity.Activity.ROW);
		return barge;
	}

	/** Rows the barge for a point and succeeds once it's past {@code x}; fails if it ever settles onto the bed. */
	private static void rowTo(Scene scene, BargeEntity barge, double x, double z, Runnable alsoCheck) {
		Vec3 aim = scene.at(x + 4, 0, z);
		double bed = scene.at(0, 0, 0).getY();
		scene.helper.onEachTick(() -> {
			if (!barge.isAlive())
				throw scene.helper.assertionException(Component.literal("The barge went"));
			barge.steer(new Vec3(aim.x, barge.getY(), aim.z));
			if (barge.getY() < bed + 0.5)
				throw scene.helper.assertionException(Component.literal("The barge sat on the river bed at y " + (barge.getY() - bed)));
		});
		scene.helper.succeedWhen(() -> {
			if (scene.helper.relativeVec(barge.position()).x < x)
				throw scene.helper.assertionException(Component.literal("Still rowing, at " + scene.helper.relativeVec(barge.position())));
			alsoCheck.run();
			barge.getPassengers().forEach(p -> p.discard());
			barge.discard();
		});
	}

	@GameTest(skyAccess = true, template = "gametest/river_40x12", batch = "barge_shallows", timeoutTicks = 400)
	public static void theBargeFloatsAlongARiverOneBlockDeep(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 39, 11);
		scene.water(0, 2, 39, 9, 1);
		rowTo(scene, launch(scene, 5.5, 6), 32, 6, () -> {});
	}

	@GameTest(skyAccess = true, template = "gametest/river_40x12", batch = "barge_plants", timeoutTicks = 400)
	public static void theBargeCutsThroughKelpSeagrassAndLilyPads(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 39, 11);
		scene.water(0, 2, 39, 9, 1);
		BlockState kelp = Blocks.KELP.defaultBlockState(), seagrass = Blocks.SEAGRASS.defaultBlockState();
		for (int z = 2; z <= 9; z++) {
			helper.setBlock(new BlockPos(14, 0, z), kelp);
			helper.setBlock(new BlockPos(16, 0, z), seagrass);
			helper.setBlock(new BlockPos(19, 1, z), Blocks.LILY_PAD.defaultBlockState());
		}
		rowTo(scene, launch(scene, 5.5, 6), 32, 6, () -> {
			// Cut away where it went (the middle of the river)
			for (int x : new int[] {14, 16, 19})
				for (int z = 5; z <= 6; z++) {
					BlockPos at = new BlockPos(x, x == 19 ? 1 : 0, z);
					if (Crossing.isWaterPlant(helper.getBlockState(at)))
						throw helper.assertionException(Component.literal("A water plant still stands in its way at " + at));
				}
		});
	}

	// ---------------------------------------------------------------- whole deliveries

	/** Succeeds once the mail is in the far box and the checks pass, the rider having been watched all the way. */
	private static void deliver(Scene scene, BlockPos to, Watch watch, Runnable checks) {
		Trials.deliver(watch.delivery.toName().replaceAll(" \\d+$", ""), scene, to, watch, checks);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_48x32", batch = "deliver_flat", timeoutTicks = DELIVERY_TICKS)
	public static void heDeliversAcrossOpenGround(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 47, 31);
		String fromName = Scene.address("GT Flat A"), toName = Scene.address("GT Flat B");
		BlockPos from = scene.mailbox(4, 16, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(43, 16, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliver(scene, to, watch, () -> {
			if (watch.rowed)
				throw helper.assertionException(Component.literal("He took the barge with no water about"));
		});
	}

	/**
	 * Dusk falls as he rides through a village (a bell, and a bed a few blocks off his way): he sleeps in the bed rather
	 * than camping, the bed taken while he's in it, and in the morning he gets up and leaves it free.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_48x32", batch = "village_bed", timeoutTicks = DELIVERY_TICKS)
	public static void inAVillageAtDuskHeSleepsInABedNobodysIn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 47, 31);
		String fromName = Scene.address("GT Bed A"), toName = Scene.address("GT Bed B");
		BlockPos from = scene.mailbox(4, 16, Direction.EAST, fromName);
		scene.mailbox(43, 16, Direction.WEST, toName);
		BlockPos bell = new BlockPos(26, 1, 23);
		helper.setBlock(bell, Blocks.BELL.defaultBlockState());
		BlockPos bellAt = helper.absolutePos(bell);
		BlockPos head = new BlockPos(22, 1, 21);
		BlockState bed = Blocks.BED.red().defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.NORTH);
		helper.setBlock(head.south(), bed.setValue(AbstractBedBlock.PART, BedPart.FOOT));
		helper.setBlock(head, bed.setValue(AbstractBedBlock.PART, BedPart.HEAD));
		BlockPos headAt = helper.absolutePos(head);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		boolean[] dusk = {false}, slept = {false}, claimed = {false};
		helper.onEachTick(() -> {
			// The game counts a place as a village only where a villager has claimed something: the bell is claimed (as a
			// villager would, gathering at it), the bed left unclaimed
			if (!claimed[0] && scene.level.getPoiManager().existsAtPosition(PoiTypes.MEETING, bellAt))
				claimed[0] = scene.level.getPoiManager().take(type -> type.is(PoiTypes.MEETING), (type, at) -> at.equals(bellAt), bellAt, 1).isPresent();
			PostmanEntity rider = watch.rider;
			if (rider == null || watch.run == null)
				return;
			DeliveryRun.Stage stage = watch.run.stage();
			boolean taken = scene.level.getBlockState(headAt).getValue(AbstractBedBlock.OCCUPIED);
			if (!dusk[0] && claimed[0] && stage == DeliveryRun.Stage.RIDING && helper.relativeVec(rider.position()).x >= 14) {
				// Late enough that monsters are out: he goes straight to bed once he's there
				helper.setTime(13500);
				dusk[0] = true;
			} else if (dusk[0] && !slept[0]) {
				if (scene.level.getEntitiesOfClass(TentEntity.class, rider.getBoundingBox().inflate(16)).size() > 0)
					throw helper.assertionException(Component.literal("He pitched his tent with a free bed in the village"));
				if (rider.getActivity() == PostmanEntity.Activity.SLEEP && taken) {
					if (rider.position().distanceTo(Vec3.atBottomCenterOf(headAt)) > 2.0)
						throw helper.assertionException(Component.literal("Asleep, but not in the bed (at " + rider.position() + ")"));
					slept[0] = true;
					helper.setTime(1000);
				}
			} else if (slept[0] && stage != DeliveryRun.Stage.CAMPING) {
				if (taken)
					throw helper.assertionException(Component.literal("He got up and left the bed taken"));
				helper.succeed();
			}
		});
	}

	private record Island(Scene scene, BlockPos to, Watch watch) {}

	/** A lake with an island in it, the far mailbox on the island; the near one {@code fromX} blocks in from the west. */
	private static Island island(GameTestHelper helper, int fromX, String what) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 79, 63);
		// The lake well out from the near mailbox (so he rides to it, unless he starts close) and right across the
		// arena, so there's no riding round it; the island in it, its mailbox well in from where he lands (more than
		// the 15 blocks he'd walk). The game-test world's flat ground runs on round the arena at the arena's height,
		// so the island is kept at least a whole long-range cell of water (15 blocks, however the arena lines up with the
		// cells) from the arena's edges: a cell over the
		// edge, half lake and half that ground, counts as land, and next to the island it was a way round the lake
		scene.water(34, 0, 79, 63, 2);
		scene.fill(48, -1, 20, 64, -1, 44, Blocks.DIRT.defaultBlockState());
		scene.ground(48, 20, 64, 44);
		String fromName = Scene.address(what + " A"), toName = Scene.address(what + " B");
		BlockPos from = scene.mailbox(fromX, 32, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(64, 32, Direction.WEST, toName);
		return new Island(scene, to, new Watch(scene, scene.post(from, fromName, toName)));
	}

	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "deliver_island", timeoutTicks = DELIVERY_TICKS)
	public static void heRowsOutToAnIslandAndLandsAtItsShore(GameTestHelper helper) {
		Island island = island(helper, 3, "GT Isle");
		Watch watch = island.watch();
		deliver(island.scene(), island.to(), watch, () -> {
			if (!watch.rowed)
				throw helper.assertionException(Component.literal("He never took the barge"));
			if (!watch.horseAboard)
				throw helper.assertionException(Component.literal("His horse didn't cross with him"));
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "deliver_island_on_foot", timeoutTicks = DELIVERY_TICKS)
	public static void onFootWithTheWaterCloseHeWalksDownToItAndCallsHisHorseOnceOver(GameTestHelper helper) {
		Island island = island(helper, 24, "GT Foot");
		Watch watch = island.watch();
		deliver(island.scene(), island.to(), watch, () -> {
			if (!watch.rowed)
				throw helper.assertionException(Component.literal("He never took the barge"));
			if (watch.hadHorseBeforeWater)
				throw helper.assertionException(Component.literal("He called his horse with the water only a few blocks off"));
			if (!watch.hadHorseAfterLanding)
				throw helper.assertionException(Component.literal("He never called his horse once he was over"));
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_48x32", batch = "deliver_channel", timeoutTicks = DELIVERY_TICKS)
	public static void heRowsANarrowChannelTheLongRangePlanMightTakeForLand(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 47, 31);
		// Nine blocks across: depending on how it lines up with the long-range plan's cells it may look like dry
		// ground from afar, and only up close does he find he has to row it
		scene.water(0, 12, 47, 20, 2);
		String fromName = Scene.address("GT Chan A"), toName = Scene.address("GT Chan B");
		BlockPos from = scene.mailbox(24, 4, Direction.SOUTH, fromName);
		BlockPos to = scene.mailbox(24, 28, Direction.NORTH, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliver(scene, to, watch, () -> {
			if (!watch.rowed)
				throw helper.assertionException(Component.literal("He never took the barge"));
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_64x48", batch = "deliver_bay", timeoutTicks = DELIVERY_TICKS)
	public static void heRidesRoundTheHeadOfTheBayRatherThanRowingAcross(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 63, 47);
		// The sea down the west side, and an inlet from it reaching most of the way across, between the two boxes
		scene.water(0, 0, 11, 47, 2);
		scene.water(0, 16, 40, 31, 2);
		String fromName = Scene.address("GT Bay A"), toName = Scene.address("GT Bay B");
		BlockPos from = scene.mailbox(28, 6, Direction.SOUTH, fromName);
		BlockPos to = scene.mailbox(28, 42, Direction.NORTH, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliver(scene, to, watch, () -> {
			if (watch.rowed)
				throw helper.assertionException(Component.literal("He rowed across the bay instead of riding round it"));
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_48x32", batch = "deliver_bridge", timeoutTicks = DELIVERY_TICKS)
	public static void heCrossesAStreamByTheBridgeAndNeverWades(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 47, 31);
		scene.water(0, 14, 47, 18, 1);
		scene.fill(22, 0, 14, 25, 0, 18, Blocks.OAK_PLANKS.defaultBlockState());
		String fromName = Scene.address("GT Ford A"), toName = Scene.address("GT Ford B");
		BlockPos from = scene.mailbox(24, 4, Direction.SOUTH, fromName);
		BlockPos to = scene.mailbox(24, 28, Direction.NORTH, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliver(scene, to, watch, () -> {
			if (watch.rowed)
				throw helper.assertionException(Component.literal("He took the barge over a stream with a bridge"));
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "deliver_cut_off_shore", timeoutTicks = DELIVERY_TICKS)
	public static void aShoreThatLeadsNowhereIsntLandedOnOverAndOver(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 79, 63);
		// A lake between the boxes, and beyond it a strip of shore cut off from the far box by a dry ravine too wide to
		// leap and too deep to climb, which from afar looks like no more than steep ground; the ravine stops short of
		// the north side, where there's a way round
		scene.water(18, 0, 44, 63, 2);
		scene.fill(52, -6, 0, 56, 0, 47, Blocks.AIR.defaultBlockState());
		// A stone wall round the edge, so neither the lake nor the barge leads out of the scene; a cliff to the long-range
		// plan too (more than twelve high, and four thick so its sampling can't miss it), so it never routes over it onto
		// the ground beyond
		BlockState wall = Blocks.STONE.defaultBlockState();
		scene.fill(0, -2, 0, 79, 13, 3, wall);
		scene.fill(0, -2, 60, 79, 13, 63, wall);
		scene.fill(0, -2, 0, 3, 13, 63, wall);
		scene.fill(76, -2, 0, 79, 13, 63, wall);
		String fromName = Scene.address("GT Shore A"), toName = Scene.address("GT Shore B");
		BlockPos from = scene.mailbox(6, 24, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(72, 24, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliver(scene, to, watch, () -> {
			if (watch.launches > 2)
				throw helper.assertionException(Component.literal("He put out in the barge " + watch.launches + " times"));
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_48x32", batch = "deliver_ditch", timeoutTicks = DELIVERY_TICKS)
	public static void heLeapsADitchOnceAndRidesOn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 47, 31);
		// Two blocks wide and three deep, right across the way
		scene.fill(0, -2, 15, 47, 0, 16, Blocks.AIR.defaultBlockState());
		String fromName = Scene.address("GT Ditch A"), toName = Scene.address("GT Ditch B");
		BlockPos from = scene.mailbox(24, 4, Direction.SOUTH, fromName);
		BlockPos to = scene.mailbox(24, 28, Direction.NORTH, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		int[] crossings = {0};
		int[] side = {0};
		helper.onEachTick(() -> {
			if (watch.rider == null || !watch.rider.isAlive())
				return;
			Vec3 at = helper.relativeVec(watch.rider.position());
			if (at.y < 0)
				throw helper.assertionException(Component.literal("He fell into the ditch at " + at));
			int now = at.z < 14.5 ? -1 : at.z > 17.5 ? 1 : 0;
			if (now != 0 && side[0] != 0 && now != side[0])
				crossings[0]++;
			if (now != 0)
				side[0] = now;
		});
		deliver(scene, to, watch, () -> {
			if (crossings[0] != 1)
				throw helper.assertionException(Component.literal("He crossed the ditch " + crossings[0] + " times"));
		});
	}
}
