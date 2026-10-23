package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.client.model.BargeAnimations;
import com.comedymonkey.postrider.client.model.BargeModel;
import com.comedymonkey.postrider.client.model.PostmanAnimations;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.testutil.ModResourceFiles;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

class BargeTest {
	private static void collect(ModelPart part, Set<String> names) {
		for (String child : new String[] {"barge", "hull", "bow", "stern", "railings", "fenders", "bench", "oar_left", "oar_right", "cargo",
			"lantern_post", "pennant", "lantern", "lantern_glow"})
			if (part.hasChild(child)) {
				names.add(child);
				collect(part.getChild(child), names);
			}
	}

	@Test
	void theBargeModelBakesWithEveryAnimatedPart() {
		Set<String> parts = new HashSet<>();
		collect(BargeModel.createBodyLayer().bakeRoot(), parts);
		assertTrue(parts.contains("lantern_glow"), "the lantern's flame, drawn full-bright");
		for (AnimationDefinition animation : List.of(BargeAnimations.ROW, BargeAnimations.IDLE))
			for (String bone : animation.boneAnimations().keySet())
				assertTrue(parts.contains(bone), "animates missing part " + bone);
		assertTrue(BargeAnimations.ROW.looping() && BargeAnimations.IDLE.looping());
	}

	@Test
	void theOarsKeepTimeWithHisRowing() {
		assertEquals(PostmanAnimations.ROW.lengthInSeconds(), BargeAnimations.ROW.lengthInSeconds(), 1e-6);
		assertEquals(BargeEntity.STROKE_TICKS, BargeAnimations.ROW.lengthInSeconds() * 20, 1e-4);
	}

	@Test
	void itsTextureIsThere() throws IOException {
		Path path = ModResourceFiles.find("assets", "postrider", "textures", "entity", "barge.png");
		assertNotNull(path);
		try (InputStream in = Files.newInputStream(path)) {
			BufferedImage image = ImageIO.read(in);
			assertEquals(256, image.getWidth());
			assertEquals(256, image.getHeight());
		}
	}

	@Test
	void itGoesAsFastAsABoat() {
		assertEquals(8.0, BargeEntity.BLOCKS_PER_SECOND, 1e-9);
		assertEquals(0.4, BargeEntity.SPEED, 1e-9);
		assertEquals(3.0F, ModEntities.BARGE.get().getWidth());
	}

	@Test
	void itTurnsGentlyTowardsWhereItsSteered() {
		assertEquals(4.0F, BargeEntity.turnToward(0, 90), 1e-6);
		assertEquals(-4.0F, BargeEntity.turnToward(0, -90), 1e-6);
		assertEquals(2.0F, BargeEntity.turnToward(0, 2), 1e-6, "comes onto the heading when close");
		assertEquals(184.0F, BargeEntity.turnToward(180, -170), 1e-6, "the short way round");
	}

	@Test
	void heSitsAtTheBowAndTheHorseStandsBehindHim() {
		Vec3 middle = new Vec3(0, 63, 0);
		for (float yaw : new float[] {0, 90, 180, 270}) {
			Vec3 ahead = Vec3.directionFromRotation(0, yaw);
			Vec3 bench = BargeEntity.aboard(middle, yaw, BargeEntity.BENCH_FORWARD, BargeEntity.BENCH_HEIGHT);
			Vec3 deck = BargeEntity.aboard(middle, yaw, BargeEntity.DECK_FORWARD, BargeEntity.DECK_HEIGHT);
			assertTrue(bench.subtract(middle).dot(ahead) > 1, "the bench is towards the bow");
			assertTrue(deck.subtract(middle).dot(ahead) < 0, "the horse stands behind it");
		}
		assertTrue(BargeEntity.BENCH_FORWARD < BargeEntity.BOW_REACH);
	}

