package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.comedymonkey.postrider.content.postman.route.LocalPlanner;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostDispatcher;
import com.comedymonkey.postrider.testutil.LoadedGameData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@ExtendWith(LoadedGameData.class)
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
		var registries = VanillaRegistries.createWorldLookup();
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
		var registries = VanillaRegistries.createWorldLookup();
		DeliveryRun loaded = DeliveryRun.load(new DeliveryRun(delivery(), null).save(registries), registries);
		assertNotNull(loaded);
	}

	@Test
	void heStandsSquareInFrontOfTheDoor() {
		BlockPos mailbox = new BlockPos(10, 64, 10);
		Vec3 post = Vec3.atBottomCenterOf(mailbox);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (double standOff : new double[] {DeliveryRun.PICKUP_STAND_OFF, DeliveryRun.DELIVER_STAND_OFF}) {
				Vec3 spot = DeliveryRun.standingSpot(mailbox, facing, standOff);
				assertEquals(standOff, spot.distanceTo(post), 1e-9, facing.getName());
				// On the door's side, in front of it rather than beside it
				assertEquals(facing.getStepX() * standOff, spot.x - post.x, 1e-9);
				assertEquals(facing.getStepZ() * standOff, spot.z - post.z, 1e-9);
			}
		}
	}

	@Test
	void heStandsWhereEachAnimationReachesTheDoor() {
		// Tuned by eye in game: the pickup reaches half a block further than the delivery's quarter
		assertEquals(1.35, DeliveryRun.PICKUP_STAND_OFF, 1e-9);
		assertEquals(1.1, DeliveryRun.DELIVER_STAND_OFF, 1e-9);
		// Half a player's width in front of him, and the box's door is at the edge of the mailbox's block
		assertTrue(DeliveryRun.DELIVER_STAND_OFF - 0.3 > 0.5, "would stand inside the box");
	}

	@Test
	void hisYawFacesTheMailbox() {
		Vec3 box = new Vec3(0, 64, 0);
		// Minecraft yaw: 0 faces south (+z), 90 west (-x), 180 north (-z), -90 east (+x)
		assertEquals(180, Math.floorMod((int) Math.round(DeliveryRun.yawToward(new Vec3(0, 64, 2), box)), 360));
		assertEquals(0, Math.floorMod((int) Math.round(DeliveryRun.yawToward(new Vec3(0, 64, -2), box)), 360));
		assertEquals(90, Math.floorMod((int) Math.round(DeliveryRun.yawToward(new Vec3(2, 64, 0), box)), 360));
		assertEquals(270, Math.floorMod((int) Math.round(DeliveryRun.yawToward(new Vec3(-2, 64, 0), box)), 360));
	}

	@Test
	void theAnimationTimingsFitInsideTheirAnimations() {
		assertTrue(DeliveryRun.SATCHEL_FILLS_AT < DeliveryRun.PICKUP_TICKS);
		assertTrue(DeliveryRun.PICKUP_TICKS <= PostmanEntity.Gesture.PICKUP.seconds * 20 + 1);
		assertTrue(DeliveryRun.MAIL_IN_AT < DeliveryRun.DELIVER_TICKS);
		assertTrue(DeliveryRun.DELIVER_TICKS <= PostmanEntity.Gesture.DELIVER.seconds * 20);
	}

	@Test
	void ridersKeepASevenBySevenSquareLoadedButOnlyTheMiddleTicks() {
		ChunkPos centre = new ChunkPos(10, -4);
		var chunks = RiderChunks.around(centre);
		assertEquals(49, chunks.size());
		assertTrue(chunks.contains(new ChunkPos(7, -7)) && chunks.contains(new ChunkPos(13, -1)));
		// Loaded far enough to plan a whole stretch ahead from anywhere in his chunk
		assertTrue(RiderChunks.RADIUS * 16 >= Journey.STRETCH);
		long ticking = chunks.stream().filter(c -> RiderChunks.ticks(centre, c)).count();
		assertEquals(9, ticking, "only the 3x3 round him grows and spawns things");
		assertTrue(RiderChunks.ticks(centre, new ChunkPos(9, -5)));
		assertFalse(RiderChunks.ticks(centre, new ChunkPos(8, -4)));
	}

	@Test
	void hisHorseCanPlanAWholeStretchAhead() {
		assertTrue(PostmanHorse.FOLLOW_RANGE > Journey.STRETCH);
		assertEquals(48.0, Journey.STRETCH);
	}

	@Test
	void atMostTwentyRidersAreOutAtOnce() {
		assertEquals(20, PostDispatcher.MAX_RIDERS);
		assertTrue(PostDispatcher.canSendMore(0, 1));
		assertTrue(PostDispatcher.canSendMore(19, 5));
		assertFalse(PostDispatcher.canSendMore(20, 5));
		assertFalse(PostDispatcher.canSendMore(3, 0));
	}

	@Test
	void hostileMobsHuntRidersButNeutralOnesDoNot() {
		assertTrue(RiderHunting.huntsRiders(Zombie.class));
		assertTrue(RiderHunting.huntsRiders(Skeleton.class));
		assertFalse(RiderHunting.huntsRiders(Creeper.class), "creepers never stalk riders");
		assertFalse(RiderHunting.huntsRiders(Enderman.class));
		assertFalse(RiderHunting.huntsRiders(ZombifiedPiglin.class));
		assertFalse(RiderHunting.huntsRiders(Cow.class));
	}

	@Test
	void spidersOnlyHuntRidersInTheDark() {
		assertTrue(RiderHunting.huntsRiders(Spider.class) && RiderHunting.huntsOnlyInTheDark(Spider.class));
		assertTrue(RiderHunting.huntsOnlyInTheDark(CaveSpider.class));
		assertFalse(RiderHunting.huntsOnlyInTheDark(Zombie.class), "zombies hunt day or night");
		assertEquals(0.5F, RiderHunting.SPIDER_LIGHT, "the same light rule as vanilla spiders");
	}

	@Test
	void aRidersLoadedSquareCanBeAnySize() {
		ChunkPos centre = new ChunkPos(0, 0);
		assertEquals(9, RiderChunks.around(centre, 1).size());
		assertEquals(81, RiderChunks.around(centre, 4).size());
		assertTrue(RiderChunks.around(centre, 4).containsAll(RiderChunks.around(centre, 3)), "the bigger square covers the smaller");
	}

	@Test
	void aHorseLeftWithoutItsRiderDoesntLinger() {
		assertTrue(PostmanHorse.RIDERLESS_TICKS > PostmanEntity.MOUNTS_AFTER, "time for him to climb on");
		assertTrue(PostmanHorse.RIDERLESS_TICKS <= 10 * 20);
	}

	@Test
	void whenStuckHeTriesTheNearestSpotsFirstAndOnlyThenFurtherOut() {
		List<int[]> offsets = DeliveryRun.jumpOffsets();
		double last = 0;
		for (int[] offset : offsets) {
			double distance = Math.hypot(offset[0], offset[1]);
			assertTrue(distance >= last - 1.5, "nearer rings come first: " + offset[0] + "," + offset[1] + " after " + last);
			last = Math.max(last, distance);
		}
		assertTrue(Math.hypot(offsets.get(0)[0], offsets.get(0)[1]) <= 2.0, "a couple of blocks away to start with");
		assertEquals(24, DeliveryRun.JUMP_RADII[DeliveryRun.JUMP_RADII.length - 1], "and no further than this before the old long jumps");
		assertTrue(offsets.size() >= 8 * DeliveryRun.JUMP_RADII.length);
	}

	@Test
	void inAColumnHeClimbsOrDriftsAndIsHimselfAgainAfter() {
		assertEquals(PostmanEntity.Activity.CLIMB.ordinal() + 1, PostmanEntity.Activity.DRIFT.ordinal(), "the two column activities, after the map");
		assertTrue(PostmanEntity.Activity.CLIMB.ordinal() > PostmanEntity.Activity.STUDY_MAP.ordinal(), "added at the end, so saved activities still read");
	}

	@Test
	void aHorseLostOnTheTripStaysLostAcrossASave() {
		var registries = VanillaRegistries.createWorldLookup();
		DeliveryRun run = new DeliveryRun(delivery(), null);
		assertFalse(run.isHorseLost(), "he sets out with his horse");
		CompoundTag saved = run.save(registries);
		saved.putBoolean("horse_lost", true);
		assertTrue(DeliveryRun.load(saved, registries).isHorseLost(), "once killed, gone for the rest of the trip, even over a restart");
		assertFalse(DeliveryRun.load(run.save(registries), registries).isHorseLost(), "a new run has it again");
	}

	@Test
	void goingDownOnFootHeWalksRatherThanRuns() {
		assertTrue(PostmanEntity.blocksPerSecond(DeliveryRun.WALK_SPEED, PostmanEntity.BASE_SPEED)
			< PostmanEntity.blocksPerSecond(DeliveryRun.RUN_SPEED, PostmanEntity.BASE_SPEED * PostmanEntity.SPRINT_BOOST), "a walk is slower than his run");
		assertEquals(DeliveryRun.WALK_BLOCKS_PER_SECOND, PostmanEntity.blocksPerSecond(DeliveryRun.WALK_SPEED, PostmanEntity.BASE_SPEED), 0.05, "a player's walking pace");
	}

	@Test
	void theMapStaysOutWhileTheRouteIsCheckedAgainstTheGround() {
		assertTrue(DeliveryRun.MAP_CHECK_TICKS >= 20 * 30, "long enough for a stage's chunks to load (half a minute at least)");
		assertTrue(DeliveryRun.MAP_CHECKS >= 1 && DeliveryRun.MAP_CHECKS <= 3, "a redo or two, then he goes regardless");
	}

	@Test
	void heIsNeverJumpedBeyondTheChunksThatTickRoundHim() {
		assertTrue(DeliveryRun.JUMP_MAX <= 16 * (com.comedymonkey.postrider.content.postman.RiderChunks.TICKING_RADIUS + 1),
			"within the ticking square: put down beyond it he'd never tick again");
		assertTrue(DeliveryRun.MAX_STRIKES >= 10, "the map is corrected many times before he's ever jumped");
	}

	@Test
	void aSandbarIsntSteppedOnto() {
		assertTrue(DeliveryRun.isScrapOfShore(false, 20), "a sandbar: twenty blocks to get about on and no way on");
		assertFalse(DeliveryRun.isScrapOfShore(false, 4000), "a proper shore, the way on just not found within the search");
		assertFalse(DeliveryRun.isScrapOfShore(true, 12), "a scrap the way on is found from (the box on an islet)");
	}

	@Test
	void theBargeHasMorePatienceThanAWalkerBeforeItCountsAsStuck() {
		assertTrue(DeliveryRun.BARGE_STALL_TICKS >= 3 * Journey.Progress.STALL_TICKS, "pressed against a bank it takes a while to back off and turn");
	}

	@Test
	void cutOffOnAFloeHeTakesToTheWaterOnceStrikingTheMapOffHasNotHelped() {
		assertFalse(DeliveryRun.isCutOff(true, LocalPlanner.Status.FOUND, 3), "a way exists");
		assertFalse(DeliveryRun.isCutOff(false, LocalPlanner.Status.RUNNING, 3), "the search ran out of budget, not of ground");
		assertFalse(DeliveryRun.isCutOff(false, LocalPlanner.Status.FAILED, 0), "the map is redone round the way ahead first");
		assertTrue(DeliveryRun.isCutOff(false, LocalPlanner.Status.FAILED, 1), "all the ground searched, the map already redone: cut off");
		assertTrue(DeliveryRun.isCutOff(false, LocalPlanner.Status.PARTIAL, 2), "nearer but never there is cut off too");
	}

	@Test
	void atAColdCampHeShiversEveryFewSecondsInsteadOfPlaying() {
		assertFalse(DeliveryRun.shiversNow(false, DeliveryRun.SHIVER_GAP / 2, false), "a camp with a fire: the harmonica, not shivering");
		assertTrue(DeliveryRun.shiversNow(true, DeliveryRun.SHIVER_GAP / 2, false));
		assertTrue(DeliveryRun.shiversNow(true, DeliveryRun.SHIVER_GAP / 2 + DeliveryRun.SHIVER_GAP * 3, false), "and again every few seconds");
		assertFalse(DeliveryRun.shiversNow(true, DeliveryRun.SHIVER_GAP / 2 + 1, false), "not every tick");
		assertFalse(DeliveryRun.shiversNow(true, DeliveryRun.SHIVER_GAP / 2, true), "not over another gesture");
	}

	@Test
	void iceAtTheBargesLevelIsSlidOntoNotSteppedOntoButAStepOfIceIsABank() {
		// Sea ice at 62 (stood on at 63) by water at 62: the barge slides onto it
		assertTrue(DeliveryRun.isSlidableIce(63, 62));
		// Ice a block higher (stood on at 64): climbed by the barge too
		assertTrue(DeliveryRun.isSlidableIce(64, 62));
		// Two blocks higher: a bank, and he steps ashore onto it
		assertFalse(DeliveryRun.isSlidableIce(65, 62));
	}

	@Test
	void aHullBumpingWithOpenWaterAheadIsCaughtOnACorner() {
		assertTrue(DeliveryRun.caughtOnACorner(true, false), "bumping, nothing ahead of the bow: the side's caught");
		assertFalse(DeliveryRun.caughtOnACorner(true, true), "bumping with the bow against a bank: hard against the shore, not a corner");
		assertFalse(DeliveryRun.caughtOnACorner(false, false));
	}
}
