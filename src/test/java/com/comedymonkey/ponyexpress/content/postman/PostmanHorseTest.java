package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.registry.ModEntities;

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
	void isHorseSizedAndHasHorseAttributes() {
		assertEquals(EntityType.HORSE.getWidth(), ModEntities.POSTMAN_HORSE.get().getWidth());
		assertEquals(EntityType.HORSE.getHeight(), ModEntities.POSTMAN_HORSE.get().getHeight());
		assertTrue(DefaultAttributes.hasSupplier(ModEntities.POSTMAN_HORSE.get()));
		assertTrue(DefaultAttributes.hasSupplier(ModEntities.POSTMAN.get()));
	}
}
