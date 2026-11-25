package com.comedymonkey.postrider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DevHeadlessTest {
	@Test
	void theBoxesAreNamedWithABarBetween() {
		assertArrayEquals(new String[] {"Testing", "Smelly"}, DevHeadless.parse("Testing|Smelly"));
		assertArrayEquals(new String[] {"Beach House", "My Post"}, DevHeadless.parse(" Beach House | My Post "), "spaces in names, and round the bar");
		assertNull(DevHeadless.parse("Testing"), "one name is no trip");
		assertNull(DevHeadless.parse("Testing|"), "nor a blank one");
		assertNull(DevHeadless.parse(null));
	}
}
