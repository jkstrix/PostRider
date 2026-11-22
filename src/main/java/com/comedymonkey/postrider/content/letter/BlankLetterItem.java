package com.comedymonkey.postrider.content.letter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.comedymonkey.postrider.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A sheet of letter paper and a quill. Use it to write; it keeps your draft until you seal it. */
public class BlankLetterItem extends Item {
	public BlankLetterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide())
			LetterScreens.get().openEditor(player, hand, stack);
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		List<Component> tooltip = new ArrayList<>();
		LetterDraft draft = stack.get(ModDataComponents.LETTER_DRAFT.get());
		if (draft != null && !draft.isBlank())
			tooltip.add(Component.translatable("item.postrider.letter.draft").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.postrider.letter.hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.forEach(builder);
	}
}
