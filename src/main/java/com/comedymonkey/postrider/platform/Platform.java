package com.comedymonkey.postrider.platform;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * What the mod needs from whichever mod loader it's running on. Each loader module provides one (found through
 * {@link Services}); the mod's own code never touches a loader directly.
 */
public interface Platform {
	/** Whether another mod is installed. */
	boolean isModLoaded(String modId);

	/** The game's own folder (where saves, logs and config live). */
	Path gameDirectory();

	/** Sends a packet to one player. The payload must be registered in {@code ModPayloads}. */
	void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

	/** Client side: sends a packet to the server. */
	void sendToServer(CustomPacketPayload payload);

	/** Opens a menu for a player, with extra data written for the client's copy of the menu. */
	void openMenu(ServerPlayer player, MenuProvider menu, Consumer<RegistryFriendlyByteBuf> data);

	/** A menu type whose client copy is made from the extra data the server sent with {@link #openMenu}. */
	<T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory);

	/**
	 * Finishes setting up a mob made in code, as a natural spawn would (equipment, variants), letting the loader's
	 * spawn hooks see it the way they see any other spawn.
	 */
	@Nullable
	SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data);

	/**
	 * Holds a chunk loaded for a rider, or lets it go, ticking or not. Tickets are saved with the world; when the world
	 * loads they're sorted out by {@code RiderChunks.validate}.
	 */
	void forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking);

	@FunctionalInterface
	interface MenuFactory<T extends AbstractContainerMenu> {
		T create(int containerId, Inventory inventory, RegistryFriendlyByteBuf data);
	}
}
