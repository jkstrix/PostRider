package com.comedymonkey.ponyexpress.network;

import java.util.List;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxInfoCache;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the open mailbox's address and owner, and the other addresses in its dimension. */
public record MailboxInfoPayload(BlockPos pos, String name, String ownerName, List<String> addresses)
		implements CustomPacketPayload {
	public static final Type<MailboxInfoPayload> TYPE = new Type<>(PonyExpress.asResource("mailbox_info"));
	public static final StreamCodec<ByteBuf, MailboxInfoPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, MailboxInfoPayload::pos,
		ByteBufCodecs.STRING_UTF8, MailboxInfoPayload::name,
		ByteBufCodecs.STRING_UTF8, MailboxInfoPayload::ownerName,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MailboxInfoPayload::addresses,
		MailboxInfoPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(MailboxInfoPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MailboxInfoCache.set(payload.pos(), payload.name(), payload.ownerName(), payload.addresses()));
	}
}
