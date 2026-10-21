package com.comedymonkey.ponyexpress.content.letter;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A sealed letter: its pages, who wrote it, and the postmark it gets once a rider delivers it. */
public record LetterContent(List<String> pages, String author, Optional<Postmark> postmark) {
	public static final Codec<LetterContent> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.sizeLimitedListOf(LetterDraft.MAX_PAGES).fieldOf("pages").forGetter(LetterContent::pages),
		Codec.STRING.fieldOf("author").forGetter(LetterContent::author),
		Postmark.CODEC.optionalFieldOf("postmark").forGetter(LetterContent::postmark)
	).apply(i, LetterContent::new));
	public static final StreamCodec<ByteBuf, LetterContent> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(LetterDraft.MAX_PAGE_LENGTH * 4).apply(ByteBufCodecs.list(LetterDraft.MAX_PAGES)), LetterContent::pages,
		ByteBufCodecs.STRING_UTF8, LetterContent::author,
		ByteBufCodecs.optional(Postmark.STREAM_CODEC), LetterContent::postmark,
		LetterContent::new);

	public LetterContent {
		pages = List.copyOf(pages);
	}

	public LetterContent postmarked(Postmark mark) {
		return new LetterContent(pages, author, Optional.of(mark));
	}

	/** Where and when a rider picked the mail up: the sending mailbox's address and the in-game day. */
	public record Postmark(String from, long day) {
		public static final Codec<Postmark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("from").forGetter(Postmark::from),
			Codec.LONG.fieldOf("day").forGetter(Postmark::day)
		).apply(i, Postmark::new));
		public static final StreamCodec<ByteBuf, Postmark> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Postmark::from,
			ByteBufCodecs.VAR_LONG, Postmark::day,
			Postmark::new);
	}
}
