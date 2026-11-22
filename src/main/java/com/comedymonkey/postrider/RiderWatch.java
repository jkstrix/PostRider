package com.comedymonkey.postrider;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.content.postman.PostmanEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Lets a player watch the riders: made a spectator and kept a few blocks off the rider most recently out, looking
 * at him (the mouse swings the view round him), then the next once that one's delivered, and so on. Shift lets go
 * of a rider until the next one. Started with {@code /postrider watch} in any world (and by the watch runs), and
 * stopped with {@code /postrider watch stop}.
 */
public final class RiderWatch {
	/** How often (in ticks) the spectator is given the next rider to follow, once his is done. */
	private static final int FOLLOW_CHECK = 10;
	/** How far back from the rider the spectator is kept, along his own line of sight. */
	public static final double FOLLOW_DISTANCE = 7.0;

	private static final class Watching {
		/** The rider the spectator was last set to follow (the player lets go with shift; nobody is forced back on). */
		@Nullable
		UUID followed;
		/** The player let go (shift): he's left where he is until the next rider. */
		boolean letGo;
	}

	private static final Map<UUID, Watching> watching = new HashMap<>();

	private RiderWatch() {}

	/** The player is a spectator from now on, following the riders. */
	public static void start(ServerPlayer player) {
		watching.put(player.getUUID(), new Watching());
		if (!player.isSpectator())
			player.setGameMode(GameType.SPECTATOR);
	}

	/** Stops following (the player stays a spectator; {@code /gamemode} is theirs). */
	public static void stop(ServerPlayer player) {
		watching.remove(player.getUUID());
	}

	public static boolean isWatching(ServerPlayer player) {
		return watching.containsKey(player.getUUID());
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		if (watching.isEmpty())
			return;
		MinecraftServer server = event.getServer();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Watching state = watching.get(player.getUUID());
			if (state != null)
				follow(server, player, state);
		}
	}

	public static void onServerStopped(ServerStoppedEvent event) {
		watching.clear();
	}

	/**
	 * Keeps the spectator a few blocks off the rider he's following, looking at him, and once that rider has
	 * finished picks the next one still on the road.
	 */
	private static void follow(MinecraftServer server, ServerPlayer player, Watching state) {
		if (!player.isSpectator())
			return;
		PostmanEntity followed = state.followed == null ? null : rider(server, state.followed);
		if (followed == null || !onTheRoad(followed)) {
			if (player.tickCount % FOLLOW_CHECK != 0)
				return;
			PostmanEntity next = youngestOnTheRoad(server);
			if (next == null || next == followed)
				return;
			state.followed = next.getUUID();
			state.letGo = false;
			followed = next;
			PostRider.LOGGER.info("Post Rider watch: {} is following {}", player.getName().getString(), next.getName().getString());
		}
		if (player.isShiftKeyDown())
			state.letGo = true;
		if (state.letGo)
			return;
		Vec3 at = viewpoint(followed.getEyePosition(), player.getLookAngle());
		// Relative turns of nothing leave the player looking wherever they were
		player.teleportTo((ServerLevel) followed.level(), at.x, at.y - player.getEyeHeight(), at.z, Set.of(Relative.X_ROT, Relative.Y_ROT),
			0, 0, false);
	}

	@Nullable
	private static PostmanEntity rider(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels())
			if (level.getEntity(id) instanceof PostmanEntity rider)
				return rider;
		return null;
	}

	/** The rider most recently out on a delivery, anywhere, for the whole of his trip; null if nobody is. */
	@Nullable
	private static PostmanEntity youngestOnTheRoad(MinecraftServer server) {
		PostmanEntity next = null;
		for (ServerLevel level : server.getAllLevels())
			for (Entity entity : level.getAllEntities())
				if (entity instanceof PostmanEntity rider && onTheRoad(rider) && (next == null || rider.tickCount < next.tickCount))
					next = rider;
		return next;
	}

	/** The one of these that has been about the least long (by its age in ticks); null if there's none. */
	@Nullable
	public static <T> T youngest(Collection<T> all, ToIntFunction<T> age) {
		T next = null;
		for (T each : all)
			if (next == null || age.applyAsInt(each) < age.applyAsInt(next))
				next = each;
		return next;
	}

	/** Where the spectator's eyes go: {@link #FOLLOW_DISTANCE} back from the rider's along the way he's looking, so the rider stays in the middle of the view. */
	public static Vec3 viewpoint(Vec3 riderEyes, Vec3 look) {
		return riderEyes.subtract(look.normalize().scale(FOLLOW_DISTANCE));
	}

	/** Whether a rider is out on a delivery (not finished, not gone). */
	public static boolean onTheRoad(PostmanEntity rider) {
		DeliveryRun run = rider.getRun();
		return rider.isAlive() && run != null && run.stage() != DeliveryRun.Stage.DONE;
	}
}
