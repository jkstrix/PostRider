package com.comedymonkey.postrider.post;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Sent mail, oldest first, waiting for a rider, and the mail riders are carrying right now. Kept with the overworld's
 * saved data so nothing is lost across restarts.
 */
public class PostOffice extends SavedData {
	public static final String ID = "postrider_post_office";
	private static final SavedData.Factory<PostOffice> FACTORY = new SavedData.Factory<>(PostOffice::new, PostOffice::load, null);

	/** A delivery a rider has set out with. */
	public record Active(PendingDelivery delivery, UUID rider) {
		public static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
			PendingDelivery.CODEC.fieldOf("delivery").forGetter(Active::delivery),
			UUIDUtil.CODEC.fieldOf("rider").forGetter(Active::rider)
		).apply(i, Active::new));
	}

	private final Deque<PendingDelivery> queue = new ArrayDeque<>();
	private final Map<UUID, Active> active = new LinkedHashMap<>();

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

	/** Puts a parcel back at the front of the queue, for when a rider couldn't set out with it. */
	public void putBack(PendingDelivery delivery) {
		queue.addFirst(delivery);
		setDirty();
	}

	public List<PendingDelivery> waiting() {
		return List.copyOf(queue);
	}

	public int size() {
		return queue.size();
	}

	/** Records that a rider has set out with a delivery. */
	public void setOut(PendingDelivery delivery, UUID rider) {
		active.put(delivery.id(), new Active(delivery, rider));
		setDirty();
	}

	/** The delivery is over, whether it arrived or was lost. */
	public void finish(UUID deliveryId) {
		if (active.remove(deliveryId) != null)
			setDirty();
	}

	public boolean isActive(UUID deliveryId) {
		return active.containsKey(deliveryId);
	}

	public boolean isRider(UUID rider) {
		for (Active each : active.values())
			if (each.rider().equals(rider))
				return true;
		return false;
	}

	public Collection<Active> active() {
		return List.copyOf(active.values());
	}

	public int activeCount() {
		return active.size();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		ListTag waiting = new ListTag();
		for (PendingDelivery delivery : queue)
			PendingDelivery.CODEC.encodeStart(ops, delivery).result().ifPresent(waiting::add);
		tag.put("queue", waiting);
		ListTag out = new ListTag();
		for (Active each : active.values())
			Active.CODEC.encodeStart(ops, each).result().ifPresent(out::add);
		tag.put("active", out);
		return tag;
	}

	public static PostOffice load(CompoundTag tag, HolderLookup.Provider registries) {
		DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		PostOffice office = new PostOffice();
		for (Tag entry : tag.getList("queue", Tag.TAG_COMPOUND))
			PendingDelivery.CODEC.parse(ops, entry).result().ifPresent(office.queue::addLast);
		for (Tag entry : tag.getList("active", Tag.TAG_COMPOUND))
			Active.CODEC.parse(ops, entry).result().ifPresent(a -> office.active.put(a.delivery().id(), a));
		return office;
	}
}
