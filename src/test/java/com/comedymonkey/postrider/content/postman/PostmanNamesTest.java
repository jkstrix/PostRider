package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

class PostmanNamesTest {
	@Test
	void namesAreNeverRepeatedInTheLists() {
		for (List<String> names : List.of(PostmanNames.FIRST_NAMES, PostmanNames.SURNAMES, PostmanNames.HORSE_NAMES))
			assertEquals(names.size(), new HashSet<>(names).size());
	}

	@Test
	void ridersGetAFirstNameAndSurname() {
		String name = PostmanNames.rider(RandomSource.create(7));
		String[] parts = name.split(" ");
		assertEquals(2, parts.length, name);
		assertTrue(PostmanNames.FIRST_NAMES.contains(parts[0]));
		assertTrue(PostmanNames.SURNAMES.contains(parts[1]));
	}

	@Test
	void namesVaryFromRiderToRider() {
		RandomSource random = RandomSource.create(42);
		Set<String> riders = new HashSet<>(), horses = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			riders.add(PostmanNames.rider(random));
			horses.add(PostmanNames.horse(random));
		}
		assertTrue(riders.size() > 30);
		assertTrue(horses.size() > 15);
	}
}
