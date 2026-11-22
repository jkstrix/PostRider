package com.comedymonkey.postrider.content.lost;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
		return canOpen(player.getUUID(), player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
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

	/** When another block takes its place, it spills whatever it still holds. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level != null && !level.isClientSide())
			dropContents();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ValueOutput.TypedOutputList<ItemStack> items = output.list("contents", ItemStack.CODEC);
		for (ItemStack item : contents)
			items.add(item);
		output.storeNullable("sender", UUIDUtil.CODEC, sender);
		output.storeNullable("recipient", UUIDUtil.CODEC, recipient);
		output.storeNullable("killer", UUIDUtil.CODEC, killer);
		output.putString("sender_name", senderName);
		output.putString("from", fromName);
		output.putString("to", toName);
		output.putString("killer_name", killerName);
		output.putLong("day", day);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		contents.clear();
		for (ItemStack item : input.listOrEmpty("contents", ItemStack.CODEC))
			contents.add(item);
		sender = input.read("sender", UUIDUtil.CODEC).orElse(null);
		recipient = input.read("recipient", UUIDUtil.CODEC).orElse(null);
		killer = input.read("killer", UUIDUtil.CODEC).orElse(null);
		senderName = input.getStringOr("sender_name", "");
		fromName = input.getStringOr("from", "");
		toName = input.getStringOr("to", "");
		killerName = input.getStringOr("killer_name", "");
		day = input.getLongOr("day", 0);
	}
}
