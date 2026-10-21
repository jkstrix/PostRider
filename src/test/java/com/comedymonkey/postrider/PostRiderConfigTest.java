package com.comedymonkey.postrider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.Journey;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;
import com.comedymonkey.postrider.content.postman.RiderChunks;
import com.comedymonkey.postrider.post.PostDispatcher;

class PostRiderConfigTest {
	@Test
	void withNoConfigLoadedEverythingIsAsDesigned() {
		assertEquals(PostDispatcher.MAX_RIDERS, PostRiderConfig.maxRiders());
		assertEquals(Journey.STRETCH, PostRiderConfig.lookahead());
		assertEquals(Journey.STRETCH, Journey.stretch());
		assertEquals(PostmanEntity.POTIONS_PER_DAY, PostRiderConfig.potionsPerDay());
		assertEquals(BargeEntity.BLOCKS_PER_SECOND, PostRiderConfig.bargeBlocksPerSecond());
		assertEquals(BargeEntity.SPEED, BargeEntity.speed(), 1e-9);
		assertEquals(6, PostRiderConfig.firstCollection());
		assertEquals(18, PostRiderConfig.lastCollection());
		assertEquals(RiderChunks.RADIUS, RiderChunks.radius());
	}

	@Test
	void theLoadedChunksAndPathfindingKeepUpWithAnyLookahead() {
		for (double stretch = 16; stretch <= 64; stretch += 8) {
			int radius = Math.max(RiderChunks.TICKING_RADIUS, (int) Math.ceil(stretch / 16));
			assertTrue(radius * 16 >= stretch, "chunks reach " + stretch);
			assertTrue(PostmanHorse.followRange(stretch) > stretch, "pathfinding plans past " + stretch);
		}
	}
}
