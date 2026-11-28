package com.comedymonkey.postrider.content;

import com.comedymonkey.postrider.content.lost.LostParcelBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;

public final class PostalBlockEvents {
	private PostalBlockEvents() {}

	/**
	 * Stops players breaking mailboxes they don't own, and lost parcels they can't claim, whatever tool or mod they
	 * use. The server decides (the client doesn't know who may claim a lost parcel) and puts the block back on the
	 * player's screen.
	 *
	 * @return true if the break is refused: the loader cancels it and sends the block back to the player's client
	 */
	public static boolean onBreak(Player player, LevelAccessor level, BlockPos pos, BlockState state) {
		if (level.isClientSide())
			return false;
		if (state.getBlock() instanceof MailboxBlock && !MailboxBlock.canBreak(player, level, state, pos)) {
			MailboxBlockEntity mailbox = MailboxBlock.mailboxAt(level, state, pos);
			String owner = mailbox != null ? mailbox.getOwnerName() : "";
			player.sendOverlayMessage(
				Component.translatable("message.postrider.mailbox.cant_break", owner).withStyle(ChatFormatting.RED));
			return true;
		}
		if (state.getBlock() instanceof LostParcelBlock && !LostParcelBlock.canBreak(player, level, pos)) {
			player.sendOverlayMessage(
				Component.translatable("message.postrider.lost_parcel.not_yours").withStyle(ChatFormatting.RED));
			return true;
		}
		return false;
	}
}
