package com.comedymonkey.postrider.content.lost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.registry.ModBlockEntities;
import com.comedymonkey.postrider.registry.ModBlocks;
import com.comedymonkey.postrider.testutil.ModResourceFiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;

class LostParcelWreckTest {
	@Test
	void itFloatsInTheWaterRatherThanPushingItAside() {
		BlockState wreck = ModBlocks.LOST_PARCEL_WRECK.get().defaultBlockState();
		assertTrue(wreck.getValue(LostParcelWreckBlock.WATERLOGGED));
		assertEquals(Fluids.WATER, wreck.getFluidState().getType());
		assertTrue(wreck.getFluidState().isSource());
	}

	@Test
	void itRidesAtTheWaterLineForEveryCauseAndFacing() {
		for (LostCause cause : LostCause.values())
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				BlockState state = ModBlocks.LOST_PARCEL_WRECK.get().defaultBlockState()
					.setValue(LostParcelBlock.CAUSE, cause).setValue(LostParcelBlock.FACING, facing);
				var box = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).bounds();
				assertTrue(box.minY > 0.75 && box.maxY <= 1, "floats at the top of its block");
			}
	}

	@Test
	void itBehavesLikeAnyLostParcel() {
		assertTrue(ModBlocks.LOST_PARCEL_WRECK.get() instanceof LostParcelBlock, "the same opening rules");
		assertTrue(ModBlockEntities.LOST_PARCEL.get().isValid(ModBlocks.LOST_PARCEL_WRECK.get().defaultBlockState()));
		assertEquals(PushReaction.BLOCK, ModBlocks.LOST_PARCEL_WRECK.get().defaultBlockState().getPistonPushReaction());
		assertTrue(ModBlocks.LOST_PARCEL_WRECK.get().getExplosionResistance() >= 1200.0F);
	}

	@Test
	void itsModelsAndSoggyTexturesAreThere() throws IOException {
		assertNotNull(ModResourceFiles.find("assets", "postrider", "blockstates", "lost_parcel_wreck.json"));
		for (String model : new String[] {"lost_parcel_wreck", "lost_parcel_wreck_skull", "lost_parcel_wreck_map"})
			assertNotNull(ModResourceFiles.find("assets", "postrider", "models", "block", model + ".json"), model);
		Path model = ModResourceFiles.find("assets", "postrider", "models", "block", "lost_parcel_wreck.json");
		String json = Files.readString(model);
		assertTrue(json.contains("lost_parcel_paper_soggy"), "the wreck's parcel is soggy");
		for (String texture : new String[] {"wreck_plank", "lost_parcel_paper_soggy", "lost_parcel_top_soggy", "lost_parcel_twine_soggy",
			"lost_parcel_tag_soggy"})
			assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "block", texture + ".png"), texture);
	}
}
