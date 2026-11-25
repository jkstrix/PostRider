package com.comedymonkey.postrider.client.dailies;

import java.util.List;
import java.util.Optional;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.gui.LetterEditScreen;
import com.comedymonkey.postrider.client.gui.MailboxScreen;
import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.registry.ModBlocks;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.comedymonkey.postrider.registry.ModItems;

import comedymonkey.dailies.api.compat.DisplayTest;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The display test of Post Rider's compat, run by Dailies' scripted run (its postrider group): two mailboxes beside the
 * player, then while recording the first is opened on each of its tabs (Inbox, Send with the other's address picked,
 * and Settings), a letter is read,
 * and a letter is written a character at a time. Dailies then checks each is drawn in first-person playback, and
 * nothing in third person.
 */
final class PostRiderDisplayTest implements DisplayTest {
	private static final BlockPos MAILBOX = new BlockPos(2, -60, 1);
	private static final BlockPos OTHER = new BlockPos(-2, -60, 1);
	private static final String OTHER_ADDRESS = "Brasswick";
	private static final String TYPED = "Dear viewer, hello";

	@Override
	public void stage(Stage stage) throws Exception {
		stage.onServer(player -> {
			mailbox(player, MAILBOX, "Testing");
			mailbox(player, OTHER, OTHER_ADDRESS);
		});
	}

	/** A mailbox of the player's, named, standing where Post Rider would put one down. */
	private static void mailbox(ServerPlayer player, BlockPos pos, String name) {
		ServerLevel level = player.serverLevel();
		BlockState lower = ModBlocks.mailbox(DyeColor.RED).get().defaultBlockState().setValue(MailboxBlock.FACING, Direction.SOUTH);
		level.setBlock(pos, lower, Block.UPDATE_ALL);
		level.setBlock(pos.above(), lower.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox)
			mailbox.rename(name, player);
	}

	/** Opens the first mailbox as using it does, then the given tab as clicking it does. */
	private static void openMailbox(Stage stage, int tab) throws Exception {
		stage.onServer(player -> {
			if (player.serverLevel().getBlockEntity(MAILBOX) instanceof MailboxBlockEntity mailbox)
				mailbox.openFor(player);
		});
		stage.waitTicks(10);
		stage.onClient(() -> {
			if (Minecraft.getInstance().screen instanceof MailboxScreen mailbox)
				mailbox.switchTab(tab);
		});
	}

	@Override
	public List<Display> displays() {
		Minecraft minecraft = Minecraft.getInstance();
		return List.of(
			new Display("a mailbox on its Inbox tab", PostRider.MOD_ID + ":mailbox", stage -> openMailbox(stage, MailboxMenu.TAB_INBOX)),
			new Display("a mailbox on its Send tab", PostRider.MOD_ID + ":mailbox", stage -> {
				openMailbox(stage, MailboxMenu.TAB_SEND);
				stage.onClient(() -> {
					if (minecraft.screen instanceof MailboxScreen mailbox)
						mailbox.pickAddress(OTHER_ADDRESS);
				});
			}),
			new Display("a mailbox on its Settings tab", PostRider.MOD_ID + ":mailbox", stage -> openMailbox(stage, MailboxMenu.TAB_SETTINGS)),
			new Display("a letter being read", PostRiderDailies.ID + "/letter_read", stage -> {
				stage.onServer(player -> {
					ItemStack letter = new ItemStack(ModItems.WRITTEN_LETTER.get());
					letter.set(ModDataComponents.LETTER.get(), new LetterContent(List.of("Dear viewer,\nthis came by post rider."), "Dailies",
						Optional.of(new LetterContent.Postmark(OTHER_ADDRESS, 12))));
					player.setItemInHand(InteractionHand.MAIN_HAND, letter);
				});
				stage.waitTicks(5);
				stage.onClient(() -> minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND));
			}),
			new Display("a letter being written", PostRiderDailies.ID + "/letter_write", stage -> {
				stage.onServer(player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LETTER.get())));
				stage.waitTicks(5);
				stage.onClient(() -> minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND));
				stage.waitTicks(2);
				for (char typed : TYPED.toCharArray()) {
					stage.onClient(() -> {
						if (minecraft.screen instanceof LetterEditScreen writing)
							writing.charTyped(typed, 0);
					});
					stage.waitTicks(1);
				}
			}));
	}
}
