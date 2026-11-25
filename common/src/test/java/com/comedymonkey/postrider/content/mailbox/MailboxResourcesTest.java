package com.comedymonkey.postrider.content.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.testutil.ModResourceFiles;
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
			json("assets", "postrider", "blockstates", name + ".json");
			json("assets", "postrider", "models", "item", name + ".json");
			json("data", "postrider", "loot_table", "blocks", name + ".json");
			json("data", "postrider", "recipe", "dye_" + name + ".json");
		}
	}

	@Test
	void onlyTheRedMailboxHasTheYellowFlag() {
		for (DyeColor color : DyeColor.values()) {
			String blockstate = json("assets", "postrider", "blockstates", color.getSerializedName() + "_mailbox.json").toString();
			String item = json("assets", "postrider", "models", "item", color.getSerializedName() + "_mailbox.json").toString();
			boolean red = color == DyeColor.RED;
			assertEquals(red, blockstate.contains("mailbox_flag_down_yellow"), color.getName());
			assertEquals(red, blockstate.contains("mailbox_flag_up_yellow"), color.getName());
			assertEquals(red, item.contains("yellow_flag"), color.getName());
		}
	}

	@Test
	void onlyTheLowerHalfDropsTheMailbox() {
		String loot = json("data", "postrider", "loot_table", "blocks", "blue_mailbox.json").toString();
		assertTrue(loot.contains("\"half\":\"lower\""));
	}

	@Test
	void theBoxIsTintedSoOneTextureCoversEveryColour() {
		String upper = json("assets", "postrider", "models", "block", "mailbox_upper.json").toString();
		assertTrue(upper.contains("\"tintindex\":0"));
		assertFalse(json("assets", "postrider", "models", "block", "mailbox_flag_up.json").toString().contains("tintindex"));
	}

	@Test
	void eachHalfIsDrawnInsideItsOwnBlockSoItIsLitCorrectly() {
		for (String model : new String[] {"mailbox_lower", "mailbox_upper", "mailbox_flag_down", "mailbox_flag_up"}) {
			for (var element : json("assets", "postrider", "models", "block", model + ".json").getAsJsonArray("elements")) {
				var from = element.getAsJsonObject().getAsJsonArray("from");
				var to = element.getAsJsonObject().getAsJsonArray("to");
				assertTrue(from.get(1).getAsDouble() >= 0 && to.get(1).getAsDouble() <= 16, model + " reaches outside its block");
			}
		}
	}

	@Test
	void theUpperHalfDrawsTheBoxAndFlag() {
		String blockstate = json("assets", "postrider", "blockstates", "blue_mailbox.json").toString();
		assertTrue(blockstate.contains("{\"when\":{\"half\":\"upper\",\"facing\":\"north\",\"flag\":\"true\"},\"apply\":{\"model\":\"postrider:block/mailbox_flag_up\"}}"));
		assertTrue(blockstate.contains("{\"when\":{\"half\":\"lower\",\"facing\":\"north\"},\"apply\":{\"model\":\"postrider:block/mailbox_lower\"}}"));
	}

	@Test
	void everyMailboxHasAnEnglishName() {
		Path lang = ModResourceFiles.find("assets", "postrider", "lang", "en_us.json");
		assumeTrue(lang != null);
		JsonObject names = ModResourceFiles.readJson(lang).getAsJsonObject();
		for (DyeColor color : DyeColor.values())
			assertTrue(names.has("block.postrider." + color.getSerializedName() + "_mailbox"), color.getName());
	}

	@Test
	void theGuiSheetExists() {
		assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "gui", "post_office.png"));
	}
}
