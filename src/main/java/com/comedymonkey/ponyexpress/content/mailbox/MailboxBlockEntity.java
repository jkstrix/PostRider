package com.comedymonkey.ponyexpress.content.mailbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.ponyexpress.network.MailboxInfoPayload;
import com.comedymonkey.ponyexpress.post.MailboxDirectory;
import com.comedymonkey.ponyexpress.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/** A mailbox's address, owner, inbox of up to 27 parcels and the 9 slots of the parcel being packed. */
public class MailboxBlockEntity extends BlockEntity implements MenuProvider {
	public static final int INBOX_SIZE = 27;
	public static final int PARCEL_SIZE = 9;

	private final ItemStackHandler inbox = new ItemStackHandler(INBOX_SIZE) {
		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
			updateFlag();
		}
	};
	private final ItemStackHandler parcel = new ItemStackHandler(PARCEL_SIZE) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	@Nullable
	private UUID owner;
	private String ownerName = "";
	private String name = "";

	public MailboxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MAILBOX.get(), pos, state);
	}

	// ---------------------------------------------------------------- owner and address

	public void setOwner(Player player) {
		owner = player.getUUID();
		ownerName = player.getGameProfile().getName();
		syncToClients();
	}

	/** Mailboxes placed without a player (by command, say) belong to whoever opens them first. */
	public boolean isOwner(Player player) {
		return owner == null || owner.equals(player.getUUID());
	}

	@Nullable
	public UUID getOwner() {
		return owner;
	}

	public String getOwnerName() {
		return ownerName;
	}

	/** The address, or "" until the owner names it. */
	public String getName() {
		return name;
	}

	public boolean hasName() {
		return !name.isEmpty();
	}

	/** Tries to take the name in the server's directory; on success this mailbox answers to it. */
	public MailboxDirectory.Result rename(String newName, Player player) {
		if (level == null || level.getServer() == null)
			return MailboxDirectory.Result.INVALID;
		if (owner == null)
			setOwner(player);
		MailboxDirectory directory = MailboxDirectory.get(level.getServer());
		MailboxDirectory.Result result = directory.register(newName, GlobalPos.of(level.dimension(), worldPosition), owner);
		if (result == MailboxDirectory.Result.OK) {
			MailboxDirectory.Entry entry = directory.at(GlobalPos.of(level.dimension(), worldPosition));
			name = entry != null ? entry.name() : newName;
			setChanged();
			syncToClients();
		}
		return result;
	}

	public void openFor(ServerPlayer player) {
		player.openMenu(this, buf -> {
			buf.writeBlockPos(worldPosition);
			buf.writeUtf(name);
		});
		sendInfo(player);
	}

	/** Tells the player's screen this mailbox's address and the other addresses it can send to. */
	public void sendInfo(ServerPlayer player) {
		if (level == null || level.getServer() == null)
			return;
		List<String> addresses = new ArrayList<>(MailboxDirectory.get(level.getServer()).namesIn(level.dimension()));
		addresses.removeIf(n -> n.equalsIgnoreCase(name));
		PacketDistributor.sendToPlayer(player, new MailboxInfoPayload(worldPosition, name, ownerName, addresses));
	}

	// ---------------------------------------------------------------- inbox and parcel

	public ItemStackHandler getInbox() {
		return inbox;
	}

	public ItemStackHandler getParcel() {
		return parcel;
	}

	public boolean isInboxEmpty() {
		for (int i = 0; i < inbox.getSlots(); i++)
			if (!inbox.getStackInSlot(i).isEmpty())
				return false;
		return true;
	}

	public boolean isInboxFull() {
		for (int i = 0; i < inbox.getSlots(); i++)
			if (inbox.getStackInSlot(i).isEmpty())
				return false;
		return true;
	}

	/** Puts a parcel or letter in the inbox. False if all 27 places are taken. */
	public boolean deliver(ItemStack mail) {
		for (int i = 0; i < inbox.getSlots(); i++) {
			if (inbox.getStackInSlot(i).isEmpty()) {
				inbox.setStackInSlot(i, mail.copyWithCount(1));
				return true;
			}
		}
		return false;
	}

	/** Empties the parcel slots, returning what was in them. */
	public List<ItemStack> takeParcelContents() {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < parcel.getSlots(); i++) {
			ItemStack stack = parcel.getStackInSlot(i);
			if (!stack.isEmpty()) {
				items.add(stack.copy());
				parcel.setStackInSlot(i, ItemStack.EMPTY);
			}
		}
		return items;
	}

	public boolean isParcelEmpty() {
		for (int i = 0; i < parcel.getSlots(); i++)
			if (!parcel.getStackInSlot(i).isEmpty())
				return false;
		return true;
	}

	void dropContents() {
		if (level == null)
			return;
		for (ItemStackHandler handler : List.of(inbox, parcel)) {
			for (int i = 0; i < handler.getSlots(); i++) {
				Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), handler.getStackInSlot(i));
				handler.setStackInSlot(i, ItemStack.EMPTY);
			}
		}
	}

	private void updateFlag() {
		if (level == null || level.isClientSide)
			return;
		boolean hasMail = !isInboxEmpty();
		// The flag is drawn by the upper half, but both halves carry it so they stay alike
		for (BlockPos pos : List.of(worldPosition, worldPosition.above())) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof MailboxBlock && state.getValue(MailboxBlock.FLAG) != hasMail)
				level.setBlock(pos, state.setValue(MailboxBlock.FLAG, hasMail), Block.UPDATE_ALL);
		}
	}

	// ---------------------------------------------------------------- menu

	@Override
	public Component getDisplayName() {
		return hasName() ? Component.literal(name) : Component.translatable("container.ponyexpress.mailbox");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new MailboxMenu(containerId, inventory, this);
	}

	// ---------------------------------------------------------------- saving and syncing

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		saveIdentity(tag);
		tag.put("inbox", inbox.serializeNBT(registries));
		tag.put("parcel", parcel.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		ownerName = tag.getString("owner_name");
		name = tag.getString("name");
		if (tag.contains("inbox"))
			inbox.deserializeNBT(registries, tag.getCompound("inbox"));
		if (tag.contains("parcel"))
			parcel.deserializeNBT(registries, tag.getCompound("parcel"));
	}

	private void saveIdentity(CompoundTag tag) {
		if (owner != null)
			tag.putUUID("owner", owner);
		tag.putString("owner_name", ownerName);
		tag.putString("name", name);
	}

	/** Clients only need to know who owns it (so non-owners can't crack it) and its address. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		saveIdentity(tag);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private void syncToClients() {
		setChanged();
		if (level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}
}
