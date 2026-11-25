package com.comedymonkey.postrider.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.lost.LostParcelBlock;
import com.comedymonkey.postrider.content.lost.LostParcelWreckBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.postman.CampfireBlock;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

public final class ModBlocks {
	public static final Registrar<Block> BLOCKS = Registrar.create(Registries.BLOCK);

	private static final Map<DyeColor, RegistryEntry<Block, MailboxBlock>> MAILBOXES = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values()) {
			MAILBOXES.put(color, BLOCKS.register(color.getSerializedName() + "_mailbox", () -> new MailboxBlock(color,
				BlockBehaviour.Properties.of()
					.mapColor(color.getMapColor())
					.strength(2.5F, 1200.0F)
					.sound(SoundType.LANTERN)
					.noOcclusion()
					.pushReaction(PushReaction.BLOCK))));
		}
	}

	public static final RegistryEntry<Block, LostParcelBlock> LOST_PARCEL = BLOCKS.registerBlock("lost_parcel", LostParcelBlock::new,
		properties -> properties
			.mapColor(MapColor.WOOD)
			.strength(1.0F, 1200.0F)
			.sound(SoundType.WOOL)
			.noOcclusion()
			.noLootTable()
			.pushReaction(PushReaction.BLOCK));
	public static final RegistryEntry<Block, LostParcelWreckBlock> LOST_PARCEL_WRECK = BLOCKS.registerBlock("lost_parcel_wreck", LostParcelWreckBlock::new,
		properties -> properties
			.mapColor(MapColor.WOOD)
			.strength(1.0F, 1200.0F)
			.sound(SoundType.WET_GRASS)
			.noOcclusion()
			.noLootTable()
			.pushReaction(PushReaction.BLOCK));

	public static final RegistryEntry<Block, CampfireBlock> CAMPFIRE = BLOCKS.registerBlock("campfire", CampfireBlock::new,
		properties -> properties
			.mapColor(MapColor.PODZOL)
			.strength(-1.0F, 3600000.0F)
			.sound(SoundType.WOOD)
			.lightLevel(state -> state.getValue(CampfireBlock.LIGHT))
			.noOcclusion()
			.noLootTable()
			.isValidSpawn((state, level, pos, type) -> false)
			.pushReaction(PushReaction.BLOCK));

	private ModBlocks() {}

	public static RegistryEntry<Block, MailboxBlock> mailbox(DyeColor color) {
		return MAILBOXES.get(color);
	}

	public static Collection<RegistryEntry<Block, MailboxBlock>> mailboxes() {
		return Collections.unmodifiableCollection(MAILBOXES.values());
	}
}
