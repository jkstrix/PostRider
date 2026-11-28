package com.comedymonkey.postrider.forge;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.RiderChunks;
import com.comedymonkey.postrider.platform.Platform;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

/** The mod's platform on Forge (found through META-INF/services). */
public class ForgePlatform implements Platform {
	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Path gameDirectory() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		ForgeNetwork.CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		// Client only (PacketDistributor.SERVER sends on the client's connection)
		ForgeNetwork.CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
	}

	/**
	 * Opens the menu with the extra data written for the client's copy of it. Forge's menus carry their extra data as
	 * plain bytes; here they're the bytes the mod wrote, read back by {@link #menuType}'s factory as the same buffer
	 * NeoForge hands it.
	 */
	@Override
	public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<RegistryFriendlyByteBuf> data) {
		RegistryFriendlyByteBuf written = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
		try {
			data.accept(written);
			player.openMenu(menu, extra -> extra.writeBytes(written));
		} finally {
			written.release();
		}
	}

	@Override
	public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
		return IForgeMenuType.create((containerId, inventory, data) -> factory.create(containerId, inventory,
			new RegistryFriendlyByteBuf(data, inventory.player.registryAccess())));
	}

	/** Forge's spawn event and then the mob's own finishing, as NeoForge's EventHooks.finalizeMobSpawn. */
	@Override
	@Nullable
	public SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
			@Nullable SpawnGroupData data) {
		return ForgeEventFactory.onFinalizeSpawn(mob, level, difficulty, reason, data);
	}

	/**
	 * Forge's own chunk tickets, held for the rider's UUID under the mod's id: the same entity tickets NeoForge's
	 * TicketController holds (NeoForge 21.1's ForcedChunkManager grew from this), saved with the level's forced chunks
	 * and validated by {@link #validateTickets} when it loads.
	 */
	@Override
	public void forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		ForgeChunkManager.forceChunk(level, PostRider.MOD_ID, owner, chunkX, chunkZ, add, ticking);
	}

	/** Forge's tab builder (its patch to vanilla's): the tab goes after the vanilla ones, as NeoForge's does. */
	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	/** Saved data with no data fixer: Forge reads it as it was written, as NeoForge does. */
	@Override
	public <T extends SavedData> SavedData.Factory<T> savedDataFactory(Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> loader) {
		return new SavedData.Factory<>(constructor, loader, null);
	}

	/** The mod's saved tickets as a level loads, handed to RiderChunks.validate (NeoForge's ticket controller callback). */
	static void validateTickets(ServerLevel level, ForgeChunkManager.TicketHelper tickets) {
		RiderChunks.validate(level, new RiderChunks.SavedTickets() {
			@Override
			public Set<UUID> owners() {
				return tickets.getEntityTickets().keySet();
			}

			@Override
			public LongCollection loadedChunks(UUID owner) {
				// The first of the pair is the chunks held without ticking (NeoForge's TicketSet.nonTicking)
				return tickets.getEntityTickets().get(owner).getFirst();
			}

			@Override
			public void removeAll(UUID owner) {
				tickets.removeAllTickets(owner);
			}

			@Override
			public void removeLoaded(UUID owner, long chunk) {
				tickets.removeTicket(owner, chunk, false);
			}
		});
	}
}
