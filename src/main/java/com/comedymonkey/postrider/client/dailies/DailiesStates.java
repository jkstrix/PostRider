package com.comedymonkey.postrider.client.dailies;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.letter.LetterDraft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

/**
 * What Post Rider's screens are recorded as, for Dailies (see {@link PostRiderDailies}): each the least that draws the
 * screen again, and the whole of it. Nothing here refers to Dailies, so it loads and is tested without it. Version 1
 * of the compat writes these; a later version must still read them.
 */
public final class DailiesStates {
	private static final int HELD = 0;
	private static final int COPIED = 1;

	private DailiesStates() {}

	/** The mailbox: what its screen holds that the server never sent (the slots are Dailies' own to record). */
	public record Mailbox(int tab, @Nullable String selected, int scroll, String typed) {}

	/**
	 * A letter being read: either the letter in one of the reader's hands, which the recording already holds as their
	 * held item (so only the hand, and a check that it is the same letter, are kept), or a copy of the letter when it
	 * isn't in either hand. And the page open.
	 */
	public record Reading(@Nullable InteractionHand hand, int check, @Nullable LetterContent copy, int page) {}

	/** A letter being written: its pages as they stand, the page open, the cursor on it, and whether sealing is being confirmed. */
	public record Writing(List<String> pages, int page, int cursor, boolean confirming) {}

	public static byte[] mailbox(Mailbox state) {
		return write(buf -> {
			buf.writeVarInt(state.tab());
			buf.writeBoolean(state.selected() != null);
			if (state.selected() != null)
				buf.writeUtf(state.selected());
			buf.writeVarInt(Math.max(0, state.scroll()));
			buf.writeUtf(state.typed());
		});
	}

	public static Mailbox readMailbox(byte[] data) {
		FriendlyByteBuf buf = read(data);
		try {
			int tab = buf.readVarInt();
			String selected = buf.readBoolean() ? buf.readUtf() : null;
			return new Mailbox(tab, selected, buf.readVarInt(), buf.readUtf());
		} finally {
			buf.release();
		}
	}

	public static byte[] reading(Reading state) {
		return write(buf -> {
			if (state.hand() != null && state.copy() == null) {
				buf.writeByte(HELD);
				buf.writeByte(state.hand().ordinal());
				buf.writeInt(state.check());
			} else {
				buf.writeByte(COPIED);
				LetterContent.STREAM_CODEC.encode(buf, state.copy());
			}
			buf.writeVarInt(state.page());
		});
	}

	public static Reading readReading(byte[] data) {
		FriendlyByteBuf buf = read(data);
		try {
			int kind = buf.readByte();
			if (kind == HELD) {
				InteractionHand hand = InteractionHand.values()[buf.readByte()];
				int check = buf.readInt();
				return new Reading(hand, check, null, buf.readVarInt());
			}
			LetterContent copy = LetterContent.STREAM_CODEC.decode(buf);
			return new Reading(null, 0, copy, buf.readVarInt());
		} finally {
			buf.release();
		}
	}

	public static byte[] writing(Writing state) {
		return write(buf -> {
			int count = Math.min(state.pages().size(), LetterDraft.MAX_PAGES);
			buf.writeVarInt(count);
			for (int i = 0; i < count; i++)
				buf.writeUtf(state.pages().get(i), LetterDraft.MAX_PAGE_LENGTH * 4);
			buf.writeVarInt(state.page());
			buf.writeVarInt(Math.max(0, state.cursor()));
			buf.writeBoolean(state.confirming());
		});
	}

	public static Writing readWriting(byte[] data) {
		FriendlyByteBuf buf = read(data);
		try {
			int count = Math.min(buf.readVarInt(), LetterDraft.MAX_PAGES);
			List<String> pages = new ArrayList<>();
			for (int i = 0; i < count; i++)
				pages.add(buf.readUtf(LetterDraft.MAX_PAGE_LENGTH * 4));
			return new Writing(pages, buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
		} finally {
			buf.release();
		}
	}

	/** A check that a held letter is the one recorded: a CRC of the letter as the game sends it. */
	public static int check(LetterContent letter) {
		byte[] bytes = write(buf -> LetterContent.STREAM_CODEC.encode(buf, letter));
		CRC32 crc = new CRC32();
		crc.update(bytes);
		return (int) crc.getValue();
	}

	private interface Writer {
		void write(FriendlyByteBuf buf);
	}

	private static byte[] write(Writer writer) {
		ByteBuf raw = Unpooled.buffer();
		try {
			FriendlyByteBuf buf = new FriendlyByteBuf(raw);
			writer.write(buf);
			byte[] bytes = new byte[raw.readableBytes()];
			raw.getBytes(raw.readerIndex(), bytes);
			return bytes;
		} finally {
			raw.release();
		}
	}

	private static FriendlyByteBuf read(byte[] data) {
		return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
	}
}
