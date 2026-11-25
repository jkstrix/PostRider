package com.comedymonkey.postrider.forge;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.forge.client.ForgeClientAccess;
import com.comedymonkey.postrider.network.ModPayloads;
import com.comedymonkey.postrider.network.PacketContext;
import com.comedymonkey.postrider.parity.ParityManifest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;

/**
 * The mod's packets on Forge: one payload channel, postrider:network at protocol {@link ModPayloads#PROTOCOL}, with
 * every payload of {@link ModPayloads#ALL} registered in its direction (NeoForge's payload registrar). A client or server
 * without the channel at the same version is refused, as on NeoForge.
 */
final class ForgeNetwork {
	static final Channel<CustomPacketPayload> CHANNEL = build();

	private ForgeNetwork() {}

	/** Builds the channel when the class is first used: in the mod's constructor, as NeoForge registers its payloads while the mod loads. */
	static void init() {}

	private static Channel<CustomPacketPayload> build() {
		PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play = ChannelBuilder.named(PostRider.asResource("network"))
			.networkProtocolVersion(Integer.parseInt(ModPayloads.PROTOCOL))
			.payloadChannel()
			.play();
		PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> toClient = play.clientbound();
		PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> toServer = play.serverbound();
		for (ModPayloads.Entry<?> entry : ModPayloads.ALL)
			register(entry.direction() == ModPayloads.Direction.TO_CLIENT ? toClient : toServer, entry);
		return toServer.build();
	}

	private static <T extends CustomPacketPayload> void register(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow, ModPayloads.Entry<T> entry) {
		ParityManifest.recordPayload(entry.type().id(), entry.direction().name());
		// (The payload's codec reads and writes any RegistryFriendlyByteBuf, so it is one for exactly that)
		@SuppressWarnings("unchecked")
		StreamCodec<RegistryFriendlyByteBuf, T> codec = (StreamCodec<RegistryFriendlyByteBuf, T>) entry.codec();
		flow.add(entry.type(), codec, (payload, context) -> {
			// Forge hands packets over on the network thread: the handlers put their game logic on the main thread
			// with enqueueWork, as they do on NeoForge
			entry.handler().handle(payload, wrap(context));
			context.setPacketHandled(true);
		});
	}

	private static PacketContext wrap(CustomPayloadEvent.Context context) {
		return new PacketContext() {
			@Override
			public Player player() {
				// The sender on the server; the local player on the client (only read in the work put on its main thread)
				return context.isServerSide() ? context.getSender() : ForgeClientAccess.localPlayer();
			}

			@Override
			public void enqueueWork(Runnable work) {
				// As NeoForge: straight away when already on the main thread, otherwise queued for it
				context.enqueueWork(work);
			}
		};
	}
}
