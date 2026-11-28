package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.comedymonkey.postrider.testutil.LoadedGameData;

import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

@ExtendWith(LoadedGameData.class)
class TerrainSurveyTest {
	private static final int MIN_Y = -64, HEIGHT = 384;

	@Test
	void savedHeightmapsReadBackColumnByColumn() {
		int bits = Mth.ceillog2(HEIGHT + 1);
		SimpleBitStorage storage = new SimpleBitStorage(bits, 256);
		int[] expected = new int[256];
		for (int i = 0; i < 256; i++) {
			expected[i] = 40 + i % 50;
			// Stored as heights above the bottom of the world, as a chunk saves them
			storage.set(i, expected[i] - MIN_Y);
		}
		assertArrayEquals(expected, TerrainSurvey.heights(storage.getRaw(), bits, MIN_Y));
	}

	@Test
	void aChunkMakesFourCellsWithWaterWhereTheTopIsAboveTheFloor() {
		int[] floors = new int[256], tops = new int[256];
		Arrays.fill(floors, 70);
		Arrays.fill(tops, 70);
		// The eastern half of the chunk is a lake, its bed at 55 and its surface at 63
		for (int z = 0; z < 16; z++)
			for (int x = 8; x < 16; x++) {
				floors[z * 16 + x] = 55;
				tops[z * 16 + x] = 63;
			}
		CoarsePlanner.Cell[] cells = TerrainSurvey.cellsOfChunk(floors, tops, MIN_Y);
		assertEquals(4, cells.length);
		assertFalse(cells[0].water());
		assertTrue(cells[1].water());
		assertFalse(cells[2].water());
		assertTrue(cells[3].water());
		assertEquals(70, cells[0].height());
		assertEquals(63, cells[1].height());
	}

	@Test
	void aTileIsFourChunksSquare() {
		assertEquals(2, TerrainSurvey.CELLS_PER_CHUNK);
		assertEquals(8, TerrainSurvey.TILE_CELLS);
	}

	@Test
	void eachChunkOfATileCountsOnce() {
		boolean[] filled = new boolean[TerrainSurvey.TILE_CHUNKS * TerrainSurvey.TILE_CHUNKS];
		assertTrue(TerrainSurvey.claim(filled, new ChunkPos(5, -3)));
		assertFalse(TerrainSurvey.claim(filled, new ChunkPos(5, -3)), "a late answer from an earlier survey is ignored");
		assertTrue(TerrainSurvey.claim(filled, new ChunkPos(6, -3)));
	}

	@Test
	void aTileOfLoadedChunksIsReadAgainSoonAndOneOffDiskKeptLonger() {
		int all = TerrainSurvey.TILE_CHUNKS * TerrainSurvey.TILE_CHUNKS;
		assertEquals(TerrainSurvey.LOADED_LIFETIME, TerrainSurvey.lifetime(all), "all loaded: a few seconds, then looked at afresh");
		assertEquals(TerrainSurvey.TILE_LIFETIME, TerrainSurvey.lifetime(all - 1), "one off disk: ten minutes");
		assertEquals(TerrainSurvey.TILE_LIFETIME, TerrainSurvey.lifetime(0));
		assertTrue(TerrainSurvey.LOADED_LIFETIME < TerrainSurvey.TILE_LIFETIME);
	}

	@Test
	void anEstimateLooksOnlyAtTheColumnsTheCellsSample() {
		int[] columns = TerrainSurvey.sampledColumns();
		assertEquals(TerrainSurvey.CELLS_PER_CHUNK * TerrainSurvey.CELLS_PER_CHUNK * 4, columns.length, "four to each cell");
		assertEquals(columns.length, java.util.Arrays.stream(columns).distinct().count(), "no column twice");
		for (int column : columns)
			assertTrue(column >= 0 && column < 256, "in the chunk: " + column);
		// The first cell's four: (2,2), (6,2), (2,6), (6,6)
		assertEquals(2 * 16 + 2, columns[0]);
		assertEquals(2 * 16 + 6, columns[1]);
		assertEquals(6 * 16 + 2, columns[2]);
		assertEquals(6 * 16 + 6, columns[3]);
	}

