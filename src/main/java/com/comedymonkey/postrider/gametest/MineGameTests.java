package com.comedymonkey.postrider.gametest;

import java.util.ArrayList;
import java.util.List;

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
 * one) from the top of a plateau as deep as the overworld is from the ground to the bedrock, down to a chamber on
 * the bedrock (a layer of it laid at the structure's bottom, so the scene is the same wherever the structure is
 * put), with a mailbox in it. The stair goes down in legs, turning back on itself at a landing at the end of each,
 * as a mine that deep must. Delivered to from the top, and from it back up to the top. Judged by
 * {@link Trials#deliver} and a {@link Lookout}: never moved, never hurt, and the stair walked, not ridden (it's a
 * block wide).
 */
@GameTestHolder(PostRider.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineGameTests {
	private static final int DELIVERY_TICKS = 6000;
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
	/** The plateau's top block: he stands on it a block higher. The overworld's ground is about this far above its bedrock. */
	static final int PLATEAU = 125;
	/** The chamber's floor is bedrock at the structure's bottom: he stands on it at 1. */
	static final int BEDROCK_FLOOR = 1;
	/** The plateau: this square of the arena, so there's room to ride about on top. */
	private static final int PLATEAU_X0 = 24, PLATEAU_X1 = 71, PLATEAU_Z0 = 24, PLATEAU_Z1 = 71;
	/** Where the stair goes in at the top, the rows its legs run along (one each way), and how long a leg is. */
	static final int STAIR_X = 30, STAIR_Z = 48, BACK_Z = 51, LEG = 40;
	/** The stair's landings, at either end, are at these x. */
	static final int EAST_LANDING = STAIR_X + LEG, WEST_LANDING = STAIR_X - 1;

	private MineGameTests() {}

	@GameTest(skyAccess = true, template = "gametest/mine_96x96", batch = "mine_down", timeoutTicks = DELIVERY_TICKS)
	public static void heClimbsDownTheMineStairToTheBoxOnTheBedrock(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		Chamber chamber = mine(scene);
		String fromName = Scene.address("GT Mine A"), toName = Scene.address("GT Mine B");
		BlockPos from = scene.mailbox(40, PLATEAU + 1, 60, Direction.WEST, fromName);
		BlockPos to = scene.mailbox(chamber.boxX(), BEDROCK_FLOOR, chamber.boxZ(), chamber.boxFacing(), toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Mine down", scene, watch, BEDROCK_FLOOR);
		lookout.place("the stair", STAIR_X, BEDROCK_FLOOR + 1, STAIR_Z - 2, EAST_LANDING, PLATEAU - 2, BACK_Z + 2);
		Trials.deliver("Mine down", scene, to, watch, () -> judge(lookout));
	}

	@GameTest(skyAccess = true, template = "gametest/mine_96x96", batch = "mine_up", timeoutTicks = DELIVERY_TICKS)
	public static void heClimbsUpTheMineStairFromTheBoxOnTheBedrock(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		quiet(scene);
		Chamber chamber = mine(scene);
		String fromName = Scene.address("GT Pit A"), toName = Scene.address("GT Pit B");
		BlockPos from = scene.mailbox(chamber.boxX(), BEDROCK_FLOOR, chamber.boxZ(), chamber.boxFacing(), fromName);
		BlockPos to = scene.mailbox(40, PLATEAU + 1, 60, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		Lookout lookout = new Lookout("Mine up", scene, watch, PLATEAU + 1);
		lookout.place("the stair", STAIR_X, BEDROCK_FLOOR + 1, STAIR_Z - 2, EAST_LANDING, PLATEAU - 2, BACK_Z + 2);
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

	/** The chamber at the foot of the stair: where its mailbox stands, facing back towards the stair. */
	record Chamber(int boxX, int boxZ, Direction boxFacing) {}

	/**
	 * The cells of the stair from the top down, each {x, z, feet}: from the hole in the plateau's top at
	 * {@link #STAIR_X}, a leg east along {@link #STAIR_Z} a block down for every block along, a flat landing across
	 * to {@link #BACK_Z} at {@link #EAST_LANDING}, a leg back west along that row, a landing at {@link #WEST_LANDING},
	 * and so on down to {@link #BEDROCK_FLOOR}.
	 */
	static List<int[]> stair() {
		List<int[]> cells = new ArrayList<>();
		int feet = PLATEAU, x = STAIR_X, z = STAIR_Z, dir = 1;
		while (feet >= BEDROCK_FLOOR) {
			for (int i = 0; i < LEG && feet >= BEDROCK_FLOOR; i++) {
				cells.add(new int[] {x, z, feet});
				x += dir;
				feet--;
			}
			if (feet < BEDROCK_FLOOR)
				break;
			// The landing: a block lower than the leg's last step, flat across to the other row
			int zTo = z == STAIR_Z ? BACK_Z : STAIR_Z, zStep = zTo > z ? 1 : -1;
			for (int zz = z; zz != zTo + zStep; zz += zStep)
				cells.add(new int[] {x, zz, feet});
			z = zTo;
			dir = -dir;
			x += dir;
			feet--;
		}
		return cells;
	}

	/**
	 * Bedrock over the whole arena's bottom, the plateau of stone on it with grass on top, and the mine: the stair
	 * (one wide, three high) cut through the stone, and at its foot a chamber nine square and four high on the
	 * bedrock, ahead of the stair's last step.
	 */
	private static Chamber mine(Scene scene) {
		scene.fill(0, 0, 0, 95, 0, 95, BEDROCK);
		scene.fill(PLATEAU_X0, 1, PLATEAU_Z0, PLATEAU_X1, PLATEAU - 1, PLATEAU_Z1, STONE);
		scene.fill(PLATEAU_X0, PLATEAU, PLATEAU_Z0, PLATEAU_X1, PLATEAU, PLATEAU_Z1, GRASS);
		List<int[]> cells = stair();
		for (int[] cell : cells)
			scene.fill(cell[0], cell[2], cell[1], cell[0], cell[2] + 2, cell[1], AIR);
		int[] last = cells.get(cells.size() - 1), before = cells.get(cells.size() - 2);
		int dir = Integer.signum(last[0] - before[0]);
		int x0 = dir > 0 ? last[0] + 1 : last[0] - 9, x1 = x0 + 8;
		scene.fill(x0, BEDROCK_FLOOR, last[1] - 4, x1, BEDROCK_FLOOR + 3, last[1] + 4, AIR);
		return new Chamber(dir > 0 ? x1 - 2 : x0 + 2, last[1], dir > 0 ? Direction.WEST : Direction.EAST);
	}
}
