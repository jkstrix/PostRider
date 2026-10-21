package com.comedymonkey.ponyexpress.post;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.content.parcel.ParcelLabel;
import com.comedymonkey.ponyexpress.testutil.ModResourceFiles;
import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

class TravelTimeTest {
	/** The translation keys a description is built from, outermost first. */
	private static List<String> keys(Component component) {
		List<String> keys = new ArrayList<>();
		collect(component, keys);
		return keys;
	}

	private static void collect(Object part, List<String> keys) {
		if (part instanceof Component component && component.getContents() instanceof TranslatableContents translatable) {
			keys.add(translatable.getKey());
			for (Object arg : translatable.getArgs())
				collect(arg, keys);
		}
	}

	@Test
	void splitsGameTicksIntoDaysHoursAndMinutes() {
		assertArrayEquals(new long[] {0, 0, 0}, TravelTime.split(0));
		assertArrayEquals(new long[] {0, 1, 0}, TravelTime.split(1000));
		assertArrayEquals(new long[] {0, 2, 30}, TravelTime.split(2500));
		assertArrayEquals(new long[] {1, 3, 0}, TravelTime.split(27000));
	}

	@Test
	void showsTheTwoLargestUnits() {
		assertEquals(List.of("time.ponyexpress.took", "time.ponyexpress.pair", "time.ponyexpress.day", "time.ponyexpress.hours"),
			keys(TravelTime.describe(24000 + 3000 + 500)));
		assertEquals(List.of("time.ponyexpress.took", "time.ponyexpress.pair", "time.ponyexpress.hours", "time.ponyexpress.minutes"),
			keys(TravelTime.describe(2500)));
	}

	@Test
	void showsASingleUnitOnItsOwnAndSaysOneNotOnes() {
		assertEquals(List.of("time.ponyexpress.took", "time.ponyexpress.hour"), keys(TravelTime.describe(1000)));
		assertEquals(List.of("time.ponyexpress.took", "time.ponyexpress.days"), keys(TravelTime.describe(48000)));
	}

	@Test
	void aVeryQuickDeliverySaysSo() {
		assertEquals(List.of("time.ponyexpress.took_moments"), keys(TravelTime.describe(5)));
	}

	@Test
	void labelsSavedBeforeTravelTimesStillLoad() {
		CompoundTag old = new CompoundTag();
		old.putString("from", "Ranch");
		old.putString("to", "Inn");
		old.putString("sender", "Steve");
		old.putLong("day", 3);
		ParcelLabel label = ParcelLabel.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		assertEquals(0, label.travelTicks());
	}

	@Test
	void everyWordingHasEnglishText() {
		JsonObject lang = ModResourceFiles.readJson(ModResourceFiles.find("assets", "ponyexpress", "lang", "en_us.json")).getAsJsonObject();
		for (String key : new String[] {"took", "took_moments", "pair", "day", "days", "hour", "hours", "minute", "minutes"})
			assertTrue(lang.has("time.ponyexpress." + key), key);
	}
}
