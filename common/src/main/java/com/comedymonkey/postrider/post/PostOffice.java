package com.comedymonkey.postrider.post;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.Roster;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.comedymonkey.postrider.platform.Services;

/**
 * Sent mail, oldest first, waiting for a rider, and the mail riders are carrying right now. Kept with the server's
 * saved data so nothing is lost across restarts.
 */
public class PostOffice extends SavedData {
	/** The file name worlds from before Minecraft 26.1 kept it under. */
	public static final String ID = "postrider_post_office";

	/**
	 * A delivery a rider has set out with: the rider entity, which of the crew it is (see {@link Roster}), and where he
	 * was last seen, so he can be brought back there if he vanishes.
	 */
	public record Active(PendingDelivery delivery, UUID rider, String riderId, Optional<BlockPos> lastSeen) {
		public static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
			PendingDelivery.CODEC.fieldOf("delivery").forGetter(Active::delivery),
			UUIDUtil.CODEC.fieldOf("rider").forGetter(Active::rider),
			Codec.STRING.optionalFieldOf("rider_id", "").forGetter(Active::riderId),
			BlockPos.CODEC.optionalFieldOf("last_seen").forGetter(Active::lastSeen)
		).apply(i, Active::new));
	}

	public static final Codec<PostOffice> CODEC = RecordCodecBuilder.create(i -> i.group(
		PendingDelivery.CODEC.listOf().optionalFieldOf("queue", List.of()).forGetter(PostOffice::waiting),
		Active.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(o -> List.copyOf(o.active.values()))
	).apply(i, PostOffice::new));
	public static final SavedDataType<PostOffice> TYPE =
		Services.PLATFORM.savedDataType(PostRider.asResource("post_office"), PostOffice::new, CODEC);

	private final Deque<PendingDelivery> queue = new ArrayDeque<>();
	private final Map<UUID, Active> active = new LinkedHashMap<>();

	public PostOffice() {}

	private PostOffice(List<PendingDelivery> waiting, List<Active> out) {
		queue.addAll(waiting);
		for (Active each : out)
			active.put(each.delivery().id(), each);
	}

	public static PostOffice get(MinecraftServer server) {
		return LegacySavedData.get(server, TYPE, ID);
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

	/** Records that one of the crew has set out with a delivery. */
	public void setOut(PendingDelivery delivery, UUID rider, String riderId) {
		active.put(delivery.id(), new Active(delivery, rider, riderId, Optional.empty()));
		setDirty();
	}

	/** The crew members out on the road right now, who can't be sent anywhere else. */
	public Set<String> busyRiders() {
		Set<String> busy = new HashSet<>();
		for (Active each : active.values())
			busy.add(each.riderId());
		return busy;
	}

	/** Whether this rider is the one carrying this delivery. */
	public boolean isCarrying(UUID deliveryId, UUID rider) {
		Active carried = active.get(deliveryId);
		return carried != null && carried.rider().equals(rider);
	}

	/** Notes where a delivery's rider is now. */
	public void saw(UUID deliveryId, BlockPos where) {
		Active carried = active.get(deliveryId);
		if (carried != null && !carried.lastSeen().equals(Optional.of(where))) {
			active.put(deliveryId, new Active(carried.delivery(), carried.rider(), carried.riderId(), Optional.of(where)));
			setDirty();
		}
	}

	/** A vanished rider has been brought back as a new entity, still carrying the mail. */
	public void replaceRider(UUID deliveryId, UUID rider, String riderId) {
		Active carried = active.get(deliveryId);
		if (carried != null) {
			active.put(deliveryId, new Active(carried.delivery(), rider, riderId, carried.lastSeen()));
			setDirty();
		}
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
}
