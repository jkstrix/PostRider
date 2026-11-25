package com.comedymonkey.postrider.gametest;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Doors on his way: he opens them (iron ones too, with no button) and goes through on foot, and leaves each as he
 * found it, shut or standing open.
 */
public final class DoorGameTests {
	private static final int DELIVERY_TICKS = 4000;
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();

	private DoorGameTests() {}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "doors", timeoutTicks = DELIVERY_TICKS)
	public static void heOpensTheDoorsOnHisWayAndLeavesThemAsHeFoundThem(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 95, 95);
		// Walled in, with two walls right across his way, three high: an iron door shut in the first, and a wooden
		// door standing open in the second
		scene.fill(0, 1, 0, 95, 3, 0, STONE);
		scene.fill(0, 1, 95, 95, 3, 95, STONE);
		scene.fill(0, 1, 0, 0, 3, 95, STONE);
		scene.fill(95, 1, 0, 95, 3, 95, STONE);
		scene.fill(40, 1, 1, 40, 3, 94, STONE);
		scene.fill(60, 1, 1, 60, 3, 94, STONE);
		BlockPos iron = door(scene, 40, 48, Blocks.IRON_DOOR, false);
		BlockPos wooden = door(scene, 60, 48, Blocks.OAK_DOOR, true);
		String fromName = Scene.address("GT Doors A"), toName = Scene.address("GT Doors B");
		BlockPos from = scene.mailbox(20, 48, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(80, 48, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		boolean[] openedTheIronDoor = {false};
		helper.onEachTick(() -> {
			if (helper.getBlockState(iron).getValue(DoorBlock.OPEN))
				openedTheIronDoor[0] = true;
		});
		Trials.deliver("Doors", scene, to, watch, () -> {
			if (!openedTheIronDoor[0])
				throw new GameTestAssertException("He never opened the iron door");
			if (helper.getBlockState(iron).getValue(DoorBlock.OPEN))
				throw new GameTestAssertException("He left the iron door open");
			if (!helper.getBlockState(wooden).getValue(DoorBlock.OPEN))
				throw new GameTestAssertException("He shut the wooden door he found standing open");
		});
	}

	/** A door in the wall at (x, z), its two halves, hinged to open east; the lower half's position. */
	private static BlockPos door(Scene scene, int x, int z, Block block, boolean open) {
		BlockState lower = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, open);
		scene.helper.setBlock(new BlockPos(x, 1, z), lower);
		scene.helper.setBlock(new BlockPos(x, 2, z), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		return new BlockPos(x, 1, z);
	}
}
