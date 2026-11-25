package com.comedymonkey.postrider.content.mailbox;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Client side: what the server last said about the open mailbox. Kept free of client-only classes so the payload
 * that fills it can be registered on both sides.
 */
public final class MailboxInfoCache {
	public record Info(BlockPos pos, String name, String ownerName, List<String> addresses) {}

	/** What the server said about saving or sending, and when. */
	public record Notice(Component message, boolean good, long at) {}

	/** How long a notice stays on the screen. */
	public static final long NOTICE_MILLIS = 6000;

	@Nullable
	private static Info current;
	private static int version;
	@Nullable
	private static Notice notice;

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

	public static void notice(Component message, boolean good) {
		notice = new Notice(message, good, System.currentTimeMillis());
	}

	/** Takes down any notice showing. */
	public static void clearNotice() {
		notice = null;
	}

	/** The latest notice, if it's recent enough to still show. */
	@Nullable
	public static Notice notice() {
		return notice != null && System.currentTimeMillis() - notice.at() < NOTICE_MILLIS ? notice : null;
	}

	/** Goes up every time new info arrives, so screens can tell when to refresh. */
	public static int version() {
		return version;
	}
}
