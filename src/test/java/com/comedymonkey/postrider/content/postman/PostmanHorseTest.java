package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import com.comedymonkey.postrider.registry.ModEntities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

class PostmanHorseTest {
	@Test
	void theSlowestPossibleHorseIsRaisedToSevenBlocksASecond() {
		double speed = PostmanHorse.rollSpeed(() -> 0.0);
		assertEquals(PostmanHorse.MIN_SPEED, speed, 1e-9);
		assertEquals(7.0, PostmanHorse.blocksPerSecond(speed), 1e-9);
	}

	@Test
	void theFastestStaysAsFastAsVanilla() {
		double speed = PostmanHorse.rollSpeed(() -> 1.0);
		assertEquals(0.3375, speed, 1e-6);
		assertTrue(PostmanHorse.blocksPerSecond(speed) > 14);
	}

	@Test
	void everyRolledHorseIsAtLeastSevenBlocksASecond() {
		Random random = new Random(1860);
		double slowest = Double.MAX_VALUE, fastest = 0;
		for (int i = 0; i < 10_000; i++) {
			double speed = PostmanHorse.rollSpeed(random::nextDouble);
			slowest = Math.min(slowest, speed);
			fastest = Math.max(fastest, speed);
		}
		assertTrue(PostmanHorse.blocksPerSecond(slowest) >= 7.0, "slowest was " + PostmanHorse.blocksPerSecond(slowest));
		assertTrue(fastest <= 0.3375 + 1e-9);
		assertTrue(fastest - slowest > 0.1, "speeds should still vary");
	}

	@Test
	void gallopsFlatOutWhenItsMarkIsFarOff() {
		assertEquals(1.0, PostmanHorse.gallopInput(0.25, 10), 1e-9);
	}

	@Test
	void easesOffCloseToItsMark() {
		assertEquals(0.5, PostmanHorse.gallopInput(0.25, PostmanHorse.EASE_OFF_DISTANCE / 2), 1e-9);
		assertEquals(0.25, PostmanHorse.gallopInput(0.25, 0.1), 1e-9);
	}

	@Test
	void standsStillOrBacksUpAsAskedWithoutGalloping() {
		assertEquals(0.0, PostmanHorse.gallopInput(0.0, 10), 1e-9);
		assertEquals(-0.2, PostmanHorse.gallopInput(-0.2, 10), 1e-9);
	}

	@Test
	void onFootHeRunsAboutAsFastAsASprintingPlayer() {
		// A mob's push and pace both scale with its speed, so its pace goes with speed squared; a sprinting player's
		// goes with 0.13 (walking speed 0.1, sprinting 30% faster) at full push
		double pace = Math.pow(0.3 * DeliveryRun.RUN_SPEED, 2);
		assertEquals(0.13, pace, 0.01);
	}

	@Test
	void isHorseSizedAndHasHorseAttributes() {
		assertEquals(EntityType.HORSE.getWidth(), ModEntities.POSTMAN_HORSE.get().getWidth());
		assertEquals(EntityType.HORSE.getHeight(), ModEntities.POSTMAN_HORSE.get().getHeight());
		assertTrue(DefaultAttributes.hasSupplier(ModEntities.POSTMAN_HORSE.get()));
		assertTrue(DefaultAttributes.hasSupplier(ModEntities.POSTMAN.get()));
	}

	@Test
	void heGallopsAllTheWayAlongAPathAndOnlyEasesOffNearItsEnd() {
		Vec3 here = new Vec3(0, 64, 0);
		// The next step is a block ahead, but the path runs on for 30: full gallop
		assertEquals(30.0, PostmanHorse.distanceToGo(here, new Vec3(30, 64, 0), new Vec3(1, 64, 0)), 1e-9);
		assertEquals(1.0, PostmanHorse.gallopInput(0.25, PostmanHorse.distanceToGo(here, new Vec3(30, 64, 0), new Vec3(1, 64, 0))), 1e-9);
		// Near the end of it he eases off
		assertTrue(PostmanHorse.gallopInput(0.25, PostmanHorse.distanceToGo(here, new Vec3(1.5, 64, 0), new Vec3(1, 64, 0))) < 1.0);
		// With no path, the mark he's steering for is all there is
		assertEquals(5.0, PostmanHorse.distanceToGo(here, null, new Vec3(3, 64, 4)), 1e-9);
	}
}
