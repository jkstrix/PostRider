package com.comedymonkey.ponyexpress.content.letter;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lets the letter items open their screens without referring to client classes; the client sets the opener
 * when it starts.
 */
public final class LetterScreens {
	public interface Opener {
		void openEditor(Player player, InteractionHand hand, ItemStack letter);

		void openReader(ItemStack letter);
	}

	private static Opener opener = new Opener() {
		@Override
		public void openEditor(Player player, InteractionHand hand, ItemStack letter) {}

		@Override
		public void openReader(ItemStack letter) {}
	};

	private LetterScreens() {}

	public static void setOpener(Opener newOpener) {
		opener = newOpener;
	}

	public static Opener get() {
		return opener;
	}
}
