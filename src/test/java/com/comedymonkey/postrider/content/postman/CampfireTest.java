package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.registry.ModBlocks;
import com.comedymonkey.postrider.testutil.ModResourceFiles;
import com.comedymonkey.postrider.testutil.LoadedGameData;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

@ExtendWith(LoadedGameData.class)
class CampfireTest {
	private static final long DUSK = 12500;

	private static JsonObject json(String... path) {
		Path file = ModResourceFiles.find(path);
		assertNotNull(file, String.join("/", path) + " is missing");
		return ModResourceFiles.readJson(file).getAsJsonObject();
	}

	@Test
	void itBurnsBrightWhenLitAndDiesDownToEmbersByThreeInTheMorning() {
		assertEquals(15, CampfireBlock.lightAt(DUSK, DUSK));
		assertEquals(9, CampfireBlock.lightAt((DUSK + CampfireBlock.EMBERS_AT) / 2, DUSK), "half burnt by midnight-ish");
		assertEquals(3, CampfireBlock.lightAt(21000, DUSK), "embers at 3am");
		assertEquals(3, CampfireBlock.lightAt(23999, DUSK), "and embers until he packs up");
	}

	@Test
	void itDiesDownALittleAtATimeAndNeverFlaresUp() {
		int last = CampfireBlock.BRIGHTEST;
		int steps = 0;
		for (long time = DUSK; time <= CampfireBlock.EMBERS_AT; time += 20) {
			int light = CampfireBlock.lightAt(time, DUSK);
			assertTrue(light <= last, "brighter at " + time);
			assertTrue(last - light <= 1, "dropped by more than one at " + time);
			if (light < last)
				steps++;
			last = light;
		}
		assertEquals(12, steps, "from 15 to 3 one level at a time");
	}

	@Test
	void aFireLitAfterThreeIsOnlyEmbers() {
		assertEquals(3, CampfireBlock.lightAt(22000, 22000));
		assertEquals(3, CampfireBlock.lightAt(100, DUSK), "after 6am the day has wrapped round: the fire is out by then anyway");
	}

	@Test
	void itGivesOffItsOwnLightLevel() {
		BlockState state = ModBlocks.CAMPFIRE.get().defaultBlockState();
		assertEquals(15, state.getLightEmission());
		assertEquals(3, state.setValue(CampfireBlock.LIGHT, CampfireBlock.EMBERS).getLightEmission());
		assertEquals(3, CampfireBlock.LIGHT.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElseThrow());
	}

	@Test
	void playersCantBreakMoveOrBlowItUpAndItDropsNothing() {
		BlockState state = ModBlocks.CAMPFIRE.get().defaultBlockState();
		assertEquals(-1.0F, state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), "unbreakable, like bedrock");
		assertTrue(ModBlocks.CAMPFIRE.get().getExplosionResistance() >= 3600000);
		assertEquals(PushReaction.IMMOVEABLE, state.getPistonPushReaction());
		assertTrue(ModBlocks.CAMPFIRE.get().getLootTable().isEmpty());
	}

	@Test
	void itsHarmlessAndNothingSpawnsOnIt() {
		BlockState state = ModBlocks.CAMPFIRE.get().defaultBlockState();
		assertFalse(state.isValidSpawn(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, EntityTypes.ZOMBIE));
		// Not a vanilla campfire or fire, so nothing burns, spreads, cooks or can be doused
		assertEquals(Block.class, CampfireBlock.class.getSuperclass());
		assertTrue(CampfireBlock.CAMP_RANGE > DeliveryRun.TENT_DISTANCE + 1, "his own tent keeps it lit");
	}

	@Test
	void heLightsItJustInFrontOfHisSeat() {
		CampSites.Site site = new CampSites.Site(new BlockPos(10, 64, 10), Direction.SOUTH);
		assertEquals(new BlockPos(10, 64, 11), CampfireBlock.spotFor(site));
	}

	@Test
	void theThirdStrikeCatchesWhileHesStillKneeling() {
		assertEquals(3, DeliveryRun.STRIKES.length);
		for (int i = 1; i < DeliveryRun.STRIKES.length; i++)
			assertTrue(DeliveryRun.STRIKES[i] > DeliveryRun.STRIKES[i - 1]);
		assertTrue(DeliveryRun.STRIKES[2] < 2.6 * 20, "before he stands back up");
		assertEquals(PostmanEntity.Gesture.LIGHT_FIRE.seconds * 20, DeliveryRun.LIGHTING_TICKS, 1e-4);
	}

	@Test
	void theFireIsRememberedWhenTheGameSaves() {
		var registries = VanillaRegistries.createWorldLookup();
		DeliveryRun run = new DeliveryRun(new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Steve's Ranch",
			GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), "Alex's Inn", List.of(new ItemStack(Items.BREAD)), 0), null);
		assertNull(DeliveryRun.load(run.save(registries), registries).campfire());
		CompoundTag saved = run.save(registries);
		saved.putLong("campfire", new BlockPos(5, 70, -3).asLong());
		saved.putLong("fire_lit_at", DUSK);
		DeliveryRun loaded = DeliveryRun.load(saved, registries);
		assertEquals(new BlockPos(5, 70, -3), loaded.campfire());
		assertEquals(DUSK, loaded.save(registries).getLongOr("fire_lit_at", -1));
	}

	@Test
	void ithasAModelForBurningAndForEmbers() {
		JsonObject variants = json("assets", "postrider", "blockstates", "campfire.json").getAsJsonObject("variants");
		for (int light = 3; light <= 15; light++) {
			String model = variants.getAsJsonObject("light=" + light).get("model").getAsString();
			assertEquals(light == 3 ? "postrider:block/campfire_embers" : "postrider:block/campfire_lit", model, "light " + light);
		}
		assertTrue(json("assets", "postrider", "models", "block", "campfire_lit.json").toString().contains("campfire_flame"));
		assertFalse(json("assets", "postrider", "models", "block", "campfire_embers.json").toString().contains("campfire_flame"));
		assertNotNull(json("assets", "postrider", "textures", "block", "campfire_flame.png.mcmeta").getAsJsonObject("animation"));
		assertTrue(json("assets", "postrider", "lang", "en_us.json").has("block.postrider.campfire"));
	}

	@Test
	void noFireIsLitOnIce() {
		assertTrue(CampfireBlock.isIce(net.minecraft.world.level.block.Blocks.ICE.defaultBlockState()));
		assertTrue(CampfireBlock.isIce(net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()));
		assertTrue(CampfireBlock.isIce(net.minecraft.world.level.block.Blocks.BLUE_ICE.defaultBlockState()));
		assertFalse(CampfireBlock.isIce(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));
		assertFalse(CampfireBlock.isIce(net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState()), "snow takes a fire; ice would melt through");
	}
}
