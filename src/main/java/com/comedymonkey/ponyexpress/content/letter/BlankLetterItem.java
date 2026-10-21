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

/** A sheet of letter paper and a quill. Use it to write; it keeps your draft until you seal it. */
public class BlankLetterItem extends Item {
	public BlankLetterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide)
			LetterScreens.get().openEditor(player, hand, stack);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		LetterDraft draft = stack.get(ModDataComponents.LETTER_DRAFT.get());
		if (draft != null && !draft.isBlank())
			tooltip.add(Component.translatable("item.ponyexpress.letter.draft").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ponyexpress.letter.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
