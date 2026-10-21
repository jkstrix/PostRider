package com.comedymonkey.ponyexpress.client;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.client.gui.LetterEditScreen;
import com.comedymonkey.ponyexpress.client.gui.LetterReadScreen;
import com.comedymonkey.ponyexpress.client.gui.MailboxScreen;
import com.comedymonkey.ponyexpress.content.letter.LetterScreens;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxBlock;
import com.comedymonkey.ponyexpress.registry.ModBlocks;
import com.comedymonkey.ponyexpress.registry.ModItems;
import com.comedymonkey.ponyexpress.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = PonyExpress.MOD_ID, dist = Dist.CLIENT)
public class PonyExpressClient {
	public PonyExpressClient(IEventBus modBus) {
		LetterScreens.setOpener(new LetterScreens.Opener() {
			@Override
			public void openEditor(Player player, InteractionHand hand, ItemStack letter) {
				Minecraft.getInstance().setScreen(new LetterEditScreen(player, hand, letter));
			}

			@Override
			public void openReader(ItemStack letter) {
				Minecraft.getInstance().setScreen(new LetterReadScreen(letter));
			}
		});
		modBus.addListener(this::registerScreens);
		modBus.addListener(this::registerBlockColors);
		modBus.addListener(this::registerItemColors);
	}

	private void registerScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenus.MAILBOX.get(), MailboxScreen::new);
	}

	/** The mailbox box faces are tint index 0; one white texture covers every dye colour. */
	private void registerBlockColors(RegisterColorHandlersEvent.Block event) {
		for (DyeColor color : DyeColor.values()) {
			MailboxBlock block = ModBlocks.mailbox(color).get();
			event.register((state, level, pos, tint) -> tint == 0 ? color.getTextureDiffuseColor() : -1, block);
		}
	}

	private void registerItemColors(RegisterColorHandlersEvent.Item event) {
		for (DyeColor color : DyeColor.values())
			event.register((stack, tint) -> tint == 0 ? color.getTextureDiffuseColor() : -1, ModItems.mailbox(color).get());
	}
}
