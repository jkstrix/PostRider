package com.comedymonkey.postrider.network;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.mailbox.MailboxInfoCache;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: how saving an address or sending a parcel went, shown on the open mailbox screen itself (a message
 * on the hotbar would be hidden behind it).
 */
public record MailboxNoticePayload(Component message, boolean good) implements CustomPacketPayload {
	public static final Type<MailboxNoticePayload> TYPE = new Type<>(PostRider.asResource("mailbox_notice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MailboxNoticePayload> STREAM_CODEC = StreamCodec.composite(
		ComponentSerialization.TRUSTED_STREAM_CODEC, MailboxNoticePayload::message,
		ByteBufCodecs.BOOL, MailboxNoticePayload::good,
		MailboxNoticePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Tells the player at their mailbox how it went. */
	public static void tell(ServerPlayer player, Component message, boolean good) {
		PacketDistributor.sendToPlayer(player, new MailboxNoticePayload(message, good));
	}

	public static void handle(MailboxNoticePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MailboxInfoCache.notice(payload.message(), payload.good()));
	}
}
