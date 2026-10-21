package com.comedymonkey.postrider.content.letter;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The pages of a letter still being written. Kept on the blank Letter so you can come back to it. */
public record LetterDraft(List<String> pages) {
	public static final int MAX_PAGES = 4;
	public static final int MAX_PAGE_LENGTH = 512;

	public static final LetterDraft EMPTY = new LetterDraft(List.of());
	public static final Codec<LetterDraft> CODEC =
		Codec.STRING.sizeLimitedListOf(MAX_PAGES).xmap(LetterDraft::new, LetterDraft::pages);
	public static final StreamCodec<ByteBuf, LetterDraft> STREAM_CODEC =
		ByteBufCodecs.stringUtf8(MAX_PAGE_LENGTH * 4).apply(ByteBufCodecs.list(MAX_PAGES)).map(LetterDraft::new, LetterDraft::pages);

	public LetterDraft {
		pages = List.copyOf(pages);
	}

	/**
	 * Cleans pages that came from a player: at most {@link #MAX_PAGES} pages of {@link #MAX_PAGE_LENGTH} characters,
	 * no formatting codes or control characters except line breaks, and no empty pages at the end.
	 */
	public static List<String> sanitize(List<String> raw) {
		List<String> pages = new ArrayList<>();
		for (int i = 0; i < raw.size() && i < MAX_PAGES; i++) {
			StringBuilder page = new StringBuilder();
			for (char c : raw.get(i).toCharArray()) {
				if (page.length() >= MAX_PAGE_LENGTH)
					break;
				if (c == '\n' || (c >= ' ' && c != '§' && c != '\u007f'))
					page.append(c);
			}
			pages.add(page.toString());
		}
		while (!pages.isEmpty() && pages.get(pages.size() - 1).isBlank())
			pages.remove(pages.size() - 1);
		return pages;
	}

	public boolean isBlank() {
		return pages.stream().allMatch(String::isBlank);
	}
}
