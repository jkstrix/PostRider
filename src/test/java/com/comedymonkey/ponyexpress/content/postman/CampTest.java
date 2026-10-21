package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.client.model.TentModel;
import com.comedymonkey.ponyexpress.post.PostOfficeHours;
import com.comedymonkey.ponyexpress.registry.ModEntities;
import com.comedymonkey.ponyexpress.testutil.ModResourceFiles;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

class CampTest {
	@Test
	void heTurnsInWhenMonstersComeOutAndIsUpAtSix() {
		assertFalse(PostOfficeHours.isBedtime(12500), "still playing at 6:30pm");
		assertFalse(PostOfficeHours.isBedtime(13187));
		assertTrue(PostOfficeHours.isBedtime(13188), "in the tent by 7:11pm");
		assertTrue(PostOfficeHours.isBedtime(23999));
		assertFalse(PostOfficeHours.isBedtime(0), "up and away at 6am");
	}

	@Test
	void theHarmonicaFillsTheGapBetweenDuskAndBedtime() {
		for (long time = PostOfficeHours.CLOSES; time < PostOfficeHours.MOBS_COME_OUT; time += 100) {
			assertFalse(PostOfficeHours.isOpen(time));
			assertFalse(PostOfficeHours.isBedtime(time));
		}
	}

	@Test
	void inHisTentMobsOnlyNoticeHimFourBlocksAway() {
		// A zombie usually notices players 35 blocks off; with this share of that, it's 4
		assertEquals(4.0, 35 * PostmanEntity.tentVisibility(35), 1e-9);
		assertEquals(4.0, 16 * PostmanEntity.tentVisibility(16), 1e-9);
		assertEquals(1.0, PostmanEntity.tentVisibility(3), 1e-9, "never easier to spot than in the open");
	}

	@Test
	void theTentIsPitchedBehindHimWithinTheChunksHeKeepsLoaded() {
		assertTrue(DeliveryRun.TENT_DISTANCE < 16);
		assertTrue(DeliveryRun.HAMMER_TICKS > 0);
		assertEquals(1.75F, ModEntities.TENT.get().getWidth());
	}

	@Test
	void heSleepsWithHisBootsOutOfTheDoorAndHisHeadInside() {
		Vec3 tent = new Vec3(100.5, 64, -20.5);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			Vec3 door = Vec3.atLowerCornerOf(facing.getNormal());
			// How far along the tent from its middle towards the door
			double soles = DeliveryRun.sleepingSpot(tent, facing.toYRot()).subtract(tent).dot(door);
			double crown = soles - DeliveryRun.SLEEPER_LENGTH;
			assertTrue(soles > DeliveryRun.TENT_HALF_LENGTH, "boots out of the door, facing " + facing);
			assertTrue(soles - DeliveryRun.TENT_HALF_LENGTH <= 0.5, "only his boots, facing " + facing);
			assertTrue(crown > -DeliveryRun.TENT_HALF_LENGTH, "head inside the back wall, facing " + facing);
			assertEquals(tent.y, DeliveryRun.sleepingSpot(tent, facing.toYRot()).y, 1e-9);
		}
	}

	@Test
	void theTentModelBakes() {
		assertNotNull(TentModel.createBodyLayer().bakeRoot().getChild("tent"));
		assertNotNull(ModResourceFiles.find("assets", "ponyexpress", "textures", "entity", "tent.png"));
	}

	@Test
	void theHarmonicaIsAMonoOggSoItFadesWithDistance() throws IOException {
		Path ogg = ModResourceFiles.find("assets", "ponyexpress", "sounds", "harmonica.ogg");
		assertNotNull(ogg);
		byte[] bytes = Files.readAllBytes(ogg);
		assertEquals("OggS", new String(bytes, 0, 4));
		// The Vorbis identification header: packet type 1, "vorbis", a 4-byte version, then the channel count
		int header = indexOf(bytes, new byte[] {1, 'v', 'o', 'r', 'b', 'i', 's'});
		assertTrue(header > 0);
		assertEquals(1, bytes[header + 7 + 4], "harmonica.ogg should be mono");
		String sounds = ModResourceFiles.readJson(ModResourceFiles.find("assets", "ponyexpress", "sounds.json")).toString();
		assertTrue(sounds.contains("ponyexpress:harmonica"));
	}

	@Test
	void theTuneIsCredited() throws IOException {
		// Tests run from a folder somewhere under the project, so look upwards for it
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null && !Files.exists(dir.resolve("CREDITS.md")))
			dir = dir.getParent();
		assertNotNull(dir, "CREDITS.md not found");
		String credits = Files.readString(dir.resolve("CREDITS.md"));
		assertTrue(credits.contains("monosolomono"));
	}

	private static int indexOf(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++)
				if (haystack[i + j] != needle[j])
					continue outer;
			return i;
		}
		return -1;
	}
}
