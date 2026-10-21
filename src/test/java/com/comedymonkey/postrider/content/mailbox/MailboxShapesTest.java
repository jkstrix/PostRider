package com.comedymonkey.postrider.content.mailbox;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

class MailboxShapesTest {
	private static final double[] DOOR_SIDE = {4, 5, 0, 13, 14.5, 15};

	@Test
	void northIsTheModelAsDrawn() {
		assertArrayEquals(DOOR_SIDE, MailboxShapes.rotate(DOOR_SIDE, Direction.NORTH));
	}

	@Test
	void turningMovesTheDoorToTheFacingSide() {
		// The door is at z = 0 facing north; facing east it should sit at x = 16
		assertArrayEquals(new double[] {1, 5, 4, 16, 14.5, 13}, MailboxShapes.rotate(DOOR_SIDE, Direction.EAST));
		assertArrayEquals(new double[] {3, 5, 1, 12, 14.5, 16}, MailboxShapes.rotate(DOOR_SIDE, Direction.SOUTH));
		assertArrayEquals(new double[] {0, 5, 3, 15, 14.5, 12}, MailboxShapes.rotate(DOOR_SIDE, Direction.WEST));
	}

	@Test
	void fourTurnsComeBackToTheStart() {
		double[] box = DOOR_SIDE;
		for (int i = 0; i < 4; i++)
			box = MailboxShapes.rotate(box, Direction.EAST);
		assertArrayEquals(DOOR_SIDE, box);
	}

	@Test
	void everyFacingHasBothHalvesInsideTheirBlock() {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				var shape = MailboxShapes.get(facing, half);
				assertFalse(shape.isEmpty(), facing + " " + half);
				AABB bounds = shape.bounds();
				assertTrue(bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0, facing + " " + half);
				assertTrue(bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1, facing + " " + half);
			}
		}
	}
}
