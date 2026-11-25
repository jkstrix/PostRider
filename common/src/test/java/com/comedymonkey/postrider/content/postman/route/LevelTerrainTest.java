package com.comedymonkey.postrider.content.postman.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.route.Terrain.Kind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;

class LevelTerrainTest {
	private static Kind kind(BlockState state) {
		return LevelTerrain.classify(state, state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()));
	}

	@Test
	void floorsThinEnoughToLeaveHisHeadInsideTwoBlocksAreLow() {
		assertEquals(Kind.LOW, kind(Blocks.MOSS_CARPET.defaultBlockState()));
		assertEquals(Kind.LOW, kind(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2)));
	}

	@Test
	void floorsThatLiftHimFurtherAreRaised() {
		assertEquals(Kind.RAISED, kind(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
		assertEquals(Kind.RAISED, kind(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4)));
	}

	@Test
	void higherBlocksAreStoodOnOrInTheWay() {
		assertEquals(Kind.SOLID, kind(Blocks.STONE.defaultBlockState()));
		assertEquals(Kind.SOLID, kind(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7)));
		assertEquals(Kind.TALL, kind(Blocks.OAK_FENCE.defaultBlockState()));
		assertEquals(Kind.OPEN, kind(Blocks.AIR.defaultBlockState()));
	}
}
