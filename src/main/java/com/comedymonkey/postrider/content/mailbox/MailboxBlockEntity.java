package com.comedymonkey.postrider.content.mailbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.advancement.PostEvents;
import com.comedymonkey.postrider.network.MailboxInfoPayload;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

/** A mailbox's address, owner, inbox of up to 27 parcels and the 9 slots of the parcel being packed. */
public class MailboxBlockEntity extends BlockEntity implements MenuProvider {
	public static final int INBOX_SIZE = 27;
	public static final int PARCEL_SIZE = 9;

	private final MailboxSlots inbox = new MailboxSlots(INBOX_SIZE, 1, () -> {
		setChanged();
		updateFlag();
	});
	private final MailboxSlots parcel = new MailboxSlots(PARCEL_SIZE, Item.ABSOLUTE_MAX_STACK_SIZE, this::setChanged);

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
		ownerName = player.getGameProfile().name();
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
	/** Makes this somebody's box with this name, with no player at hand (a box put down by a headless test run). */
	public MailboxDirectory.Result claim(String newName, UUID newOwner) {
		if (level == null || level.getServer() == null)
			return MailboxDirectory.Result.INVALID;
		owner = newOwner;
		MailboxDirectory directory = MailboxDirectory.get(level.getServer());
		MailboxDirectory.Result result = directory.register(newName, GlobalPos.of(level.dimension(), worldPosition), owner);
		if (result == MailboxDirectory.Result.OK) {
			MailboxDirectory.Entry entry = directory.at(GlobalPos.of(level.dimension(), worldPosition));
			name = entry != null ? entry.name() : newName;
		}
		setChanged();
		syncToClients();
		return result;
	}

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
		if (isOwner(player)) {
			List<ItemStack> mail = new ArrayList<>();
			for (int i = 0; i < inbox.getSlots(); i++)
				mail.add(inbox.getStackInSlot(i));
			PostEvents.received(player, mail);
		}
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

	public MailboxSlots getInbox() {
		return inbox;
	}

	public MailboxSlots getParcel() {
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
		for (MailboxSlots handler : List.of(inbox, parcel)) {
			for (int i = 0; i < handler.getSlots(); i++) {
				Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), handler.getStackInSlot(i));
				handler.setStackInSlot(i, ItemStack.EMPTY);
			}
		}
	}

	/** Recolouring swaps one mailbox block for another and keeps everything; anything else empties it. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		// By now the level already holds the block taking this one's place
		if (level == null || level.isClientSide() || level.getBlockState(pos).getBlock() instanceof MailboxBlock)
			return;
		dropContents();
		if (level.getServer() != null)
			MailboxDirectory.get(level.getServer()).remove(GlobalPos.of(level.dimension(), pos));
	}

	private void updateFlag() {
		if (level == null || level.isClientSide())
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
		return hasName() ? Component.literal(name) : Component.translatable("container.postrider.mailbox");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new MailboxMenu(containerId, inventory, this);
	}

	// ---------------------------------------------------------------- saving and syncing

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		saveIdentity(output);
		inbox.save(output.child("inbox"));
		parcel.save(output.child("parcel"));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("owner_name", "");
		name = input.getStringOr("name", "");
		input.child("inbox").ifPresent(inbox::load);
		input.child("parcel").ifPresent(parcel::load);
	}

	private void saveIdentity(ValueOutput output) {
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		output.putString("owner_name", ownerName);
		output.putString("name", name);
	}

	/** Clients only need to know who owns it (so non-owners can't crack it) and its address. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveIdentity(output);
		return output.buildResult();
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private void syncToClients() {
		setChanged();
		if (level != null && !level.isClientSide())
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}
}
