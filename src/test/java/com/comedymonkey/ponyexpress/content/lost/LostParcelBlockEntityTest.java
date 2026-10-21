package com.comedymonkey.ponyexpress.content.lost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class LostParcelBlockEntityTest {
	private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID KILLER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
	private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000d");

	private static LostParcelBlockEntity parcel(UUID killer) {
		LostParcelBlockEntity parcel = new LostParcelBlockEntity(BlockPos.ZERO, ModBlocks.LOST_PARCEL.get().defaultBlockState());
		parcel.fill(List.of(new ItemStack(Items.DIAMOND, 3), ItemStack.EMPTY, new ItemStack(Items.BREAD)), SENDER, "Steve",
			"Steve's Ranch", "Alex's Inn", RECIPIENT, killer, killer != null ? "Zombie" : "", 14);
		return parcel;
	}

	@Test
	void theSenderAddresseeAndKillerCanOpenIt() {
		LostParcelBlockEntity parcel = parcel(KILLER);
		assertTrue(parcel.canOpen(SENDER, false));
		assertTrue(parcel.canOpen(RECIPIENT, false));
		assertTrue(parcel.canOpen(KILLER, false));
	}

	@Test
	void strangersCannotButOperatorsCan() {
		LostParcelBlockEntity parcel = parcel(null);
		assertFalse(parcel.canOpen(STRANGER, false));
		assertTrue(parcel.canOpen(STRANGER, true));
	}

	@Test
	void keepsOnlyRealItemsAndHandsThemOverOnce() {
		LostParcelBlockEntity parcel = parcel(null);
		assertEquals(2, parcel.getContents().size());
		List<ItemStack> taken = parcel.takeContents();
		assertEquals(3, taken.get(0).getCount());
		assertTrue(parcel.getContents().isEmpty());
	}

	@Test
	void survivesSaving() {
		var registries = VanillaRegistries.createLookup();
		CompoundTag saved = parcel(KILLER).saveWithoutMetadata(registries);
		LostParcelBlockEntity loaded = new LostParcelBlockEntity(BlockPos.ZERO, ModBlocks.LOST_PARCEL.get().defaultBlockState());
		loaded.loadCustomOnly(saved, registries);
		assertEquals(2, loaded.getContents().size());
		assertEquals("Steve", loaded.getSenderName());
		assertEquals("Steve's Ranch", loaded.getFromName());
		assertEquals("Alex's Inn", loaded.getToName());
		assertEquals("Zombie", loaded.getKillerName());
		assertEquals(14, loaded.getDay());
		assertTrue(loaded.canOpen(KILLER, false));
		assertFalse(loaded.canOpen(STRANGER, false));
	}
}
