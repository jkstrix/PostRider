package com.comedymonkey.postrider.content.lost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.testutil.ModResourceFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;

class LostParcelsTest {
	private static final BlockPos ORIGIN = new BlockPos(100, 64, -40);

	@Test
	void usesTheSpotItselfWhenItCan() {
		assertEquals(ORIGIN, LostParcels.findSpot(ORIGIN, pos -> true));
	}

	@Test
	void triesStraightAboveAndBelowBeforeMovingSideways() {
		assertEquals(ORIGIN.above(), LostParcels.findSpot(ORIGIN, pos -> pos.equals(ORIGIN.above()) || pos.equals(ORIGIN.east())));
		assertEquals(ORIGIN.below(2), LostParcels.findSpot(ORIGIN, pos -> pos.equals(ORIGIN.below(2)) || pos.equals(ORIGIN.east())));
	}

	@Test
	void picksTheNearestRing() {
		Set<BlockPos> free = Set.of(ORIGIN.offset(3, 0, 0), ORIGIN.offset(1, 0, 1));
		assertEquals(ORIGIN.offset(1, 0, 1), LostParcels.findSpot(ORIGIN, free::contains));
	}

	@Test
	void givesUpBeyondTheSearchRadius() {
		BlockPos far = ORIGIN.offset(LostParcels.SEARCH_RADIUS + 1, 0, 0);
		assertNull(LostParcels.findSpot(ORIGIN, far::equals));
		assertNull(LostParcels.findSpot(ORIGIN, pos -> false));
	}

	@Test
	void theSkullShowsOnlyWhenKilledAndTheMapOnlyWhenStuck() {
		Path file = ModResourceFiles.find("assets", "postrider", "blockstates", "lost_parcel.json");
		JsonArray parts = ModResourceFiles.readJson(file).getAsJsonObject().getAsJsonArray("multipart");
		int plain = 0;
		for (JsonElement part : parts) {
			JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
			String model = part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString();
			if (model.endsWith("lost_parcel_skull"))
				assertEquals("killed", when.get("cause").getAsString());
			else if (model.endsWith("lost_parcel_map"))
				assertEquals("stuck", when.get("cause").getAsString());
			else {
				assertNull(when.get("cause"));
				plain++;
			}
		}
		assertEquals(4, plain);
		for (String model : new String[] {"lost_parcel", "lost_parcel_skull", "lost_parcel_map"})
			assertEquals(true, ModResourceFiles.find("assets", "postrider", "models", "block", model + ".json") != null, model);
	}

	@Test
	void lostInTheVoidItGoesToTheNearestLand() {
		// Land only from x 30 on: far beyond the usual few blocks' search
		BlockPos fell = new BlockPos(0, -80, 0);
		BlockPos spot = LostParcels.nearestColumn(fell, LostParcels.LAND_SEARCH_RADIUS, (x, z) -> x >= 30 ? new BlockPos(x, 64, z) : null);
		assertEquals(new BlockPos(30, 64, 0), spot, "the nearest column with ground, straight across");
		assertNull(LostParcels.nearestColumn(fell, LostParcels.LAND_SEARCH_RADIUS, (x, z) -> null), "nothing within reach");
		assertTrue(LostParcels.LAND_SEARCH_RADIUS >= 48);
	}
}
