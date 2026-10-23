package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

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
}
