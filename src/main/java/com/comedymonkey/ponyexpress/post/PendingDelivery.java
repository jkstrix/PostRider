package com.comedymonkey.ponyexpress.post;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/** A parcel that has been sent and is waiting for a rider to carry it. */
public record PendingDelivery(UUID id, UUID sender, String senderName, String fromName, GlobalPos from,
		String toName, List<ItemStack> items, long sentAt) {
	public static final Codec<PendingDelivery> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(PendingDelivery::id),
		UUIDUtil.CODEC.fieldOf("sender").forGetter(PendingDelivery::sender),
		Codec.STRING.fieldOf("sender_name").forGetter(PendingDelivery::senderName),
		Codec.STRING.fieldOf("from_name").forGetter(PendingDelivery::fromName),
		GlobalPos.CODEC.fieldOf("from").forGetter(PendingDelivery::from),
		Codec.STRING.fieldOf("to_name").forGetter(PendingDelivery::toName),
		ItemStack.CODEC.listOf().fieldOf("items").forGetter(PendingDelivery::items),
		Codec.LONG.fieldOf("sent_at").forGetter(PendingDelivery::sentAt)
	).apply(i, PendingDelivery::new));

	public PendingDelivery {
		items = List.copyOf(items);
	}
}
