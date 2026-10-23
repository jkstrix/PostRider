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

	@Test
	void onFootHeRunsLikeASprintingPlayerWellBehindAnyHorse() {
		double sprinting = PostmanEntity.BASE_SPEED * PostmanEntity.SPRINT_BOOST;
		double running = PostmanEntity.blocksPerSecond(DeliveryRun.RUN_SPEED, sprinting);
		assertEquals(DeliveryRun.RUN_BLOCKS_PER_SECOND, running, 1e-6);
		assertTrue(running < PostmanHorse.MIN_BLOCKS_PER_SECOND - 1, "his slowest horse is quicker than his legs");
		// Walking up to a mailbox (not sprinting, at the usual move speed) is about a player's walk
		assertEquals(4.0, PostmanEntity.blocksPerSecond(1.0, PostmanEntity.BASE_SPEED), 0.3);
		// The sums: twice the move speed is four times as fast
		assertEquals(4 * PostmanEntity.blocksPerSecond(0.5, 0.3), PostmanEntity.blocksPerSecond(1.0, 0.3), 1e-9);
	}

	@Test
	void goingDownTheHorseIsHeldToAWalk() {
		assertEquals(1.0, PostmanHorse.gallopInput(0.2, 30, false), 1e-9, "flat out on the level");
		assertEquals(PostmanHorse.WALK_INPUT, PostmanHorse.gallopInput(0.2, 30, true), 1e-9, "a walk going down");
		assertTrue(PostmanHorse.WALK_INPUT < 0.5, "a walk, not a canter");
		assertEquals(0, PostmanHorse.gallopInput(0, 30, true), 1e-9, "standing still is standing still");
	}
}
