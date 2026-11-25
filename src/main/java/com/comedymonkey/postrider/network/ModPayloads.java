package com.comedymonkey.postrider.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Every packet the mod sends, which way it goes and what handles it, in the order they're registered. Each mod loader
 * registers this list its own way.
 */
public final class ModPayloads {
	/** The version of the mod's network protocol: client and server must agree. */
	public static final String PROTOCOL = "1";

	public enum Direction { TO_CLIENT, TO_SERVER }

	@FunctionalInterface
	public interface Handler<T extends CustomPacketPayload> {
		void handle(T payload, PacketContext context);
	}

	public record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Direction direction, Handler<T> handler) {}

	public static final List<Entry<?>> ALL = List.of(
		new Entry<>(MailboxInfoPayload.TYPE, MailboxInfoPayload.STREAM_CODEC, Direction.TO_CLIENT, MailboxInfoPayload::handle),
		new Entry<>(MailboxNoticePayload.TYPE, MailboxNoticePayload.STREAM_CODEC, Direction.TO_CLIENT, MailboxNoticePayload::handle),
		new Entry<>(RenameMailboxPayload.TYPE, RenameMailboxPayload.STREAM_CODEC, Direction.TO_SERVER, RenameMailboxPayload::handle),
		new Entry<>(SendParcelPayload.TYPE, SendParcelPayload.STREAM_CODEC, Direction.TO_SERVER, SendParcelPayload::handle),
		new Entry<>(EditLetterPayload.TYPE, EditLetterPayload.STREAM_CODEC, Direction.TO_SERVER, EditLetterPayload::handle));

	private ModPayloads() {}
}
