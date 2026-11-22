package com.comedymonkey.postrider.content.parcel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.comedymonkey.postrider.post.JourneyNotes;
import com.comedymonkey.postrider.post.TravelTime;
import com.comedymonkey.postrider.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * A sealed parcel. Use it to tear it open: like emptying a bundle, everything inside is tossed out on the ground in
 * front of you, and the wrapping is gone. What's inside stays a surprise until then.
 */
public class ParcelItem extends Item {
	public ParcelItem(Properties properties) {
		super(properties);
	}

	/** What's inside, as copies, in the order it was packed. */
	public static List<ItemStack> contents(ItemStack parcel) {
		return parcel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack parcel = player.getItemInHand(hand);
		// Thrown out rather than put in the inventory: the game puts the result back in the hand after use, which
		// would wipe anything slipped into that slot meanwhile
		List<ItemStack> contents = contents(parcel);
		if (contents.isEmpty())
			return InteractionResult.FAIL;
		parcel.shrink(1);
		if (!level.isClientSide()) {
			for (ItemStack item : contents)
				player.drop(item, true, Prediction.SERVER_ONLY);
			level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F,
				0.8F + level.getRandom().nextFloat() * 0.4F);
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		List<Component> tooltip = new ArrayList<>();
		ParcelLabel label = stack.get(ModDataComponents.PARCEL_LABEL.get());
		if (label != null) {
			tooltip.add(Component.translatable("item.postrider.parcel.from", label.from(), label.sender()).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("item.postrider.parcel.to", label.to()).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("item.postrider.parcel.day", label.day()).withStyle(ChatFormatting.DARK_AQUA));
			if (label.travelTicks() > 0)
				tooltip.add(TravelTime.describe(label.travelTicks()).copy().withStyle(ChatFormatting.DARK_AQUA));
		}
		stack.getOrDefault(ModDataComponents.JOURNEY_NOTES.get(), JourneyNotes.NONE).addTo(tooltip);
		tooltip.add(Component.translatable("item.postrider.parcel.hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.forEach(builder);
	}
}
