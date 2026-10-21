package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PotionTest {
	@Test
	void heCarriesTwoPotionsADay() {
		assertEquals(2, PostmanEntity.POTIONS_PER_DAY);
		assertEquals(2, PostmanEntity.restocked(0, -1, 0), "a new rider sets out with a full pair");
		assertEquals(1, PostmanEntity.restocked(1, 5, 5), "no more until tomorrow");
		assertEquals(0, PostmanEntity.restocked(0, 5, 5));
		assertEquals(2, PostmanEntity.restocked(0, 5, 6), "restocked the next day");
		assertEquals(2, PostmanEntity.restocked(1, 5, 9), "never more than two, however many days pass");
	}

	@Test
	void eachDayStartsAtSixInTheMorning() {
		assertEquals(0, PostmanEntity.day(0));
		assertEquals(0, PostmanEntity.day(23999), "5:59am is still yesterday");
		assertEquals(1, PostmanEntity.day(24000));
		assertEquals(10, PostmanEntity.day(10 * 24000 + 12000));
	}

	@Test
	void heDrinksOnceHesBelowHalfHealth() {
		assertFalse(PostmanEntity.shouldDrink(20, 20, 2, false));
		assertFalse(PostmanEntity.shouldDrink(10, 20, 2, false), "half health is still fine");
		assertTrue(PostmanEntity.shouldDrink(9.5F, 20, 2, false));
		assertTrue(PostmanEntity.shouldDrink(1, 20, 1, false));
		assertFalse(PostmanEntity.shouldDrink(5, 20, 0, false), "none left");
		assertFalse(PostmanEntity.shouldDrink(5, 20, 2, true), "one at a time");
		assertFalse(PostmanEntity.shouldDrink(0, 20, 2, false), "too late");
	}

	@Test
	void eachPotionIsInstantHealthTwo() {
		// Instant Health heals 4 << amplifier: amplifier 1 (level II) is 8 health, 4 hearts
		assertEquals(4 << 1, PostmanEntity.POTION_HEALS, 1e-6);
		// Drinking takes as long as a player's 32-tick drink, and matches the animation
		assertEquals(32, PostmanEntity.DRINK_TICKS);
		assertEquals(PostmanEntity.Gesture.DRINK.seconds * 20, PostmanEntity.DRINK_TICKS, 1e-4);
	}

	@Test
	void twoPotionsCanPullHimBackFromTheBrink() {
		// From just under half health, both potions bring him back to full
		float health = 9;
		for (int i = 0; i < PostmanEntity.POTIONS_PER_DAY; i++)
			health = Math.min(20, health + PostmanEntity.POTION_HEALS);
		assertEquals(20, health, 1e-6);
	}
}
