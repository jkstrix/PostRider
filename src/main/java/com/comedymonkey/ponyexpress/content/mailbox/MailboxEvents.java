package com.comedymonkey.ponyexpress.content.mailbox;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class MailboxEvents {
	private MailboxEvents() {}

	/** Stops anyone but the owner (or an operator) from breaking a mailbox, whatever tool or mod they use. */
	public static void onBreak(BlockEvent.BreakEvent event) {
		BlockState state = event.getState();
		if (state.getBlock() instanceof MailboxBlock && !MailboxBlock.canBreak(event.getPlayer(), event.getLevel(), state, event.getPos())) {
			event.setCanceled(true);
			MailboxBlockEntity mailbox = MailboxBlock.mailboxAt(event.getLevel(), state, event.getPos());
			String owner = mailbox != null ? mailbox.getOwnerName() : "";
			event.getPlayer().displayClientMessage(
				Component.translatable("message.ponyexpress.mailbox.cant_break", owner).withStyle(ChatFormatting.RED), true);
		}
	}
}
