package com.comedymonkey.postrider.gametest;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Builds the little worlds the in-world tests ride through, out of blocks, in test coordinates (the ground is at
 * y 0, so a rider stands at y 1): grass, water, mailboxes with addresses, and mail posted between them. Test-only.
 */
final class Scene {
	final GameTestHelper helper;
	final ServerLevel level;

	Scene(GameTestHelper helper) {
		this.helper = helper;
		this.level = helper.getLevel();
		// Riders camp from dusk: the tests keep it morning
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
		level.setDayTime(1000);
	}

	void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = x0; x <= x1; x++)
			for (int y = y0; y <= y1; y++)
				for (int z = z0; z <= z1; z++)
					helper.setBlock(new BlockPos(x, y, z), state);
	}

	/** Grass to stand on, at y 0. */
	void ground(int x0, int z0, int x1, int z1) {
		fill(x0, 0, z0, x1, 0, z1, Blocks.GRASS_BLOCK.defaultBlockState());
	}

	/** Still water this many blocks deep, its surface level with the grass, on a stone bed. */
	void water(int x0, int z0, int x1, int z1, int depth) {
		fill(x0, 1 - depth, z0, x1, 0, z1, Blocks.WATER.defaultBlockState());
		fill(x0, -depth, z0, x1, -depth, z1, Blocks.STONE.defaultBlockState());
	}

	/** Where the water's surface is, in world height. */
	double surface() {
		return helper.absolutePos(BlockPos.ZERO).getY() + 8 / 9.0;
	}

	BlockPos at(int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	Vec3 at(double x, double y, double z) {
		return helper.absoluteVec(new Vec3(x, y, z));
	}

	/** A mailbox on the ground, its door facing this way, with an address no other test will use. Its world position. */
	BlockPos mailbox(int x, int z, Direction facing, String address) {
		return mailbox(x, 1, z, facing, address);
	}

	/** A mailbox standing at this height (its lower half at y; the ground is at y - 1). */
	BlockPos mailbox(int x, int y, int z, Direction facing, String address) {
		BlockState lower = ModBlocks.mailbox(DyeColor.RED).get().defaultBlockState().setValue(MailboxBlock.FACING, facing);
		helper.setBlock(new BlockPos(x, y, z), lower);
		helper.setBlock(new BlockPos(x, y + 1, z), lower.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER));
		BlockPos pos = at(x, y, z);
		if (!(level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox))
			throw new GameTestAssertException("No mailbox at " + pos);
		MailboxDirectory.Result named = mailbox.rename(address, helper.makeMockPlayer(GameType.SURVIVAL));
		if (named != MailboxDirectory.Result.OK)
			throw new GameTestAssertException("Couldn't name the mailbox " + address + ": " + named);
		return pos;
	}

	/** An address for this run only (addresses are unique across the world, which outlives a run). */
	static String address(String what) {
		return what + " " + (1000 + new java.util.Random().nextInt(9000));
	}

	/** Posts a parcel from one mailbox to another, as the Send button does; a rider collects it at the next round. */
	PendingDelivery post(BlockPos from, String fromName, String toName) {
		PendingDelivery delivery = new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Tester", fromName,
			GlobalPos.of(level.dimension(), from), toName, List.of(new ItemStack(Items.BREAD, 3)), level.getGameTime());
		PostOffice.get(level.getServer()).enqueue(delivery);
		return delivery;
	}

	/** The rider carrying this delivery, while he's out with it. */
	@Nullable
	PostmanEntity rider(PendingDelivery delivery) {
		for (PostOffice.Active active : PostOffice.get(level.getServer()).active())
			if (active.delivery().id().equals(delivery.id()))
				return level.getEntity(active.rider()) instanceof PostmanEntity rider ? rider : null;
		return null;
	}

	/** Whether the post office still has this delivery out with a rider (he hasn't finished, and vanished, yet). */
	boolean stillOut(PendingDelivery delivery) {
		return PostOffice.get(level.getServer()).isActive(delivery.id());
	}

		boolean delivered(BlockPos mailbox) {
		return level.getBlockEntity(mailbox) instanceof MailboxBlockEntity box && !box.isInboxEmpty();
	}
}
