package com.comedymonkey.postrider.content.postman.route;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import com.comedymonkey.postrider.PostRider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.Nullable;



import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The lie of the land for long-range plans, one world at a time: the height of the ground and where the water is,
 * four columns to each 8x8-block cell, looked up in tiles of 4x4 chunks. It comes from loaded chunks first, then
 * from chunks saved on disk, and where neither has it the ground is estimated from the world generator's noise (its
 * base heights, as the game places structures by): no chunk is generated for a plan, so a plan thousands of blocks
 * long is done in seconds. Kept in memory only, and forgotten after a while so changes to the world come through:
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
	/** Where in each cell the four columns are sampled. */
	private static final int[][] SAMPLES = {{2, 2}, {6, 2}, {2, 6}, {6, 6}};

	private static final Map<ResourceKey<Level>, TerrainSurvey> SURVEYS = new HashMap<>();

	private final ServerLevel level;
	private final int heightBits;
	private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(256, 0.75F, true);
	private final Queue<Arrival> arrivals = new ConcurrentLinkedQueue<>();

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

	/** How the survey stands, for the log: tiles read, and tiles still waiting on chunks off disk. */
	public String describe() {
		int waiting = 0;
		for (Tile tile : tiles.values())
			if (!tile.isDone())
				waiting++;
		return tiles.size() + " tiles, " + waiting + " waiting on chunks off disk";
	}

	/** Forgets everything (the server is stopping). */
	public static void clear() {
		SURVEYS.clear();
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
		} else if (tile.isDone() && now - tile.refreshedAt >= LOADED_LIFETIME) {
			refresh(tile, tx, tz, now);
		}
		return tile.isDone() ? tile.cells[Math.floorMod(cz, TILE_CELLS) * TILE_CELLS + Math.floorMod(cx, TILE_CELLS)] : null;
	}

	/** Any chunk of a tile that was estimated (or read off disk) and has since been loaded is read from the real ground. */
	private void refresh(Tile tile, int tx, int tz, long now) {
		tile.refreshedAt = now;
		for (int dx = 0; dx < TILE_CHUNKS; dx++)
			for (int dz = 0; dz < TILE_CHUNKS; dz++) {
				int chunk = dz * TILE_CHUNKS + dx;
				if (!tile.guessed[chunk])
					continue;
				ChunkPos pos = new ChunkPos(tx * TILE_CHUNKS + dx, tz * TILE_CHUNKS + dz);
				LevelChunk loaded = level.getChunkSource().getChunkNow(pos.x, pos.z);
				if (loaded == null)
					continue;
				tile.guessed[chunk] = false;
				tile.filled[chunk] = false;
				tile.chunksLeft++;
				fill(tile, pos, loadedHeights(loaded, Heightmap.Types.OCEAN_FLOOR), loadedHeights(loaded, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
			}
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
		// Read off disk on the disk thread; a chunk that was never generated comes back without heights and is
		// estimated on the server thread (the generator's noise is sampled there, and nothing can go missing)
		level.getChunkSource().chunkMap.read(pos).whenComplete((saved, error) -> {
			Arrival arrival = fromSaved(pos, saved, error, heightBits, minY);
			arrivals.add(arrival != null ? arrival : new Arrival(pos, null, null));
		});
	}

	/** On the disk thread: a saved, finished chunk's heights, or null if it hasn't been generated (or the save is odd). */
	@Nullable
	private static Arrival fromSaved(ChunkPos pos, @Nullable Optional<CompoundTag> saved, @Nullable Throwable error, int bits, int minY) {
		if (error != null || saved == null || saved.isEmpty())
			return null;
		CompoundTag tag = saved.get();
		if (!tag.getString("Status").endsWith("full"))
			return null;
		CompoundTag heightmaps = tag.getCompound("Heightmaps");
		try {
			return new Arrival(pos, heights(heightmaps.getLongArray("OCEAN_FLOOR"), bits, minY),
				heights(heightmaps.getLongArray("MOTION_BLOCKING_NO_LEAVES"), bits, minY));
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * A chunk that was never generated, estimated from the generator's noise at the columns the cells sample: the
	 * ground (the ocean floor, as the generator calls it) and the top of any water over it. Nothing is generated.
	 */
	private static Arrival estimated(ChunkPos pos, ChunkGenerator generator, RandomState random, LevelHeightAccessor heights) {
		int[] floors = new int[256], tops = new int[256];
		for (int column : sampledColumns()) {
			int x = pos.getMinBlockX() + column % 16, z = pos.getMinBlockZ() + column / 16;
			floors[column] = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
			tops[column] = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
		}
		return new Arrival(pos, floors, tops);
	}

	/** The columns of a chunk (index z * 16 + x) that its cells sample: the only ones an estimate need look at. */
	public static int[] sampledColumns() {
		int[] columns = new int[CELLS_PER_CHUNK * CELLS_PER_CHUNK * SAMPLES.length];
		int i = 0;
		for (int cx = 0; cx < CELLS_PER_CHUNK; cx++)
			for (int cz = 0; cz < CELLS_PER_CHUNK; cz++)
				for (int[] sample : SAMPLES)
					columns[i++] = (cz * CoarsePlanner.CELL + sample[1]) * 16 + cx * CoarsePlanner.CELL + sample[0];
		return columns;
	}

	private void tick() {
		for (Arrival arrival; (arrival = arrivals.poll()) != null; ) {
			if (arrival.floors() == null) {
				try {
					arrival = estimated(arrival.pos(), level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level);
				} catch (RuntimeException e) {
					PostRider.LOGGER.warn("Route survey couldn't estimate chunk {}: taken as plain ground at sea level", arrival.pos(), e);
					int[] plain = new int[256];
					Arrays.fill(plain, level.getSeaLevel() + 1);
					arrival = new Arrival(arrival.pos(), plain, plain);
				}
			}
			fill(arrival.pos(), arrival.floors(), arrival.tops());
		}
	}

	private void fill(ChunkPos pos, int[] floors, int[] tops) {
		Tile tile = tiles.get(ChunkPos.asLong(Math.floorDiv(pos.x, TILE_CHUNKS), Math.floorDiv(pos.z, TILE_CHUNKS)));
		if (tile != null) {
			// Off disk or estimated: to be read from the real ground once the chunk is loaded
			tile.guessed[Math.floorMod(pos.z, TILE_CHUNKS) * TILE_CHUNKS + Math.floorMod(pos.x, TILE_CHUNKS)] = true;
			fill(tile, pos, floors, tops);
		}
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
		/** Chunks whose heights came off disk or from the generator's noise, not the loaded chunk itself. */
		final boolean[] guessed = new boolean[TILE_CHUNKS * TILE_CHUNKS];
		final long since;
		long refreshedAt;
		long lifetime = TILE_LIFETIME;
		int chunksLeft = TILE_CHUNKS * TILE_CHUNKS;

		Tile(long since) {
			this.since = since;
			this.refreshedAt = since;
		}

		boolean isDone() {
			return chunksLeft <= 0;
		}
	}
}
