package com.comedymonkey.ponyexpress.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

class PostOfficeTest {
	private static PendingDelivery delivery(String to, ItemStack... items) {
		return new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Steve's Ranch",
			GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 1)), to, List.of(items), 1200L);
	}

	@Test
	void handsOutParcelsOldestFirst() {
		PostOffice office = new PostOffice();
		PendingDelivery first = delivery("Inn", new ItemStack(Items.IRON_INGOT, 5));
		PendingDelivery second = delivery("Hall", new ItemStack(Items.BREAD));
		office.enqueue(first);
		office.enqueue(second);
		assertEquals(2, office.size());
		assertSame(first, office.poll());
		assertSame(second, office.poll());
		assertNull(office.poll());
	}

	@Test
	void enqueueingMarksItForSaving() {
		PostOffice office = new PostOffice();
		office.enqueue(delivery("Inn", new ItemStack(Items.APPLE)));
		assertTrue(office.isDirty());
	}

	@Test
	void deliveriesKeepTheirOwnCopyOfTheItemList() {
		List<ItemStack> items = new ArrayList<>(List.of(new ItemStack(Items.APPLE)));
		PendingDelivery pending = new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Ranch",
			GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), "Inn", items, 0);
		items.add(new ItemStack(Items.DIAMOND));
		assertEquals(1, pending.items().size());
		assertThrows(UnsupportedOperationException.class, () -> pending.items().add(ItemStack.EMPTY));
	}

	@Test
	void tracksWhichRiderCarriesWhichDelivery() {
		PostOffice office = new PostOffice();
		PendingDelivery pending = delivery("Inn", new ItemStack(Items.APPLE));
		UUID rider = UUID.randomUUID();
		office.setOut(pending, rider);
		assertTrue(office.isActive(pending.id()));
		assertTrue(office.isRider(rider));
		assertEquals(1, office.activeCount());
		office.finish(pending.id());
		assertFalse(office.isRider(rider));
		assertEquals(0, office.activeCount());
	}

	@Test
	void aParcelPutBackGoesToTheFrontOfTheQueue() {
		PostOffice office = new PostOffice();
		PendingDelivery first = delivery("Inn", new ItemStack(Items.APPLE));
		PendingDelivery second = delivery("Hall", new ItemStack(Items.BREAD));
		office.enqueue(second);
		office.putBack(first);
		assertSame(first, office.poll());
	}

	@Test
	void ridersOnTheRoadSurviveSaving() {
		var registries = VanillaRegistries.createLookup();
		PostOffice office = new PostOffice();
		PendingDelivery pending = delivery("Inn", new ItemStack(Items.APPLE));
		UUID rider = UUID.randomUUID();
		office.setOut(pending, rider);
		PostOffice loaded = PostOffice.load(office.save(new CompoundTag(), registries), registries);
		assertTrue(loaded.isRider(rider));
		assertEquals("Inn", loaded.active().iterator().next().delivery().toName());
	}

	@Test
	void queueSurvivesSavingAndLoadingWithItemData() {
		var registries = VanillaRegistries.createLookup();
		ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
		PostOffice office = new PostOffice();
		office.enqueue(delivery("Alex's Inn", new ItemStack(Items.IRON_INGOT, 32), named));

		CompoundTag saved = office.save(new CompoundTag(), registries);
		PostOffice loaded = PostOffice.load(saved, registries);

		assertEquals(1, loaded.size());
		PendingDelivery back = loaded.poll();
		assertEquals("Alex's Inn", back.toName());
		assertEquals("Steve", back.senderName());
		assertEquals(1200L, back.sentAt());
		assertEquals(2, back.items().size());
		assertEquals(32, back.items().get(0).getCount());
		assertEquals("Old Faithful", back.items().get(1).getHoverName().getString());
	}
}
