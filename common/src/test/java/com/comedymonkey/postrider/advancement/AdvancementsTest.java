package com.comedymonkey.postrider.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.Roster;
import com.comedymonkey.postrider.registry.ModTriggers;
import com.comedymonkey.postrider.testutil.ModResourceFiles;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

class AdvancementsTest {
	private static Map<String, JsonObject> advancements() throws IOException {
		Path dir = ModResourceFiles.find("data", "postrider", "advancement");
		assertNotNull(dir);
		Map<String, JsonObject> all = new HashMap<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				all.put(name.substring(0, name.length() - ".json".length()), ModResourceFiles.readJson(file).getAsJsonObject());
			}
		}
		return all;
	}

	private static JsonObject lang() {
		return ModResourceFiles.readJson(ModResourceFiles.find("assets", "postrider", "lang", "en_us.json")).getAsJsonObject();
	}

	@Test
	void allFifteenAreThereOnOneTab() throws IOException {
		Map<String, JsonObject> all = advancements();
		assertEquals(15, all.size());
		long roots = all.values().stream().filter(a -> !a.has("parent")).count();
		assertEquals(1, roots, "one tab");
		assertTrue(all.get("open_for_business").getAsJsonObject("display").has("background"), "the tab has a background");
		for (var entry : all.entrySet())
			if (entry.getValue().has("parent")) {
				String parent = entry.getValue().get("parent").getAsString();
				assertTrue(parent.startsWith("postrider:") && all.containsKey(parent.substring("postrider:".length())), entry.getKey() + " hangs off " + parent);
			}
	}

	@Test
	void everyOneIsNamedAndHasARealIcon() throws IOException {
		JsonObject lang = lang();
		for (var entry : advancements().entrySet()) {
			JsonObject display = entry.getValue().getAsJsonObject("display");
			for (String part : new String[] {"title", "description"}) {
				String key = display.getAsJsonObject(part).get("translate").getAsString();
				assertTrue(lang.has(key), entry.getKey() + " is missing " + key);
			}
			String icon = display.getAsJsonObject("icon").get("id").getAsString();
			assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(icon)), entry.getKey() + "'s icon " + icon);
		}
	}

	@Test
	void everyPostEventCriterionIsOneTheModFires() throws IOException {
		for (var entry : advancements().entrySet())
			for (var criterion : entry.getValue().getAsJsonObject("criteria").entrySet()) {
				JsonObject json = criterion.getValue().getAsJsonObject();
				String trigger = json.get("trigger").getAsString();
				assertTrue(trigger.equals("postrider:post_event") || trigger.equals("minecraft:inventory_changed"), trigger);
				if (!trigger.equals("postrider:post_event"))
					continue;
				PostEventTrigger.Instance instance = PostEventTrigger.Instance.CODEC.parse(JsonOps.INSTANCE, json.get("conditions"))
					.getOrThrow(message -> new AssertionError(entry.getKey() + ": " + message));
				assertTrue(PostEvents.ALL.contains(instance.event()), entry.getKey() + " listens for " + instance.event());
				instance.rider().ifPresent(rider -> assertNotNull(Roster.byId(rider), entry.getKey() + " names " + rider));
			}
	}

	@Test
	void meetTheCrewMeansEveryRiderOnTheRoster() throws IOException {
		JsonObject crew = advancements().get("meet_the_crew");
		Set<String> greeted = new HashSet<>();
		for (JsonElement criterion : crew.getAsJsonObject("criteria").asMap().values())
			greeted.add(criterion.getAsJsonObject().getAsJsonObject("conditions").get("rider").getAsString());
		Set<String> roster = new HashSet<>();
		Roster.ALL.forEach(rider -> roster.add(rider.id()));
		assertEquals(roster, greeted);
		assertEquals(20, crew.getAsJsonArray("requirements").size(), "all of them, each one required");
	}

	@Test
	void rainbowRouteMeansEveryColour() throws IOException {
		JsonObject rainbow = advancements().get("rainbow_route");
		assertEquals(DyeColor.values().length, rainbow.getAsJsonObject("criteria").size());
		for (DyeColor colour : DyeColor.values())
			assertTrue(rainbow.getAsJsonObject("criteria").has(colour.getSerializedName()), colour.getName());
	}

	@Test
	void highwayRobberyIsASecretUntilYouDoIt() throws IOException {
		assertTrue(advancements().get("highway_robbery").getAsJsonObject("display").get("hidden").getAsBoolean());
		assertFalse(advancements().get("first_class").getAsJsonObject("display").get("hidden").getAsBoolean());
	}

	@Test
	void theTriggerMatchesTheEventTheRiderAndTheCount() {
		PostEventTrigger.Instance chris = new PostEventTrigger.Instance(Optional.empty(), PostEvents.GREETED, Optional.of("chris"), 1);
		assertTrue(chris.matches(PostEvents.GREETED, "chris", 1));
		assertFalse(chris.matches(PostEvents.GREETED, "gus", 1), "Gus is not Chris");
		assertFalse(chris.matches(PostEvents.SENT, "chris", 1));
		PostEventTrigger.Instance anyone = new PostEventTrigger.Instance(Optional.empty(), PostEvents.GREETED, Optional.empty(), 1);
		assertTrue(anyone.matches(PostEvents.GREETED, "gus", 1));
		PostEventTrigger.Instance penPal = new PostEventTrigger.Instance(Optional.empty(), PostEvents.SENT_LETTER, Optional.empty(), 10);
		assertFalse(penPal.matches(PostEvents.SENT_LETTER, null, 9));
		assertTrue(penPal.matches(PostEvents.SENT_LETTER, null, 10));
	}

	@Test
	void theTriggerAndTheLettersSentStatisticAreRegistered() {
		assertTrue(BuiltInRegistries.TRIGGER_TYPES.containsKey(ResourceLocation.parse("postrider:post_event")));
		assertNotNull(ModTriggers.POST_EVENT.get());
		assertEquals(ResourceLocation.parse("postrider:letters_sent"), ModTriggers.LETTERS_SENT.get());
		assertTrue(lang().has("stat.postrider.letters_sent"));
	}
}
