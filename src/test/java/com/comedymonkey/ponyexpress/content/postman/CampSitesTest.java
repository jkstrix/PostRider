package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.post.PendingDelivery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

class CampSitesTest {
	private static final BlockPos HERE = new BlockPos(0, 64, 0);

	@Test
	void pitchesRightWhereHeStandsOnFlatGround() {
		CampSites.Site site = CampSites.find(HERE, (x, z) -> 64);
		assertNotNull(site);
		assertEquals(HERE, site.seat());
	}

	@Test
	void theTentGoesBehindHisSeatWithinThePitch() {
		CampSites.Site site = new CampSites.Site(HERE, Direction.SOUTH);
		Vec3 tent = site.tentPos();
		assertEquals(-DeliveryRun.TENT_DISTANCE, tent.z - site.seatPos().z, 1e-9);
		assertTrue(DeliveryRun.TENT_DISTANCE + 1.7 <= CampSites.TENT_LENGTH + 0.5, "the back pegs fit the pitch");
		assertEquals(Direction.SOUTH.toYRot(), site.yaw());
	}

	@Test
	void wontPitchOnTheLipOfARavine() {
		// A ravine running north-south at x = 1..3: nothing near it is level enough on both sides
		CampSites.Floor ravine = (x, z) -> x >= 1 && x <= 3 ? 40 : 64;
		CampSites.Site site = CampSites.find(HERE, ravine);
		assertNotNull(site, "there's flat ground further from the edge");
		int x = site.seat().getX();
		// At least two clear blocks between him (or his tent) and the drop
		assertTrue(x <= -2 || x >= 6, "seat at x = " + x + " is too near the ravine");
	}

	@Test
	void wontPitchAcrossAStep() {
		assertNull(CampSites.check(0, 0, Direction.NORTH, (x, z) -> z > 1 ? 65 : 64));
	}

	@Test
	void aGentleSlopeAroundThePitchIsFine() {
		assertNotNull(CampSites.check(0, 0, Direction.NORTH, (x, z) -> Math.abs(x) == 2 ? 65 : 64));
	}

	@Test
	void campsInTheOpenWithNowhereFlatEnough() {
		assertNull(CampSites.find(HERE, (x, z) -> CampSites.UNUSABLE));
		assertNull(CampSites.find(HERE, (x, z) -> (x + z) % 2 == 0 ? 64 : 66));
	}

	@Test
	void picksTheNearestPitch() {
		CampSites.Site site = CampSites.find(HERE, (x, z) -> x >= 5 ? 64 : CampSites.UNUSABLE);
		assertNotNull(site);
		assertTrue(site.seat().getX() <= 8);
	}

	@Test
	void anEveningInProgressSurvivesSaving() {
		var registries = VanillaRegistries.createLookup();
		PendingDelivery delivery = new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Ranch",
			GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), "Inn", List.of(new ItemStack(Items.BREAD)), 0);
		DeliveryRun run = new DeliveryRun(delivery, GlobalPos.of(Level.OVERWORLD, new BlockPos(900, 64, 0)));
		assertNotNull(DeliveryRun.load(run.save(registries), registries));
	}
}
