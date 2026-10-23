package com.comedymonkey.postrider.content.postman;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** The chunks held ahead along a rider's plan: loaded (and so generated), not ticking, under his own name. */
public final class PostmanHorseChunks {
	private PostmanHorseChunks() {}

	public static void hold(ServerLevel level, UUID owner, long chunk, boolean hold) {
		ChunkPos pos = new ChunkPos(chunk);
		RiderChunks.CONTROLLER.forceChunk(level, owner, pos.x, pos.z, hold, false);
	}
}
