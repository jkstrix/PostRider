package com.comedymonkey.postrider.post;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Telling senders and recipients what became of their mail: chat for whoever is online, letters for the rest. */
public final class Notices {
	private Notices() {}

	public static void delivered(MinecraftServer server, PendingDelivery delivery, String riderName, @Nullable UUID recipient) {
		tell(server, delivery.sender(), Component.translatable("message.postrider.delivered.sender",
			delivery.toName(), riderName).withStyle(ChatFormatting.DARK_GREEN));
		if (recipient != null)
			tell(server, recipient, Component.translatable("message.postrider.delivered.recipient",
				delivery.toName(), delivery.fromName()).withStyle(ChatFormatting.DARK_GREEN));
	}

	/**
	 * Tells the sender their mail went astray, and leaves the post office's letter in their mailbox saying where it
	 * is. With no room or no mailbox, the chat message is all they get.
	 */
	public static void failed(MinecraftServer server, PendingDelivery delivery, String reason, BlockPos at, boolean parcelLeft, long day) {
		boolean lettered = false;
		ServerLevel home = server.getLevel(delivery.from().dimension());
		if (home != null) {
			home.getChunkAt(delivery.from().pos());
			if (home.getBlockEntity(delivery.from().pos()) instanceof MailboxBlockEntity mailbox)
				lettered = mailbox.deliver(Mail.failureLetter(delivery, reason, at, parcelLeft, day));
		}
		tell(server, delivery.sender(), Component.translatable(lettered ? "message.postrider.failed.sender" : "message.postrider.failed.sender_no_letter",
			delivery.toName(), at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.GOLD));
	}

	private static void tell(MinecraftServer server, UUID player, Component message) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null)
			online.sendSystemMessage(message);
	}
}
