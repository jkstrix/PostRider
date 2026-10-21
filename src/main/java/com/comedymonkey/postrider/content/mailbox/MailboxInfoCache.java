package com.comedymonkey.postrider.content.mailbox;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * Client side: what the server last said about the open mailbox. Kept free of client-only classes so the payload
 * that fills it can be registered on both sides.
 */
public final class MailboxInfoCache {
	public record Info(BlockPos pos, String name, String ownerName, List<String> addresses) {}

	@Nullable
	private static Info current;
	private static int version;

	private MailboxInfoCache() {}

	public static void set(BlockPos pos, String name, String ownerName, List<String> addresses) {
		current = new Info(pos, name, ownerName, List.copyOf(addresses));
		version++;
	}

	/** The info for the mailbox at this position, or null if none has arrived for it yet. */
	@Nullable
	public static Info get(BlockPos pos) {
		return current != null && current.pos().equals(pos) ? current : null;
	}

	/** Goes up every time new info arrives, so screens can tell when to refresh. */
	public static int version() {
		return version;
	}
}
