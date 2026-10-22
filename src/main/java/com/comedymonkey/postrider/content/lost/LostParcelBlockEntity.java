package com.comedymonkey.postrider.content.lost;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What a lost parcel holds, where it was going, and who may claim it. */
public class LostParcelBlockEntity extends BlockEntity {
	private final List<ItemStack> contents = new ArrayList<>();
	@Nullable
	private UUID sender;
	private String senderName = "";
	private String fromName = "";
	private String toName = "";
	@Nullable
	private UUID recipient;
	@Nullable
	private UUID killer;
	private String killerName = "";
	private long day;

	public LostParcelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LOST_PARCEL.get(), pos, state);
	}

	public void fill(List<ItemStack> items, UUID sender, String senderName, String fromName, String toName,
			@Nullable UUID recipient, @Nullable UUID killer, String killerName, long day) {
		contents.clear();
		for (ItemStack item : items)
			if (!item.isEmpty())
				contents.add(item.copy());
		this.sender = sender;
		this.senderName = senderName;
		this.fromName = fromName;
		this.toName = toName;
		this.recipient = recipient;
		this.killer = killer;
		this.killerName = killerName;
		this.day = day;
		setChanged();
	}

	/** The sender, the addressee, whoever killed the rider, or an operator. */
	public boolean canOpen(UUID player, boolean operator) {
		return operator || player.equals(sender) || player.equals(recipient) || player.equals(killer);
	}

	public boolean canOpen(Player player) {
		return canOpen(player.getUUID(), player.hasPermissions(2));
	}

	/** Takes everything out, leaving the parcel empty. */
	public List<ItemStack> takeContents() {
		List<ItemStack> taken = new ArrayList<>(contents);
		contents.clear();
		setChanged();
		return taken;
	}

	public List<ItemStack> getContents() {
		return List.copyOf(contents);
	}

	void dropContents() {
		if (level == null)
			return;
		for (ItemStack item : takeContents())
			Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5, item);
	}

	public String getSenderName() {
		return senderName;
	}

	public String getFromName() {
		return fromName;
	}

	public String getToName() {
		return toName;
	}

	/** Whether this player is the one who brought the rider down. */
	public boolean isKiller(Player player) {
		return killer != null && killer.equals(player.getUUID());
	}

	public String getKillerName() {
		return killerName;
	}

	public long getDay() {
		return day;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag items = new ListTag();
		for (ItemStack item : contents)
			items.add(item.save(registries));
		tag.put("contents", items);
		if (sender != null)
			tag.putUUID("sender", sender);
		if (recipient != null)
			tag.putUUID("recipient", recipient);
		if (killer != null)
			tag.putUUID("killer", killer);
		tag.putString("sender_name", senderName);
		tag.putString("from", fromName);
		tag.putString("to", toName);
		tag.putString("killer_name", killerName);
		tag.putLong("day", day);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		contents.clear();
		for (Tag item : tag.getList("contents", Tag.TAG_COMPOUND))
			ItemStack.parse(registries, item).ifPresent(contents::add);
		sender = tag.hasUUID("sender") ? tag.getUUID("sender") : null;
		recipient = tag.hasUUID("recipient") ? tag.getUUID("recipient") : null;
		killer = tag.hasUUID("killer") ? tag.getUUID("killer") : null;
		senderName = tag.getString("sender_name");
		fromName = tag.getString("from");
		toName = tag.getString("to");
		killerName = tag.getString("killer_name");
		day = tag.getLong("day");
	}
}
