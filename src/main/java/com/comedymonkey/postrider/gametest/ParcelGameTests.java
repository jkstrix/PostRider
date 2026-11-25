package com.comedymonkey.postrider.gametest;

import java.util.List;
import java.util.UUID;

import com.comedymonkey.postrider.post.Mail;
import com.comedymonkey.postrider.post.PendingDelivery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Opening a parcel, in a real world with a real player: everything in it ends up on the ground. */
public final class ParcelGameTests {
	private ParcelGameTests() {}

	@GameTest(skyAccess = true, template = "gametest/river_40x12", batch = "parcel", timeoutTicks = 100)
	@SuppressWarnings("removal") // the plain mock player can't drop items on the ground; only a server player can
	public static void openingAParcelThrowsOutEverythingInItAndUsesItUp(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 39, 11);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(20, 1, 6));
		player.moveTo(at.x, at.y, at.z, 0, 0);
		// Two stacks of the same thing: these used to vanish when the parcel unpacked into the hand's slot
		PendingDelivery delivery = new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Steve's Ranch",
			GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), "Alex's Inn",
			List.of(new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), new ItemStack(Items.IRON_INGOT, 20)), 0);
		ItemStack parcel = Mail.wrap(delivery, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, parcel);
		InteractionResultHolder<ItemStack> result = parcel.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		if (result.getResult() != InteractionResult.CONSUME)
			throw new GameTestAssertException("Opening it should be a server-side success, was " + result.getResult());
		helper.succeedWhen(() -> {
			int bread = 0, iron = 0;
			for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(8))) {
				if (item.getItem().is(Items.BREAD))
					bread += item.getItem().getCount();
				if (item.getItem().is(Items.IRON_INGOT))
					iron += item.getItem().getCount();
			}
			if (bread != 128 || iron != 20)
				throw new GameTestAssertException("On the ground: " + bread + " bread and " + iron + " iron");
			if (!result.getObject().isEmpty())
				throw new GameTestAssertException("The parcel wasn't used up");
		});
	}
}
