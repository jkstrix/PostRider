package com.comedymonkey.postrider.forge;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.platform.Platform;
import com.mojang.serialization.Codec;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

/** The mod's platform on Forge (found through META-INF/services). */
public class ForgePlatform implements Platform {
	@Override
	public boolean isModLoaded(String modId) {
		return ModList.isLoaded(modId);
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

	/** Forge's own spawn event and then the mob's own finishing, as NeoForge's EventHooks.finalizeMobSpawn. */
	@Override
	@Nullable
	public SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData data) {
		return ForgeEventFactory.onFinalizeSpawn(mob, level, difficulty, reason, data);
	}

	@Override
	public void forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		ForgeRiderTickets.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
	}

	/** Forge's tab builder (its patch to vanilla's): the tab goes after the vanilla ones, as NeoForge's does. */
	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	/**
	 * Saved data with no data fixer, as on NeoForge: vanilla can't read a file without one, and Forge's patch (as
	 * NeoForge's does) reads it as it was saved.
	 */
	@Override
	public <T extends SavedData> SavedDataType<T> savedDataType(Identifier id, Supplier<T> constructor, Codec<T> codec) {
		return new SavedDataType<>(id, constructor, codec, null);
	}
}
