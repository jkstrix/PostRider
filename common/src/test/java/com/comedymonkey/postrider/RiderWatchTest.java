package com.comedymonkey.postrider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class RiderWatchTest {
	@Test
	void theSpectatorIsKeptBackFromTheRiderAlongHisOwnLineOfSight() {
		Vec3 eyes = new Vec3(100, 65.6, 200);
		assertEquals(new Vec3(100, 65.6, 200 - RiderWatch.FOLLOW_DISTANCE), RiderWatch.viewpoint(eyes, new Vec3(0, 0, 1)), "looking north-to-south: stood to the north");
		assertEquals(new Vec3(100, 65.6 + RiderWatch.FOLLOW_DISTANCE, 200), RiderWatch.viewpoint(eyes, new Vec3(0, -2, 0)), "looking down: up above him, however long the look vector");
	}

	@Test
	void theRiderMostRecentlyOutIsTheOneFollowed() {
		record Out(String name, int age) {}
		List<Out> out = List.of(new Out("Eli", 900), new Out("Nell", 40), new Out("Gus", 2000));
		assertEquals("Nell", RiderWatch.youngest(out, Out::age).name(), "the youngest, for the whole of his trip");
		assertNull(RiderWatch.youngest(List.<Out>of(), Out::age), "nobody out");
	}
}
