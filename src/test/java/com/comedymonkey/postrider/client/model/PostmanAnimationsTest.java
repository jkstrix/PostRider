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
		Map.entry("harmonica", PostmanAnimations.HARMONICA), Map.entry("sleep", PostmanAnimations.SLEEP),
		Map.entry("drink", PostmanAnimations.DRINK),
		Map.entry("light_fire", PostmanAnimations.LIGHT_FIRE),
		Map.entry("study_map", PostmanAnimations.STUDY_MAP),
		Map.entry("climb", PostmanAnimations.CLIMB),
		Map.entry("drift", PostmanAnimations.DRIFT),
		Map.entry("wipe_brow", PostmanAnimations.WIPE_BROW),
		Map.entry("tip_hat", PostmanAnimations.TIP_HAT),
		Map.entry("pat_horse", PostmanAnimations.PAT_HORSE),
		Map.entry("check_satchel", PostmanAnimations.CHECK_SATCHEL),
		Map.entry("stretch", PostmanAnimations.STRETCH),
		Map.entry("yawn", PostmanAnimations.YAWN),
		Map.entry("look_back", PostmanAnimations.LOOK_BACK),
		Map.entry("scratch_head", PostmanAnimations.SCRATCH_HEAD),
		Map.entry("swat_fly", PostmanAnimations.SWAT_FLY),
		Map.entry("adjust_hat", PostmanAnimations.ADJUST_HAT),
		Map.entry("pull_hat_down", PostmanAnimations.PULL_HAT_DOWN),
		Map.entry("shade_eyes", PostmanAnimations.SHADE_EYES),
		Map.entry("shiver", PostmanAnimations.SHIVER),
		Map.entry("swig", PostmanAnimations.SWIG),
		Map.entry("pocket_watch", PostmanAnimations.POCKET_WATCH),
		Map.entry("read_letter", PostmanAnimations.READ_LETTER),
		Map.entry("hum", PostmanAnimations.HUM),
		Map.entry("neck_roll", PostmanAnimations.NECK_ROLL),
		Map.entry("crack_knuckles", PostmanAnimations.CRACK_KNUCKLES));

	private static Set<String> partNames() {
		ModelPart root = PostmanModel.createBodyLayer().bakeRoot();
		Set<String> names = new HashSet<>();
		collect(root, names);
		return names;
	}

	private static void collect(ModelPart part, Set<String> names) {
		for (String child : new String[] {"root", "body", "head", "right_arm", "left_arm", "satchel", "satchel_flat", "satchel_full",
			"right_leg", "left_leg", "hat", "hat_brim_right_r1", "hat_brim_left_r1", "harmonica", "hammer", "potion", "canteen", "pocket_watch", "letter", "striker", "flint", "map", "map_right"})
			if (part.hasChild(child)) {
				names.add(child);
				collect(part.getChild(child), names);
			}
	}

	@Test
	void allThirtyEightApprovedAnimationsAreThere() {
		assertEquals(38, ALL.size());
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
		for (String looping : new String[] {"walk", "idle", "ride", "ride_idle", "camp_hammer", "camp_rest", "row", "harmonica", "sleep", "climb", "drift"})
			assertTrue(ALL.get(looping).looping(), looping);
		for (String once : new String[] {"pickup", "deliver", "whistle", "hurt", "wave", "drink", "light_fire", "study_map", "wipe_brow", "tip_hat", "pat_horse", "check_satchel", "stretch", "yawn", "look_back", "scratch_head", "swat_fly", "adjust_hat", "pull_hat_down", "shade_eyes", "shiver", "swig", "pocket_watch", "read_letter", "hum", "neck_roll", "crack_knuckles"})
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
	void heDrinksFromABottleInHisRightHandWhateverElseHesDoing() {
		assertTrue(PostmanModel.createBodyLayer().bakeRoot().getChild("root").getChild("body").getChild("right_arm").hasChild("potion"));
		assertTrue(PostmanModel.overridesUpperBody(PostmanEntity.Gesture.DRINK), "his arm leaves the reins to drink");
		assertFalse(PostmanModel.overridesUpperBody(PostmanEntity.Gesture.PICKUP));
		// Only his arm and head are taken over, so the drink mustn't rely on moving his legs
		assertFalse(PostmanAnimations.DRINK.boneAnimations().containsKey("right_leg"));
		assertFalse(PostmanAnimations.DRINK.boneAnimations().containsKey("root"));
	}

	@Test
	void theBottleIsPaintedOnTheSkin() throws IOException {
		Path skin = ModResourceFiles.find("assets", "postrider", "textures", "entity", "postman.png");
		BufferedImage image;
		try (InputStream in = Files.newInputStream(skin)) {
			image = ImageIO.read(in);
		}
		for (int x = 32; x < 54; x++)
			for (int y = 8; y < 14; y++)
				if (x < 44 || (x < 50 && y < 11) || y < 10)
					assertEquals(255, image.getRGB(x, y) >>> 24, "bottle pixel " + x + "," + y);
	}

	@Test
	void mannerismsOnlyMoveHisHeadArmsAndHatSoTheyWorkInTheSaddle() {
		Set<String> upperBody = Set.of("head", "right_arm", "left_arm", "hat");
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values()) {
			if (gesture.ordinal() < PostmanEntity.Gesture.WIPE_BROW.ordinal())
				continue;
			assertTrue(PostmanModel.overridesUpperBody(gesture), gesture.name());
			for (String bone : PostmanModel.gestureAnimation(gesture).boneAnimations().keySet())
				assertTrue(upperBody.contains(bone), gesture.name() + " moves " + bone);
			assertFalse(PostmanModel.gestureAnimation(gesture).looping(), gesture.name());
		}
		assertFalse(PostmanModel.overridesUpperBody(PostmanEntity.Gesture.PICKUP), "the pickup uses his whole body");
	}

	@Test
	void hisPropsArePaintedOnTheSkin() throws IOException {
		Path skin = ModResourceFiles.find("assets", "postrider", "textures", "entity", "postman.png");
		BufferedImage image;
		try (InputStream in = Files.newInputStream(skin)) {
			image = ImageIO.read(in);
		}
		// Canteen and its cap, the pocket watch, and the letter's two faces
		int[][] areas = {{56, 0, 8, 6}, {48, 5, 4, 2}, {58, 11, 6, 3}, {47, 11, 4, 3}, {51, 11, 4, 3}};
		for (int[] area : areas)
			for (int x = area[0]; x < area[0] + area[2]; x++)
				for (int y = area[1]; y < area[1] + area[3]; y++)
					assertEquals(255, image.getRGB(x, y) >>> 24, "prop pixel " + x + "," + y);
	}

	@Test
	void heKneelsToLightTheFireWithFlintAndSteel() {
		ModelPart body = PostmanModel.createBodyLayer().bakeRoot().getChild("root").getChild("body");
		assertTrue(body.getChild("right_arm").hasChild("striker"));
		assertTrue(body.getChild("left_arm").hasChild("flint"));
		assertFalse(PostmanModel.overridesUpperBody(PostmanEntity.Gesture.LIGHT_FIRE), "he kneels, so it's his whole body");
		assertTrue(PostmanAnimations.LIGHT_FIRE.boneAnimations().containsKey("root"));
		assertEquals(3.0F, PostmanAnimations.LIGHT_FIRE.lengthInSeconds(), 1e-4);
	}

	@Test
	void theFlintAndSteelArePaintedOnTheSkin() throws IOException {
		Path skin = ModResourceFiles.find("assets", "postrider", "textures", "entity", "postman.png");
		BufferedImage image;
		try (InputStream in = Files.newInputStream(skin)) {
			image = ImageIO.read(in);
		}
		// The striker (48,36 8x4) and the flint (56,36 6x3)
		int[][] areas = {{48, 36, 8, 4}, {56, 36, 6, 3}};
		for (int[] area : areas)
			for (int x = area[0]; x < area[0] + area[2]; x++)
				for (int y = area[1]; y < area[1] + area[3]; y++)
					assertEquals(255, image.getRGB(x, y) >>> 24, "prop pixel " + x + "," + y);
	}

	@Test
	void heStudiesAMapHeldOpenInBothHands() {
		ModelPart map = PostmanModel.createBodyLayer().bakeRoot().getChild("root").getChild("body").getChild("map");
		assertTrue(map.hasChild("map_right"), "the right half unfolds about the crease");
		// Only his head, arms and the map move, so he can study it in the saddle; it holds its last pose for as
		// long as he plans
		Set<String> upperBody = Set.of("head", "right_arm", "left_arm", "map", "map_right");
		for (String bone : PostmanAnimations.STUDY_MAP.boneAnimations().keySet())
			assertTrue(upperBody.contains(bone), "study_map moves " + bone);
		assertFalse(PostmanAnimations.STUDY_MAP.looping());
		assertTrue(PostmanAnimations.STUDY_MAP.lengthInSeconds() >= 20.0F);
	}

	@Test
	void theMapIsPaintedOnEverySkin() throws IOException {
		// Both faces of both halves (64,35 to 88,43): unpainted pixels are see-through and the map would have holes
		java.util.List<Path> skins = new java.util.ArrayList<>();
		skins.add(ModResourceFiles.find("assets", "postrider", "textures", "entity", "postman.png"));
		for (com.comedymonkey.postrider.content.postman.Roster.Rider rider : com.comedymonkey.postrider.content.postman.Roster.ALL)
			skins.add(ModResourceFiles.find("assets", "postrider", "textures", "entity", "rider", rider.id() + ".png"));
		for (Path skin : skins) {
			BufferedImage image;
			try (InputStream in = Files.newInputStream(skin)) {
				image = ImageIO.read(in);
			}
			for (int x = 64; x < 88; x++)
				for (int y = 35; y < 43; y++)
					assertEquals(255, image.getRGB(x, y) >>> 24, skin.getFileName() + " map pixel " + x + "," + y);
		}
	}

	@Test
	void heClimbsHandOverHandAndDriftsGently() {
		assertTrue(PostmanAnimations.CLIMB.looping());
		assertEquals(1.0F, PostmanAnimations.CLIMB.lengthInSeconds(), 1e-4);
		for (String limb : new String[] {"right_arm", "left_arm", "right_leg", "left_leg"})
			assertTrue(PostmanAnimations.CLIMB.boneAnimations().containsKey(limb), limb + " moves on a ladder");
		assertTrue(PostmanAnimations.DRIFT.looping());
		assertTrue(PostmanAnimations.DRIFT.boneAnimations().containsKey("root"), "he bobs in the water");
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
