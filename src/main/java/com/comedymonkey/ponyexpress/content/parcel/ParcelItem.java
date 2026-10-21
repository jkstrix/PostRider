package com.comedymonkey.ponyexpress.content.parcel;

import java.util.List;

import com.comedymonkey.ponyexpress.post.TravelTime;
import com.comedymonkey.ponyexpress.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/** A sealed parcel. Use it to unwrap it: everything inside goes into your inventory, and what doesn't fit drops. */
public class ParcelItem extends Item {
	public ParcelItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack parcel = player.getItemInHand(hand);
		if (!level.isClientSide) {
			ItemContainerContents contents = parcel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			player.setItemInHand(hand, ItemStack.EMPTY);
			for (ItemStack item : contents.nonEmptyItemsCopy())
				player.getInventory().placeItemBackInInventory(item);
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.8F);
		}
		return InteractionResultHolder.sidedSuccess(ItemStack.EMPTY, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		ParcelLabel label = stack.get(ModDataComponents.PARCEL_LABEL.get());
		if (label != null) {
			tooltip.add(Component.translatable("item.ponyexpress.parcel.from", label.from(), label.sender()).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("item.ponyexpress.parcel.to", label.to()).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("item.ponyexpress.parcel.day", label.day()).withStyle(ChatFormatting.DARK_AQUA));
			if (label.travelTicks() > 0)
				tooltip.add(TravelTime.describe(label.travelTicks()).copy().withStyle(ChatFormatting.DARK_AQUA));
		}
		tooltip.add(Component.translatable("item.ponyexpress.parcel.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
