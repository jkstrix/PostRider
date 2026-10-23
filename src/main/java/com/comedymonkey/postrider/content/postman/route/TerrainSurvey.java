package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The lie of the land for long-range plans, one world at a time: the height of the ground and where the water is,
 * four columns to each 8x8-block cell, looked up in tiles of 4x4 chunks. It comes from loaded chunks first, then
 * from chunks saved on disk, and where neither has it the chunk is generated in the background (a few at a time,
 * let go again once read). Kept in memory only, and forgotten after a while so changes to the world come through:
 * a tile of loaded chunks is cheap to read and is looked at afresh every few seconds (a bridge or a canal someone
 * has just built is seen at once), one that had to come off disk or be generated is kept for ten minutes.
 */
public final class TerrainSurvey implements CoarsePlanner.Cells {
	/** A tile is this many chunks square. */
	public static final int TILE_CHUNKS = 4;
	public static final int CELLS_PER_CHUNK = 16 / CoarsePlanner.CELL;
	public static final int TILE_CELLS = TILE_CHUNKS * CELLS_PER_CHUNK;
	public static final int MAX_TILES = 4096;
	/** How long a tile read off disk or generated is trusted before it's looked at again (10 minutes). */
	public static final long TILE_LIFETIME = 12_000;
	/** How long a tile of loaded chunks is kept before it's read again (5 seconds): a plan's many lookups, no more. */
	public static final long LOADED_LIFETIME = 100;
	/** Chunks being generated for surveys at once, across the whole server. */
	public static final int MAX_GENERATING = 16;
	/** A chunk that hasn't generated in this long is given up on (and counted as plain ground at sea level). */
	public static final int GENERATE_TIMEOUT = 1200;
	/** Keeps a chunk loaded while it's generated and read; it lapses by itself if it's ever left behind. */
	public static final TicketType<ChunkPos> SURVEY = TicketType.create("postrider_survey", Comparator.comparingLong(ChunkPos::toLong),
		GENERATE_TIMEOUT + 200);
	/** Where in each cell the four columns are sampled. */
	private static final int[][] SAMPLES = {{2, 2}, {6, 2}, {2, 6}, {6, 6}};

	private static final Map<ResourceKey<Level>, TerrainSurvey> SURVEYS = new HashMap<>();
	private static int generating;

	private final ServerLevel level;
	private final int heightBits;
	private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(256, 0.75F, true);
	private final Queue<Arrival> arrivals = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<ChunkPos> toGenerate = new ArrayDeque<>();
	/** Chunks being generated, with the tick each was asked for. */
	private final Long2LongLinkedOpenHashMap generatingSince = new Long2LongLinkedOpenHashMap();

	private TerrainSurvey(ServerLevel level) {
		this.level = level;
		heightBits = Mth.ceillog2(level.getHeight() + 1);
	}

	public static TerrainSurvey of(ServerLevel level) {
		return SURVEYS.computeIfAbsent(level.dimension(), key -> new TerrainSurvey(level));
	}

	/** Each tick: takes in what's been read off disk, and starts and collects chunks being generated. */
	public static void tickAll(MinecraftServer server) {
		for (TerrainSurvey survey : SURVEYS.values())
			if (server.getLevel(survey.level.dimension()) == survey.level)
				survey.tick();
	}

	/** How the survey stands, for the log: tiles read, tiles still waiting on chunks, chunks queued and being generated. */
	public String describe() {
		int waiting = 0;
		for (Tile tile : tiles.values())
			if (!tile.isDone())
				waiting++;
		return tiles.size() + " tiles, " + waiting + " waiting on chunks, " + toGenerate.size() + " chunks queued to generate, " + generatingSince.size() + " generating";
	}

	/** Forgets everything (the server is stopping). */
	public static void clear() {
		SURVEYS.clear();
		generating = 0;
	}

	@Override
	@Nullable
	public CoarsePlanner.Cell cell(int cx, int cz) {
		int tx = Math.floorDiv(cx, TILE_CELLS), tz = Math.floorDiv(cz, TILE_CELLS);
		long key = ChunkPos.asLong(tx, tz);
		Tile tile = tiles.get(key);
		long now = level.getGameTime();
		if (tile != null && tile.isDone() && now - tile.since > tile.lifetime) {
			tiles.remove(key);
			tile = null;
		}
		if (tile == null) {
			tile = survey(tx, tz, now);
			tiles.put(key, tile);
			trim();
		}
		return tile.isDone() ? tile.cells[Math.floorMod(cz, TILE_CELLS) * TILE_CELLS + Math.floorMod(cx, TILE_CELLS)] : null;
	}

