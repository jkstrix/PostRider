package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;

class CrossingTest {
	/** Land for x < 10, a lake from x 10 on, its surface at 62.875. */
	private static final Crossing.Water LAKE_EAST = (x, z) -> x >= 10 ? 62.875 : Double.NaN;

	@Test
	void theBargeIsLaunchedOnOpenWaterAheadWithRoomAllRound() {
		Vec3 rider = new Vec3(6.5, 64, 0.5), target = new Vec3(200, 64, 0.5);
		Vec3 launch = Crossing.launchSpot(rider, target, LAKE_EAST);
		assertNotNull(launch);
		// Two blocks clear of the bank (x 10) so the barge fits
		assertEquals(12.5, launch.x, 1e-9);
		assertEquals(62.875, launch.y, 1e-9);
		assertEquals(0.5, launch.z, 1e-9);
	}

	@Test
	void noLaunchWithoutWaterAheadOrRoomForTheBarge() {
		Vec3 rider = new Vec3(6.5, 64, 0.5);
		assertNull(Crossing.launchSpot(rider, new Vec3(-200, 64, 0.5), LAKE_EAST), "the water is behind him");
		Crossing.Water creek = (x, z) -> x >= 10 && Math.abs(z) <= 1 ? 62.875 : Double.NaN;
		assertNull(Crossing.launchSpot(rider, new Vec3(200, 64, 0.5), creek), "a creek too narrow for the barge");
		Crossing.Water farLake = (x, z) -> x >= 40 ? 62.875 : Double.NaN;
		assertNull(Crossing.launchSpot(rider, new Vec3(200, 64, 0.5), farLake), "no water close enough yet");
	}

	@Test
	void onTheWaterHeSteersStraightForTheFarShore() {
		Vec3 from = new Vec3(0, 63, 0), target = new Vec3(0, 63, -300);
		assertEquals(Journey.nextAim(from, target), Crossing.waterAim(from, target, a -> false, a -> true, a -> false));
		assertEquals(Journey.nextAim(from, target), Crossing.waterAim(from, target, a -> true, a -> false, a -> false),
			"land ahead: run in to the shore");
	}

	@Test
	void heGivesOceanMonumentsAWideBerth() {
		Vec3 from = new Vec3(0, 63, 0), target = new Vec3(0, 63, -300);
		Vec3 straight = Journey.nextAim(from, target);
		Vec3 aim = Crossing.waterAim(from, target, a -> false, a -> true, a -> a.distanceTo(straight) < 1);
		assertNotNull(aim);
		assertFalse(aim.distanceTo(straight) < 1, "swerves round the monument");
		assertNull(Crossing.waterAim(from, target, a -> false, a -> true, a -> true), "monuments every way");
		assertTrue(Crossing.MONUMENT_BERTH >= 16, "out of a guardian's reach");
	}

	@Test
	void heSteersClearOfDrowned() {
		Vec3 from = new Vec3(0, 63, 0), target = new Vec3(0, 63, -300);
		Vec3 straight = Journey.nextAim(from, target);
		List<Vec3> drowned = List.of(straight.add(3, -2, 0));
		assertTrue(Crossing.nearAny(straight, drowned, Crossing.DROWNED_BERTH));
		Vec3 aim = Crossing.waterAim(from, target, a -> false, a -> true, a -> Crossing.nearAny(a, drowned, Crossing.DROWNED_BERTH));
		assertNotNull(aim);
		assertTrue(Journey.horizontalDistance(aim, drowned.get(0)) >= Crossing.DROWNED_BERTH, "round them, not past them");
		assertFalse(Crossing.nearAny(straight, List.of(), Crossing.DROWNED_BERTH));
	}

	@Test
	void heStepsAshoreOnTheGroundNearestTheBow() {
		// Dry ground from x 20 on, at y 64; the bow at x 18.5
		CampSites.Floor bank = (x, z) -> x >= 20 ? 64 : CampSites.UNUSABLE;
		BlockPos spot = Crossing.landingSpot(new Vec3(18.5, 63, 0.5), new Vec3(300, 64, 0.5), bank);
		assertEquals(new BlockPos(20, 64, 0), spot, "the nearest dry block, straight towards the destination");
		assertNull(Crossing.landingSpot(new Vec3(0.5, 63, 0.5), new Vec3(300, 64, 0.5), bank), "no land within reach of the bow");
	}

	@Test
	void theWaterIsDangerousToo() {
		// Drowned (tridents and all) and guardians hunt riders like players; pufferfish sting him as they would anyone
		assertTrue(RiderHunting.huntsRiders(Drowned.class));
		assertTrue(RiderHunting.huntsRiders(Guardian.class));
		assertTrue(RiderHunting.huntsRiders(ElderGuardian.class));
	}
}
