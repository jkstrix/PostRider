package com.comedymonkey.postrider.gametest;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Riders underground: each test walls the arena in with rock, puts a mountain across it between the two mailboxes,
 * and carves caves through the mountain. From above (which is all his long-range plan can see) the mountain is just
 * steep ground, so these trials are about whether the plan through the blocks ahead finds its way in, through and
 * out, without wading, being teleported free, going round in circles or standing about.
 */
@GameTestHolder(PostRider.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaveGameTests {
	/** Caves can take a while: he may try the wrong branch first and the plan is redone when a stretch won't go. */
	private static final int CAVE_TICKS = 6000;
	/** How high the mountain and the walls round the arena are (the rider can't climb a step of more than one). */
	private static final int ROCK = 24;
	/** How thick the rock round the arena is: thick enough that from above it looks as steep as the mountain. */
	private static final int RIM = 8;

	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private CaveGameTests() {}

	// ---------------------------------------------------------------- building caves

	/** A point on a tunnel's way: the west/north corner of its 2x2 square, and the y his feet are at there. */
	private record Way(int x, int z, int floor) {}

	/** Grass over the whole arena and a wall of rock {@link #RIM} thick round its edge, {@link #ROCK} high. */
	private static void walledArena(Scene scene, int width, int length) {
		scene.ground(0, 0, width - 1, length - 1);
		scene.fill(0, 1, 0, RIM - 1, ROCK, length - 1, STONE);
		scene.fill(width - RIM, 1, 0, width - 1, ROCK, length - 1, STONE);
		scene.fill(0, 1, 0, width - 1, ROCK, RIM - 1, STONE);
		scene.fill(0, 1, length - RIM, width - 1, ROCK, length - 1, STONE);
	}

	/** Solid rock {@link #ROCK} high over this ground. */
	private static void mountain(Scene scene, int x0, int z0, int x1, int z1) {
		scene.fill(x0, 1, z0, x1, ROCK, z1, STONE);
	}

	/**
	 * Carves a tunnel {@code width} blocks wide through the rock along these points (each leg runs along x or z),
	 * {@code height} blocks high. The floor slopes a block at a time between points at different heights, with a
	 * block more of headroom along a sloping leg (a horse steps up with its back half still on the lower block, so
	 * it needs the room over both), and is always solid underneath.
	 */
	private static void tunnel(Scene scene, int width, int height, Way... ways) {
		for (int i = 1; i < ways.length; i++) {
			Way a = ways[i - 1], b = ways[i];
			boolean alongX = a.z() == b.z();
			if (!alongX && a.x() != b.x())
				throw new IllegalArgumentException("A tunnel's legs run along x or z: " + a + " to " + b);
			int from = alongX ? a.x() : a.z(), to = alongX ? b.x() : b.z();
			int low = Math.min(from, to), high = Math.max(from, to) + width - 1;
			int room = height - 1 + (a.floor() != b.floor() ? 1 : 0);
			for (int p = low; p <= high; p++) {
				int floor = floorAt(a, b, from, to, p);
				int top = Math.max(floor, Math.max(floorAt(a, b, from, to, p - 1), floorAt(a, b, from, to, p + 1))) + room;
				for (int k = 0; k < width; k++) {
					int x = alongX ? p : a.x() + k, z = alongX ? a.z() + k : p;
					scene.helper.setBlock(new BlockPos(x, floor - 1, z), STONE);
					scene.fill(x, floor, z, x, top, z, AIR);
				}
			}
		}
	}

	/** The floor along a leg, a block at a time between its ends (and level beyond them). */
	private static int floorAt(Way a, Way b, int from, int to, int p) {
		int n = Math.abs(to - from);
		if (n == 0)
			return a.floor();
		double t = Math.min(1, Math.max(0, (double) (p - from) / (to - from)));
		return a.floor() + (int) Math.round((b.floor() - a.floor()) * t);
	}

	/** A ladder up a wall, from {@code y0} to {@code y1}, facing this way (away from the wall it hangs on). */
	private static void ladder(Scene scene, int x, int y0, int y1, int z, Direction facing) {
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
		for (int y = y0; y <= y1; y++)
			scene.helper.setBlock(new BlockPos(x, y, z), ladder);
	}

	/** Mail posted from one box to the other. */
	private static Watch post(Scene scene, BlockPos from, String fromName, String toName) {
		return new Watch(scene, scene.post(from, fromName, toName));
	}

	/** Notes in the log which way he went: through the mountain's rock (a cave) or round it. */
	private static void noteTheWay(Scene scene, Watch watch, String trial, int rockX0, int rockX1, int rockZ1) {
		boolean[] noted = {false, false};
		scene.helper.onEachTick(() -> {
			if (watch.rider == null || !watch.rider.isAlive())
				return;
			Vec3 at = scene.helper.relativeVec(watch.rider.position());
			boolean inRock = at.x >= rockX0 && at.x < rockX1 + 1 && at.z <= rockZ1 + 1;
			boolean round = at.z > rockZ1 + 1;
			if (inRock && !noted[0]) {
				noted[0] = true;
				PostRider.LOGGER.info("Trial {}: he went into the cave at {}", trial, at);
			}
			if (round && !noted[1]) {
				noted[1] = true;
				PostRider.LOGGER.info("Trial {}: he went round the mountain, at {}", trial, at);
			}
		});
	}

	// ---------------------------------------------------------------- the trials

	/**
	 * A mountain right across the arena, 24 high, with one winding cave through it (2 wide, 3 high, up a slope and
	 * down again) as the only way to the far box. From above it's a wall of steep ground: only the plan through the
	 * blocks can find the cave.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "cave_through", timeoutTicks = CAVE_TICKS)
	public static void heFindsTheCaveThroughAMountainHisMapOnlyShowsTheTopOf(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		walledArena(scene, 96, 96);
		mountain(scene, 36, RIM, 59, 95 - RIM);
		// In at the west foot, north, east up a slope, south across the middle, and east down to the far foot
		tunnel(scene, 2, 3, new Way(34, 48, 1), new Way(41, 48, 1), new Way(41, 38, 1), new Way(49, 38, 4), new Way(49, 58, 4),
			new Way(60, 58, 1));
		String fromName = Scene.address("GT Cave A"), toName = Scene.address("GT Cave B");
		BlockPos from = scene.mailbox(16, 48, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(80, 48, Direction.WEST, toName);
		Watch watch = post(scene, from, fromName, toName);
		noteTheWay(scene, watch, "Cave through", 36, 59, 95);
		Trials.deliver("Cave through", scene, to, watch, () -> {
			if (watch.rowed)
				throw new GameTestAssertException("He took the barge with no water about");
		});
	}

	/**
	 * The same mountain, but stopping short of the north wall, so there's a way round as well as a short cave
	 * straight through. From above the cave can't be seen; which way he goes is noted in the log (see
	 * {@link #noteTheWay}) and judged there, as either is a fair delivery.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "cave_or_round", timeoutTicks = CAVE_TICKS)
	public static void heChoosesBetweenAShortCaveAndTheLongWayRound(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		walledArena(scene, 96, 96);
		// The mountain leaves a 12-block corridor along the north wall; the boxes are down near the south wall, so
		// riding round is about three times as far as the 26 blocks of cave
		mountain(scene, 36, RIM, 59, 75);
		tunnel(scene, 2, 3, new Way(34, 20, 1), new Way(46, 20, 1), new Way(46, 16, 1), new Way(60, 16, 1));
		String fromName = Scene.address("GT Pass A"), toName = Scene.address("GT Pass B");
		BlockPos from = scene.mailbox(16, 20, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(80, 18, Direction.WEST, toName);
		Watch watch = post(scene, from, fromName, toName);
		noteTheWay(scene, watch, "Cave or round", 36, 59, 75);
		Trials.deliver("Cave or round", scene, to, watch, () -> {
			if (watch.rowed)
				throw new GameTestAssertException("He took the barge with no water about");
		});
	}

	/**
	 * A cave that branches: straight on is a dead end (the nearest to the far box as the crow flies, so the most
	 * tempting), north leads through, and south comes out at the bottom of a pit four deep, with a ladder up one
	 * wall that only someone on foot can climb.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_128x64", batch = "cave_branches", timeoutTicks = CAVE_TICKS)
	public static void inABranchingCaveHeFindsTheBranchThatLeadsThrough(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		walledArena(scene, 128, 64);
		mountain(scene, 48, RIM, 79, 63 - RIM);
		// The pit in the east valley, dug into rock, with the ladder up its north wall onto the grass
		scene.fill(78, -4, 42, 89, -3, 53, STONE);
		scene.fill(80, -3, 44, 87, 0, 51, AIR);
		ladder(scene, 83, -3, 0, 44, Direction.SOUTH);
		// The trunk and the dead end, straight along the line between the boxes
		tunnel(scene, 2, 3, new Way(46, 31, 1), new Way(72, 31, 1));
		// North and through
		tunnel(scene, 2, 3, new Way(56, 31, 1), new Way(56, 16, 1), new Way(80, 16, 1));
		// South, east and down into the pit
		tunnel(scene, 2, 3, new Way(56, 31, 1), new Way(56, 46, 1), new Way(74, 46, 1), new Way(80, 46, -3));
		String fromName = Scene.address("GT Fork A"), toName = Scene.address("GT Fork B");
		BlockPos from = scene.mailbox(20, 32, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(108, 32, Direction.WEST, toName);
		Watch watch = post(scene, from, fromName, toName);
		boolean[] inPit = {false};
		scene.helper.onEachTick(() -> {
			if (watch.rider != null && watch.rider.isAlive() && scene.helper.relativeVec(watch.rider.position()).y < 0 && !inPit[0]) {
				inPit[0] = true;
				PostRider.LOGGER.info("Trial Cave branches: he came out into the pit, at {}", scene.helper.relativeVec(watch.rider.position()));
			}
		});
		Trials.deliver("Cave branches", scene, to, watch, () -> {
			if (watch.rowed)
				throw new GameTestAssertException("He took the barge with no water about");
		});
	}

	/**
	 * A cave whose floor dips into a pool right across it, with a dry ledge along one side too low for a horse: he
	 * has to get down, walk the ledge and call the horse back, and never put a foot in the water.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "cave_pool", timeoutTicks = CAVE_TICKS)
	public static void heKeepsHisFeetDryWhereACaveFloodsByWalkingTheLedge(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		walledArena(scene, 96, 96);
		mountain(scene, 36, RIM, 59, 95 - RIM);
		tunnel(scene, 2, 3, new Way(34, 48, 1), new Way(44, 48, 1), new Way(44, 40, 1), new Way(60, 40, 1));
		// The pool: the floor of the east leg drops away under water for eight blocks
		scene.fill(48, -1, 40, 55, -1, 41, STONE);
		scene.fill(48, 0, 40, 55, 0, 41, Blocks.WATER.defaultBlockState());
		// The ledge along the north side of the pool, one wide and two high, from dry floor to dry floor
		scene.fill(47, 0, 39, 56, 0, 39, STONE);
		scene.fill(47, 1, 39, 56, 2, 39, AIR);
		String fromName = Scene.address("GT Pool A"), toName = Scene.address("GT Pool B");
		BlockPos from = scene.mailbox(16, 48, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(80, 40, Direction.WEST, toName);
		Watch watch = post(scene, from, fromName, toName);
		Trials.deliver("Cave pool", scene, to, watch, () -> {
			if (watch.rowed)
				throw new GameTestAssertException("He took the barge in a cave");
		});
	}

	/**
	 * A cave that loops back on itself: the entrance opens onto a ring of tunnel (up a slope on one side and down on
	 * the other), with a short dead-end spur off its east side towards the far box, and the way out leaving from a
	 * corner of the ring. He should go round the ring once at most, not keep circling it.
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "cave_loop", timeoutTicks = CAVE_TICKS)
	public static void aCaveThatLoopsBackOnItselfDoesntSendHimRoundInCircles(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		walledArena(scene, 96, 96);
		mountain(scene, 36, RIM, 59, 95 - RIM);
		tunnel(scene, 2, 3, new Way(34, 48, 1), new Way(41, 48, 1));
		// The ring, joined at the middle of its west side
		tunnel(scene, 2, 3, new Way(41, 48, 1), new Way(41, 40, 1), new Way(53, 40, 1), new Way(53, 56, 3), new Way(41, 56, 3),
			new Way(41, 48, 1));
		// The spur, a block short of the far face
		tunnel(scene, 2, 3, new Way(53, 48, 2), new Way(57, 48, 2));
		// The way out, from the ring's north-east corner
		tunnel(scene, 2, 3, new Way(53, 40, 1), new Way(53, 30, 1), new Way(60, 30, 1));
		String fromName = Scene.address("GT Loop A"), toName = Scene.address("GT Loop B");
		BlockPos from = scene.mailbox(16, 48, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(80, 48, Direction.WEST, toName);
		Watch watch = post(scene, from, fromName, toName);
		Trials.deliver("Cave loop", scene, to, watch, () -> {
			if (watch.rowed)
				throw new GameTestAssertException("He took the barge with no water about");
		});
	}
}
