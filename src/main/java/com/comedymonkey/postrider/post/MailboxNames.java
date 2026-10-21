package com.comedymonkey.postrider.post;

import java.util.Collection;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The rules for mailbox addresses: 3 to 24 characters, unique on the server ignoring case. */
public final class MailboxNames {
	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 24;
	private static final String ALLOWED_PUNCTUATION = " '-_.&!#,()";

	public enum Problem {
		TOO_SHORT,
		TOO_LONG,
		BAD_CHARACTER
	}

	private MailboxNames() {}

	/** The name as it will be stored: trimmed, with runs of whitespace collapsed to one space. */
	public static String clean(String raw) {
		return raw.trim().replaceAll("\\s+", " ");
	}

	/** What's wrong with an already cleaned name, or null if it can be used. */
	@Nullable
	public static Problem check(String cleaned) {
		if (cleaned.length() < MIN_LENGTH)
			return Problem.TOO_SHORT;
		if (cleaned.length() > MAX_LENGTH)
			return Problem.TOO_LONG;
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (!Character.isLetterOrDigit(c) && ALLOWED_PUNCTUATION.indexOf(c) < 0)
				return Problem.BAD_CHARACTER;
		}
		return null;
	}

	/** Whether another mailbox already has this address (ignoring case). */
	public static boolean isTaken(String cleaned, Collection<String> others) {
		for (String other : others)
			if (key(other).equals(key(cleaned)))
				return true;
		return false;
	}

	/** What to tell the player about a problem with an address. */
	public static MutableComponent describe(Problem problem) {
		return switch (problem) {
			case TOO_SHORT -> Component.translatable("message.postrider.mailbox.name_too_short", MIN_LENGTH);
			case TOO_LONG -> Component.translatable("message.postrider.mailbox.name_too_long", MAX_LENGTH);
			case BAD_CHARACTER -> Component.translatable("message.postrider.mailbox.name_bad_character");
		};
	}

	/** The lookup key for a name, so "Steve's Ranch" and "steve's ranch" are the same address. */
	public static String key(String name) {
		return clean(name).toLowerCase(Locale.ROOT);
	}
}
