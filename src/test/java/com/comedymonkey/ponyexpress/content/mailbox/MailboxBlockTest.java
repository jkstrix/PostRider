package com.comedymonkey.ponyexpress.content.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.registry.ModBlockEntities;
import com.comedymonkey.ponyexpress.registry.ModBlocks;
import com.comedymonkey.ponyexpress.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;

class MailboxBlockTest {
	@Test
	void thereIsOneMailboxForEveryDyeColour() {
		Set<MailboxBlock> blocks = new HashSet<>();
		for (DyeColor color : DyeColor.values()) {
			MailboxBlock block = ModBlocks.mailbox(color).get();
			assertEquals(color, block.getColor());
			assertEquals(color.getSerializedName() + "_mailbox", ModBlocks.mailbox(color).getId().getPath());
			blocks.add(block);
		}
		assertEquals(16, blocks.size());
	}

	@Test
	void eachMailboxHasATwoBlockItem() {
		for (DyeColor color : DyeColor.values()) {
			DoubleHighBlockItem item = ModItems.mailbox(color).get();
			assertSame(ModBlocks.mailbox(color).get(), item.getBlock());
		}
	}

	@Test
	void startsFacingNorthAsTheLowerHalfWithTheFlagDown() {
		BlockState state = ModBlocks.mailbox(DyeColor.RED).get().defaultBlockState();
		assertEquals(Direction.NORTH, state.getValue(MailboxBlock.FACING));
		assertEquals(DoubleBlockHalf.LOWER, state.getValue(MailboxBlock.HALF));
		assertFalse(state.getValue(MailboxBlock.FLAG));
	}

	@Test
	void cannotBePushedOrBlownUpEasily() {
		BlockState state = ModBlocks.mailbox(DyeColor.BLUE).get().defaultBlockState();
		assertEquals(PushReaction.BLOCK, state.getPistonPushReaction());
		assertTrue(ModBlocks.mailbox(DyeColor.BLUE).get().getExplosionResistance() >= 1200);
	}

	@Test
	void recolouringKeepsFacingHalfAndFlag() {
		BlockState red = ModBlocks.mailbox(DyeColor.RED).get().defaultBlockState()
			.setValue(MailboxBlock.FACING, Direction.WEST)
			.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER)
			.setValue(MailboxBlock.FLAG, true);
		BlockState green = MailboxBlock.recoloured(red, ModBlocks.mailbox(DyeColor.GREEN).get());
		assertSame(ModBlocks.mailbox(DyeColor.GREEN).get(), green.getBlock());
		assertEquals(Direction.WEST, green.getValue(MailboxBlock.FACING));
		assertEquals(DoubleBlockHalf.UPPER, green.getValue(MailboxBlock.HALF));
		assertTrue(green.getValue(MailboxBlock.FLAG));
	}

	@Test
	void onlyTheLowerHalfHoldsTheMailbox() {
		MailboxBlock block = ModBlocks.mailbox(DyeColor.WHITE).get();
		BlockState lower = block.defaultBlockState();
		BlockState upper = lower.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER);
		assertInstanceOf(MailboxBlockEntity.class, block.newBlockEntity(BlockPos.ZERO, lower));
		assertNull(block.newBlockEntity(BlockPos.ZERO.above(), upper));
		assertEquals(BlockPos.ZERO, MailboxBlock.lowerPos(upper, BlockPos.ZERO.above()));
		assertEquals(BlockPos.ZERO, MailboxBlock.lowerPos(lower, BlockPos.ZERO));
	}

	@Test
	void theBlockEntityTypeAcceptsEveryColour() {
		for (DyeColor color : DyeColor.values())
			assertTrue(ModBlockEntities.MAILBOX.get().isValid(ModBlocks.mailbox(color).get().defaultBlockState()), color.getName());
		assertNotNull(MailboxBlock.CODEC);
	}
}
