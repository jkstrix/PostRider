package com.comedymonkey.postrider.content;

import com.comedymonkey.postrider.content.lost.LostParcelBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class PostalBlockEvents {
	private PostalBlockEvents() {}

	/**
	 * Stops players breaking mailboxes they don't own, and lost parcels they can't claim, whatever tool or mod they
	 * use. The server decides (the client doesn't know who may claim a lost parcel) and puts the block back on the
	 * player's screen.
	 */
	public static void onBreak(BreakBlockEvent event) {
		if (event.getLevel().isClientSide())
			return;
		BlockState state = event.getState();
		if (state.getBlock() instanceof MailboxBlock && !MailboxBlock.canBreak(event.getPlayer(), event.getLevel(), state, event.getPos())) {
			cancel(event);
			MailboxBlockEntity mailbox = MailboxBlock.mailboxAt(event.getLevel(), state, event.getPos());
			String owner = mailbox != null ? mailbox.getOwnerName() : "";
			event.getPlayer().sendOverlayMessage(
				Component.translatable("message.postrider.mailbox.cant_break", owner).withStyle(ChatFormatting.RED));
		} else if (state.getBlock() instanceof LostParcelBlock && !LostParcelBlock.canBreak(event.getPlayer(), event.getLevel(), event.getPos())) {
			cancel(event);
			event.getPlayer().sendOverlayMessage(
				Component.translatable("message.postrider.lost_parcel.not_yours").withStyle(ChatFormatting.RED));
		}
	}

	private static void cancel(BreakBlockEvent event) {
		event.setCanceled(true);
		event.setNotifyClient(true);
	}
}
