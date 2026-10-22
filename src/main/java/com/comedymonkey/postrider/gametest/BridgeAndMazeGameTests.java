package com.comedymonkey.postrider.gametest;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Deliveries over narrow bridges and through mazes: the only way on is a snaking footbridge over a chasm or a
 * river, a bridge just wide enough to ride, or a hedge maze with dead ends and loops. Hard going for both the
 * long-range plan (its 8x8 cells can't see a one-block bridge or a hedge) and the block-by-block plan (which looks
 * about 40 blocks ahead, less than a maze's way round). Each is judged by {@link Trials#deliver}.
 */
@GameTestHolder(PostRider.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BridgeAndMazeGameTests {
	/** Mazes take a while: the map, a few stretches of planning, the way round the dead ends. */
	private static final int TRIAL_TICKS = 6000;

	private BridgeAndMazeGameTests() {}

	// ---------------------------------------------------------------- the chasm and its bridges

	/**
	 * The banks stand this high above the arena floor, so the chasm between them is too deep to drop into (5 on
	 * horseback, 3 on foot) and nothing below the floor is needed.
	 */
	private static final int BANK_TOP = 7;
	/** The chasm (or the river) runs north to south across the whole arena between these columns: 24 blocks wide. */
	private static final int GAP_WEST = 40, GAP_EAST = 63;

	/**
	 * A bridge that snakes across the gap in five arms with four right-angle bends: east along z 32, north up x 47,
	 * east along z 24, south down x 55 (the long arm) and east along z 40 to the far bank. The one-block version
	 * starts on the line between the mailboxes (z 32); the two-block version is the same, widened inwards.
	 */
	private static final int[][] FOOT_BRIDGE = {{40, 32, 47, 32}, {47, 24, 47, 31}, {48, 24, 55, 24}, {55, 25, 55, 40}, {56, 40, 63, 40}};
	private static final int[][] RIDING_BRIDGE = {{40, 32, 47, 33}, {46, 24, 47, 33}, {46, 24, 55, 25}, {54, 24, 55, 41}, {54, 40, 63, 41}};

	/** The 128x64 arena with both banks raised to {@link #BANK_TOP}, grass on top, and the chasm floor at y 0. */
	private static Scene chasm(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 127, 63);
		BlockState stone = Blocks.STONE.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
		scene.fill(0, 1, 0, GAP_WEST - 1, BANK_TOP - 1, 63, stone);
		scene.fill(0, BANK_TOP, 0, GAP_WEST - 1, BANK_TOP, 63, grass);
		scene.fill(GAP_EAST + 1, 1, 0, 127, BANK_TOP - 1, 63, stone);
		scene.fill(GAP_EAST + 1, BANK_TOP, 0, 127, BANK_TOP, 63, grass);
		return scene;
	}

	/** Lays a bridge's arms at this height. */
	private static void bridge(Scene scene, int[][] arms, int y, BlockState deck) {
		for (int[] arm : arms)
			scene.fill(arm[0], y, arm[1], arm[2], y, arm[3], deck);
	}

	/** Where he (or whatever he's riding) is, in scene coordinates. */
	private static Vec3 bodyAt(Scene scene, Watch watch) {
		Entity body = watch.rider.getVehicle() != null ? watch.rider.getVehicle() : watch.rider;
		return scene.helper.relativeVec(body.position());
	}

	/** Fails the test the moment he, or any horse of his, is below the banks: in the chasm. */
	private static void nobodyFallsIn(Scene scene, Watch watch) {
		double top = BANK_TOP + 0.5;
		scene.helper.onEachTick(() -> {
			if (watch.rider != null && watch.rider.isAlive() && bodyAt(scene, watch).y < top)
				throw new GameTestAssertException("He fell into the chasm, at " + bodyAt(scene, watch));
			for (PostmanHorse horse : scene.level.getEntitiesOfClass(PostmanHorse.class, scene.helper.getBounds()))
				if (scene.helper.relativeVec(horse.position()).y < top)
					throw new GameTestAssertException("A horse fell into the chasm, at " + scene.helper.relativeVec(horse.position()));
		});
	}

	/** What he did about his horse over the gap and beyond it. */
	private static final class GapCrossing {
		boolean walkedTheGap, rodeTheGap, mountedBeyond;
		/** Where he was first seen on foot over the gap. */
		Vec3 gotDownAt;
	}

	/** Watches whether he's on foot or horseback over the gap, and whether he has his horse again past it. */
	private static GapCrossing watchTheGap(Scene scene, Watch watch) {
		GapCrossing crossing = new GapCrossing();
		scene.helper.onEachTick(() -> {
			// Afloat he has no horse under him, but he isn't walking either
			if (watch.rider == null || !watch.rider.isAlive() || watch.rider.getVehicle() instanceof BargeEntity)
				return;
			Vec3 at = bodyAt(scene, watch);
			boolean mounted = watch.rider.getHorse() != null;
			if (at.x >= GAP_WEST && at.x < GAP_EAST + 1) {
				if (mounted)
					crossing.rodeTheGap = true;
				else {
					if (!crossing.walkedTheGap)
						crossing.gotDownAt = at;
					crossing.walkedTheGap = true;
				}
			} else if (at.x >= GAP_EAST + 1 && mounted && crossing.walkedTheGap) {
				crossing.mountedBeyond = true;
			}
		});
		return crossing;
	}

	@GameTest(skyAccess = true, template = "gametest/arena_128x64", batch = "bridge_snake", timeoutTicks = TRIAL_TICKS)
	public static void heWalksAOneBlockBridgeThatSnakesOverAChasmAndWhistlesHisHorseBeyond(GameTestHelper helper) {
		Scene scene = chasm(helper);
		bridge(scene, FOOT_BRIDGE, BANK_TOP, Blocks.STONE_BRICKS.defaultBlockState());
		String fromName = Scene.address("GT Snake A"), toName = Scene.address("GT Snake B");
		BlockPos from = scene.mailbox(6, BANK_TOP + 1, 32, Direction.EAST, fromName);
		// The far box well past the bridge, so riding the rest is worth whistling for the horse
		BlockPos to = scene.mailbox(121, BANK_TOP + 1, 32, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		nobodyFallsIn(scene, watch);
		GapCrossing crossing = watchTheGap(scene, watch);
		helper.onEachTick(() -> {
			if (crossing.rodeTheGap)
				throw new GameTestAssertException("He rode out onto a one-block bridge, at " + bodyAt(scene, watch));
		});
		Trials.deliver("Snake bridge", scene, to, watch, () -> {
			if (!crossing.walkedTheGap)
				throw new GameTestAssertException("He never walked the bridge");
			if (!crossing.mountedBeyond)
				throw new GameTestAssertException("He never whistled his horse back on the far side");
			if (watch.rowed)
				throw new GameTestAssertException("He took the barge over a dry chasm");
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_128x64", batch = "bridge_ride", timeoutTicks = TRIAL_TICKS)
	public static void heRidesATwoBlockBridgeThatSnakesOverAChasm(GameTestHelper helper) {
		Scene scene = chasm(helper);
		bridge(scene, RIDING_BRIDGE, BANK_TOP, Blocks.STONE_BRICKS.defaultBlockState());
		String fromName = Scene.address("GT Ride A"), toName = Scene.address("GT Ride B");
		BlockPos from = scene.mailbox(6, BANK_TOP + 1, 32, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(121, BANK_TOP + 1, 32, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		nobodyFallsIn(scene, watch);
		GapCrossing crossing = watchTheGap(scene, watch);
		helper.onEachTick(() -> {
			if (crossing.walkedTheGap)
				throw new GameTestAssertException("He got down for a bridge he could ride, at " + crossing.gotDownAt);
		});
		Trials.deliver("Ride bridge", scene, to, watch, () -> {
			if (!crossing.rodeTheGap)
				throw new GameTestAssertException("He never rode the bridge");
		});
	}

	/**
	 * The same footbridge over a river, with the far mailbox on an island so there's no riding round: the only ways
	 * over are the bridge (on foot, 48 blocks of it, starting 12 blocks off his line) and the barge (24 blocks of
	 * rowing straight across). The long-range plan can't see a one-block bridge, so by his rules he should row: the
	 * test takes either, as long as he doesn't wade, and the log says which he chose ("route planned").
	 */
	@GameTest(skyAccess = true, template = "gametest/arena_128x64", batch = "bridge_snake_water", timeoutTicks = TRIAL_TICKS)
	public static void overARiverHeTakesTheFootbridgeOrTheBargeButNeverWades(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 127, 63);
		scene.water(GAP_WEST, 0, 127, 63, 2);
		// The island, with room to land and ride on it
		scene.fill(GAP_EAST + 1, -1, 8, 119, -1, 55, Blocks.DIRT.defaultBlockState());
		scene.ground(GAP_EAST + 1, 8, 119, 55);
		bridge(scene, FOOT_BRIDGE, 0, Blocks.OAK_PLANKS.defaultBlockState());
		String fromName = Scene.address("GT Wet Snake A"), toName = Scene.address("GT Wet Snake B");
		// The boxes north of the bridge, so the straight line across is open water with room to launch on
		BlockPos from = scene.mailbox(6, 20, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(112, 20, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		GapCrossing crossing = watchTheGap(scene, watch);
		Trials.deliver("Snake bridge water", scene, to, watch, () -> {
			PostRider.LOGGER.info("Trial Snake bridge water: he crossed by the {}", watch.rowed ? "barge" : "bridge");
			if (watch.launches > 1)
				throw new GameTestAssertException("He put out in the barge " + watch.launches + " times");
			if (!watch.rowed && !crossing.walkedTheGap)
				throw new GameTestAssertException("He neither rowed nor walked the bridge");
			if (watch.rowed && !watch.hadHorseAfterLanding)
				throw new GameTestAssertException("He never had his horse again once he was over");
		});
	}

	// ---------------------------------------------------------------- the mazes

	/**
	 * The mazes, drawn as the usual grid: odd rows and columns are passages, even ones walls, and the entrance and
	 * exit are on the middle row. Each passage character is as many blocks wide as the maze's passages, each wall
	 * one. Made with a seeded depth-first carve (so every one has dead ends); the loop maze has 18 inner walls
	 * knocked out as well, so there are rings to go round and more than one way through. The shortest way through
	 * each is about 95 blocks.
	 */
	private static final String[] HEDGE_MAZE = {
		"###############################",
		"# #         #             #   #",
		"# ### # ##### ######### # # # #",
		"#   # #             #   # # # #",
		"### # ############# # ### # # #",
		"# # # #   #       # # #   # # #",
		"# # ### # # ##### # # # ##### #",
		"# #     #   #     # # #       #",
		"# ########### ####### ####### #",
		"#       #   #   #     #     # #",
		"# ##### # # ### # ##### # ### #",
		"#     # # #   #   #   # # #   #",
		"##### # # ### ##### ### # # ###",
		"#     # # # #     #     # #   #",
		"# # ##### # ##### # ##### ### #",
		"  # #     #     #   #   #   #  ",
		"# ### ##### # ######### ### # #",
		"#     #     # #           #   #",
		"# ##### ##### # ### ### #######",
		"# #         #     # # # #     #",
		"# ######### ####### # # # ### #",
		"#     #   # #       #   # # # #",
		"##### # # ### ########### # # #",
		"#   #   # #   #             # #",
		"# ####### # ### ############# #",
		"# #       #     #     #     # #",
		"# # ############# ### # ### # #",
		"#   #             # # # # # # #",
		"# ### ############# # # # # # #",
		"#     #                 #     #",
		"###############################",
	};

	private static final String[] FOOT_MAZE = {
		"###############################",
		"# #         # #           #   #",
		"# ### ##### # # ##### ### # # #",
		"# #   #     #   #   # #     # #",
		"# # ### ######### # # #########",
		"# #   #           # #         #",
		"# ### ############# # ####### #",
		"#     #     #       #     # # #",
		"####### # ### ########### # # #",
		"#     # #     #       #     # #",
		"# ### # ########### # ##### # #",
		"#   # # #   #       #   #   # #",
		"##### # # # # ######### ##### #",
		"#     #   #   #         #   # #",
		"# ### ######### ######### # # #",
		"    #     #   #     #     #    ",
		"# # ##### # # ##### # ####### #",
		"# #     # # #   #   #   #   # #",
		"# ##### ### # ### ##### # # ###",
		"# #     #   #     #   #   #   #",
		"### ##### ######### # ####### #",
		"#   #     #       # #         #",
		"# # # ##### ##### # ######### #",
		"# # #       #     # #   #     #",
		"# # ######### ##### # ### #####",
		"# #   #       #     #     #   #",
		"# ### # ######### ### ##### # #",
		"#   # # #         # #   #   # #",
		"# # ### # ######### ### # ### #",
		"# #       #               #   #",
		"###############################",
	};

	private static final String[] LOOP_MAZE = {
		"###############################",
		"#     #                       #",
		"##### # ####### # # ##### #####",
		"#   #   #       # # #   # #   #",
		"# # # # # ##### ### # # ### # #",
		"# #   # #     #         #   # #",
		"# ####### ### ### # ### # ### #",
		"# #     # #   #   #   #   # # #",
		"# ### # ### ### ##### ##### # #",
		"#     #     # #     # #     # #",
		"########### # # # # # # ### # #",
		"#         #     # # #     # # #",
		"# ### # # # # ### ####### # # #",
		"#     # #   # #   #   #     # #",
		"# ##### ### # # ### # # ##### #",
		"  # #   #   # #     #   # #    ",
		"# # # # ### # # ######### # ###",
		"#   # # #         #     # #   #",
		"# ### # # ####### # ### # ### #",
		"#     # #       #   #   #   # #",
		"# # ### ### ### ### ### ### # #",
		"# # # #   #   #   #   #   # # #",
		"# # # # # # ##### # # ### # # #",
		"# # #     # #   # # # #   # # #",
		"# # ### ### # # # # # # ### # #",
		"# #   #     # #   #   #     # #",
		"# ### ####### ##### # ##### # #",
		"#   #   #     #     # #   # # #",
		"### ### # # # # ##### # # ### #",
		"#     #   #     #             #",
		"###############################",
	};

	/** Hedges are this high: too high to step up, and the horse can't see over them either. */
	private static final int HEDGE_HEIGHT = 3;
	/** The mailboxes stand here, either side of the maze, on the row of its entrance and exit. */
	private static final int BOX_WEST = 12, BOX_EAST = 84, BOX_ROW = 47;

	private static BlockState hedge() {
		return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	/** Where a maze character starts in blocks: walls are one block, passages {@code width}. */
	private static int offset(int index, int width) {
		return (index + 1) / 2 + index / 2 * width;
	}

	/** How many blocks square a maze of this many characters is, with passages this wide. */
	private static int span(String[] maze, int width) {
		return offset(maze.length, width);
	}

	/**
	 * The 96x96 arena with a maze in the middle of it, hedges running from its west and east faces to the arena's
	 * edges so the only way across is through it, a low wall round the edge so nobody wanders off, and a mailbox
	 * either side on the row of its doors. Returns the two mailboxes' positions.
	 */
	private static BlockPos[] mazeScene(Scene scene, String[] maze, int width, String fromName, String toName) {
		scene.ground(0, 0, 95, 95);
		BlockState wall = Blocks.STONE.defaultBlockState(), hedge = hedge(), air = Blocks.AIR.defaultBlockState();
		scene.fill(0, 1, 0, 95, 4, 0, wall);
		scene.fill(0, 1, 95, 95, 4, 95, wall);
		scene.fill(0, 1, 0, 0, 4, 95, wall);
		scene.fill(95, 1, 0, 95, 4, 95, wall);
		int size = span(maze, width);
		int x0 = (96 - size) / 2, z0 = (96 - size) / 2;
		scene.fill(x0, 1, 0, x0, HEDGE_HEIGHT, 95, hedge);
		scene.fill(x0 + size - 1, 1, 0, x0 + size - 1, HEDGE_HEIGHT, 95, hedge);
		for (int row = 0; row < maze.length; row++)
			for (int column = 0; column < maze[row].length(); column++) {
				int x = x0 + offset(column, width), z = z0 + offset(row, width);
				int w = column % 2 == 1 ? width : 1, d = row % 2 == 1 ? width : 1;
				scene.fill(x, 1, z, x + w - 1, HEDGE_HEIGHT, z + d - 1, maze[row].charAt(column) == '#' ? hedge : air);
			}
		if (offset(maze.length / 2, width) + z0 != BOX_ROW)
			throw new GameTestAssertException("The maze's doors aren't on row " + BOX_ROW);
		return new BlockPos[] {scene.mailbox(BOX_WEST, BOX_ROW, Direction.EAST, fromName), scene.mailbox(BOX_EAST, BOX_ROW, Direction.WEST, toName)};
	}

	/** Whether a place is inside the maze's hedges (not the entrance or exit columns themselves). */
	private static boolean inside(Vec3 at, String[] maze, int width) {
		int size = span(maze, width), x0 = (96 - size) / 2;
		return at.x > x0 + 1 && at.x < x0 + size - 1;
	}

	/** Posts the mail from the west box, and watches for his horse inside the maze (or him on foot in it). */
	private record MazeRun(Scene scene, BlockPos to, Watch watch, boolean[] rodeInside, boolean[] walkedInside) {}

	private static MazeRun mazeRun(GameTestHelper helper, String[] maze, int width, String what) {
		Scene scene = new Scene(helper);
		String fromName = Scene.address(what + " A"), toName = Scene.address(what + " B");
		BlockPos[] boxes = mazeScene(scene, maze, width, fromName, toName);
		Watch watch = new Watch(scene, scene.post(boxes[0], fromName, toName));
		boolean[] rode = {false}, walked = {false};
		helper.onEachTick(() -> {
			if (watch.rider == null || !watch.rider.isAlive())
				return;
			Vec3 at = bodyAt(scene, watch);
			if (!inside(at, maze, width))
				return;
			if (watch.rider.getHorse() != null)
				rode[0] = true;
			else
				walked[0] = true;
		});
		return new MazeRun(scene, boxes[1], watch, rode, walked);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "maze_hedge", timeoutTicks = TRIAL_TICKS)
	public static void heRidesThroughAHedgeMazeWithDeadEnds(GameTestHelper helper) {
		MazeRun run = mazeRun(helper, HEDGE_MAZE, 2, "GT Hedge");
		Trials.deliver("Hedge maze", run.scene(), run.to(), run.watch(), () -> {
			if (!run.rodeInside()[0])
				throw new GameTestAssertException("He never rode into the maze");
			if (run.walkedInside()[0])
				throw new GameTestAssertException("He got down in a maze with room for his horse");
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "maze_foot", timeoutTicks = TRIAL_TICKS)
	public static void heWalksAMazeTooNarrowForHisHorse(GameTestHelper helper) {
		MazeRun run = mazeRun(helper, FOOT_MAZE, 1, "GT Foot Maze");
		Trials.deliver("Foot maze", run.scene(), run.to(), run.watch(), () -> {
			if (!run.walkedInside()[0])
				throw new GameTestAssertException("He never walked into the maze");
			if (run.rodeInside()[0])
				throw new GameTestAssertException("His horse was inside a maze it can't fit through");
		});
	}

	/** A maze with loops: rings to go round and round, and more than one way through, to tempt him into circles. */
	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "maze_loop", timeoutTicks = TRIAL_TICKS)
	public static void heDoesntGoRoundInCirclesInAMazeWithLoops(GameTestHelper helper) {
		MazeRun run = mazeRun(helper, LOOP_MAZE, 2, "GT Loop");
		Trials.deliver("Loop maze", run.scene(), run.to(), run.watch(), () -> {
			if (!run.rodeInside()[0])
				throw new GameTestAssertException("He never rode into the maze");
		});
	}
}
