package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.route.LocalPlanner.Move;
import com.comedymonkey.postrider.content.postman.route.LocalPlanner.Step;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

class DoorsTest {
	private static final List<Step> HALL = List.of(
		new Step(0, 64, 0, false, Move.START),
		new Step(1, 64, 0, false, Move.WALK),
		new Step(2, 64, 0, false, Move.WALK),
		new Step(3, 64, 0, false, Move.WALK),
		new Step(4, 64, 0, false, Move.WALK),
		new Step(5, 64, 0, false, Move.WALK),
		new Step(6, 64, 0, false, Move.WALK),
		new Step(7, 64, 0, true, Move.MOUNT),
		new Step(8, 64, 0, true, Move.WALK));
	private static final BlockPos DOOR = new BlockPos(3, 64, 0), STABLE_DOOR = new BlockPos(7, 64, 0);
	private static final Set<BlockPos> DOORS = Set.of(DOOR, STABLE_DOOR);

	@Test
	void theDoorsJustAheadAreTheOnesHeOpens() {
		assertEquals(List.of(), Doors.ahead(HALL, 0, 8, DOORS::contains), "the door is three steps on: not yet");
		assertEquals(List.of(DOOR), Doors.ahead(HALL, 1, 8, DOORS::contains), "two steps on: open it");
		assertEquals(List.of(DOOR), Doors.ahead(HALL, 3, 8, DOORS::contains), "in the doorway");
		assertEquals(List.of(DOOR), Doors.ahead(HALL, 4, 8, DOORS::contains), "just through it: still held open");
		assertEquals(List.of(), Doors.ahead(HALL, 5, 8, DOORS::contains), "well past it");
	}

	@Test
	void aDoorAtTheEndOfTheStretchOrOnHorsebackIsNotOpened() {
		assertEquals(List.of(), Doors.ahead(HALL, 1, 2, DOORS::contains), "the stretch ends before the door");
		assertEquals(List.of(), Doors.ahead(HALL, 6, 8, DOORS::contains), "the stable door is on a step in the saddle: no horse goes through one");
	}

	@Test
	void heShutsADoorOnlyOnceHesClearOfIt() {
		assertFalse(Doors.clearOf(new Vec3(3.5, 64, 0.5), DOOR), "standing in it");
		assertFalse(Doors.clearOf(new Vec3(5.2, 64, 0.5), DOOR), "it would swing on his heels");
		assertTrue(Doors.clearOf(new Vec3(6.0, 64, 0.5), DOOR), "clear of it");
		assertTrue(Doors.clearOf(new Vec3(6.0, 70, 0.5), DOOR), "only how far along the ground counts");
	}
}
