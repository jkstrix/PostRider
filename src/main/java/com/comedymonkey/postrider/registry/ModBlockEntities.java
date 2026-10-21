package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.lost.LostParcelBlockEntity;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PostRider.MOD_ID);

	@SuppressWarnings("DataFlowIssue")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MailboxBlockEntity>> MAILBOX =
		BLOCK_ENTITIES.register("mailbox", () -> BlockEntityType.Builder.of(MailboxBlockEntity::new,
			ModBlocks.mailboxes().stream().map(b -> (Block) b.get()).toArray(Block[]::new)).build(null));

	@SuppressWarnings("DataFlowIssue")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LostParcelBlockEntity>> LOST_PARCEL =
		BLOCK_ENTITIES.register("lost_parcel", () -> BlockEntityType.Builder.of(LostParcelBlockEntity::new, ModBlocks.LOST_PARCEL.get(), ModBlocks.LOST_PARCEL_WRECK.get()).build(null));

	private ModBlockEntities() {}
}