	private void trim() {
		Iterator<Tile> oldest = tiles.values().iterator();
		while (tiles.size() > MAX_TILES && oldest.hasNext()) {
			if (oldest.next().isDone())
				oldest.remove();
		}
	}

	private Tile survey(int tx, int tz, long now) {
		Tile tile = new Tile(now);
		int loaded = 0;
		for (int dx = 0; dx < TILE_CHUNKS; dx++)
			for (int dz = 0; dz < TILE_CHUNKS; dz++) {
				ChunkPos pos = new ChunkPos(tx * TILE_CHUNKS + dx, tz * TILE_CHUNKS + dz);
				LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
				if (chunk != null) {
					fill(tile, pos, loadedHeights(chunk, Heightmap.Types.OCEAN_FLOOR), loadedHeights(chunk, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
					loaded++;
				} else {
					readSaved(pos);
				}
			}
		tile.lifetime = lifetime(loaded);
		return tile;
	}

	/** How long a tile is kept, from how many of its chunks were loaded when it was read (all of them: not long). */
	static long lifetime(int chunksLoaded) {
		return chunksLoaded >= TILE_CHUNKS * TILE_CHUNKS ? LOADED_LIFETIME : TILE_LIFETIME;
	}

	private void readSaved(ChunkPos pos) {
		int minY = level.getMinBuildHeight();
		level.getChunkSource().chunkMap.read(pos).whenComplete((saved, error) -> arrivals.add(fromSaved(pos, saved, error, heightBits, minY)));
	}

	/** On the disk thread: a saved, finished chunk's heights, or a note that it needs generating. */
	private static Arrival fromSaved(ChunkPos pos, @Nullable Optional<CompoundTag> saved, @Nullable Throwable error, int bits, int minY) {
		if (error != null || saved == null || saved.isEmpty())
			return new Arrival(pos, null, null);
		CompoundTag tag = saved.get();
		if (!tag.getString("Status").endsWith("full"))
			return new Arrival(pos, null, null);
		CompoundTag heightmaps = tag.getCompound("Heightmaps");
		try {
			return new Arrival(pos, heights(heightmaps.getLongArray("OCEAN_FLOOR"), bits, minY),
				heights(heightmaps.getLongArray("MOTION_BLOCKING_NO_LEAVES"), bits, minY));
		} catch (RuntimeException e) {
			// An old or odd save: generate it instead
			return new Arrival(pos, null, null);
		}
	}

	private void tick() {
		long now = level.getGameTime();
		for (Arrival arrival; (arrival = arrivals.poll()) != null; ) {
			if (arrival.floors() == null)
				toGenerate.add(arrival.pos());
			else
				fill(arrival.pos(), arrival.floors(), arrival.tops());
		}
		Iterator<Long2LongLinkedOpenHashMap.Entry> waiting = generatingSince.long2LongEntrySet().fastIterator();
		while (waiting.hasNext()) {
			Long2LongLinkedOpenHashMap.Entry entry = waiting.next();
			ChunkPos pos = new ChunkPos(entry.getLongKey());
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
			boolean late = now - entry.getLongValue() > GENERATE_TIMEOUT;
			if (chunk == null && !late)
				continue;
			if (chunk != null) {
				fill(pos, loadedHeights(chunk, Heightmap.Types.OCEAN_FLOOR), loadedHeights(chunk, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
			} else {
				PostRider.LOGGER.warn("Route survey gave up waiting for chunk {} to generate", pos);
				int[] plain = new int[256];
				Arrays.fill(plain, level.getSeaLevel() + 1);
				fill(pos, plain, plain);
			}
			level.getChunkSource().removeRegionTicket(SURVEY, pos, 0, pos);
			waiting.remove();
			generating--;
		}
		while (generating < MAX_GENERATING && !toGenerate.isEmpty()) {
			ChunkPos pos = toGenerate.poll();
			if (generatingSince.containsKey(pos.toLong()))
				continue;
			level.getChunkSource().addRegionTicket(SURVEY, pos, 0, pos);
			generatingSince.put(pos.toLong(), now);
			generating++;
		}
	}

	private void fill(ChunkPos pos, int[] floors, int[] tops) {
		Tile tile = tiles.get(ChunkPos.asLong(Math.floorDiv(pos.x, TILE_CHUNKS), Math.floorDiv(pos.z, TILE_CHUNKS)));
		if (tile != null)
			fill(tile, pos, floors, tops);
	}

	private void fill(Tile tile, ChunkPos pos, int[] floors, int[] tops) {
		CoarsePlanner.Cell[] chunkCells = cellsOfChunk(floors, tops, level.getMinBuildHeight());
		if (!claim(tile.filled, pos))
			return;
		int baseX = Math.floorMod(pos.x, TILE_CHUNKS) * CELLS_PER_CHUNK, baseZ = Math.floorMod(pos.z, TILE_CHUNKS) * CELLS_PER_CHUNK;
		for (int cx = 0; cx < CELLS_PER_CHUNK; cx++)
			for (int cz = 0; cz < CELLS_PER_CHUNK; cz++)
				tile.cells[(baseZ + cz) * TILE_CELLS + baseX + cx] = chunkCells[cz * CELLS_PER_CHUNK + cx];
		tile.chunksLeft--;
	}

	private static int[] loadedHeights(LevelChunk chunk, Heightmap.Types type) {
		int[] heights = new int[256];
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++)
				heights[z * 16 + x] = chunk.getHeight(type, x, z) + 1;
		return heights;
	}

	// ---------------------------------------------------------------- the sums

	/**
	 * Marks a tile's chunk as heard back from; false if it already was. Each chunk counts once, as a tile surveyed
	 * again can still hear back about its chunks from the time before.
	 */
	static boolean claim(boolean[] filled, ChunkPos pos) {
		int chunk = Math.floorMod(pos.z, TILE_CHUNKS) * TILE_CHUNKS + Math.floorMod(pos.x, TILE_CHUNKS);
		if (filled[chunk])
			return false;
		filled[chunk] = true;
		return true;
	}

	/** A saved heightmap (as stored in a chunk's "Heightmaps") as the first free height in each of its 256 columns. */
	public static int[] heights(long[] packed, int bits, int minY) {
		SimpleBitStorage storage = new SimpleBitStorage(bits, 256, packed);
		int[] heights = new int[256];
		for (int i = 0; i < 256; i++)
			heights[i] = storage.get(i) + minY;
		return heights;
	}

	/** The cells of one chunk (x fastest) from its floor and top heights (column index z * 16 + x). */
	public static CoarsePlanner.Cell[] cellsOfChunk(int[] floors, int[] tops, int minY) {
		CoarsePlanner.Cell[] cells = new CoarsePlanner.Cell[CELLS_PER_CHUNK * CELLS_PER_CHUNK];
		for (int cx = 0; cx < CELLS_PER_CHUNK; cx++)
			for (int cz = 0; cz < CELLS_PER_CHUNK; cz++) {
				int[] cellFloors = new int[SAMPLES.length], cellTops = new int[SAMPLES.length];
				for (int i = 0; i < SAMPLES.length; i++) {
					int column = (cz * CoarsePlanner.CELL + SAMPLES[i][1]) * 16 + cx * CoarsePlanner.CELL + SAMPLES[i][0];
					cellFloors[i] = floors[column];
					cellTops[i] = tops[column];
				}
				cells[cz * CELLS_PER_CHUNK + cx] = CoarsePlanner.Cell.of(cellFloors, cellTops, minY);
			}
		return cells;
	}

	private record Arrival(ChunkPos pos, int @Nullable [] floors, int @Nullable [] tops) {}

	private static final class Tile {
		final CoarsePlanner.Cell[] cells = new CoarsePlanner.Cell[TILE_CELLS * TILE_CELLS];
		final boolean[] filled = new boolean[TILE_CHUNKS * TILE_CHUNKS];
		final long since;
		long lifetime = TILE_LIFETIME;
		int chunksLeft = TILE_CHUNKS * TILE_CHUNKS;

		Tile(long since) {
			this.since = since;
		}

		boolean isDone() {
			return chunksLeft <= 0;
		}
	}
}