	@Test
	void oneNoiseColumnGivesBothTheFloorAndTheWaterTop() {
		// Stone up to y=40, water to y=62, air above: the floor is 41, the top of the water 63
		net.minecraft.world.level.block.state.BlockState[] states = new net.minecraft.world.level.block.state.BlockState[HEIGHT];
		for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++)
			states[y - MIN_Y] = (y <= 40 ? Blocks.STONE : y <= 62 ? Blocks.WATER : Blocks.AIR).defaultBlockState();
		net.minecraft.world.level.NoiseColumn column = new net.minecraft.world.level.NoiseColumn(MIN_Y, states);
		int maxY = MIN_Y + HEIGHT;
		assertEquals(41, TerrainSurvey.firstFree(column, MIN_Y, maxY, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG.isOpaque()));
		assertEquals(63, TerrainSurvey.firstFree(column, MIN_Y, maxY, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG.isOpaque()));
	}

	@Test
	void aColumnOfNothingIsTheBottomOfTheWorld() {
		net.minecraft.world.level.block.state.BlockState[] states = new net.minecraft.world.level.block.state.BlockState[HEIGHT];
		Arrays.fill(states, Blocks.AIR.defaultBlockState());
		net.minecraft.world.level.NoiseColumn column = new net.minecraft.world.level.NoiseColumn(MIN_Y, states);
		assertEquals(MIN_Y, TerrainSurvey.firstFree(column, MIN_Y, MIN_Y + HEIGHT, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG.isOpaque()));
	}

	@Test
	void theNoiseIsSampledAFewMillisecondsATick() {
		assertTrue(TerrainSurvey.ESTIMATE_NANOS_PER_TICK > 0);
		assertTrue(TerrainSurvey.ESTIMATE_NANOS_PER_TICK <= 10_000_000, "well inside a 50 ms tick");
	}

	@Test
	void aTreeIsStoodUnderNotOn() {
		// (Block tags aren't bound outside the game, so logs and wart blocks, known by their tags, aren't tried here)
		assertTrue(TerrainSurvey.isGrowth(Blocks.DARK_OAK_LEAVES.defaultBlockState()));
		assertTrue(TerrainSurvey.isGrowth(Blocks.RED_MUSHROOM_BLOCK.defaultBlockState()));
		assertTrue(TerrainSurvey.isGrowth(Blocks.MUSHROOM_STEM.defaultBlockState()));
		assertTrue(TerrainSurvey.isGrowth(Blocks.BAMBOO.defaultBlockState()));
		assertTrue(TerrainSurvey.isGrowth(Blocks.MANGROVE_ROOTS.defaultBlockState()));
		assertFalse(TerrainSurvey.isGrowth(Blocks.STONE.defaultBlockState()));
		assertFalse(TerrainSurvey.isGrowth(Blocks.OAK_PLANKS.defaultBlockState()), "a house of planks is a wall, not a tree");
		assertTrue(TerrainSurvey.holdsUp(Blocks.GRASS_BLOCK.defaultBlockState()));
		assertTrue(TerrainSurvey.holdsUp(Blocks.WATER.defaultBlockState()), "water is the top of a wet column");
		assertFalse(TerrainSurvey.holdsUp(Blocks.MUSHROOM_STEM.defaultBlockState()));
		assertFalse(TerrainSurvey.holdsUp(Blocks.AIR.defaultBlockState()));
		assertFalse(TerrainSurvey.holdsUp(Blocks.SHORT_GRASS.defaultBlockState()));
	}

	@Test
	void theColumnsOfAnIceFieldMakeAnIceCell() {
		int[] floors = new int[256], tops = new int[256], icy = new int[256];
		Arrays.fill(floors, 64);
		Arrays.fill(tops, 64);
		for (int z = 0; z < 8; z++)
			for (int x = 0; x < 8; x++)
				icy[z * 16 + x] = 1;
		CoarsePlanner.Cell[] cells = TerrainSurvey.cellsOfChunk(floors, tops, icy, MIN_Y);
		assertTrue(cells[0].ice(), "the first cell's columns are all sea ice");
		assertTrue(cells[0].water(), "an ice field is barge country");
		assertFalse(cells[1].ice());
		assertFalse(TerrainSurvey.cellsOfChunk(floors, tops, MIN_Y)[0].ice(), "no ice flagged: no ice");
	}

	private static final long MS = 1_000_000;
	private static final long TICK = 50 * MS;

	@Test
	void anIdleServerLetsTheSurveyUseItsSpareTimeUpToTheMost() {
		assertEquals(TerrainSurvey.ESTIMATE_NANOS_MAX, TerrainSurvey.estimateBudget(TICK, 5 * MS, 0), "45 ms spare: as much as it may take");
		assertEquals(15 * MS, TerrainSurvey.estimateBudget(TICK, 30 * MS, 0), "20 ms spare, a quarter kept free");
		assertEquals(30 * MS, TerrainSurvey.estimateBudget(TICK, 5 * MS, 0), "45 ms spare: the most, 30");
	}

	@Test
	void aBusyServerKeepsTheSurveyToTheLeast() {
		assertEquals(TerrainSurvey.ESTIMATE_NANOS_PER_TICK, TerrainSurvey.estimateBudget(TICK, 45 * MS, 0));
		assertEquals(TerrainSurvey.ESTIMATE_NANOS_PER_TICK, TerrainSurvey.estimateBudget(TICK, 80 * MS, 0), "a server running behind: still the least, so a trip gets planned");
	}

	@Test
	void theSurveysOwnTimeIsntTakenForTheServerBeingBusy() {
		// The server's ticks average 25 ms, 20 of them the survey's own: the world takes 5
		assertEquals(TerrainSurvey.estimateBudget(TICK, 5 * MS, 0), TerrainSurvey.estimateBudget(TICK, 25 * MS, 20 * MS));
		assertEquals(TerrainSurvey.ESTIMATE_NANOS_MAX, TerrainSurvey.estimateBudget(TICK, 25 * MS, 20 * MS));
	}

	@Test
	void aFasterTickRateLeavesLessSpare() {
		assertEquals(TerrainSurvey.ESTIMATE_NANOS_PER_TICK, TerrainSurvey.estimateBudget(10 * MS, 5 * MS, 0), "ticks of 10 ms with 5 used: the least");
		assertTrue(TerrainSurvey.ESTIMATE_NANOS_MAX <= TICK * 3 / 5, "never more than three fifths of a tick at the normal rate");
	}

	@Test
	void unexploredGroundIsEstimatedFromTheMiddleOfEachCellForAllFourOfItsSamples() {
		int[] estimated = TerrainSurvey.estimatedColumns(), sampled = TerrainSurvey.sampledColumns();
		assertEquals(sampled.length / 4, estimated.length, "one column a cell, where the real ground has four");
		for (int cell = 0; cell < estimated.length; cell++) {
			int ex = estimated[cell] % 16, ez = estimated[cell] / 16;
			assertEquals(4, ex % CoarsePlanner.CELL, "in the middle of the cell across");
			assertEquals(4, ez % CoarsePlanner.CELL, "and down");
			for (int sample = 0; sample < 4; sample++) {
				int column = sampled[cell * 4 + sample];
				assertEquals(ex / CoarsePlanner.CELL, column % 16 / CoarsePlanner.CELL, "the samples it stands for are in its cell");
				assertEquals(ez / CoarsePlanner.CELL, column / 16 / CoarsePlanner.CELL);
			}
		}
	}
}