	@Test
	void aRunOnTheWaterSurvivesSaving() {
		var registries = VanillaRegistries.createLookup();
		PendingDelivery delivery = new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Ranch",
			GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), "Island", List.of(new ItemStack(Items.BREAD)), 0);
		CompoundTag tag = new DeliveryRun(delivery, GlobalPos.of(Level.OVERWORLD, new BlockPos(900, 64, 0))).save(registries);
		tag.putString("stage", DeliveryRun.Stage.BARGING.name());
		assertEquals(DeliveryRun.Stage.BARGING, DeliveryRun.load(tag, registries).stage());
	}

	@Test
	void itSlowsDownToTakeABend() {
		assertEquals(1.0, BargeEntity.turningSpeed(0), 1e-9, "full ahead on its heading");
		assertEquals(0.5, BargeEntity.turningSpeed(45), 1e-9);
		assertEquals(0.5, BargeEntity.turningSpeed(-45), 1e-9, "either way");
		assertEquals(0.3, BargeEntity.turningSpeed(170), 1e-9, "never quite stops");
	}

	@Test
	void itRidesHighEnoughToFloatInWaterOneBlockDeep() {
		double surface = 62 + 8 / 9.0, bottom = BargeEntity.floatHeight(surface);
		assertTrue(bottom < surface, "the hull sits in the water, not on top of it");
		assertTrue(surface - bottom <= 1 / 16.0 + 1e-9, "only its bottom pixel is under");
		// Still water a block deep: the hull clears the bottom with room to spare
		assertTrue(bottom > 62, "afloat over a bed one block down");
	}

	@Test
	void itsHitBoxRunsFromTheBottomOfTheHullToTheTopsOfTheRailings() {
		assertEquals(3.0F, ModEntities.BARGE.get().getWidth());
		assertEquals(BargeEntity.HULL_DEPTH + BargeEntity.RAILING_HEIGHT, ModEntities.BARGE.get().getHeight(), 1e-6);
		// A solid block at the waterline (a beach, a sandbar) stands above the bottom of the hull, so it bumps into it
		double surface = 62 + 8 / 9.0;
		assertTrue(BargeEntity.floatHeight(surface) < 63, "it can't slide over the top of a bank at water level");
	}

	@Test
	void heStepsAshoreWhenTheBowIsAgainstLandWhereHeMeansToLand() {
		assertTrue(DeliveryRun.landsHere(true, true, false, 0), "where his plan lands");
		assertTrue(DeliveryRun.landsHere(true, false, true, 0), "at the end of his way across the water");
		assertTrue(DeliveryRun.landsHere(true, false, false, DeliveryRun.AGROUND_TICKS), "run hard aground");
		assertFalse(DeliveryRun.landsHere(true, false, false, 0), "brushing a bank on the way: rows on");
		assertFalse(DeliveryRun.landsHere(false, true, true, 0), "no land at the bow yet");
	}

	@Test
	void ashoreOnFootCloseToTheMailboxHeWalksTheRest() {
		assertTrue(DeliveryRun.walksTheRest(10), "a few blocks: not worth calling the horse");
		assertFalse(DeliveryRun.walksTheRest(40), "a ride yet: he whistles");
	}

	@Test
	void itStopsWithItsBowShortOfWhatIsntOpenWater() {
		// Facing south (yaw 0 is +z): a block beyond the bow is BOW_REACH + 1 along
		net.minecraft.world.phys.Vec3 ahead = BargeEntity.beyondBow(new net.minecraft.world.phys.Vec3(10, 63, 10), 0, BargeEntity.STOPS_SHORT);
		assertEquals(10, ahead.x, 1e-6);
		assertEquals(10 + BargeEntity.BOW_REACH + BargeEntity.STOPS_SHORT, ahead.z, 1e-6);
		assertTrue(BargeEntity.STOPS_SHORT >= BargeEntity.BOW_REACH - 1.5, "the bow reaches a block past the hull box: it must stop at least that short");
	}
}
