package com.comedymonkey.postrider.content.hopper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.client.model.IceHopperModel;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModItems;
import com.comedymonkey.postrider.testutil.ModResourceFiles;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

class IceHopperTest {
	private static BlockGetter column(Map<Integer, BlockState> blocks) {
		return new BlockGetter() {
			@Override
			public BlockState getBlockState(BlockPos pos) {
				return blocks.getOrDefault(pos.getY(), Blocks.AIR.defaultBlockState());
			}

			@Override
			public FluidState getFluidState(BlockPos pos) {
				return getBlockState(pos).getFluidState();
			}

			@Override
			public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
				return null;
			}

			@Override
			public int getHeight() {
				return 384;
			}

			@Override
			public int getMinBuildHeight() {
				return -64;
			}
		};
	}

	@Test
	void itHopsOntoIceWithRoomOverItAndOntoNothingElse() {
		BlockState ice = Blocks.ICE.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
		assertTrue(IceHopperEntity.canHopOnto(column(Map.of(62, ice)), new BlockPos(0, 62, 0)), "sea ice, clear above");
		assertTrue(IceHopperEntity.canHopOnto(column(Map.of(62, Blocks.PACKED_ICE.defaultBlockState())), new BlockPos(0, 62, 0)), "packed ice too");
		assertFalse(IceHopperEntity.canHopOnto(column(Map.of(62, stone)), new BlockPos(0, 62, 0)), "a bank is a bank");
		assertFalse(IceHopperEntity.canHopOnto(column(Map.of(62, water)), new BlockPos(0, 62, 0)), "open water: nothing to hop onto");
		assertFalse(IceHopperEntity.canHopOnto(column(Map.of(62, ice, 64, stone)), new BlockPos(0, 62, 0)), "a roof over the ice would crush the boat");
	}

	@Test
	void itIsABoatSizedBoatWithItsOwnItem() {
		assertEquals(1.375F, ModEntities.ICE_HOPPER.get().getWidth(), 1e-6);
		assertEquals(0.5625F, ModEntities.ICE_HOPPER.get().getHeight(), 1e-6);
		assertNotNull(ModItems.ICE_HOPPER.get());
		assertEquals("item.postrider.ice_hopper", ModItems.ICE_HOPPER.get().getDescriptionId());
		assertTrue(IceHopperEntity.MIN_HOP_SPEED > 0 && IceHopperEntity.REACH >= 0.5, "it hops under way, looking a little ahead of the bow");
	}

	@Test
	void itsModelBakesWithTheHullBowAndOarsAndItsSkinsAreThere() {
		ModelPart root = IceHopperModel.createBodyLayer().bakeRoot();
		ModelPart boat = root.getChild("boat");
		for (String part : Set.of("hull", "bow", "oar_left", "oar_right"))
			assertTrue(boat.hasChild(part), part);
		assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "entity", "ice_hopper.png"), "the boat's skin");
		assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "item", "ice_hopper.png"), "the item's icon");
		assertNotNull(ModResourceFiles.find("assets", "postrider", "models", "item", "ice_hopper.json"));
		assertNotNull(ModResourceFiles.find("data", "postrider", "recipe", "ice_hopper.json"), "any boat and two packed ice");
	}

	@Test
	void theOarsDipAndSweepWithTheStroke() {
		ModelPart oar = IceHopperModel.createBodyLayer().bakeRoot().getChild("boat").getChild("oar_left");
		IceHopperModel.swing(oar, 0, 1);
		float restX = oar.xRot, restY = oar.yRot;
		IceHopperModel.swing(oar, (float) Math.PI / 2, 1);
		assertTrue(oar.xRot != restX || oar.yRot != restY, "an oar mid-stroke isn't where it was at rest");
	}

	@Test
	void theHullRidesAtTheWaterlineAfloatAndSitsOnTheIceAndTheRowerSitsInTheFrontSeat() {
		assertEquals(IceHopperEntity.AFLOAT_LIFT, IceHopperEntity.hullLift(true), 1e-9, "afloat, drawn up to the waterline like any boat");
		assertEquals(0.0, IceHopperEntity.hullLift(false), 1e-9, "on ice, where it sits");
		assertEquals(IceHopperEntity.FRONT_SEAT, IceHopperEntity.seatOffset(1, 0), 1e-9, "a lone rower takes the front seat");
		assertEquals(IceHopperEntity.FRONT_SEAT, IceHopperEntity.seatOffset(2, 0), 1e-9);
		assertEquals(IceHopperEntity.BACK_SEAT, IceHopperEntity.seatOffset(2, 1), 1e-9, "the passenger the back one");
		assertTrue(IceHopperEntity.SEAT_HEIGHT > 0.2 && IceHopperEntity.SEAT_HEIGHT <= 0.375, "sat on the seats, not through the hull");
	}
}
