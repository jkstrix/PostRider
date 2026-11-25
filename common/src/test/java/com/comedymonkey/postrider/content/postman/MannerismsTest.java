package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.PostmanEntity.Gesture;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

class MannerismsTest {
	private static final Mannerisms.Situation ORDINARY = new Mannerisms.Situation(true, false, false, false, false, false, false, 300);

	private static Mannerisms.Situation with(boolean riding, boolean hot, boolean cold, boolean raining, boolean evening, boolean buggy,
		boolean detouring, double toGo) {
		return new Mannerisms.Situation(riding, hot, cold, raining, evening, buggy, detouring, toGo);
	}

	private static double share(Gesture gesture, Mannerisms.Situation situation) {
		double total = 0;
		for (Gesture each : Gesture.values())
			total += Mannerisms.weight(each, situation);
		return Mannerisms.weight(gesture, situation) / total;
	}

	@Test
	void workAndGreetingsAreNeverPickedAtRandom() {
		for (Gesture gesture : new Gesture[] {Gesture.PICKUP, Gesture.DELIVER, Gesture.WHISTLE, Gesture.DRINK, Gesture.WAVE, Gesture.TIP_HAT, Gesture.READ_LETTER})
			assertEquals(0, Mannerisms.weight(gesture, ORDINARY), gesture.name());
		RandomSource random = RandomSource.create(1);
		for (int i = 0; i < 2000; i++) {
			Gesture picked = Mannerisms.pick(ORDINARY, null, random);
			assertNotNull(picked);
			assertTrue(Mannerisms.weight(picked, ORDINARY) > 0, picked.name());
		}
	}

	@Test
	void heNeverDoesTheSameThingTwiceRunning() {
		RandomSource random = RandomSource.create(2);
		Gesture last = null;
		for (int i = 0; i < 2000; i++) {
			Gesture picked = Mannerisms.pick(ORDINARY, last, random);
			assertNotEquals(last, picked);
			last = picked;
		}
	}

	@Test
	void everyEverydayMannerismTurnsUpEventually() {
		Map<Gesture, Integer> seen = new EnumMap<>(Gesture.class);
		RandomSource random = RandomSource.create(3);
		Mannerisms.Situation anything = with(true, true, true, true, true, true, true, 1000);
		for (int i = 0; i < 20000; i++)
			seen.merge(Mannerisms.pick(anything, null, random), 1, Integer::sum);
		for (Gesture gesture : Gesture.values())
			if (Mannerisms.weight(gesture, anything) > 0)
				assertTrue(seen.containsKey(gesture), gesture.name());
		assertEquals(18, seen.size(), "every random mannerism (all but greetings and the address check)");
	}

	@Test
	void theWeatherAndTheLandMakeTheirMark() {
		Mannerisms.Situation desert = with(true, true, false, false, false, false, false, 300);
		assertTrue(share(Gesture.WIPE_BROW, desert) > 3 * share(Gesture.WIPE_BROW, ORDINARY), "sweating in the desert");
		assertTrue(share(Gesture.SWIG, desert) > share(Gesture.SWIG, ORDINARY));
		assertEquals(0, Mannerisms.weight(Gesture.SHIVER, ORDINARY), "only shivers in the cold");
		assertTrue(Mannerisms.weight(Gesture.SHIVER, with(true, false, true, false, false, false, false, 300)) > 0);
		assertEquals(0, Mannerisms.weight(Gesture.PULL_HAT_DOWN, ORDINARY), "hat down for the rain");
		assertTrue(share(Gesture.SWAT_FLY, with(true, false, false, false, false, true, false, 300)) > 3 * share(Gesture.SWAT_FLY, ORDINARY), "swamps are buggy");
	}

