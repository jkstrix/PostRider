package com.comedymonkey.postrider.content.letter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.comedymonkey.postrider.post.JourneyNotes;
import com.comedymonkey.postrider.post.TravelTime;
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

/** A sealed letter. Use it to read it. */
public class WrittenLetterItem extends Item {
	public WrittenLetterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide())
			LetterScreens.get().openReader(stack);
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		List<Component> tooltip = new ArrayList<>();
		LetterContent letter = stack.get(ModDataComponents.LETTER.get());
		if (letter == null)
			return;
		tooltip.add(Component.translatable("item.postrider.written_letter.author", letter.author()).withStyle(ChatFormatting.GRAY));
		letter.postmark().ifPresent(mark -> {
			tooltip.add(Component.translatable("item.postrider.written_letter.postmark", mark.from(), mark.day()).withStyle(ChatFormatting.DARK_AQUA));
			if (mark.travelTicks() > 0)
				tooltip.add(TravelTime.describe(mark.travelTicks()).copy().withStyle(ChatFormatting.DARK_AQUA));
		});
		stack.getOrDefault(ModDataComponents.JOURNEY_NOTES.get(), JourneyNotes.NONE).addTo(tooltip);
		tooltip.forEach(builder);
	}
}
