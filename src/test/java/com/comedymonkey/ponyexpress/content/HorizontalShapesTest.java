package com.comedymonkey.ponyexpress.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;

class HorizontalShapesTest {
	private static final double[] BOX = {1, 0, 2, 5, 4, 14};

	@Test
	void facingTheWayItWasDrawnChangesNothing() {
		assertArrayEquals(BOX, HorizontalShapes.rotate(BOX, Direction.SOUTH, Direction.SOUTH));
		assertArrayEquals(BOX, HorizontalShapes.rotate(BOX, Direction.NORTH, Direction.NORTH));
	}

	@Test
	void aQuarterTurnClockwiseMovesWestToNorth() {
		// A box against the west side (x = 1..5) ends up against the north side (z = 1..5)
		assertArrayEquals(new double[] {2, 0, 1, 14, 4, 5}, HorizontalShapes.turnClockwise(BOX));
	}

	@Test
	void turnsFollowTheCompassFromTheModelsFront() {
		assertArrayEquals(HorizontalShapes.turnClockwise(BOX), HorizontalShapes.rotate(BOX, Direction.SOUTH, Direction.WEST));
		assertArrayEquals(HorizontalShapes.rotate(BOX, Direction.NORTH, Direction.SOUTH), HorizontalShapes.rotate(BOX, Direction.SOUTH, Direction.NORTH));
		assertArrayEquals(HorizontalShapes.rotate(BOX, Direction.SOUTH, Direction.EAST),
			HorizontalShapes.turnClockwise(HorizontalShapes.turnClockwise(HorizontalShapes.turnClockwise(BOX))));
	}
}
