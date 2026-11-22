package com.comedymonkey.postrider.gametest;

import com.comedymonkey.postrider.content.hopper.IceHopperEntity;
import com.comedymonkey.postrider.registry.ModEntities;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The ice hopper in a real world: a pool with sea ice beyond it, and the boat driven at the ice. */
public final class IceHopperGameTests {
	private static final String ARENA = "gametest/arena_128x64";

	private IceHopperGameTests() {}

	/** Driven east across a pool, it hops up onto the ice sheet beyond and slides on, rather than stopping against its edge. */
	@GameTest(skyAccess = true, template = ARENA, batch = "ice_hopper", timeoutTicks = 400)
	public static void itHopsFromTheWaterOntoTheIceAndSlidesOn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(10, 10, 70, 30);
		scene.water(12, 12, 30, 28, 2);
		scene.fill(31, 0, 12, 60, 0, 28, Blocks.ICE.defaultBlockState());
		IceHopperEntity hopper = helper.spawn(ModEntities.ICE_HOPPER.get(), new Vec3(26.5, scene.surface() - helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getY() + 0.2, 20.5));
		hopper.setYRot(-90);
		double iceTop = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0)).getY();
		helper.onEachTick(() -> {
			// Rowed east at a boat's pace until it's up on the ice
			if (hopper.getY() < iceTop - 0.05)
				hopper.setDeltaMovement(0.35, hopper.getDeltaMovement().y, 0);
		});
		helper.succeedWhen(() -> {
			Vec3 at = helper.relativeVec(hopper.position());
			if (hopper.getY() < iceTop - 0.05 || at.x < 34)
				throw helper.assertionException(Component.literal("Not up on the ice and sliding yet: at " + at + " (ice top " + (iceTop - helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getY()) + ")"));
		});
	}

	/** Sliding along a sheet of ice at a step of ice one block higher, it hops up the step and slides on. */
	@GameTest(skyAccess = true, template = ARENA, batch = "ice_hopper_step", timeoutTicks = 400)
	public static void itHopsUpABlockOfIceFromTheSheet(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(10, 10, 70, 30);
		scene.fill(12, 0, 12, 40, 0, 28, Blocks.ICE.defaultBlockState());
		scene.fill(41, 0, 12, 60, 1, 28, Blocks.ICE.defaultBlockState());
		IceHopperEntity hopper = helper.spawn(ModEntities.ICE_HOPPER.get(), new Vec3(30.5, 1.0, 20.5));
		hopper.setYRot(-90);
		double stepTop = helper.absolutePos(new net.minecraft.core.BlockPos(0, 2, 0)).getY();
		helper.onEachTick(() -> {
			if (hopper.getY() < stepTop - 0.05)
				hopper.setDeltaMovement(0.35, hopper.getDeltaMovement().y, 0);
		});
		helper.succeedWhen(() -> {
			Vec3 at = helper.relativeVec(hopper.position());
			if (hopper.getY() < stepTop - 0.05 || at.x < 44)
				throw helper.assertionException(Component.literal("Not up the step and sliding yet: at " + at));
		});
	}
}
