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
		assertEquals("test run heopensthedoorsonhiswayandleavesthemashefoundthem", DevWatch.command("heOpensTheDoorsOnHisWayAndLeavesThemAsHeFoundThem"),
			"a test is registered by its method name in lower case");
		assertEquals("test run x", DevWatch.command(" postrider:x "), "a namespace in front is dropped: the command takes the bare name");
	}

	@Test
	void theSpectatorIsKeptBackFromTheRiderAlongHisOwnLineOfSight() {
		Vec3 eyes = new Vec3(100, 65.6, 200);
		Vec3 behind = DevWatch.viewpoint(eyes, new Vec3(0, 0, 1));
		assertEquals(new Vec3(100, 65.6, 200 - DevWatch.FOLLOW_DISTANCE), behind, "looking north-to-south: stood to the north");
		Vec3 above = DevWatch.viewpoint(eyes, new Vec3(0, -2, 0));
		assertEquals(new Vec3(100, 65.6 + DevWatch.FOLLOW_DISTANCE, 200), above, "looking down: up above him, however long the look vector");
	}

	@Test
	void aTripAcrossAWorldIsThreeNamesWithBarsBetween() {
		DevWatch.WorldRun run = DevWatch.worldRun("New World (1)|Testing|Smelly");
		assertEquals("New World (1)", run.world());
		assertEquals("Testing", run.from());
		assertEquals("Smelly", run.to());
		assertEquals(false, run.fresh());
		DevWatch.WorldRun fresh = DevWatch.worldRun("new|spawn|random:3000");
		assertEquals(true, fresh.fresh());
		assertEquals("postrider_watch_postrider12", fresh.folder("postrider12"), "a fresh world's folder is by its seed (and one left from an earlier run goes first)");
		assertEquals("postrider_watch_random", fresh.folder(""), "no seed: a random one each time");
		assertEquals(null, DevWatch.worldRun(""), "unset: the tests run instead");
		assertEquals(null, DevWatch.worldRun("new|spawn"), "a name missing");
	}

	@Test
	void aSeedIsANumberAsItIsOrAnythingElseByItsHash() {
		assertEquals(12345L, DevWatch.seed("12345"));
		assertEquals("postrider".hashCode(), DevWatch.seed("postrider"), "as the game takes a word for a seed");
	}
}
