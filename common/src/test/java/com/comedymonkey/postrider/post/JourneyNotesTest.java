package com.comedymonkey.postrider.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

class JourneyNotesTest {
	private static PendingDelivery delivery() {
		return new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Ranch", GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
			"Island", List.of(new ItemStack(Items.BREAD)), 0);
	}

	@Test
	void aBargeCrossingOrANightOutMakesForAWeatheredDelivery() {
		assertFalse(JourneyNotes.NONE.weathered());
		assertTrue(new JourneyNotes(true, 0).weathered());
		assertTrue(new JourneyNotes(false, 1).weathered());
	}

	@Test
	void theTooltipSaysWhatTheMailWentThrough() {
		List<Component> lines = new ArrayList<>();
		new JourneyNotes(true, 2).addTo(lines);
		assertEquals(2, lines.size());
		lines.clear();
		JourneyNotes.NONE.addTo(lines);
		assertTrue(lines.isEmpty(), "an easy ride says nothing");
	}

	@Test
	void notesSurviveSaving() {
		JourneyNotes notes = new JourneyNotes(true, 3);
		assertEquals(notes, JourneyNotes.CODEC.parse(JsonOps.INSTANCE, JourneyNotes.CODEC.encodeStart(JsonOps.INSTANCE, notes).getOrThrow()).getOrThrow());
	}

	@Test
	void onlyWeatheredMailCarriesNotes() {
		ItemStack easy = Mail.wrap(delivery(), 2, 100, JourneyNotes.NONE);
		assertNull(easy.get(ModDataComponents.JOURNEY_NOTES.get()));
		ItemStack hard = Mail.wrap(delivery(), 2, 100, new JourneyNotes(true, 1));
		assertEquals(new JourneyNotes(true, 1), hard.get(ModDataComponents.JOURNEY_NOTES.get()));
	}

	@Test
	void aRunRemembersWhatItsBeenThroughAcrossRestarts() {
		var registries = VanillaRegistries.createLookup();
		CompoundTag tag = new DeliveryRun(delivery(), GlobalPos.of(Level.OVERWORLD, new BlockPos(900, 64, 0))).save(registries);
		assertEquals(JourneyNotes.NONE, DeliveryRun.load(tag, registries).notes());
		tag.putBoolean("crossed_water", true);
		tag.putInt("nights_out", 2);
		assertEquals(new JourneyNotes(true, 2), DeliveryRun.load(tag, registries).notes());
	}
}
