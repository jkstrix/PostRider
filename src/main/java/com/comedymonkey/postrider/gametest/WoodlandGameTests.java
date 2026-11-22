package com.comedymonkey.postrider.gametest;

import java.util.Random;
import java.util.function.BiPredicate;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.DeliveryRun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Riders in woodland: trees built block by block (vanilla trees would come out different every run), spaced for a
 * horse or packed into thickets, low canopies, fallen logs, and a clearing with one way out. Each is a whole
 * delivery judged by {@link Trials}, plus a check on whether he got down from his horse where he should (and
 * shouldn't) and got back on again. Run with {@code ./gradlew runGameTestServer}; each is in its own batch.
 */
public final class WoodlandGameTests {
	/** Studying the map, riding across a big arena with detours, getting down and whistling the horse back. */
	private static final int DELIVERY_TICKS = 6000;
	private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
	/** Leaves that never decay, however far from a trunk. */
	private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState WALL = Blocks.STONE.defaultBlockState();

	private WoodlandGameTests() {}

	// ---------------------------------------------------------------- building woods

	/** A tree: a trunk this tall (from y 1) with a canopy on top, two blocks of leaves round it and a tuft above. */
	private static void tree(Scene scene, int x, int z, int height) {
		for (int y = 1; y <= height; y++)
			scene.helper.setBlock(new BlockPos(x, y, z), LOG);
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++) {
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				if (!corner)
					for (int y = height; y <= height + 1; y++)
						leaf(scene, x + dx, y, z + dz);
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1)
					leaf(scene, x + dx, height + 2, z + dz);
			}
	}

	/** A leaf block where there's only air (never through a trunk). */
	private static void leaf(Scene scene, int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (scene.helper.getBlockState(pos).isAir())
			scene.helper.setBlock(pos, LEAVES);
	}

	/**
	 * Trees on a loose grid over the ground from (x0, z0) to (x1, z1): {@code spacing} apart, alternate rows shifted
	 * by half, each nudged a block either way, so with a spacing of 6 there are always 3 clear blocks between trunks
	 * (room for a horse). Only where {@code allowed} says, and always the same wood (a fixed seed), so runs compare.
	 */
	private static void wood(Scene scene, int x0, int z0, int x1, int z1, int spacing, BiPredicate<Integer, Integer> allowed) {
		Random random = new Random(0x5EED);
		int row = 0;
		for (int gz = z0 + spacing / 2; gz <= z1; gz += spacing, row++)
			for (int gx = x0 + spacing / 2 + (row % 2 == 0 ? 0 : spacing / 2); gx <= x1; gx += spacing) {
				int x = gx + random.nextInt(3) - 1, z = gz + random.nextInt(3) - 1;
				int height = 4 + random.nextInt(3);
				if (x >= x0 && x <= x1 && z >= z0 && z <= z1 && allowed.test(x, z))
					tree(scene, x, z, height);
			}
	}

	/** Whether (x, z) is more than {@code clear} blocks from a mailbox at (bx, bz), as the crow flies. */
	private static boolean clearOf(int x, int z, int bx, int bz, int clear) {
		return Math.hypot(x - bx, z - bz) > clear;
	}

	/**
	 * A thicket: trunks on every other block, so there's a block's gap between them in each direction, enough for
	 * someone on foot and not for a horse (2 wide), with a roof of leaves 5 blocks up.
	 */
	private static void thicket(Scene scene, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++)
			for (int z = z0; z <= z1; z++) {
				if ((x - x0) % 2 == 0 && (z - z0) % 2 == 0)
					for (int y = 1; y <= 4; y++)
						scene.helper.setBlock(new BlockPos(x, y, z), LOG);
				for (int y = 5; y <= 6; y++)
					leaf(scene, x, y, z);
			}
	}

	/** A stone wall 4 high round the edge of the arena, so there's no riding round the scene outside it. */
	private static void wall(Scene scene, int x1, int z1) {
		scene.fill(0, 1, 0, x1, 4, 0, WALL);
		scene.fill(0, 1, z1, x1, 4, z1, WALL);
		scene.fill(0, 1, 0, 0, 4, z1, WALL);
		scene.fill(x1, 1, 0, x1, 4, z1, WALL);
	}

	/** A fallen log along z at this x, {@code high} blocks tall (logs lying on logs), between z0 and z1. */
	private static void fallenLog(Scene scene, int x, int z0, int z1, int high) {
		BlockState lying = LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
		scene.fill(x, 1, z0, x, high, z1, lying);
	}

	// ---------------------------------------------------------------- watching the saddle

	/** Keeps count, every tick he's on the road, of how often he got down from his horse and back on again. */
	private static final class Saddle {
		int dismounts, remounts;
		private boolean hadHorse;

		Saddle(Scene scene, Watch watch) {
			scene.helper.onEachTick(() -> {
				if (watch.rider == null || !watch.rider.isAlive() || watch.run == null || watch.run.stage() != DeliveryRun.Stage.RIDING)
					return;
				boolean horse = watch.rider.getHorse() != null;
				if (hadHorse && !horse) {
					dismounts++;
					PostRider.LOGGER.info("Saddle: he got down at {}", scene.helper.relativeVec(watch.rider.position()));
				} else if (!hadHorse && horse && dismounts > 0) {
					remounts++;
					PostRider.LOGGER.info("Saddle: he got back on at {}", scene.helper.relativeVec(watch.rider.position()));
				}
				hadHorse = horse;
			});
		}
	}

	private record Trip(Scene scene, BlockPos to, Watch watch, Saddle saddle) {}

	/** Mail posted from a mailbox at (fromX, fromZ) to one at (toX, toZ), the two facing each other along x. */
	private static Trip post(Scene scene, String what, int fromX, int fromZ, int toX, int toZ) {
		String fromName = Scene.address(what + " A"), toName = Scene.address(what + " B");
		BlockPos from = scene.mailbox(fromX, fromZ, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(toX, toZ, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		return new Trip(scene, to, watch, new Saddle(scene, watch));
	}

	/** The delivery, judged by {@link Trials}; the trial's name is short, for the results table. */
	private static void deliver(String trial, Trip trip, Runnable checks) {
		boolean[] noted = {false};
		Trials.deliver(trial, trip.scene(), trip.to(), trip.watch(), () -> {
			try {
				checks.run();
			} catch (GameTestAssertException e) {
				// The delivery got there but not as it should have: its numbers still go in the results file (once;
				// the checks are run again every tick until the test gives up)
				if (!noted[0])
					Trials.record(trial, trip.watch(), false);
				noted[0] = true;
				throw e;
			}
			PostRider.LOGGER.info("Trial {}: he got down {} times and back on {} times", trial, trip.saddle().dismounts, trip.saddle().remounts);
		});
	}

	private static void neverGotDown(String trial, Trip trip) {
		if (trip.saddle().dismounts > 0)
			throw trip.scene().helper.assertionException(Component.literal(trial + ": he got down from his horse " + trip.saddle().dismounts + " times where he could have ridden"));
	}

	private static void gotDownAndBackOn(String trial, Trip trip) {
		if (trip.saddle().dismounts == 0)
			throw trip.scene().helper.assertionException(Component.literal(trial + ": he never got down, yet no horse fits through"));
		if (trip.saddle().remounts == 0)
			throw trip.scene().helper.assertionException(Component.literal(trial + ": he got down " + trip.saddle().dismounts + " times and never got back on his horse"));
	}

	// ---------------------------------------------------------------- the trials

	/** Trees all over, spaced for a horse: he should ride the whole way, weaving between the trunks. */
	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "wood_open", timeoutTicks = DELIVERY_TICKS)
	public static void heRidesThroughAnOpenWoodWithoutGettingDown(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 79, 63);
		wood(scene, 2, 2, 77, 61, 6, (x, z) -> clearOf(x, z, 4, 32, 5) && clearOf(x, z, 75, 32, 5));
		Trip trip = post(scene, "GT Wood", 4, 32, 75, 32);
		deliver("Open wood", trip, () -> neverGotDown("Open wood", trip));
	}

	/**
	 * A band of thicket right across the way, too tight for a horse but a block's gap for a man: he should get down
	 * at it, walk through, whistle the horse back on the far side and ride on.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "wood_thicket", timeoutTicks = DELIVERY_TICKS)
	public static void heWalksThroughAThicketAndWhistlesHisHorseBackBeyondIt(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 79, 63);
		wall(scene, 79, 63);
		// Open wood either side, and the band from one wall to the other
		wood(scene, 2, 2, 28, 61, 6, (x, z) -> clearOf(x, z, 4, 32, 5));
		wood(scene, 50, 2, 77, 61, 6, (x, z) -> clearOf(x, z, 75, 32, 5));
		thicket(scene, 36, 1, 42, 62);
		Trip trip = post(scene, "GT Thicket", 4, 32, 75, 32);
		deliver("Thicket", trip, () -> gotDownAndBackOn("Thicket", trip));
	}

	/**
	 * A wood whose canopy hangs two blocks over the ground across most of the way: fine on foot, too low for a
	 * horse. The ride round its end is longer, but not by as much as getting down and walking would cost, so he
	 * should ride round. The long-range plan can't see leaves, so it's the stretch planning that has to find it.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "wood_low_canopy", timeoutTicks = DELIVERY_TICKS)
	public static void heRidesRoundALowCanopyRatherThanWalkingUnderIt(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 79, 63);
		wall(scene, 79, 63);
		// Leaves at y 3 from x 22 to 41 (20 blocks of it) over all but the south end, where there's a clear ride
		// round; thin trunks under it every so often, none in the way of someone walking straight through
		scene.fill(22, 3, 1, 41, 3, 39, LEAVES);
		scene.fill(22, 4, 1, 41, 4, 39, LEAVES);
		for (int x = 25; x <= 41; x += 8)
			for (int z = 5; z <= 37; z += 8)
				scene.fill(x, 1, z, x, 2, z, LOG);
		Trip trip = post(scene, "GT Canopy", 4, 20, 75, 20);
		deliver("Low canopy", trip, () -> neverGotDown("Low canopy", trip));
	}

	/**
	 * Fallen logs lying across the way: one a block high (he steps over it), three in a row with gaps between (he
	 * walks along their tops), one two logs high across most of the width (too tall to step and nothing to leap, so
	 * he goes round its end), and another low one beyond.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "wood_fallen_logs", timeoutTicks = DELIVERY_TICKS)
	public static void heStepsOverFallenLogsAndGoesRoundOneTooHigh(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 79, 63);
		wall(scene, 79, 63);
		fallenLog(scene, 20, 1, 62, 1);
		fallenLog(scene, 28, 1, 62, 1);
		fallenLog(scene, 30, 1, 62, 1);
		fallenLog(scene, 32, 1, 62, 1);
		fallenLog(scene, 44, 1, 40, 2);
		fallenLog(scene, 58, 1, 62, 1);
		Trip trip = post(scene, "GT Logs", 4, 32, 75, 32);
		// Where he (or his horse) was highest while at the tall log: on top of it would be y 3
		double[] highest = {0};
		helper.onEachTick(() -> {
			if (trip.watch().rider == null || !trip.watch().rider.isAlive())
				return;
			var body = trip.watch().rider.getVehicle() != null ? trip.watch().rider.getVehicle() : trip.watch().rider;
			var at = helper.relativeVec(body.position());
			if (at.x >= 43 && at.x <= 46)
				highest[0] = Math.max(highest[0], at.y);
		});
		deliver("Fallen logs", trip, () -> {
			neverGotDown("Fallen logs", trip);
			if (highest[0] >= 2.5)
				throw helper.assertionException(Component.literal("Fallen logs: he got up to y " + highest[0] + " at the log two high, instead of going round it"));
		});
	}

	/**
	 * He sets out from a clearing walled in by thicket too dense even to walk through, its one gap on the far side
	 * from where he's going: the stretch planning has to take him out the back way and round, rather than at the
	 * wall nearest the mailbox, without going round in circles or standing about.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "wood_clearing", timeoutTicks = DELIVERY_TICKS)
	public static void heLeavesAClearingByItsOnlyGapThoughItFacesAwayFromTheMailbox(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 95, 95);
		int cx = 24, cz = 48;
		// A ring of trunks packed solid, 2 thick, round the clearing, leaves over it; a 5-wide gap on the west side
		for (int x = cx - 12; x <= cx + 12; x++)
			for (int z = cz - 12; z <= cz + 12; z++) {
				double r = Math.hypot(x - cx, z - cz);
				boolean gap = x < cx && Math.abs(z - cz) <= 2;
				if (r >= 9 && r < 11 && !gap)
					scene.fill(x, 1, z, x, 5, z, LOG);
				if (r >= 8 && r < 12 && !gap)
					for (int y = 6; y <= 7; y++)
						leaf(scene, x, y, z);
			}
		// Open wood all round outside the ring, and none inside it
		wood(scene, 2, 2, 93, 93, 7, (x, z) -> Math.hypot(x - cx, z - cz) > 14 && clearOf(x, z, 90, 48, 5));
		Trip trip = post(scene, "GT Clearing", cx, cz, 90, 48);
		deliver("Clearing", trip, () -> neverGotDown("Clearing", trip));
	}
}
