package com.comedymonkey.postrider.content;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Blocks no explosion takes down (mailboxes, lost parcels, the campfire). An explosion that reaches one still drops what
 * it would drop, but the block stays: that's how they behaved on NeoForge, where the mod emptied the block's own
 * explosion handling and the game had already dropped the loot. (Lost parcels and the campfire have no loot; a mailbox
 * has, but its blast resistance keeps any ordinary explosion off it.)
 */
public final class ExplosionProof {
	private ExplosionProof() {}

	/** Vanilla's explosion handling, less taking the block away. */
	public static void dropWithoutBreaking(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
			BiConsumer<ItemStack, BlockPos> onHit) {
		if (state.isAir() || explosion.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK)
			return;
		boolean doDropExperienceHack = explosion.getIndirectSourceEntity() instanceof Player;
		if (!state.getBlock().dropFromExplosion(explosion))
			return;
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		LootParams.Builder params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
			.withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
			.withOptionalParameter(LootContextParams.THIS_ENTITY, explosion.getDirectSourceEntity());
		if (explosion.getBlockInteraction() == Explosion.BlockInteraction.DESTROY_WITH_DECAY)
			params.withParameter(LootContextParams.EXPLOSION_RADIUS, explosion.radius());
		state.spawnAfterBreak(level, pos, ItemStack.EMPTY, doDropExperienceHack);
		state.getDrops(params).forEach(stack -> onHit.accept(stack, pos));
	}
}
