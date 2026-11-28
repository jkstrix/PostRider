package com.comedymonkey.postrider.fabric;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.RiderChunks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The riders' chunk tickets on Fabric, doing what NeoForge's TicketController (postrider:rider) does for the mod:
 * <ul>
 * <li>tickets are held per rider (by UUID), in chunks held loaded ("normal") or loaded with natural spawning
 * ("ticking"), and a chunk keeps one ticket of each kind for as long as any rider holds it;</li>
 * <li>the tickets are those NeoForge adds (its neoforge:entity and neoforge:entity_with_natural_spawning types): loading
 * and simulating at {@link ChunkMap#FORCED_TICKET_LEVEL}, not saved by vanilla, and a chunk held with natural
 * spawning spawns mobs even with no player close enough (mixin.ChunkMapMixin);</li>
 * <li>they're saved with each level (here as saved data, data/postrider/rider_tickets.dat in the level's folder;
 * NeoForge keeps them in the level's chunk_tickets.dat);</li>
 * <li>when the server closes a level's tickets they're put aside, and when a level loads (MinecraftServer.prepareLevels,
 * mixin.MinecraftServerMixin) the saved ones are handed to {@link RiderChunks#validate}, then the rest are held again,
 * before the server waits for the level's first chunks.</li>
 * </ul>
 * The two ticket types aren't put in the ticket type registry (vanilla only tells tickets apart by their type's identity,
 * and never saves these), so the game's registries are the same as on NeoForge, where they're NeoForge's own.
 */
public final class FabricRiderTickets extends SavedData {
	// Keeping the dimension active too: NeoForge's world tick counts any ticket controller's chunks as keeping a world
	// awake with no player in it (its patch to ServerLevel.tick), where vanilla only counts tickets with this flag, so
	// without it a rider froze 15 s after the last player left his dimension
	private static final int FLAGS = TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE;
	private static final TicketType RIDER = new TicketType(TicketType.NO_TIMEOUT, FLAGS);
	private static final TicketType RIDER_WITH_NATURAL_SPAWNING = new TicketType(TicketType.NO_TIMEOUT, FLAGS);

	/** One rider's saved tickets: the chunks held loaded, and those held with natural spawning (NeoForge's TicketSet). */
	private record OwnerTickets(UUID owner, LongSet normal, LongSet naturalSpawning) {
		private static final Codec<LongSet> CHUNKS = Codec.LONG_STREAM.xmap(LongOpenHashSet::toSet, LongCollection::longStream);
		static final Codec<OwnerTickets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("uuid").forGetter(OwnerTickets::owner),
			CHUNKS.optionalFieldOf("normal", LongSets.emptySet()).forGetter(OwnerTickets::normal),
			CHUNKS.optionalFieldOf("natural_spawning", LongSets.emptySet()).forGetter(OwnerTickets::naturalSpawning))
			.apply(instance, OwnerTickets::new));
	}

	private static final Codec<FabricRiderTickets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		OwnerTickets.CODEC.listOf().optionalFieldOf("entity_chunks", List.of()).forGetter(FabricRiderTickets::pack))
		.apply(instance, FabricRiderTickets::unpack));

	static final SavedDataType<FabricRiderTickets> TYPE = new SavedDataType<>(PostRider.asResource("rider_tickets"), FabricRiderTickets::new, CODEC, null);

	/** The riders holding each chunk, held now. */
	private final Long2ObjectMap<Set<UUID>> loading = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectMap<Set<UUID>> loadingNaturalSpawning = new Long2ObjectOpenHashMap<>();
	/** The riders' saved tickets, not yet held again (read from the save), or put aside as the server closed the level. */
	private final Long2ObjectMap<Set<UUID>> deactivatedLoading = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectMap<Set<UUID>> deactivatedLoadingNaturalSpawning = new Long2ObjectOpenHashMap<>();

	public FabricRiderTickets() {}

	/** Holds a chunk for a rider, or lets it go (Platform.forceChunk; NeoForge's TicketController.forceChunk). */
	static void forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean naturalSpawning) {
		FabricRiderTickets tickets = level.getDataStorage().computeIfAbsent(TYPE);
		TicketStorage storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
		long chunk = new ChunkPos(chunkX, chunkZ).pack();
		if (add) {
			// Loads it at once if it wasn't, as NeoForge does
			if (tickets.add(storage, owner, chunk, naturalSpawning))
				level.getChunk(chunkX, chunkZ);
		} else {
			tickets.remove(storage, owner, chunk, naturalSpawning, false);
		}
	}

	/** Whether a chunk is held with natural spawning for some rider: it spawns mobs with no player close enough. */
	public static boolean forcesNaturalSpawning(ServerLevel level, ChunkPos chunk) {
		FabricRiderTickets tickets = level.getDataStorage().get(TYPE);
		return tickets != null && tickets.loadingNaturalSpawning.containsKey(chunk.pack());
	}

	/**
	 * As a level loads, before the server waits for its first chunks: hands its saved tickets to
	 * {@link RiderChunks#validate}, which drops some, and holds the rest again (NeoForge's
	 * ForcedChunkManager.activateAllDeactivatedTickets).
	 */
	public static void activate(ServerLevel level) {
		FabricRiderTickets tickets = level.getDataStorage().get(TYPE);
		if (tickets == null || tickets.hasNoDeactivatedTickets())
			return;
		Map<UUID, OwnerTickets> saved = tickets.gatherDeactivated();
		if (!saved.isEmpty())
			RiderChunks.validate(level, tickets.savedTickets(saved));
		TicketStorage storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
		inheritSources(storage, RIDER, tickets.deactivatedLoading, tickets.loading);
		inheritSources(storage, RIDER_WITH_NATURAL_SPAWNING, tickets.deactivatedLoadingNaturalSpawning, tickets.loadingNaturalSpawning);
	}

	/** As the server closes a level's tickets (vanilla takes them all away): puts the riders' aside, still to be saved. */
	public static void deactivate(ServerLevel level) {
		FabricRiderTickets tickets = level.getDataStorage().get(TYPE);
		if (tickets == null)
			return;
		inheritSources(null, null, tickets.loading, tickets.deactivatedLoading);
		inheritSources(null, null, tickets.loadingNaturalSpawning, tickets.deactivatedLoadingNaturalSpawning);
	}

	private static Ticket ticket(boolean naturalSpawning) {
		return new Ticket(naturalSpawning ? RIDER_WITH_NATURAL_SPAWNING : RIDER, ChunkMap.FORCED_TICKET_LEVEL);
	}

	private Long2ObjectMap<Set<UUID>> sources(boolean naturalSpawning, boolean deactivated) {
		if (deactivated)
			return naturalSpawning ? deactivatedLoadingNaturalSpawning : deactivatedLoading;
		return naturalSpawning ? loadingNaturalSpawning : loading;
	}

	private boolean add(TicketStorage storage, UUID owner, long chunk, boolean naturalSpawning) {
		Set<UUID> sources = sources(naturalSpawning, false).computeIfAbsent(chunk, c -> new HashSet<>());
		if (sources.isEmpty())
			storage.addTicket(chunk, ticket(naturalSpawning));
		if (sources.add(owner)) {
			setDirty();
			return true;
		}
		return false;
	}

	private boolean remove(@Nullable TicketStorage storage, UUID owner, long chunk, boolean naturalSpawning, boolean deactivated) {
		Long2ObjectMap<Set<UUID>> map = sources(naturalSpawning, deactivated);
		Set<UUID> sources = map.get(chunk);
		if (sources == null || !sources.remove(owner))
			return false;
		if (sources.isEmpty()) {
			map.remove(chunk);
			// A ticket put aside isn't in the level's ticket storage
			if (!deactivated && storage != null)
				storage.removeTicket(chunk, ticket(naturalSpawning));
		}
		setDirty();
		return true;
	}

	private static void inheritSources(@Nullable TicketStorage storage, @Nullable TicketType type, Long2ObjectMap<Set<UUID>> from,
			Long2ObjectMap<Set<UUID>> to) {
		Ticket ticket = type == null ? null : new Ticket(type, ChunkMap.FORCED_TICKET_LEVEL);
		for (Long2ObjectMap.Entry<Set<UUID>> entry : Long2ObjectMaps.fastIterable(from)) {
			if (ticket != null && storage != null)
				storage.addTicket(entry.getLongKey(), ticket);
			to.computeIfAbsent(entry.getLongKey(), c -> new HashSet<>()).addAll(entry.getValue());
		}
		from.clear();
	}

	private boolean hasNoDeactivatedTickets() {
		return deactivatedLoading.isEmpty() && deactivatedLoadingNaturalSpawning.isEmpty();
	}

	/** The tickets put aside, by rider, as they were when the level loaded. */
	private Map<UUID, OwnerTickets> gatherDeactivated() {
		Map<UUID, OwnerTickets> owners = new HashMap<>();
		gather(deactivatedLoading, false, owners);
		gather(deactivatedLoadingNaturalSpawning, true, owners);
		return owners;
	}

	private static void gather(Long2ObjectMap<Set<UUID>> sources, boolean naturalSpawning, Map<UUID, OwnerTickets> owners) {
		for (Long2ObjectMap.Entry<Set<UUID>> entry : Long2ObjectMaps.fastIterable(sources))
			for (UUID owner : entry.getValue()) {
				OwnerTickets tickets = owners.computeIfAbsent(owner, o -> new OwnerTickets(o, new LongOpenHashSet(), new LongOpenHashSet()));
				(naturalSpawning ? tickets.naturalSpawning() : tickets.normal()).add(entry.getLongKey());
			}
	}

	/** The saved tickets as RiderChunks.validate sees them (NeoForge's TicketHelper). */
	private RiderChunks.SavedTickets savedTickets(Map<UUID, OwnerTickets> saved) {
		return new RiderChunks.SavedTickets() {
			@Override
			public Set<UUID> owners() {
				return saved.keySet();
			}

			@Override
			public LongCollection loadedChunks(UUID owner) {
				return saved.get(owner).normal();
			}

			@Override
			public void removeAll(UUID owner) {
				OwnerTickets tickets = saved.get(owner);
				if (tickets == null)
					return;
				for (long chunk : tickets.normal())
					remove(null, owner, chunk, false, true);
				for (long chunk : tickets.naturalSpawning())
					remove(null, owner, chunk, true, true);
			}

			@Override
			public void removeLoaded(UUID owner, long chunk) {
				OwnerTickets tickets = saved.get(owner);
				if (tickets != null && tickets.normal().contains(chunk))
					remove(null, owner, chunk, false, true);
			}
		};
	}

	/** Every rider's tickets, held or put aside, for saving. */
	private List<OwnerTickets> pack() {
		Map<UUID, OwnerTickets> owners = new HashMap<>();
		gather(loading, false, owners);
		gather(loadingNaturalSpawning, true, owners);
		gather(deactivatedLoading, false, owners);
		gather(deactivatedLoadingNaturalSpawning, true, owners);
		return List.copyOf(owners.values());
	}

	/** Read from the save: every ticket is put aside until the level loads. */
	private static FabricRiderTickets unpack(List<OwnerTickets> owners) {
		FabricRiderTickets tickets = new FabricRiderTickets();
		for (OwnerTickets owner : owners) {
			for (long chunk : owner.normal())
				tickets.deactivatedLoading.computeIfAbsent(chunk, c -> new HashSet<>()).add(owner.owner());
			for (long chunk : owner.naturalSpawning())
				tickets.deactivatedLoadingNaturalSpawning.computeIfAbsent(chunk, c -> new HashSet<>()).add(owner.owner());
		}
		return tickets;
	}
}
