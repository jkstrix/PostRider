package com.comedymonkey.ponyexpress.content.mailbox;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.Shapes;
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

	private static final Map<Direction, VoxelShape> LOWER_SHAPES = build(LOWER);
	private static final Map<Direction, VoxelShape> UPPER_SHAPES = build(UPPER);

	private MailboxShapes() {}

	public static VoxelShape get(Direction facing, DoubleBlockHalf half) {
		return (half == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(facing);
	}

	/** One north-facing box, as {minX, minY, minZ, maxX, maxY, maxZ} in pixels, turned to face the given way. */
	public static double[] rotate(double[] box, Direction facing) {
		double x1 = box[0], y1 = box[1], z1 = box[2], x2 = box[3], y2 = box[4], z2 = box[5];
		return switch (facing) {
			case EAST -> new double[] {16 - z2, y1, x1, 16 - z1, y2, x2};
			case SOUTH -> new double[] {16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1};
			case WEST -> new double[] {z1, y1, 16 - x2, z2, y2, 16 - x1};
			default -> box.clone();
		};
	}

	private static Map<Direction, VoxelShape> build(double[][] boxes) {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			VoxelShape shape = Shapes.empty();
			for (double[] box : boxes) {
				double[] r = rotate(box, facing);
				shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
			}
			shapes.put(facing, shape.optimize());
		}
		return shapes;
	}
}
