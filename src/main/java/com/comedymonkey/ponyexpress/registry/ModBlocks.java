package com.comedymonkey.ponyexpress.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.content.lost.LostParcelBlock;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxBlock;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PonyExpress.MOD_ID);

	private static final Map<DyeColor, DeferredBlock<MailboxBlock>> MAILBOXES = new EnumMap<>(DyeColor.class);

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

	public static final DeferredBlock<LostParcelBlock> LOST_PARCEL = BLOCKS.register("lost_parcel", () -> new LostParcelBlock(
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.strength(1.0F, 1200.0F)
			.sound(SoundType.WOOL)
			.noOcclusion()
			.noLootTable()
			.pushReaction(PushReaction.BLOCK)));

	private ModBlocks() {}

	public static DeferredBlock<MailboxBlock> mailbox(DyeColor color) {
		return MAILBOXES.get(color);
	}

	public static Collection<DeferredBlock<MailboxBlock>> mailboxes() {
		return Collections.unmodifiableCollection(MAILBOXES.values());
	}
}
