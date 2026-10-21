package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.registry.ModEntities;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

class PostmanEntityTest {
	@Test
	void isPlayerSized() {
		assertEquals(0.6F, ModEntities.POSTMAN.get().getWidth());
		assertEquals(1.8F, ModEntities.POSTMAN.get().getHeight());
	}

	@Test
	void hasAPlayersHealth() {
		AttributeSupplier attributes = PostmanEntity.createAttributes().build();
		assertEquals(20.0, attributes.getBaseValue(Attributes.MAX_HEALTH));
		assertTrue(attributes.getBaseValue(Attributes.FOLLOW_RANGE) >= 64);
	}

	@Test
	void gestureEventsAreUniqueAndClearOfVanillaOnes() {
		Set<Byte> ids = new HashSet<>();
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values()) {
			assertTrue(gesture.eventId >= 100, gesture.name());
			assertTrue(ids.add(gesture.eventId), gesture.name());
			assertSame(gesture, PostmanEntity.Gesture.byEvent(gesture.eventId));
		}
		assertNull(PostmanEntity.Gesture.byEvent((byte) 3));
	}
}
