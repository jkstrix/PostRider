package com.comedymonkey.ponyexpress.content;

import com.comedymonkey.ponyexpress.content.lost.LostParcelBlock;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxBlock;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class PostalBlockEvents {
	private PostalBlockEvents() {}

	/**
	 * Stops players breaking mailboxes they don't own, and lost parcels they can't claim, whatever tool or mod they
	 * use.
	 */
	public static void onBreak(BlockEvent.BreakEvent event) {
		BlockState state = event.getState();
		if (state.getBlock() instanceof MailboxBlock && !MailboxBlock.canBreak(event.getPlayer(), event.getLevel(), state, event.getPos())) {
			event.setCanceled(true);
			MailboxBlockEntity mailbox = MailboxBlock.mailboxAt(event.getLevel(), state, event.getPos());
			String owner = mailbox != null ? mailbox.getOwnerName() : "";
			event.getPlayer().displayClientMessage(
				Component.translatable("message.ponyexpress.mailbox.cant_break", owner).withStyle(ChatFormatting.RED), true);
		} else if (state.getBlock() instanceof LostParcelBlock && !LostParcelBlock.canBreak(event.getPlayer(), event.getLevel(), event.getPos())) {
			event.setCanceled(true);
			event.getPlayer().displayClientMessage(
				Component.translatable("message.ponyexpress.lost_parcel.not_yours").withStyle(ChatFormatting.RED), true);
		}
	}
}
