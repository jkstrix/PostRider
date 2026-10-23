package com.comedymonkey.postrider.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class DevWatchTest {
	@Test
	void allTheTestsUnlessOneIsNamed() {
		assertEquals("test runall", DevWatch.command("all"));
		assertEquals("test runall", DevWatch.command("true"), "the property on its own");
		assertEquals("test runall", DevWatch.command(null));
		assertEquals("test run postrider:heopensthedoorsonhiswayandleavesthemashefoundthem", DevWatch.command("heOpensTheDoorsOnHisWayAndLeavesThemAsHeFoundThem"),
			"a test is registered by its method name in lower case");
		assertEquals("test run postrider:x", DevWatch.command(" postrider:x "), "the namespace may be given");
	}

	@Test
	void theSpectatorIsKeptBackFromTheRiderAlongHisOwnLineOfSight() {
		Vec3 eyes = new Vec3(100, 65.6, 200);
		Vec3 behind = DevWatch.viewpoint(eyes, new Vec3(0, 0, 1));
		assertEquals(new Vec3(100, 65.6, 200 - DevWatch.FOLLOW_DISTANCE), behind, "looking north-to-south: stood to the north");
		Vec3 above = DevWatch.viewpoint(eyes, new Vec3(0, -2, 0));
		assertEquals(new Vec3(100, 65.6 + DevWatch.FOLLOW_DISTANCE, 200), above, "looking down: up above him, however long the look vector");
	}
}
