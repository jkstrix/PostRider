package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.registry.ModEntities;

import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

class HorseSpotsTest {
	private static final Vec3 RIDER = new Vec3(10.5, 64, -20.5);

	@Test
	void triesBesideHimFirstThenBehindAndInFrontThenWhereHeStands() {
		List<Vec3> spots = HorseSpots.candidates(RIDER, 0);
		assertEquals(15, spots.size());
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
}
