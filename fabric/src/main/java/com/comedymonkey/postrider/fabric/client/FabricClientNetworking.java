package com.comedymonkey.postrider.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client's half of the platform's networking, kept apart so a dedicated server never loads it. */
public final class FabricClientNetworking {
	private FabricClientNetworking() {}

	public static void sendToServer(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
