package com.comedymonkey.ponyexpress.post;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The queue of sent parcels waiting for a rider, oldest first. Kept with the overworld's saved data so nothing is
 * lost across restarts.
 */
public class PostOffice extends SavedData {
	public static final String ID = "ponyexpress_post_office";
	private static final SavedData.Factory<PostOffice> FACTORY = new SavedData.Factory<>(PostOffice::new, PostOffice::load, null);

	private final Deque<PendingDelivery> queue = new ArrayDeque<>();

	public static PostOffice get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
	}

	public void enqueue(PendingDelivery delivery) {
		queue.addLast(delivery);
		setDirty();
	}

	/** Takes the oldest waiting parcel off the queue, or null if there are none. */
	@Nullable
	public PendingDelivery poll() {
		PendingDelivery next = queue.pollFirst();
		if (next != null)
			setDirty();
		return next;
	}

	public List<PendingDelivery> waiting() {
		return List.copyOf(queue);
	}

	public int size() {
		return queue.size();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		ListTag list = new ListTag();
		for (PendingDelivery delivery : queue)
			PendingDelivery.CODEC.encodeStart(ops, delivery).result().ifPresent(list::add);
		tag.put("queue", list);
		return tag;
	}

	public static PostOffice load(CompoundTag tag, HolderLookup.Provider registries) {
		DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		PostOffice office = new PostOffice();
		for (Tag entry : tag.getList("queue", Tag.TAG_COMPOUND))
			PendingDelivery.CODEC.parse(ops, entry).result().ifPresent(office.queue::addLast);
		return office;
	}
}
