package com.comedymonkey.ponyexpress.content;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Block outlines drawn for one facing, turned to each of the four horizontal ways a block can face. */
public final class HorizontalShapes {
	private HorizontalShapes() {}

	/** One box, as {minX, minY, minZ, maxX, maxY, maxZ} in pixels, turned a quarter turn clockwise (seen from above). */
	public static double[] turnClockwise(double[] box) {
		return new double[] {16 - box[5], box[1], box[0], 16 - box[2], box[4], box[3]};
	}

	/** A box drawn for a model whose front faces {@code front}, turned so the front faces {@code facing}. */
	public static double[] rotate(double[] box, Direction front, Direction facing) {
		int turns = Math.floorMod(facing.get2DDataValue() - front.get2DDataValue(), 4);
		double[] turned = box.clone();
		for (int i = 0; i < turns; i++)
			turned = turnClockwise(turned);
		return turned;
	}

	/** The combined outline of the boxes for every horizontal facing. */
	public static Map<Direction, VoxelShape> build(double[][] boxes, Direction front) {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			VoxelShape shape = Shapes.empty();
			for (double[] box : boxes) {
				double[] r = rotate(box, front, facing);
				shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
			}
			shapes.put(facing, shape.optimize());
		}
		return shapes;
	}
}
