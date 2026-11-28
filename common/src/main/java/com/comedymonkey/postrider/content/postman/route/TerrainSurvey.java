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
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;



import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The lie of the land for long-range plans, one world at a time: the height of the ground and where the water is,
 * four columns to each 8x8-block cell, looked up in tiles of 4x4 chunks. It comes from loaded chunks first, then
 * from chunks saved on disk, and where neither has it the ground is estimated from the world generator's noise (its
 * base heights, as the game places structures by), one column in the middle of each cell: no chunk is generated for
 * a plan. The noise is costly (a fraction of a millisecond a column), so it's sampled a column at a time in the
 * tick's spare time ({@link #estimateBudget}), and a long trip into unexplored land is surveyed over some seconds
 * rather than in one tick that stalls the server. Kept in memory only, and forgotten after a while so changes to the world come through:
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
	/** The least the server thread spends estimating ground from the noise each tick, all worlds together (4 ms); see estimateBudget. */
	public static final long ESTIMATE_NANOS_PER_TICK = 4_000_000;
	/** Where in each cell the four columns are sampled. */
	private static final int[][] SAMPLES = {{2, 2}, {6, 2}, {2, 6}, {6, 6}};
	private static final int[] SAMPLED_COLUMNS = sampledColumns();
	private static final int[] ESTIMATED_COLUMNS = estimatedColumns();

	private static final Map<ResourceKey<Level>, TerrainSurvey> SURVEYS = new HashMap<>();

	private final ServerLevel level;
	private final int heightBits;
	private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(256, 0.75F, true);
	private final Queue<Arrival> arrivals = new ConcurrentLinkedQueue<>();
	/** The never-generated chunk being estimated, a column at a time, if any. */
	@Nullable
	private Estimate estimating;

	private TerrainSurvey(ServerLevel level) {
		this.level = level;
		heightBits = Mth.ceillog2(level.getHeight() + 1);
	}

	public static TerrainSurvey of(ServerLevel level) {
		return SURVEYS.computeIfAbsent(level.dimension(), key -> new TerrainSurvey(level));
	}

	/** Each tick: takes in what's been read off disk, and estimates never-generated chunks for as long as the budget allows. */
	public static void tickAll(MinecraftServer server) {
		long start = System.nanoTime();
		long budget = estimateBudget(server.tickRateManager().nanosecondsPerTick(), server.getAverageTickTimeNanos(), averageEstimateNanos);
		long deadline = start + budget;
		boolean estimated = false;
		for (TerrainSurvey survey : SURVEYS.values())
			if (server.getLevel(survey.level.dimension()) == survey.level)
				estimated |= survey.tick(deadline);
		// The survey's own share of the server's tick time, so it isn't taken for the server being busy
		long spent = estimated ? System.nanoTime() - start : 0;
		averageEstimateNanos += (spent - averageEstimateNanos) / AVERAGE_OVER;
	}

	/** The most the survey takes of a tick (30 ms of 50), however idle the server: the rest is the world's. */
	public static final long ESTIMATE_NANOS_MAX = 30_000_000;
	/** Of a tick's spare time, this share is left free: the server's ticks vary, and a slow one mustn't run late. */
	public static final double SPARE_KEPT = 0.25;
	/** The survey's own time is averaged over about this many ticks, as the server averages its tick times. */
	private static final int AVERAGE_OVER = 100;
	private static long averageEstimateNanos;

	/**
	 * How long the survey may spend estimating never-generated ground this tick: the tick's spare time (what the
	 * server doesn't use of it, with a share kept free), never less than {@link #ESTIMATE_NANOS_PER_TICK} and never
	 * more than {@link #ESTIMATE_NANOS_MAX}. On a server with time to spare a long trip into unexplored land is
	 * surveyed several times faster, so the route is worked out as quickly as it used to be; on a busy one it goes no
	 * faster than the least, and the server never stalls for it. The server's average tick time includes the survey's
	 * own, which is taken off first, or the survey would see its own work as the server being busy.
	 */
	static long estimateBudget(long nanosPerTick, long averageTickNanos, long averageEstimateNanos) {
		long serverNanos = Math.max(0, averageTickNanos - averageEstimateNanos);
		long spare = (long) ((nanosPerTick - serverNanos) * (1 - SPARE_KEPT));
		return Math.clamp(spare, ESTIMATE_NANOS_PER_TICK, ESTIMATE_NANOS_MAX);
	}

	/** How the survey stands, for the log: tiles read, and tiles still waiting on chunks off disk. */
	public String describe() {
		int waiting = 0;
		for (Tile tile : tiles.values())
			if (!tile.isDone())
				waiting++;
		return tiles.size() + " tiles, " + waiting + " waiting on chunks off disk or still being estimated";
	}

	/** Forgets everything (the server is stopping). */
	public static void clear() {
		SURVEYS.clear();
	}

	@Override
	@Nullable
	public CoarsePlanner.Cell cell(int cx, int cz) {
		int tx = Math.floorDiv(cx, TILE_CELLS), tz = Math.floorDiv(cz, TILE_CELLS);
		long key = ChunkPos.pack(tx, tz);
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

	/**
	 * Every chunk of a tile that's loaded now is read afresh from the real ground: one that was estimated (or read off
	 * disk) and has since been loaded, and one that was loaded already, which may have changed since (a bridge or a
	 * canal built, a lake filled in). A tile that was only partly loaded is kept for ten minutes, and its loaded chunks
	 * were once left as they were read all that time.
	 */
	private void refresh(Tile tile, int tx, int tz, long now) {
		tile.refreshedAt = now;
		for (int dx = 0; dx < TILE_CHUNKS; dx++)
			for (int dz = 0; dz < TILE_CHUNKS; dz++) {
				int chunk = dz * TILE_CHUNKS + dx;
				ChunkPos pos = new ChunkPos(tx * TILE_CHUNKS + dx, tz * TILE_CHUNKS + dz);
				LevelChunk loaded = level.getChunkSource().getChunkNow(pos.x(), pos.z());
				if (loaded == null)
					continue;
				tile.guessed[chunk] = false;
				tile.filled[chunk] = false;
				tile.chunksLeft++;
				int[][] ground = groundOf(loaded);
				fill(tile, pos, ground[0], ground[1], ground[2]);
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
				LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
				if (chunk != null) {
					int[][] ground = groundOf(chunk);
					fill(tile, pos, ground[0], ground[1], ground[2]);
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
		int minY = level.getMinY();
		// Read off disk on the disk thread; a chunk that was never generated comes back without heights and is
		// estimated on the server thread (the generator's noise is sampled there, and nothing can go missing)
		level.getChunkSource().chunkMap.read(pos).whenComplete((saved, error) -> {
			Arrival arrival = fromSaved(pos, saved, error, heightBits, minY);
			arrivals.add(arrival != null ? arrival : new Arrival(pos, null, null, null));
		});
	}

	/** On the disk thread: a saved, finished chunk's heights, or null if it hasn't been generated (or the save is odd). */
	@Nullable
	private static Arrival fromSaved(ChunkPos pos, @Nullable Optional<CompoundTag> saved, @Nullable Throwable error, int bits, int minY) {
		if (error != null || saved == null || saved.isEmpty())
			return null;
		CompoundTag tag = saved.get();
		if (!tag.getStringOr("Status", "").endsWith("full"))
			return null;
		CompoundTag heightmaps = tag.getCompoundOrEmpty("Heightmaps");
		try {
			// (Its biomes aren't read off disk: an ice field there is known once the chunk is loaded and read afresh)
			return new Arrival(pos, heights(heightmaps.getLongArray("OCEAN_FLOOR").orElse(new long[0]), bits, minY),
				heights(heightmaps.getLongArray("MOTION_BLOCKING_NO_LEAVES").orElse(new long[0]), bits, minY), new int[256]);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * A chunk that was never generated, estimated from the generator's noise at the columns the cells sample: the
	 * ground (the ocean floor, as the generator calls it) and the top of any water over it. Nothing is generated.
	 * Sampling a column is costly (the generator builds its noise anew for each), so it's done a column at a time.
	 */
	private static final class Estimate {
		final ChunkPos pos;
		final int[] floors = new int[256], tops = new int[256], icy = new int[256];
		int next;
		/** The generator's biomes, from its climate noise (made for the first column). */
		@Nullable
		BiomeResolver biomes;

		Estimate(ChunkPos pos) {
			this.pos = pos;
		}

		/**
		 * Estimates one more cell, from the column in its middle, its heights standing for all four of the cell's
		 * samples; true once every cell is. (A column costs a fraction of a millisecond of noise, and a long trip into
		 * unexplored land needs hundreds of thousands of them: one a cell is a quarter of the work, and the real ground
		 * is read in with all four samples once the chunk loads, ahead of him.)
		 */
		boolean step(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights) {
			int cell = next++;
			int column = ESTIMATED_COLUMNS[cell];
			int x = pos.getMinBlockX() + column % 16, z = pos.getMinBlockZ() + column / 16;
			// One pass of the noise for both heights, where asking for each would build it twice
			NoiseColumn blocks = generator.getBaseColumn(x, z, heights, random);
			int floor = firstFree(blocks, heights.getMinY(), heights.getMaxY() + 1, Heightmap.Types.OCEAN_FLOOR_WG.isOpaque());
			int top = firstFree(blocks, heights.getMinY(), heights.getMaxY() + 1, Heightmap.Types.WORLD_SURFACE_WG.isOpaque());
			if (biomes == null)
				biomes = generator.getBiomeSource().createUncachedResolver(random);
			int ice = isIceField(biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z))) ? 1 : 0;
			for (int sample = 0; sample < SAMPLES.length; sample++) {
				int sampled = SAMPLED_COLUMNS[cell * SAMPLES.length + sample];
				floors[sampled] = floor;
				tops[sampled] = top;
				icy[sampled] = ice;
			}
			return next >= ESTIMATED_COLUMNS.length;
		}
	}

	/**
	 * The first free height over the topmost block of a column that a heightmap counts (as the generator's own base
	 * heights give it), or the bottom of the world if there's none.
	 */
	static int firstFree(NoiseColumn column, int minY, int maxY, Predicate<BlockState> counts) {
		for (int y = maxY - 1; y >= minY; y--)
			if (counts.test(column.getBlock(y)))
				return y + 1;
		return minY;
	}

	/** The columns of a chunk (index z * 16 + x) that its cells sample: the only ones an estimate need look at. */
	/**
	 * The column in the middle of each cell (index z * 16 + x), in the same order as {@link #sampledColumns} takes the
	 * cells: where never-generated ground is estimated, one column a cell.
	 */
	public static int[] estimatedColumns() {
		int[] columns = new int[CELLS_PER_CHUNK * CELLS_PER_CHUNK];
		int i = 0;
		for (int cx = 0; cx < CELLS_PER_CHUNK; cx++)
			for (int cz = 0; cz < CELLS_PER_CHUNK; cz++)
				columns[i++] = (cz * CoarsePlanner.CELL + CoarsePlanner.CELL / 2) * 16 + cx * CoarsePlanner.CELL + CoarsePlanner.CELL / 2;
		return columns;
	}

	public static int[] sampledColumns() {
		int[] columns = new int[CELLS_PER_CHUNK * CELLS_PER_CHUNK * SAMPLES.length];
		int i = 0;
		for (int cx = 0; cx < CELLS_PER_CHUNK; cx++)
			for (int cz = 0; cz < CELLS_PER_CHUNK; cz++)
				for (int[] sample : SAMPLES)
					columns[i++] = (cz * CoarsePlanner.CELL + sample[1]) * 16 + cx * CoarsePlanner.CELL + sample[0];
		return columns;
	}

	/**
	 * Takes in every chunk read off disk, and estimates the never-generated ones a column at a time until the deadline
	 * (at least one column a tick, so no world waits on another for ever), carrying on next tick from where it got to.
	 * True if it estimated anything.
	 */
	private boolean tick(long deadline) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		boolean estimated = false;
		while (true) {
			if (estimating == null) {
				Arrival arrival = arrivals.poll();
				if (arrival == null)
					return estimated;
				if (arrival.floors() != null) {
					fill(arrival.pos(), arrival.floors(), arrival.tops(), arrival.icy());
					continue;
				}
				if (!awaited(arrival.pos()))
					continue;
				estimating = new Estimate(arrival.pos());
			}
			estimated = true;
			try {
				if (estimating.step(generator, random, level)) {
					fill(estimating.pos, estimating.floors, estimating.tops, estimating.icy);
					estimating = null;
				}
			} catch (RuntimeException e) {
				PostRider.LOGGER.warn("Route survey couldn't estimate chunk {}: taken as plain ground at sea level", estimating.pos, e);
				int[] plain = new int[256];
				Arrays.fill(plain, level.getSeaLevel() + 1);
				fill(estimating.pos, plain, plain, new int[256]);
				estimating = null;
			}
			if (System.nanoTime() >= deadline)
				return estimated;
		}
	}

	/** Whether a chunk's tile is still waiting to hear about it: one forgotten, or already filled in, isn't estimated. */
	private boolean awaited(ChunkPos pos) {
		Tile tile = tiles.get(ChunkPos.pack(Math.floorDiv(pos.x(), TILE_CHUNKS), Math.floorDiv(pos.z(), TILE_CHUNKS)));
		return tile != null && !tile.filled[Math.floorMod(pos.z(), TILE_CHUNKS) * TILE_CHUNKS + Math.floorMod(pos.x(), TILE_CHUNKS)];
	}

	private void fill(ChunkPos pos, int[] floors, int[] tops, int[] icy) {
		Tile tile = tiles.get(ChunkPos.pack(Math.floorDiv(pos.x(), TILE_CHUNKS), Math.floorDiv(pos.z(), TILE_CHUNKS)));
		if (tile != null) {
			// Off disk or estimated: to be read from the real ground once the chunk is loaded
			tile.guessed[Math.floorMod(pos.z(), TILE_CHUNKS) * TILE_CHUNKS + Math.floorMod(pos.x(), TILE_CHUNKS)] = true;
			fill(tile, pos, floors, tops, icy);
		}
	}

	private void fill(Tile tile, ChunkPos pos, int[] floors, int[] tops, int[] icy) {
		CoarsePlanner.Cell[] chunkCells = cellsOfChunk(floors, tops, icy, level.getMinY());
		if (!claim(tile.filled, pos))
			return;
		int baseX = Math.floorMod(pos.x(), TILE_CHUNKS) * CELLS_PER_CHUNK, baseZ = Math.floorMod(pos.z(), TILE_CHUNKS) * CELLS_PER_CHUNK;
		for (int cx = 0; cx < CELLS_PER_CHUNK; cx++)
			for (int cz = 0; cz < CELLS_PER_CHUNK; cz++)
				tile.cells[(baseZ + cz) * TILE_CELLS + baseX + cx] = chunkCells[cz * CELLS_PER_CHUNK + cx];
		tile.chunksLeft--;
	}

	/**
	 * The ground at the columns a loaded chunk's cells sample, and the top of any water over it (each as the first
	 * free height, like the heightmaps), read from the blocks: the heightmaps count a tree trunk as ground, and a
	 * trunk sampled in a wood would read as a cliff face, so a tree (or a giant mushroom) is looked under to what it
	 * stands on. The floors first, then the tops; only the sampled columns are filled in.
	 */
	static int[][] groundOf(LevelChunk chunk) {
		int[] floors = new int[256], tops = new int[256], icy = new int[256];
		int minY = chunk.getMinY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int column : SAMPLED_COLUMNS) {
			int x = column % 16, z = column / 16;
			int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			// Down through the tree, if it's one, and the air under its canopy, to the ground or the water beneath
			while (y >= minY && !holdsUp(chunk.getBlockState(pos.set(x, y, z))))
				y--;
			tops[column] = y + 1;
			// Ice underfoot, or the biome of a frozen sea or river: an ice field
			boolean iceUnderfoot = y >= minY && chunk.getBlockState(pos.set(x, y, z)).is(BlockTags.ICE);
			icy[column] = iceUnderfoot || isIceField(chunk.getNoiseBiome(QuartPos.fromBlock(chunk.getPos().getMinBlockX() + x), QuartPos.fromBlock(y), QuartPos.fromBlock(chunk.getPos().getMinBlockZ() + z))) ? 1 : 0;
			// And on down through the water to its floor
			while (y >= minY && !chunk.getBlockState(pos.set(x, y, z)).is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP))
				y--;
			floors[column] = y + 1;
		}
		return new int[][] {floors, tops, icy};
	}

	/** Whether a biome is an ice field: a frozen ocean (deep or not) or a frozen river, sea ice with leads and holes in it. */
	public static boolean isIceField(Holder<Biome> biome) {
		return biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN) || biome.is(Biomes.FROZEN_RIVER);
	}

	/** Whether a block is ground, or water, as the survey sees it: anything that blocks motion but a tree, or any liquid. */
	static boolean holdsUp(BlockState state) {
		return !state.getFluidState().isEmpty() || state.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP) && !isGrowth(state);
	}

	/** A tree's trunk or leaves, a giant mushroom or fungus, bamboo: stood under, not on, and no cliff. */
	static boolean isGrowth(BlockState state) {
		return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS)
			|| state.getBlock() instanceof LeavesBlock || state.getBlock() instanceof HugeMushroomBlock || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.SHROOMLIGHT)
			|| state.is(Blocks.BAMBOO) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER);
	}

	// ---------------------------------------------------------------- the sums

	/**
	 * Marks a tile's chunk as heard back from; false if it already was. Each chunk counts once, as a tile surveyed
	 * again can still hear back about its chunks from the time before.
	 */
	static boolean claim(boolean[] filled, ChunkPos pos) {
		int chunk = Math.floorMod(pos.z(), TILE_CHUNKS) * TILE_CHUNKS + Math.floorMod(pos.x(), TILE_CHUNKS);
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
		return cellsOfChunk(floors, tops, new int[256], minY);
	}

	/** As above, with which columns are in an ice field (1 for one that is). */
	public static CoarsePlanner.Cell[] cellsOfChunk(int[] floors, int[] tops, int[] icy, int minY) {
		CoarsePlanner.Cell[] cells = new CoarsePlanner.Cell[CELLS_PER_CHUNK * CELLS_PER_CHUNK];
		for (int cx = 0; cx < CELLS_PER_CHUNK; cx++)
			for (int cz = 0; cz < CELLS_PER_CHUNK; cz++) {
				int[] cellFloors = new int[SAMPLES.length], cellTops = new int[SAMPLES.length];
				boolean[] cellIcy = new boolean[SAMPLES.length];
				for (int i = 0; i < SAMPLES.length; i++) {
					int column = (cz * CoarsePlanner.CELL + SAMPLES[i][1]) * 16 + cx * CoarsePlanner.CELL + SAMPLES[i][0];
					cellFloors[i] = floors[column];
					cellTops[i] = tops[column];
					cellIcy[i] = icy[column] != 0;
				}
				cells[cz * CELLS_PER_CHUNK + cx] = CoarsePlanner.Cell.of(cellFloors, cellTops, cellIcy, minY);
			}
		return cells;
	}

	private record Arrival(ChunkPos pos, int @Nullable [] floors, int @Nullable [] tops, int @Nullable [] icy) {}

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
