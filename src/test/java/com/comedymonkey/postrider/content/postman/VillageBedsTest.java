package com.comedymonkey.postrider.content.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.post.PendingDelivery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

class VillageBedsTest {
	private static PendingDelivery delivery() {
		return new PendingDelivery(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Steve", "Steve's Ranch",
			net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, new BlockPos(0, 64, 0)), "Alex's Inn", java.util.List.of(), 0);
	}

	private static BlockState bed(BedPart part, boolean occupied) {
		return Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, part).setValue(BedBlock.OCCUPIED, occupied);
	}

	@Test
	void onlyTheHeadOfABedNobodysInIsFree() {
		assertTrue(VillageBeds.isFreeBedHead(bed(BedPart.HEAD, false)));
		assertFalse(VillageBeds.isFreeBedHead(bed(BedPart.HEAD, true)), "someone's in it");
		assertFalse(VillageBeds.isFreeBedHead(bed(BedPart.FOOT, false)), "the foot end isn't where a sleeper lies");
		assertFalse(VillageBeds.isFreeBedHead(Blocks.STONE.defaultBlockState()));
		assertTrue(VillageBeds.isBed(bed(BedPart.FOOT, true)));
	}

	@Test
	void heLiesWithHisSolesAtTheFootAndHisHeadOnThePillow() {
		BlockPos head = new BlockPos(10, 64, 10);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			BlockPos foot = VillageBeds.footOf(head, facing);
			assertEquals(head, foot.relative(facing), "a bed faces from its foot to its head");
			Vec3 soles = VillageBeds.soles(head, facing, VillageBeds.BED_TOP);
			assertTrue(new AABB(foot).contains(soles.x, foot.getY() + 0.5, soles.z), facing + ": his soles are on the foot end");
			assertEquals(foot.getY() + VillageBeds.BED_TOP, soles.y, 1e-9, "on top of the mattress");
			// Lying on his back, facing the foot, his head is behind him: a body's length back from his soles
			Vec3 facingWay = Vec3.directionFromRotation(0, VillageBeds.yaw(facing));
			assertTrue(facingWay.dot(Vec3.atLowerCornerOf(facing.getOpposite().getNormal())) > 0.99, facing + ": he faces the foot of the bed");
			Vec3 crown = soles.subtract(facingWay.scale(DeliveryRun.SLEEPER_LENGTH - 0.2));
			assertTrue(new AABB(head).contains(crown.x, head.getY() + 0.5, crown.z), facing + ": his head is on the pillow");
		}
	}

	@Test
	void aBedHeIsInSurvivesSaving() {
		var registries = VanillaRegistries.createLookup();
		PendingDelivery delivery = delivery();
		CompoundTag tag = new DeliveryRun(delivery, null).save(registries);
		tag.putString("camp_step", "SLEEPING");
		tag.putLong("bed", new BlockPos(5, 70, -3).asLong());
		tag.putBoolean("in_bed", true);
		tag.putDouble("bed_stand_x", 4.5);
		tag.putDouble("bed_stand_y", 70);
		tag.putDouble("bed_stand_z", -1.5);
		DeliveryRun loaded = DeliveryRun.load(tag, registries);
		assertNotNull(loaded);
		CompoundTag again = loaded.save(registries);
		assertEquals(new BlockPos(5, 70, -3).asLong(), again.getLong("bed"));
		assertTrue(again.getBoolean("in_bed"));
		assertEquals(-1.5, again.getDouble("bed_stand_z"), 1e-9);
	}

	@Test
	void aWalkToABedThatWasntSavedStartsTheEveningOver() {
		var registries = VanillaRegistries.createLookup();
		PendingDelivery delivery = delivery();
		CompoundTag tag = new DeliveryRun(delivery, null).save(registries);
		tag.putString("camp_step", "TO_BED");
		DeliveryRun loaded = DeliveryRun.load(tag, registries);
		assertNotNull(loaded);
		assertEquals("FINDING", loaded.save(registries).getString("camp_step"));
	}
}
