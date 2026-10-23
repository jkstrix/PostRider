package com.comedymonkey.postrider.content.hopper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
	void itsModelBakesWithTheHullBowAndOarsAndItsSkinsAreThere() throws java.io.IOException {
		ModelPart root = IceHopperModel.createBodyLayer().bakeRoot();
		ModelPart boat = root.getChild("boat");
		for (String part : Set.of("hull", "bow", "oar_left", "oar_right"))
			assertTrue(boat.hasChild(part), part);
		assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "entity", "ice_hopper.png"), "the boat's skin");
		// The item is the boat itself in miniature (a 3D model skinned with the entity's texture), not a flat icon
		java.nio.file.Path item = ModResourceFiles.find("assets", "postrider", "models", "item", "ice_hopper.json");
		assertNotNull(item);
		String json = java.nio.file.Files.readString(item);
		assertTrue(json.contains("\"elements\"") && json.contains("postrider:entity/ice_hopper"), "a 3D model skinned with the boat's own texture");
		assertTrue(json.contains("\"gui\"") && json.contains("firstperson_righthand"), "placed in the hotbar and the hand");
		// Item models draw from the block atlas, which stitches only the block and item folders: the boat's skin is added to it
		java.nio.file.Path atlas = ModResourceFiles.find("assets", "minecraft", "atlases", "blocks.json");
		assertNotNull(atlas, "the block atlas additions");
		assertTrue(java.nio.file.Files.readString(atlas).contains("postrider:entity/ice_hopper"), "or the item shows as the missing texture");
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

	@Test
	void itHopsUpOneBlockOfIceTooButNeverOntoIceItsAlreadyOn() {
		BlockState ice = Blocks.ICE.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
		// Afloat in the water block at 62 (the hull at 62.5), sea ice ahead at 62: up to 63
		assertEquals(63.0, IceHopperEntity.hopTop(column(Map.of(62, ice)), new BlockPos(0, 62, 0), 62.5), 1e-9);
		// A block of ice standing on the sheet (at 63) ahead: up to 64, from the water or from the sheet
		assertEquals(64.0, IceHopperEntity.hopTop(column(Map.of(62, ice, 63, ice)), new BlockPos(0, 62, 0), 62.5), 1e-9, "from the water, over the sheet's edge and up the block");
		assertEquals(64.0, IceHopperEntity.hopTop(column(Map.of(62, ice, 63, ice)), new BlockPos(0, 63, 0), 63.0), 1e-9, "from the sheet");
		// Sliding on the sheet (hull at 63) with sheet ahead: nothing to hop
		assertTrue(Double.isNaN(IceHopperEntity.hopTop(column(Map.of(62, ice)), new BlockPos(0, 63, 0), 63.0)));
		// Two blocks up is a wall
		assertTrue(Double.isNaN(IceHopperEntity.hopTop(column(Map.of(62, stone, 63, stone, 64, ice)), new BlockPos(0, 62, 0), 62.5)));
		assertTrue(IceHopperEntity.MIN_HOP_SPEED <= 0.04, "a single tick of rowing against the ice is enough to hop");
	}

	@Test
	void bumpedAgainstTheIceWithNoWayOnItHopsTheWayItsBeingRowed() {
		net.minecraft.world.phys.Vec3 still = net.minecraft.world.phys.Vec3.ZERO, east = new net.minecraft.world.phys.Vec3(0.3, 0, 0);
		assertNotNull(IceHopperEntity.hopHeading(east, false, 0), "under way: the way it's moving");
		assertEquals(1.0, IceHopperEntity.hopHeading(east, false, 0).x, 1e-9);
		assertNull(IceHopperEntity.hopHeading(still, false, -90), "drifting against the ice, nobody rowing: no hop");
		net.minecraft.world.phys.Vec3 rowed = IceHopperEntity.hopHeading(still, true, -90);
		assertNotNull(rowed, "stopped dead against the ice but rowing forward: the way it's facing");
		assertEquals(1.0, rowed.x, 1e-6, "facing east (yaw -90)");
	}

	@Test
	void itLandsAHairOverTheIceSoTheServerAllowsTheStepOntoIt() {
		assertTrue(IceHopperEntity.HOP_CLEARANCE > 1e-6 && IceHopperEntity.HOP_CLEARANCE < 0.05, "clear of the ice's row by more than the server's millionth, and no visible float");
	}
}
