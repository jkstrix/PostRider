package com.comedymonkey.ponyexpress.content.letter;

import java.util.List;

import com.comedymonkey.ponyexpress.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A sealed letter. Use it to read it. */
public class WrittenLetterItem extends Item {
	public WrittenLetterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide)
			LetterScreens.get().openReader(stack);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		LetterContent letter = stack.get(ModDataComponents.LETTER.get());
		if (letter == null)
			return;
		tooltip.add(Component.translatable("item.ponyexpress.written_letter.author", letter.author()).withStyle(ChatFormatting.GRAY));
		letter.postmark().ifPresent(mark -> tooltip.add(
			Component.translatable("item.ponyexpress.written_letter.postmark", mark.from(), mark.day()).withStyle(ChatFormatting.DARK_AQUA)));
	}
}
