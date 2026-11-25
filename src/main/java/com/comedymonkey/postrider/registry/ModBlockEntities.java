package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.lost.LostParcelBlockEntity;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final Registrar<BlockEntityType<?>> BLOCK_ENTITIES =
		Registrar.create(Registries.BLOCK_ENTITY_TYPE);

	public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<MailboxBlockEntity>> MAILBOX =
		BLOCK_ENTITIES.register("mailbox", () -> new BlockEntityType<>(MailboxBlockEntity::new,
			ModBlocks.mailboxes().stream().map(b -> (Block) b.get()).toArray(Block[]::new)));

	public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<LostParcelBlockEntity>> LOST_PARCEL =
		BLOCK_ENTITIES.register("lost_parcel", () -> new BlockEntityType<>(LostParcelBlockEntity::new, ModBlocks.LOST_PARCEL.get(), ModBlocks.LOST_PARCEL_WRECK.get()));

	private ModBlockEntities() {}
}
