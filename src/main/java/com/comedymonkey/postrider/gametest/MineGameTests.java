package com.comedymonkey.postrider.gametest;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.gametest.ClimbingGameTests.Lookout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A mine: a stair of blocks one wide at forty-five degrees (a block down for every block along, as a miner digs
 * one) from the top of a plateau down to a chamber on the bedrock, with a mailbox in it. Delivered to from the top,
 * and from it back up to the top. Judged by {@link Trials#deliver} and a {@link Lookout}: never moved, never hurt,
 * and the stair walked, not ridden (it's a block wide).
 */
@GameTestHolder(PostRider.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineGameTests {
	private static final int DELIVERY_TICKS = 6000;
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/** The plateau's top block: he stands on it a block higher. */
	private static final int PLATEAU = 24;
	/** The chamber's floor is the test world's bedrock, four below the structure's bottom: he stands on it at -3. */
	private static final int BEDROCK_FLOOR = -3;
	private static final int STAIR_X = 30, STAIR_Z = 48;

	private MineGameTests() {}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "mine_down", timeoutTicks = DELIVERY_TICKS)
	public static void heClimbsDownTheMineStairToTheBoxOnTheBedrock(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		int bottom = mine(scene);
		String fromName = Scene.address("GT Mine A"), toName = Scene.address("GT Mine B");
		BlockPos from = scene.mailbox(10, PLATEAU + 1, STAIR_Z, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(bottom + 6, BEDROCK_FLOOR, STAIR_Z, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Mine down", scene, watch, BEDROCK_FLOOR);
		lookout.place("the stair", STAIR_X + 2, BEDROCK_FLOOR, STAIR_Z, bottom - 2, PLATEAU, STAIR_Z);
		Trials.deliver("Mine down", scene, to, watch, () -> judge(lookout));
	}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "mine_up", timeoutTicks = DELIVERY_TICKS)
	public static void heClimbsUpTheMineStairFromTheBoxOnTheBedrock(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		int bottom = mine(scene);
		String fromName = Scene.address("GT Pit A"), toName = Scene.address("GT Pit B");
		BlockPos from = scene.mailbox(bottom + 6, BEDROCK_FLOOR, STAIR_Z, Direction.WEST, fromName);
		BlockPos to = scene.mailbox(10, PLATEAU + 1, STAIR_Z, Direction.EAST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Mine up", scene, watch, PLATEAU + 1);
		lookout.place("the stair", STAIR_X + 2, BEDROCK_FLOOR, STAIR_Z, bottom - 2, PLATEAU, STAIR_Z);
		Trials.deliver("Mine up", scene, to, watch, () -> judge(lookout));
	}

	private static void judge(Lookout lookout) {
		if (!lookout.been("the stair"))
			throw new GameTestAssertException("He never went by the stair");
		if (lookout.rodeAt("the stair"))
			throw new GameTestAssertException("He rode a stair one block wide");
		if (!lookout.gotDown)
			throw new GameTestAssertException("He never got down from his horse");
	}

	/** No monsters in the dark (the tests are about finding the way, not fighting). */
	private static void quiet(Scene scene) {
		scene.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, scene.level.getServer());
	}

	/**
	 * A stone plateau over the whole arena, grass on top, and the mine: from a hole in the top at {@link #STAIR_X}
	 * a stair one wide and three high going down a block for every block east, through the stone and the ground
	 * under it to the bedrock, where it opens into a chamber nine square and four high. Returns the x the stair
	 * reaches the chamber at.
	 */
	private static int mine(Scene scene) {
		// From the structure's own bottom (the test world's ground is a block below it), so there's no gap under it
		scene.fill(0, 0, 0, 95, PLATEAU - 1, 95, STONE);
		scene.fill(0, PLATEAU, 0, 95, PLATEAU, 95, GRASS);
		int feet = PLATEAU, x = STAIR_X;
		while (feet > BEDROCK_FLOOR) {
			scene.fill(x, feet, STAIR_Z, x, feet + 2, STAIR_Z, AIR);
			feet--;
			x++;
		}
		scene.fill(x, BEDROCK_FLOOR, STAIR_Z - 4, x + 8, BEDROCK_FLOOR + 3, STAIR_Z + 4, AIR);
		return x;
	}
}
