package com.comedymonkey.postrider.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
}
