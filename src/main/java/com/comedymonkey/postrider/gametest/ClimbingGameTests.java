package com.comedymonkey.postrider.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Deliveries up, down and under things: a tower with a ramp spiralling up it, rooms under a hill reached by a tunnel
 * or a ladder, a cave that leads nowhere, a cliff the long-range plan takes for a slope, and a base of rooms on three
 * levels joined by ladders and bubble columns. Each is judged by {@link Trials#deliver}, and on top of that by a
 * {@link Lookout}: he's never moved more than a few blocks in a tick, never hurt, and only gets off to walk up to the
 * mailbox once he's about level with it (a box on top of a tower is no nearer from its foot).
 */
@GameTestHolder(PostRider.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClimbingGameTests {
	/** Long enough to study the map, ride a hundred blocks or so, climb, and deliver; a loop or a stall fails sooner. */
	private static final int DELIVERY_TICKS = 6000;
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState STAIRS = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
	private static final BlockState SLAB = Blocks.STONE_BRICK_SLAB.defaultBlockState();
	/** The spire is a long climb on foot: five hundred blocks of stair. */
	private static final int SPIRE_TICKS = 9000;
	/** How high the spire's stair climbs: as near the top of the test world as its template allows. */
	static final int SPIRE_HEIGHT = 250;

	private ClimbingGameTests() {}

	// ---------------------------------------------------------------- the tower

	@GameTest(skyAccess = true, template = "gametest/tower_48x48", batch = "climb_spiral", timeoutTicks = DELIVERY_TICKS)
	public static void heRidesTheRampSpirallingUpTheTower(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 47, 47);
		// A 10x10 tower with a ramp three wide (room for the horse) going once round it, a step up every two blocks,
		// 26 blocks up to the top; the mailbox at the top's north-east corner, right over the foot of the ramp, so
		// the ramp passes within a few blocks of it (across) while he's still twenty blocks below it
		int top = spiral(scene, 19, 19, 10, 3, 2);
		String fromName = Scene.address("GT Spiral A"), toName = Scene.address("GT Spiral B");
		BlockPos from = scene.mailbox(6, 40, Direction.EAST, fromName);
		BlockPos to = mailbox(scene, 27, top, 20, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Spiral up", scene, watch, top);
		lookout.place("ramp", 16, 10, 16, 31, 30, 31);
		Trials.deliver("Spiral up", scene, to, watch, () -> {
			if (!lookout.been("ramp"))
				throw new GameTestAssertException("He never went up the ramp");
			if (!lookout.rodeAt("ramp"))
				throw new GameTestAssertException("He walked the ramp instead of riding it");
		});
	}

	@GameTest(skyAccess = true, template = "gametest/tower_48x48", batch = "climb_spiral_foot", timeoutTicks = DELIVERY_TICKS)
	public static void aRampTooNarrowForTheHorseIsWalked(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 47, 47);
		// The same tower with its ramp only one block wide: he has to get down and walk it
		int top = spiral(scene, 19, 19, 10, 1, 2);
		String fromName = Scene.address("GT Narrow A"), toName = Scene.address("GT Narrow B");
		BlockPos from = scene.mailbox(6, 40, Direction.EAST, fromName);
		BlockPos to = mailbox(scene, 27, top, 20, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Spiral on foot", scene, watch, top);
		lookout.place("ramp", 18, 4, 18, 29, 30, 29);
		Trials.deliver("Spiral on foot", scene, to, watch, () -> {
			if (!lookout.been("ramp"))
				throw new GameTestAssertException("He never went up the ramp");
			if (lookout.rodeAt("ramp"))
				throw new GameTestAssertException("He rode a ramp one block wide");
			if (!lookout.gotDown)
				throw new GameTestAssertException("He never got down from his horse");
		});
	}

	/**
	 * A square tower {@code size} blocks across with its north-west corner at (x0, z0), and a ramp
	 * {@code width} wide spiralling once round the outside of it, clockwise from the north-west corner, up a block
	 * every {@code stepLength} blocks along. The ground at the foot of the ramp is the arena floor; the top of the
	 * tower is one step up from the ramp's end. Returns the height a rider stands at on top.
	 */
	private static int spiral(Scene scene, int x0, int z0, int size, int width, int stepLength) {
		int c0x = x0, c1x = x0 + size - 1, c0z = z0, c1z = z0 + size - 1;
		int b0x = c0x - width, b1x = c1x + width, b0z = c0z - width, b1z = c1z + width;
		int side = size + width;
		int last = 4 * side - 1;
		for (int x = b0x; x <= b1x; x++)
			for (int z = b0z; z <= b1z; z++) {
				int t;
				if (z < c0z && x <= c1x)
					t = x - b0x;
				else if (x > c1x && z <= c1z)
					t = side + (z - b0z);
				else if (z > c1z && x >= c0x)
					t = 2 * side + (b1x - x);
				else if (x < c0x && z >= c0z)
					t = 3 * side + (b1z - z);
				else
					continue;
				int height = t / stepLength;
				if (height >= 1)
					scene.fill(x, 1, z, x, height, z, STONE);
			}
		int topBlock = last / stepLength + 1;
		scene.fill(c0x, 1, c0z, c1x, topBlock, c1z, STONE);
		return topBlock + 1;
	}

	// ---------------------------------------------------------------- the spire

	@GameTest(skyAccess = true, template = "gametest/spire_64x64", batch = "climb_spire", timeoutTicks = SPIRE_TICKS)
	public static void heClimbsTheStairWindingUpTheSpire(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 63, 63);
		// A round tower as tall as the world allows, with a stair one block wide winding up round it to the box on
		// top: a block up every two blocks, by a stair block or a bottom slab (stretches of each), seven turns
		int top = spire(scene, 31, 31, 9, SPIRE_HEIGHT);
		String fromName = Scene.address("GT Spire A"), toName = Scene.address("GT Spire B");
		BlockPos from = scene.mailbox(6, 31, Direction.EAST, fromName);
		BlockPos to = mailbox(scene, 33, top, 31, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Spire", scene, watch, top);
		// From a few blocks up: a horse can step onto the first block or two of the stair from the ground beside it
		lookout.place("the stair", 20, 4, 20, 42, top - 1, 42);
		Trials.deliver("Spire", scene, to, watch, () -> {
			if (!lookout.been("the stair"))
				throw new GameTestAssertException("He never went up the stair");
			if (lookout.rodeAt("the stair"))
				throw new GameTestAssertException("He rode a stair one block wide");
			if (!lookout.gotDown)
				throw new GameTestAssertException("He never got down from his horse");
		});
	}

	// ---------------------------------------------------------------- under the hill

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "under_tunnel", timeoutTicks = DELIVERY_TICKS)
	public static void heFindsTheTunnelIntoTheRoomUnderTheHill(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 95, 95);
		// A terraced hill he can ride up anywhere, with a room under its top that only a tunnel from the west face
		// reaches: in, north, east and south again, two wide and three high all the way
		terracedHill(scene, 28, 28, 40, 8);
		scene.fill(42, 1, 44, 53, 4, 51, AIR);
		scene.fill(28, 1, 47, 37, 3, 48, AIR);
		scene.fill(36, 1, 38, 37, 3, 48, AIR);
		scene.fill(36, 1, 38, 45, 3, 39, AIR);
		scene.fill(44, 1, 38, 45, 3, 43, AIR);
		String fromName = Scene.address("GT Tunnel A"), toName = Scene.address("GT Tunnel B");
		BlockPos from = scene.mailbox(8, 12, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(52, 47, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Tunnel base", scene, watch, 1);
		lookout.place("bend", 36, 1, 38, 37, 3, 39);
		Trials.deliver("Tunnel base", scene, to, watch, () -> {
			if (!lookout.been("bend"))
				throw new GameTestAssertException("He never came through the tunnel");
		});
	}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "under_ladder", timeoutTicks = DELIVERY_TICKS)
	public static void heClimbsDownTheLadderShaftIntoTheRoomUnderTheHill(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 95, 95);
		// The same hill and room, reached only down a one-block shaft from the top with a ladder on its west wall,
		// which carries on down the room's wall to the floor
		terracedHill(scene, 28, 28, 40, 8);
		scene.fill(42, 1, 44, 53, 4, 51, AIR);
		scene.fill(42, 5, 47, 42, 8, 47, AIR);
		ladder(scene, 42, 1, 8, 47, Direction.EAST);
		String fromName = Scene.address("GT Shaft A"), toName = Scene.address("GT Shaft B");
		BlockPos from = scene.mailbox(8, 12, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(52, 47, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Ladder shaft", scene, watch, 1);
		lookout.place("shaft", 42, 5, 47, 42, 8, 47);
		Trials.deliver("Ladder shaft", scene, to, watch, () -> {
			if (!lookout.been("shaft"))
				throw new GameTestAssertException("He never came down the shaft");
			if (!lookout.gotDown)
				throw new GameTestAssertException("He never got down from his horse");
		});
	}

	/**
	 * A hill {@code size} blocks square with its north-west corner at (x0, z0), terraced a block up every two blocks
	 * in from the edge (a horse steps up each terrace), to a flat top {@code top} blocks high.
	 */
	private static void terracedHill(Scene scene, int x0, int z0, int size, int top) {
		int x1 = x0 + size - 1, z1 = z0 + size - 1;
		for (int x = x0; x <= x1; x++)
			for (int z = z0; z <= z1; z++) {
				int in = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
				int height = Math.min(top, (in + 2) / 2);
				scene.fill(x, 1, z, x, height, z, STONE);
			}
	}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "under_cave", timeoutTicks = DELIVERY_TICKS)
	public static void theCaveThatLeadsNowhereDoesntTakeHimIn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 95, 95);
		// A long hill with sheer sides six high right across the way, open ground round either end of it, and a
		// cave into its west face straight ahead of him (two wide, three high, fourteen in, then a bend north)
		// that ends in rock: the real way is round
		scene.fill(28, 1, 10, 67, 6, 85, STONE);
		scene.fill(28, 1, 47, 41, 3, 48, AIR);
		scene.fill(40, 1, 41, 41, 3, 46, AIR);
		String fromName = Scene.address("GT Cave A"), toName = Scene.address("GT Cave B");
		BlockPos from = scene.mailbox(6, 47, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(90, 47, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Dead-end cave", scene, watch, 1);
		lookout.place("deep in the cave", 33, 1, 41, 41, 3, 48);
		Trials.deliver("Dead-end cave", scene, to, watch, () -> {
			if (lookout.been("deep in the cave"))
				throw new GameTestAssertException("He went into the cave that leads nowhere");
		});
	}

	// ---------------------------------------------------------------- the cliff

	@GameTest(skyAccess = true, template = "gametest/arena_80x64", batch = "climb_cliff", timeoutTicks = DELIVERY_TICKS)
	public static void aCliffTheMapTakesForASlopeDoesntKeepHimAtItsFoot(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 79, 63);
		// The east half of the arena is a plateau six blocks up with a sheer face, the mailbox on top of it straight
		// across from the sender; the one way up is a ramp against the face at the north end, fifty blocks from the
		// straight line. From afar the plateau's edge is just a steep cell, so the long-range plan will try to go
		// straight up it: he should still find the ramp rather than shuttle along the foot of the cliff
		scene.fill(40, 1, 0, 79, 6, 63, STONE);
		for (int x = 28; x <= 39; x++)
			scene.fill(x, 1, 1, x, (x - 28) / 2 + 1, 3, STONE);
		String fromName = Scene.address("GT Cliff A"), toName = Scene.address("GT Cliff B");
		BlockPos from = scene.mailbox(6, 56, Direction.EAST, fromName);
		BlockPos to = mailbox(scene, 72, 7, 56, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Cliff foot", scene, watch, 7);
		lookout.place("ramp", 28, 1, 1, 39, 8, 3);
		Trials.deliver("Cliff foot", scene, to, watch, () -> {
			if (!lookout.been("ramp"))
				throw new GameTestAssertException("He got up without the ramp");
		});
	}

	// ---------------------------------------------------------------- the base

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "under_base", timeoutTicks = DELIVERY_TICKS)
	public static void heWorksHisWayThroughTheBaseByLadderAndBubbleColumn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		scene.ground(0, 0, 95, 95);
		// A solid block of a base, 40 square and 18 high, the mailbox in a room at the back of it on the ground
		// level, with no way to it but through the rooms:
		scene.fill(30, 1, 30, 69, 18, 69, STONE);
		// Ground level: in by a tunnel on the west face (two wide, three high) to the hall, off which a passage leads
		// south to a room that goes nowhere; the way on is a ladder in a niche in the hall's east wall, up a shaft
		scene.fill(30, 1, 48, 37, 3, 49, AIR);
		scene.fill(38, 1, 44, 45, 4, 53, AIR);
		scene.fill(40, 1, 54, 41, 3, 57, AIR);
		scene.fill(36, 1, 58, 45, 3, 63, AIR);
		scene.fill(45, 1, 45, 45, 4, 45, STONE);
		scene.fill(45, 1, 47, 45, 4, 47, STONE);
		scene.fill(45, 5, 46, 45, 6, 46, AIR);
		ladder(scene, 45, 1, 6, 46, Direction.WEST);
		// Second level (he stands at y 7): the ladder comes up into a room, and two passages (one wide, two high, on
		// foot) lead from it to the next room, one straight east and one round by the south: a loop
		scene.fill(42, 7, 40, 49, 9, 47, AIR);
		scene.fill(50, 7, 43, 57, 8, 43, AIR);
		scene.fill(58, 7, 40, 65, 9, 47, AIR);
		scene.fill(46, 7, 48, 46, 8, 49, AIR);
		scene.fill(46, 7, 50, 61, 8, 50, AIR);
		scene.fill(61, 7, 48, 61, 8, 49, AIR);
		// From the second room a bubble column over soul sand, in the east wall, goes up to the third level: in at
		// the bottom through ladders (which the water can't flow out through), out at the top onto the floor above
		scene.fill(65, 7, 44, 65, 9, 44, STONE);
		scene.fill(65, 7, 46, 65, 9, 46, STONE);
		ladder(scene, 65, 7, 9, 45, Direction.NORTH);
		bubbleColumn(scene, 66, 7, 12, 45, false);
		// Third level (y 13): the room the column comes up into, a passage south and west to another room, and in
		// that room's floor a whirlpool over magma down to the ground level, out at the bottom through ladders into
		// the mailbox's room
		scene.fill(58, 13, 41, 66, 15, 48, AIR);
		scene.fill(60, 13, 49, 60, 14, 52, AIR);
		scene.fill(56, 13, 52, 59, 14, 52, AIR);
		scene.fill(48, 13, 50, 55, 15, 57, AIR);
		bubbleColumn(scene, 52, 1, 12, 54, true);
		ladder(scene, 52, 1, 2, 55, Direction.EAST);
		scene.fill(48, 1, 56, 55, 4, 63, AIR);
		String fromName = Scene.address("GT Base A"), toName = Scene.address("GT Base B");
		BlockPos from = scene.mailbox(22, 48, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(54, 60, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Complex base", scene, watch, 1);
		lookout.place("the room that goes nowhere", 36, 1, 58, 45, 3, 63);
		lookout.place("the ladder", 45, 1, 46, 45, 6, 46);
		lookout.place("the column up", 66, 7, 45, 66, 12, 45);
		lookout.place("the whirlpool", 52, 1, 54, 52, 12, 54);
		Trials.deliver("Complex base", scene, to, watch, () -> {
			if (lookout.been("the room that goes nowhere"))
				throw new GameTestAssertException("He went into the room that goes nowhere");
			for (String way : new String[] {"the ladder", "the column up", "the whirlpool"})
				if (!lookout.been(way))
					throw new GameTestAssertException("He never went by " + way);
			if (!lookout.gotDown)
				throw new GameTestAssertException("He never got down from his horse");
		});
	}

	// ---------------------------------------------------------------- building

	/**
	 * The cells of a path going round (cx, cz) at radius {@code r}, anticlockwise from the east, a block at a time
	 * (never corner to corner), {@code length} cells long. Each cell is {x, z}.
	 */
	static List<int[]> helix(int cx, int cz, double r, int length) {
		List<int[]> cells = new ArrayList<>();
		int[] last = null;
		for (double angle = 0; cells.size() < length; angle += 0.005) {
			int x = (int) Math.floor(cx + 0.5 + r * Math.cos(angle)), z = (int) Math.floor(cz + 0.5 + r * Math.sin(angle));
			if (last != null && last[0] == x && last[1] == z)
				continue;
			if (last != null && last[0] != x && last[1] != z) {
				// Corner to corner: round by the cell further out, so the stair never leans into the core
				int[] a = {x, last[1]}, b = {last[0], z};
				last = Math.hypot(a[0] - cx, a[1] - cz) >= Math.hypot(b[0] - cx, b[1] - cz) ? a : b;
				cells.add(last);
				if (cells.size() >= length)
					break;
			}
			last = new int[] {x, z};
			cells.add(last);
		}
		return cells;
	}

	/**
	 * A round tower (a solid core of radius {@code r} - 1) with a stair one block wide winding up round it,
	 * {@code height} blocks, a block up every two blocks along: a full block, then a stair block (in one stretch
	 * of every two) or a bottom slab (in the other), a block higher. The stair starts on the ground at the tower's
	 * east side and comes out on the flat top. Returns the height a rider stands at on top.
	 */
	private static int spire(Scene scene, int cx, int cz, double r, int height) {
		int length = height * 2;
		List<int[]> path = helix(cx, cz, r, length + 1);
		int reach = (int) Math.ceil(r) + 1;
		for (int x = cx - reach; x <= cx + reach; x++)
			for (int z = cz - reach; z <= cz + reach; z++) {
				double distance = Math.hypot(x - cx, z - cz);
				if (distance <= r - 1)
					scene.fill(x, 1, z, x, height, z, STONE);
				else if (distance <= r + 1)
					scene.fill(x, height, z, x, height, z, STONE);
			}
		int stretch = Math.max(1, path.size() / 64);
		for (int i = 0; i < path.size(); i++) {
			int[] cell = path.get(i);
			int floor = i / 2;
			BlockState block;
			int y;
			if (i % 2 == 0) {
				block = STONE;
				y = floor;
			} else if ((i / stretch) % 2 == 0) {
				int[] before = path.get(i - 1);
				block = STAIRS.setValue(StairBlock.FACING, Direction.getNearest(cell[0] - before[0], 0, cell[1] - before[1]));
				y = floor + 1;
			} else {
				block = SLAB;
				y = floor + 1;
			}
			scene.fill(cell[0], y, cell[1], cell[0], y, cell[1], block);
			scene.fill(cell[0], y + 1, cell[1], cell[0], y + 3, cell[1], AIR);
		}
		return height + 1;
	}

	/** No monsters in the dark rooms (the tests are about finding the way, not fighting). */
	private static void quiet(Scene scene) {
		scene.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, scene.level.getServer());
	}

	/** A mailbox standing on the block below (x, y, z), its door facing this way, as {@link Scene#mailbox} but at any height. */
	private static BlockPos mailbox(Scene scene, int x, int y, int z, Direction facing, String address) {
		BlockState lower = ModBlocks.mailbox(DyeColor.RED).get().defaultBlockState().setValue(MailboxBlock.FACING, facing);
		scene.helper.setBlock(new BlockPos(x, y, z), lower);
		scene.helper.setBlock(new BlockPos(x, y + 1, z), lower.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER));
		BlockPos pos = scene.at(x, y, z);
		if (!(scene.level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox))
			throw new GameTestAssertException("No mailbox at " + pos);
		MailboxDirectory.Result named = mailbox.rename(address, scene.helper.makeMockPlayer(GameType.SURVIVAL));
		if (named != MailboxDirectory.Result.OK)
			throw new GameTestAssertException("Couldn't name the mailbox " + address + ": " + named);
		return pos;
	}

	/** A ladder from y0 up to y1 in the column (x, z), facing this way (its back to the solid block behind it). */
	private static void ladder(Scene scene, int x, int y0, int y1, int z, Direction facing) {
		scene.fill(x, y0, z, x, y1, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
	}

	/**
	 * A bubble column in the column (x, z) from y0 up to y1: over magma pulling down if {@code drag}, else over soul
	 * sand pushing up. The blocks beside it have to be solid (or ladders) all the way up, or the water runs out.
	 */
	private static void bubbleColumn(Scene scene, int x, int y0, int y1, int z, boolean drag) {
		scene.fill(x, y0 - 1, z, x, y0 - 1, z, (drag ? Blocks.MAGMA_BLOCK : Blocks.SOUL_SAND).defaultBlockState());
		scene.fill(x, y0, z, x, y1, z, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, drag));
	}

	// ---------------------------------------------------------------- watching

	/**
	 * Keeps an eye on the rider for what hills and holes can go wrong with, every tick, failing the trial at once:
	 * he was moved more than a few blocks in a tick (teleported), he was hurt, or he got off to walk up to the
	 * mailbox while well above or below it. Also notes which places he's been to, and whether he was on his horse
	 * there, for the test's own checks at the end.
	 */
	private static final class Lookout {
		/** Further than this in one tick isn't riding, or being thrown out of a bubble column: he was moved. */
		static final double JUMP = 6;
		/** How far above or below the mailbox he can be when he gets off to walk the last few steps to it. */
		static final double LEVEL_WITH_BOX = 3;

		private final String trial;
		private final Scene scene;
		private final Watch watch;
		private final int boxY;
		private final Map<String, int[]> places = new LinkedHashMap<>();
		private final Map<String, Boolean> visited = new LinkedHashMap<>();
		private final Map<String, Boolean> rode = new LinkedHashMap<>();
		@Nullable
		private Vec3 last;
		private boolean walkedUp;
		/** His horse was gone while he was on the road. */
		boolean gotDown;

		Lookout(String trial, Scene scene, Watch watch, int boxY) {
			this.trial = trial;
			this.scene = scene;
			this.watch = watch;
			this.boxY = boxY;
			scene.helper.onEachTick(this::look);
		}

		/** A place to note him passing through: these blocks, inclusive, in test coordinates. */
		void place(String name, int x0, int y0, int z0, int x1, int y1, int z1) {
			places.put(name, new int[] {x0, y0, z0, x1, y1, z1});
		}

		boolean been(String place) {
			return visited.getOrDefault(place, false);
		}

		boolean rodeAt(String place) {
			return rode.getOrDefault(place, false);
		}

		private void look() {
			if (watch.rider == null || !watch.rider.isAlive() || !scene.stillOut(watch.delivery)) {
				last = null;
				return;
			}
			Entity body = watch.rider.getVehicle() != null ? watch.rider.getVehicle() : watch.rider;
			Vec3 at = scene.helper.relativeVec(body.position());
			DeliveryRun.Stage stage = watch.run == null ? null : watch.run.stage();
			if (last != null && at.distanceTo(last) > JUMP)
				fail("he was moved from " + spot(last) + " to " + spot(at));
			last = at;
			if (watch.rider.getHealth() < watch.rider.getMaxHealth() - 0.01)
				fail("he was hurt at " + spot(at) + " (health " + watch.rider.getHealth() + ")");
			if (stage == DeliveryRun.Stage.WALKING_TO_BOX && !walkedUp) {
				walkedUp = true;
				if (Math.abs(at.y - boxY) > LEVEL_WITH_BOX)
					fail("he got off to walk up to the box from " + spot(at) + ", " + Math.round(Math.abs(at.y - boxY)) + " blocks "
						+ (at.y < boxY ? "below" : "above") + " it");
			}
			if (stage == DeliveryRun.Stage.RIDING && watch.setOff >= 0 && watch.rider.getHorse() == null)
				gotDown = true;
			int x = Mth.floor(at.x), y = Mth.floor(at.y + 1e-3), z = Mth.floor(at.z);
			for (Map.Entry<String, int[]> place : places.entrySet()) {
				int[] b = place.getValue();
				if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
					visited.put(place.getKey(), true);
					if (body != watch.rider)
						rode.put(place.getKey(), true);
				}
			}
		}

		private void fail(String trouble) {
			Trials.record(trial, watch, false);
			throw new GameTestAssertException(trial + ": " + trouble);
		}

		private static String spot(Vec3 at) {
			return String.format("%.1f %.1f %.1f", at.x, at.y, at.z);
		}
	}
}
