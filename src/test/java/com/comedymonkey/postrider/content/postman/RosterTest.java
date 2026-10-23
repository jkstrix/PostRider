package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.post.PostDispatcher;
import com.comedymonkey.postrider.testutil.ModResourceFiles;

import net.minecraft.util.RandomSource;

class RosterTest {
	private static BufferedImage skin(Roster.Rider rider) throws IOException {
		Path path = ModResourceFiles.find("assets", "postrider", "textures", "entity", "rider", rider.id() + ".png");
		assertNotNull(path, rider.id() + " has no skin");
		try (InputStream in = Files.newInputStream(path)) {
			return ImageIO.read(in);
		}
	}

	private static boolean painted(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) >>> 24 != 0;
	}

	@Test
	void theCrewIsTwentyStrongAndThatsHowManyCanBeOut() {
		assertEquals(20, Roster.ALL.size());
		assertEquals(Roster.ALL.size(), PostDispatcher.MAX_RIDERS, "everyone can be out at once, but no more");
	}

	@Test
	void everyoneHasTheirOwnNameFaceAndHorse() {
		assertEquals(20, Roster.ALL.stream().map(Roster.Rider::id).collect(Collectors.toSet()).size());
		assertEquals(20, Roster.ALL.stream().map(Roster.Rider::name).collect(Collectors.toSet()).size());
		assertEquals(20, Roster.ALL.stream().map(Roster.Rider::texture).collect(Collectors.toSet()).size());
		assertEquals(20, Roster.ALL.stream().map(r -> r.horse().name()).collect(Collectors.toSet()).size());
		for (Roster.Rider rider : Roster.ALL)
			assertEquals(rider, Roster.byId(rider.id()));
		assertNull(Roster.byId("nobody"));
	}

	@Test
	void chrisIsJustChrisAndRidesTheFastestHorse() {
		Roster.Rider chris = Roster.byId("chris");
		assertNotNull(chris);
		assertEquals("Chris", chris.name());
		for (Roster.Rider other : Roster.ALL)
			if (other != chris)
				assertTrue(other.horse().blocksPerSecond() < chris.horse().blocksPerSecond(), other.name() + " is as quick as Chris");
		assertEquals("Lightning", chris.horse().name());
	}

	@Test
	void everyHorseGallopsAtLeastSevenBlocksASecond() {
		double slowest = Double.MAX_VALUE;
		for (Roster.Rider rider : Roster.ALL) {
			Roster.Horse horse = rider.horse();
			assertTrue(horse.blocksPerSecond() >= PostmanHorse.MIN_BLOCKS_PER_SECOND, horse.name());
			assertTrue(horse.speed() >= PostmanHorse.MIN_SPEED - 1e-9, horse.name());
			// Within what a vanilla horse can be bred to (0.3375 movement speed)
			assertTrue(horse.speed() <= 0.3375, horse.name());
			assertEquals(horse.blocksPerSecond(), PostmanHorse.blocksPerSecond(horse.speed()), 1e-9);
			slowest = Math.min(slowest, horse.blocksPerSecond());
		}
		assertEquals("Molasses", Roster.byId("gus").horse().name());
		assertEquals(slowest, Roster.byId("gus").horse().blocksPerSecond(), 1e-9, "Gus brings up the rear");
	}

	@Test
	void theWomenRideOnTheSlimBuildAndAreSpokenOfAsHer() {
		Set<String> women = Set.of("mattie", "rosa", "clara", "hattie", "lottie", "nell", "ada", "sadie");
		for (Roster.Rider rider : Roster.ALL) {
			boolean woman = women.contains(rider.id());
			assertEquals(woman, rider.she(), rider.id());
			assertEquals(woman, rider.slim(), rider.id());
			assertEquals(woman ? "her" : "his", rider.possessive(), rider.id());
		}
	}

	@Test
	void longHairIsOnlyPaintedForWhoeverWearsIt() throws IOException {
		// The first pixel of each long-hair part's front face (box UV: one box-depth in from its corner, see PostmanModel)
		int[][] fronts = {{1, 33}, {22, 34}, {31, 33}, {38, 34}};
		String[] parts = {"hair_back", "hair_bun", "hair_braid", "hair_pigtails"};
		for (Roster.Rider rider : Roster.ALL) {
			BufferedImage image = skin(rider);
			assertEquals(128, image.getWidth());
			assertEquals(64, image.getHeight());
			for (int i = 0; i < parts.length; i++) {
				boolean wears = parts[i].equals(rider.hair().part());
				assertEquals(wears, painted(image, fronts[i][0], fronts[i][1]), rider.id() + " " + parts[i]);
			}
		}
	}

	@Test
	void slimSkinsAreLaidOutForThreeWideArmsAndANarrowHead() throws IOException {
		for (Roster.Rider rider : Roster.ALL) {
			BufferedImage image = skin(rider);
			// A 4-wide arm's box runs 16 pixels across; a 3-wide one stops at 14
			assertEquals(!rider.slim(), painted(image, 55, 24), rider.id() + " right arm");
			assertEquals(!rider.slim(), painted(image, 47, 56), rider.id() + " left arm");
			// An 8-wide head's back face ends at x 31; a 7-wide one at 29
			assertEquals(!rider.slim(), painted(image, 31, 12), rider.id() + " head");
			assertTrue(painted(image, 12, 12), rider.id() + " has a face");
		}
	}

	@Test
	void onlyFreeRidersAreSentOut() {
		RandomSource random = RandomSource.create(5);
		Set<String> busy = new HashSet<>();
		for (int i = 0; i < Roster.ALL.size(); i++) {
			Roster.Rider next = Roster.pickFree(busy, random);
			assertNotNull(next);
			assertFalse(busy.contains(next.id()), next.id() + " is already out");
			busy.add(next.id());
		}
		assertNull(Roster.pickFree(busy, random), "the whole crew is out");
		busy.remove("chris");
		assertEquals("chris", Roster.pickFree(busy, random).id(), "the only one left");
	}

	@Test
	void everyoneGetsTheirTurn() {
		RandomSource random = RandomSource.create(6);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 2000; i++)
			seen.add(Roster.pickFree(Set.of(), random).id());
		assertEquals(20, seen.size());
	}

	@Test
	void everyRidersSkinCarriesThePropsOfTheBase() throws IOException {
		// The canteen (56,0 8x6) and the bread's net (ends 56,6 4x2, sides 54,8 8x3) are painted on the base skin and stamped onto each rider's
		int[][] areas = {{56, 0, 8, 6}, {56, 6, 4, 2}, {54, 8, 8, 3}};
		for (Roster.Rider rider : Roster.ALL) {
			BufferedImage image = skin(rider);
			for (int[] area : areas)
				for (int x = area[0]; x < area[0] + area[2]; x++)
					for (int y = area[1]; y < area[1] + area[3]; y++)
						assertTrue(painted(image, x, y), rider.id() + " prop pixel " + x + "," + y);
		}
	}
}
