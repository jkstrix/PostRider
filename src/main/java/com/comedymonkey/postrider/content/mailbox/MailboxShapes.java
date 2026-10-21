package com.comedymonkey.postrider.content.mailbox;

import java.util.Map;

import com.comedymonkey.postrider.content.HorizontalShapes;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Outlines of the two mailbox halves, matching the Blockbench model, for each way it can face. */
public final class MailboxShapes {
	// Boxes in pixels for a mailbox facing north (door at z = 0); the upper half's are relative to its own block
	private static final double[][] LOWER = {
		{6, 0, 6, 10, 16, 10},
		{5, 0, 5, 11, 1, 11}
	};
	private static final double[][] UPPER = {
		{6, 0, 6, 10, 5, 10},
		{4, 5, 0, 13, 14.5, 15}
	};

	private static final Map<Direction, VoxelShape> LOWER_SHAPES = HorizontalShapes.build(LOWER, Direction.NORTH);
	private static final Map<Direction, VoxelShape> UPPER_SHAPES = HorizontalShapes.build(UPPER, Direction.NORTH);

	private MailboxShapes() {}

	public static VoxelShape get(Direction facing, DoubleBlockHalf half) {
		return (half == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(facing);
	}

	/** One north-facing box, as {minX, minY, minZ, maxX, maxY, maxZ} in pixels, turned to face the given way. */
	public static double[] rotate(double[] box, Direction facing) {
		return HorizontalShapes.rotate(box, Direction.NORTH, facing);
	}
}
