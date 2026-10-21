package com.comedymonkey.postrider.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.testutil.ModResourceFiles;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;

class PostmanAnimationsTest {
	private static final Map<String, AnimationDefinition> ALL = Map.ofEntries(
		Map.entry("walk", PostmanAnimations.WALK), Map.entry("idle", PostmanAnimations.IDLE),
		Map.entry("ride", PostmanAnimations.RIDE), Map.entry("ride_idle", PostmanAnimations.RIDE_IDLE),
		Map.entry("pickup", PostmanAnimations.PICKUP), Map.entry("deliver", PostmanAnimations.DELIVER),
		Map.entry("whistle", PostmanAnimations.WHISTLE), Map.entry("camp_hammer", PostmanAnimations.CAMP_HAMMER),
		Map.entry("camp_rest", PostmanAnimations.CAMP_REST), Map.entry("row", PostmanAnimations.ROW),
		Map.entry("hurt", PostmanAnimations.HURT), Map.entry("wave", PostmanAnimations.WAVE),
		Map.entry("harmonica", PostmanAnimations.HARMONICA), Map.entry("sleep", PostmanAnimations.SLEEP));

	private static Set<String> partNames() {
		ModelPart root = PostmanModel.createBodyLayer().bakeRoot();
		Set<String> names = new HashSet<>();
		collect(root, names);
		return names;
	}

	private static void collect(ModelPart part, Set<String> names) {
		for (String child : new String[] {"root", "body", "head", "right_arm", "left_arm", "satchel", "satchel_flat", "satchel_full",
			"right_leg", "left_leg", "hat", "hat_brim_right_r1", "hat_brim_left_r1", "harmonica", "hammer"})
			if (part.hasChild(child)) {
				names.add(child);
				collect(part.getChild(child), names);
			}
	}

	@Test
	void allFourteenApprovedAnimationsAreThere() {
		assertEquals(14, ALL.size());
	}

	@Test
	void theHarmonicaIsAtHisMouthAndHeLoopsTheTune() {
		assertTrue(partNames().contains("harmonica"));
		assertTrue(PostmanAnimations.HARMONICA.looping());
		assertEquals(2.0F, PostmanAnimations.HARMONICA.lengthInSeconds());
	}

	@Test
	void everyAnimatedBoneExistsInTheModel() {
		Set<String> parts = partNames();
		for (var entry : ALL.entrySet())
			for (String bone : entry.getValue().boneAnimations().keySet())
				assertTrue(parts.contains(bone), entry.getKey() + " animates missing bone " + bone);
	}

	@Test
	void continuousAnimationsLoopAndGesturesPlayOnce() {
		for (String looping : new String[] {"walk", "idle", "ride", "ride_idle", "camp_hammer", "camp_rest", "row", "harmonica", "sleep"})
			assertTrue(ALL.get(looping).looping(), looping);
		for (String once : new String[] {"pickup", "deliver", "whistle", "hurt", "wave"})
			assertFalse(ALL.get(once).looping(), once);
	}

	@Test
	void gestureTimingsMatchTheirAnimations() {
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values())
			assertEquals(PostmanModel.gestureAnimation(gesture).lengthInSeconds(), gesture.seconds, 1e-4, gesture.name());
		assertEquals(PostmanAnimations.HURT.lengthInSeconds(), PostmanEntity.HURT_SECONDS, 1e-4);
	}

	@Test
	void lengthsMatchWhatWasApprovedInBlockbench() {
		assertEquals(1.0F, PostmanAnimations.WALK.lengthInSeconds());
		assertEquals(0.75F, PostmanAnimations.RIDE.lengthInSeconds());
		assertEquals(2.2F, PostmanAnimations.PICKUP.lengthInSeconds(), 1e-4);
		assertEquals(6.0F, PostmanAnimations.CAMP_REST.lengthInSeconds());
	}

	@Test
	void heHoldsAHammerAndHisHatCanTipOverHisFace() {
		Set<String> parts = partNames();
		assertTrue(parts.contains("hammer"));
		assertTrue(PostmanModel.createBodyLayer().bakeRoot().getChild("root").getChild("body").getChild("right_arm").hasChild("hammer"));
		assertTrue(PostmanAnimations.SLEEP.boneAnimations().containsKey("hat"), "the hat tips over his face while he sleeps");
		assertTrue(PostmanAnimations.SLEEP.looping());
		assertEquals(4.0F, PostmanAnimations.SLEEP.lengthInSeconds());
	}

	@Test
	void hisRootBonePivotsAtHisFeetSoHeLiesOnTheGround() {
		// HierarchicalModel sends "root" animations to root(); that must be the bone at ground level (24 pixels down)
		ModelPart root = new PostmanModel(PostmanModel.createBodyLayer().bakeRoot()).root();
		assertEquals(24.0F, root.y);
		assertTrue(root.hasChild("body") && root.hasChild("right_leg"));
	}

	@Test
	void theHammerIsPaintedOnTheSkin() throws IOException {
		Path skin = ModResourceFiles.find("assets", "postrider", "textures", "entity", "postman.png");
		BufferedImage image;
		try (InputStream in = Files.newInputStream(skin)) {
			image = ImageIO.read(in);
		}
		// Handle (32,0 16x8) and head (48,0 8x5): unpainted pixels are see-through and the hammer would vanish
		for (int x = 32; x < 48; x++)
			for (int y = 0; y < 8; y++)
				assertEquals(255, image.getRGB(x, y) >>> 24, "handle pixel " + x + "," + y);
		for (int x = 48; x < 56; x++)
			for (int y = 0; y < 5; y++)
				assertEquals(255, image.getRGB(x, y) >>> 24, "head pixel " + x + "," + y);
	}

	@Test
	void heOnlyLooksAroundWhenHeIsntBusy() {
		assertTrue(PostmanModel.headTurns(PostmanEntity.Activity.NONE, false));
		assertFalse(PostmanModel.headTurns(PostmanEntity.Activity.NONE, true), "eyes on the mailbox while he's at it");
		for (PostmanEntity.Activity activity : PostmanEntity.Activity.values())
			if (activity != PostmanEntity.Activity.NONE)
				assertFalse(PostmanModel.headTurns(activity, false), activity.name());
	}

	@Test
	void theModelHasBothSatchels() {
		Set<String> parts = partNames();
		assertTrue(parts.contains("satchel_flat"));
		assertTrue(parts.contains("satchel_full"));
	}
}
