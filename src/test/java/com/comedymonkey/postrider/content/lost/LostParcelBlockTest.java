package com.comedymonkey.postrider.content.lost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.registry.ModBlockEntities;
import com.comedymonkey.postrider.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

class LostParcelBlockTest {
	private static BlockState state(LostCause cause, Direction facing) {
		return ModBlocks.LOST_PARCEL.get().defaultBlockState().setValue(LostParcelBlock.CAUSE, cause).setValue(LostParcelBlock.FACING, facing);
	}

	@Test
	void everyCauseAndFacingHasAnOutlineInsideTheBlock() {
		for (LostCause cause : LostCause.values()) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				var shape = state(cause, facing).getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
				assertFalse(shape.isEmpty(), cause + " " + facing);
				AABB bounds = shape.bounds();
				assertTrue(bounds.minX >= 0 && bounds.maxX <= 1 && bounds.minZ >= 0 && bounds.maxZ <= 1, cause + " " + facing);
			}
		}
	}

	@Test
	void theSkullAndMapAddToTheOutline() {
		AABB plain = state(LostCause.UNDELIVERABLE, Direction.SOUTH)
			.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).bounds();
		AABB skull = state(LostCause.KILLED, Direction.SOUTH)
			.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).bounds();
		AABB map = state(LostCause.STUCK, Direction.SOUTH)
			.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).bounds();
		assertTrue(skull.maxX > plain.maxX);
		assertTrue(map.maxX > plain.maxX);
	}

	@Test
	void staysPutAndDropsOnlyItsContents() {
		BlockState state = ModBlocks.LOST_PARCEL.get().defaultBlockState();
		assertEquals(PushReaction.IMMOVEABLE, state.getPistonPushReaction());
		assertTrue(ModBlocks.LOST_PARCEL.get().getExplosionResistance() >= 1200);
		assertTrue(ModBlocks.LOST_PARCEL.get().getLootTable().isEmpty());
	}

	@Test
	void holdsItsBlockEntity() {
		BlockState state = ModBlocks.LOST_PARCEL.get().defaultBlockState();
		assertTrue(ModBlockEntities.LOST_PARCEL.get().isValid(state));
		assertTrue(ModBlocks.LOST_PARCEL.get().newBlockEntity(BlockPos.ZERO, state) instanceof LostParcelBlockEntity);
	}

	@Test
	void causesHaveTheNamesTheBlockstateUses() {
		assertEquals("killed", LostCause.KILLED.getSerializedName());
		assertEquals("stuck", LostCause.STUCK.getSerializedName());
		assertEquals("undeliverable", LostCause.UNDELIVERABLE.getSerializedName());
	}
}
