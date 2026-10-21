package com.comedymonkey.ponyexpress.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comedymonkey.ponyexpress.post.PendingDelivery;
import com.comedymonkey.ponyexpress.post.PostDispatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

class DeliveryRunTest {
	private static PendingDelivery delivery() {
		return new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Steve's Ranch",
			GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)), "Alex's Inn", List.of(new ItemStack(Items.BREAD, 4)), 0);
	}

	@Test
	void aRunStartsByPickingUpTheMail() {
		DeliveryRun run = new DeliveryRun(delivery(), GlobalPos.of(Level.OVERWORLD, new BlockPos(500, 70, 300)));
		assertEquals(DeliveryRun.Stage.PICKING_UP, run.stage());
		assertFalse(run.isOver());
	}

	@Test
	void aRunSurvivesSavingWithItsDeliveryAndDestination() {
		var registries = VanillaRegistries.createLookup();
		PendingDelivery delivery = delivery();
		DeliveryRun run = new DeliveryRun(delivery, GlobalPos.of(Level.OVERWORLD, new BlockPos(500, 70, 300)));
		CompoundTag saved = run.save(registries);
		DeliveryRun loaded = DeliveryRun.load(saved, registries);
		assertNotNull(loaded);
		assertEquals(delivery.id(), loaded.delivery().id());
		assertEquals(4, loaded.delivery().items().get(0).getCount());
		assertEquals(DeliveryRun.Stage.PICKING_UP, loaded.stage());
		assertTrue(saved.contains("destination"));
	}

	@Test
	void aRunToAnAddressThatVanishedStillSaves() {
		var registries = VanillaRegistries.createLookup();
		DeliveryRun loaded = DeliveryRun.load(new DeliveryRun(delivery(), null).save(registries), registries);
		assertNotNull(loaded);
	}

	@Test
	void theAnimationTimingsFitInsideTheirAnimations() {
		assertTrue(DeliveryRun.SATCHEL_FILLS_AT < DeliveryRun.PICKUP_TICKS);
		assertTrue(DeliveryRun.PICKUP_TICKS <= PostmanEntity.Gesture.PICKUP.seconds * 20 + 1);
		assertTrue(DeliveryRun.MAIL_IN_AT < DeliveryRun.DELIVER_TICKS);
		assertTrue(DeliveryRun.DELIVER_TICKS <= PostmanEntity.Gesture.DELIVER.seconds * 20);
	}

	@Test
	void ridersKeepAThreeByThreeSquareOfChunksLoaded() {
		var chunks = RiderChunks.around(new ChunkPos(10, -4));
		assertEquals(9, chunks.size());
		assertTrue(chunks.contains(new ChunkPos(10, -4)));
		assertTrue(chunks.contains(new ChunkPos(9, -5)));
		assertTrue(chunks.contains(new ChunkPos(11, -3)));
	}

	@Test
	void atMostThirtyTwoRidersAreOutAtOnce() {
		assertTrue(PostDispatcher.canSendMore(0, 1));
		assertTrue(PostDispatcher.canSendMore(31, 5));
		assertFalse(PostDispatcher.canSendMore(32, 5));
		assertFalse(PostDispatcher.canSendMore(3, 0));
	}

	@Test
	void hostileMobsHuntRidersButNeutralOnesDoNot() {
		assertTrue(RiderHunting.huntsRiders(Zombie.class));
		assertTrue(RiderHunting.huntsRiders(Skeleton.class));
		assertTrue(RiderHunting.huntsRiders(Creeper.class));
		assertFalse(RiderHunting.huntsRiders(EnderMan.class));
		assertFalse(RiderHunting.huntsRiders(ZombifiedPiglin.class));
		assertFalse(RiderHunting.huntsRiders(Cow.class));
	}
}
