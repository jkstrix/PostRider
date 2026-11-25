package com.comedymonkey.postrider.neoforge;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.RiderChunks;
import com.comedymonkey.postrider.platform.Platform;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.longs.LongCollection;

/** The mod's platform on NeoForge (found through META-INF/services). */
public class NeoForgePlatform implements Platform {
	/** The riders' chunk tickets, saved with the world under postrider:rider. */
	static final TicketController TICKETS = new TicketController(PostRider.asResource(RiderChunks.TICKET_NAME),
		(level, tickets) -> RiderChunks.validate(level, saved(tickets)));

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
		PacketDistributor.sendToPlayer(player, payload);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		PacketDistributor.sendToServer(payload);
	}

	@Override
	public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<RegistryFriendlyByteBuf> data) {
		player.openMenu(menu, data);
	}

	@Override
	public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
		return IMenuTypeExtension.create(factory::create);
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
			@Nullable SpawnGroupData data) {
		return EventHooks.finalizeMobSpawn(mob, level, difficulty, reason, data);
	}

	@Override
	public void forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		TICKETS.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
	}

	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	@Override
	public <T extends SavedData> SavedData.Factory<T> savedDataFactory(Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> loader) {
		// NeoForge reads saved data with no data fixer as it was written
		return new SavedData.Factory<>(constructor, loader, null);
	}

	private static RiderChunks.SavedTickets saved(TicketHelper tickets) {
		return new RiderChunks.SavedTickets() {
			@Override
			public Set<UUID> owners() {
				return tickets.getEntityTickets().keySet();
			}

			@Override
			public LongCollection loadedChunks(UUID owner) {
				return tickets.getEntityTickets().get(owner).nonTicking();
			}

			@Override
			public void removeAll(UUID owner) {
				tickets.removeAllTickets(owner);
			}

			@Override
			public void removeLoaded(UUID owner, long chunk) {
				tickets.removeTicket(owner, chunk, false);
			}
		};
	}
}