	@Test
	void theDayAndTheRoadMakeTheirMarkToo() {
		assertTrue(share(Gesture.YAWN, with(true, false, false, false, true, false, false, 300)) > 5 * share(Gesture.YAWN, ORDINARY), "tired by evening");
		assertTrue(share(Gesture.SCRATCH_HEAD, with(true, false, false, false, false, false, true, 300)) > 5 * share(Gesture.SCRATCH_HEAD, ORDINARY), "puzzled by a detour");
		assertTrue(share(Gesture.SHADE_EYES, with(true, false, false, false, false, false, false, 2000)) > share(Gesture.SHADE_EYES, ORDINARY), "scanning the horizon on a long ride");
		assertEquals(0, Mannerisms.weight(Gesture.PAT_HORSE, with(false, false, false, false, false, false, false, 300)), "no horse to pat on foot");
		assertTrue(Mannerisms.weight(Gesture.PAT_HORSE, ORDINARY) > 0);
	}

	@Test
	void theQuietBetweenMannerismsIsTwentyToFortyFiveSeconds() {
		RandomSource random = RandomSource.create(4);
		for (int i = 0; i < 1000; i++) {
			int gap = Mannerisms.gap(random);
			assertTrue(gap >= 20 * 20 && gap <= 45 * 20, String.valueOf(gap));
		}
	}

	@Test
	void heGreetsEachPassingPlayerOncePerEncounter() {
		Set<UUID> greeted = new HashSet<>();
		UUID chris = UUID.randomUUID();
		assertFalse(Mannerisms.greets(greeted, chris, 20 * 20, Mannerisms.GREET_RANGE, true), "too far to greet");
		assertFalse(Mannerisms.greets(greeted, chris, 10 * 10, Mannerisms.GREET_RANGE, false), "out of sight");
		assertTrue(Mannerisms.greets(greeted, chris, 10 * 10, Mannerisms.GREET_RANGE, true), "hello!");
		assertFalse(Mannerisms.greets(greeted, chris, 5 * 5, Mannerisms.GREET_RANGE, true), "already greeted");

		// Still nearby: remembered
		Mannerisms.forget(greeted, Map.of(chris, 25.0 * 25));
		assertFalse(Mannerisms.greets(greeted, chris, 5 * 5, Mannerisms.GREET_RANGE, true));
		// Wandered off and back again: a fresh greeting
		Mannerisms.forget(greeted, Map.of(chris, 40.0 * 40));
		assertTrue(Mannerisms.greets(greeted, chris, 10 * 10, Mannerisms.GREET_RANGE, true));
		// Gone altogether
		Mannerisms.forget(greeted, Map.of());
		assertTrue(greeted.isEmpty());
	}

	@Test
	void villagersAreGreetedCloserAndOnlySometimes() {
		assertTrue(Mannerisms.VILLAGER_RANGE < Mannerisms.GREET_RANGE);
		assertTrue(Mannerisms.VILLAGER_GREET_CHANCE > 0 && Mannerisms.VILLAGER_GREET_CHANCE < 1);
	}

	@Test
	void goingRoundSomethingCountsAsADetour() {
		Vec3 from = new Vec3(0, 64, 0), target = new Vec3(0, 64, -500);
		assertFalse(DeliveryRun.isDetour(from, Journey.nextAim(from, target), target), "straight ahead");
		Vec3 swerve = Journey.aims(from, target).get(3);
		assertTrue(DeliveryRun.isDetour(from, swerve, target), "swerving 50 degrees");
	}

	@Test
	void heEatsNowAndThenWhereverHeIs() {
		assertTrue(Mannerisms.weight(Gesture.EAT, ORDINARY) > 0, "a bite on an ordinary ride");
		assertTrue(Mannerisms.weight(Gesture.EAT, with(false, false, false, false, false, false, false, 300)) > 0, "and on foot");
		assertTrue(Mannerisms.weight(Gesture.EAT, ORDINARY) < Mannerisms.weight(Gesture.CHECK_SATCHEL, ORDINARY), "but only now and then");
	}
}
