package com.comedymonkey.postrider.network;

import net.minecraft.world.entity.player.Player;

/** Where a packet came from, as each mod loader hands it to the mod's handlers. */
public interface PacketContext {
	/** The player who sent it (on the server), or the local player (on the client). */
	Player player();

	/** Runs the work on the game's main thread, after the packet has been read. */
	void enqueueWork(Runnable work);
}
