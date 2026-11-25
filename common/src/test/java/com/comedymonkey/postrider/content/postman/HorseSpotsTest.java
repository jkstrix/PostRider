package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.registry.ModEntities;

import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

class HorseSpotsTest {
	private static final Vec3 RIDER = new Vec3(10.5, 64, -20.5);

	@Test
	void triesBesideHimFirstThenBehindAndInFrontThenWhereHeStands() {
		List<Vec3> spots = HorseSpots.candidates(RIDER, 0);
		assertEquals(27, spots.size(), "five places and four footings, each at three heights");
		for (int i = 0; i < 12; i++)
			assertEquals(HorseSpots.DISTANCE, horizontal(spots.get(i)), 1e-6, "spot " + i);
		assertEquals(RIDER, spots.get(12));
	}

	@Test
	void triesLevelGroundBeforeUpOrDown() {
		List<Vec3> spots = HorseSpots.candidates(RIDER, 0);
		assertEquals(RIDER.y, spots.get(0).y);
		assertEquals(RIDER.y + 1, spots.get(1).y);
		assertEquals(RIDER.y - 1, spots.get(2).y);
	}

	@Test
	void skipsSpotsWithNoRoom() {
		List<Vec3> spots = HorseSpots.candidates(RIDER, 0);
		Vec3 behind = spots.get(6);
		assertEquals(behind, HorseSpots.find(RIDER, 0, behind::equals));
		assertNull(HorseSpots.find(RIDER, 0, spot -> false));
	}

	@Test
	void ridersSitInTheSaddleAsLowAsPlayers() {
		Vec3 rider = ModEntities.POSTMAN.get().getDimensions().attachments().get(EntityAttachment.VEHICLE, 0, 0);
		Vec3 player = EntityType.PLAYER.getDimensions().attachments().get(EntityAttachment.VEHICLE, 0, 0);
		assertEquals(player, rider);
	}

	private static double horizontal(Vec3 spot) {
		return Math.sqrt((spot.x - RIDER.x) * (spot.x - RIDER.x) + (spot.z - RIDER.z) * (spot.z - RIDER.z));
	}

	@Test
	void theHorseNeedsRoomForTheRiderOnItsBackToo() {
		net.minecraft.world.phys.AABB horse = new net.minecraft.world.phys.AABB(0, 64, 0, 1.4, 65.6, 1.4);
		net.minecraft.world.phys.AABB room = HorseSpots.roomFor(horse);
		assertEquals(64 + HorseSpots.RIDDEN_HEIGHT, room.maxY, 1e-9, "three blocks up: a rider sits higher than the horse's back");
		assertEquals(horse.minX, room.minX, 1e-9);
		assertEquals(horse.maxZ, room.maxZ, 1e-9);
		assertTrue(HorseSpots.RIDDEN_HEIGHT >= 2.7, "a horse and a rider");
	}

	@Test
	void onAStripOfIceTwoWideTheHorseIsPutDownSquareOnIt() {
		// He stands at the middle of block (74, -71); the footings two by two that take in his block have their middles on
		// the block corners round him, nearest first, and come after the usual spots
		Vec3 rider = new Vec3(74.3, 63, -70.6);
		List<Vec3> footings = HorseSpots.footings(rider);
		assertEquals(4, footings.size());
		assertEquals(new Vec3(74, 63, -71), footings.get(0), "the corner nearest him first");
		for (Vec3 footing : footings)
			assertTrue(footing.x == 74 || footing.x == 75, footing.toString());
		for (Vec3 footing : footings)
			assertTrue(footing.z == -71 || footing.z == -70, footing.toString());
		List<Vec3> spots = HorseSpots.candidates(rider, 0);
		assertTrue(spots.indexOf(rider) < spots.indexOf(footings.get(0)), "where he stands is tried before the footings");
		assertTrue(spots.contains(new Vec3(74, 64, -70)) && spots.contains(new Vec3(74, 62, -70)), "each footing a block up and down too");
	}
}
