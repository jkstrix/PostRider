package com.comedymonkey.postrider.content.letter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.parcel.ParcelLabel;
import com.comedymonkey.postrider.network.EditLetterPayload;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.comedymonkey.postrider.registry.ModItems;
import com.comedymonkey.postrider.testutil.ModResourceFiles;
import com.google.gson.JsonObject;

import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

class LetterItemsTest {
	@Test
	void blankLettersStackButWrittenLettersAndParcelsDoNot() {
		assertEquals(16, new ItemStack(ModItems.LETTER.get()).getMaxStackSize());
		assertEquals(1, new ItemStack(ModItems.WRITTEN_LETTER.get()).getMaxStackSize());
		assertEquals(1, new ItemStack(ModItems.PARCEL.get()).getMaxStackSize());
	}

	@Test
	void lettersAndLabelsSurviveSavingOnAnItem() {
		var registries = VanillaRegistries.createLookup();
		ItemStack letter = new ItemStack(ModItems.WRITTEN_LETTER.get());
		letter.set(ModDataComponents.LETTER.get(), new LetterContent(List.of("Howdy"), "Steve", Optional.empty()));
		Tag saved = letter.save(registries);
		ItemStack loaded = ItemStack.parse(registries, saved).orElseThrow();
		assertEquals("Steve", loaded.get(ModDataComponents.LETTER.get()).author());

		ItemStack parcel = new ItemStack(ModItems.PARCEL.get());
		parcel.set(ModDataComponents.PARCEL_LABEL.get(), new ParcelLabel("Ranch", "Inn", "Steve", 4, 3500));
		ItemStack parcelBack = ItemStack.parse(registries, parcel.save(registries)).orElseThrow();
		assertEquals("Inn", parcelBack.get(ModDataComponents.PARCEL_LABEL.get()).to());
	}

	@Test
	void lettersCanOnlyBeWrittenFromTheHotbarOrOffhand() {
		assertTrue(EditLetterPayload.isWritableSlot(0));
		assertTrue(EditLetterPayload.isWritableSlot(8));
		assertTrue(EditLetterPayload.isWritableSlot(Inventory.SLOT_OFFHAND));
		assertFalse(EditLetterPayload.isWritableSlot(9));
		assertFalse(EditLetterPayload.isWritableSlot(-1));
	}

	@Test
	void eachItemHasItsBlockbenchModelAndTextures() {
		for (String item : List.of("letter", "written_letter", "parcel")) {
			Path model = ModResourceFiles.find("assets", "postrider", "models", "item", item + ".json");
			assertNotNull(model, item);
			JsonObject json = ModResourceFiles.readJson(model).getAsJsonObject();
			assertTrue(json.has("elements"), item + " should be the 3D Blockbench model");
			for (var texture : json.getAsJsonObject("textures").entrySet()) {
				String path = texture.getValue().getAsString().replace("postrider:", "");
				assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", path + ".png"), item + " " + path);
			}
		}
	}

	@Test
	void flatLettersUseFrontLightingInTheInventory() {
		for (String item : List.of("letter", "written_letter")) {
			JsonObject json = ModResourceFiles.readJson(ModResourceFiles.find("assets", "postrider", "models", "item", item + ".json")).getAsJsonObject();
			assertEquals("front", json.get("gui_light").getAsString(), item);
		}
	}

	@Test
	void theLetterRecipeIsPaperFeatherAndInk() {
		String recipe = ModResourceFiles.readJson(ModResourceFiles.find("data", "postrider", "recipe", "letter.json")).toString();
		assertTrue(recipe.contains("minecraft:paper"));
		assertTrue(recipe.contains("minecraft:feather"));
		assertTrue(recipe.contains("minecraft:ink_sac"));
	}

	@Test
	void theLetterPageSheetExists() {
		assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "gui", "letter_page.png"));
	}

	@Test
	void everyItemHasAnEnglishName() {
		JsonObject lang = ModResourceFiles.readJson(ModResourceFiles.find("assets", "postrider", "lang", "en_us.json")).getAsJsonObject();
		for (String item : List.of("letter", "written_letter", "parcel"))
			assertTrue(lang.has("item.postrider." + item), item);
	}
}
