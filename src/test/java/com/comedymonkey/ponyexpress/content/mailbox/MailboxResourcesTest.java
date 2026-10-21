package com.comedymonkey.ponyexpress.content.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.testutil.ModResourceFiles;
import com.google.gson.JsonObject;

import net.minecraft.world.item.DyeColor;

class MailboxResourcesTest {
	private static JsonObject json(String... path) {
		Path file = ModResourceFiles.find(path);
		assertNotNull(file, String.join("/", path) + " is missing");
		return ModResourceFiles.readJson(file).getAsJsonObject();
	}

	@Test
	void everyColourHasItsBlockstateItemModelLootTableAndDyeRecipe() {
		for (DyeColor color : DyeColor.values()) {
			String name = color.getSerializedName() + "_mailbox";
			json("assets", "ponyexpress", "blockstates", name + ".json");
			json("assets", "ponyexpress", "models", "item", name + ".json");
			json("data", "ponyexpress", "loot_table", "blocks", name + ".json");
			json("data", "ponyexpress", "recipe", "dye_" + name + ".json");
		}
	}

	@Test
	void onlyTheRedMailboxHasTheYellowFlag() {
		for (DyeColor color : DyeColor.values()) {
			String blockstate = json("assets", "ponyexpress", "blockstates", color.getSerializedName() + "_mailbox.json").toString();
			String item = json("assets", "ponyexpress", "models", "item", color.getSerializedName() + "_mailbox.json").toString();
			boolean red = color == DyeColor.RED;
			assertEquals(red, blockstate.contains("mailbox_flag_down_yellow"), color.getName());
			assertEquals(red, blockstate.contains("mailbox_flag_up_yellow"), color.getName());
			assertEquals(red, item.contains("yellow_flag"), color.getName());
		}
	}

	@Test
	void onlyTheLowerHalfDropsTheMailbox() {
		String loot = json("data", "ponyexpress", "loot_table", "blocks", "blue_mailbox.json").toString();
		assertTrue(loot.contains("\"half\":\"lower\""));
	}

	@Test
	void theBoxIsTintedSoOneTextureCoversEveryColour() {
		String base = json("assets", "ponyexpress", "models", "block", "mailbox_base.json").toString();
		assertTrue(base.contains("\"tintindex\":0"));
		assertFalse(json("assets", "ponyexpress", "models", "block", "mailbox_flag_up.json").toString().contains("tintindex"));
	}

	@Test
	void everyMailboxHasAnEnglishName() {
		Path lang = ModResourceFiles.find("assets", "ponyexpress", "lang", "en_us.json");
		assumeTrue(lang != null);
		JsonObject names = ModResourceFiles.readJson(lang).getAsJsonObject();
		for (DyeColor color : DyeColor.values())
			assertTrue(names.has("block.ponyexpress." + color.getSerializedName() + "_mailbox"), color.getName());
	}

	@Test
	void theGuiSheetExists() {
		assertNotNull(ModResourceFiles.find("assets", "ponyexpress", "textures", "gui", "post_office.png"));
	}
}
