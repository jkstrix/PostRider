package com.comedymonkey.postrider.fabric;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.fabric.client.FabricClientNetworking;
import com.comedymonkey.postrider.platform.Platform;
import com.mojang.serialization.Codec;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The mod's platform on Fabric (found through META-INF/services). */
public class FabricPlatform implements Platform {
	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public Path gameDirectory() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		// Client only: the client networking class is only loaded when this is called
		FabricClientNetworking.sendToServer(payload);
	}

	/**
	 * Opens the menu with the extra data written for the client's copy of it. Fabric's extended menus carry typed data;
	 * here it's the bytes the mod wrote, read back by {@link #menuType}'s factory as the same buffer NeoForge hands it.
	 */
	@Override
	public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<RegistryFriendlyByteBuf> data) {
		player.openMenu(new ExtendedMenuProvider<byte[]>() {
			@Override
			public byte[] getScreenOpeningData(ServerPlayer opener) {
				RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), opener.registryAccess());
				try {
					data.accept(buf);
					byte[] bytes = new byte[buf.readableBytes()];
					buf.readBytes(bytes);
					return bytes;
				} finally {
					buf.release();
				}
			}

			@Override
			public Component getDisplayName() {
				return menu.getDisplayName();
			}

			@Override
			@Nullable
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
				return menu.createMenu(containerId, inventory, opener);
			}
		});
	}

	@Override
	public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
		return new ExtendedMenuType<T, byte[]>((containerId, inventory, bytes) -> factory.create(containerId, inventory,
			new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), inventory.player.registryAccess())), ByteBufCodecs.BYTE_ARRAY);
	}

	/** Fabric has no spawn event of its own here, so the mob's own finishing (NeoForge's FinalizeSpawnEvent then this). */
	@Override
	@Nullable
	public SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData data) {
		return mob.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	public void forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		FabricRiderTickets.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
	}

	/** Fabric's tab builder: the tab goes after the vanilla ones, on the pages Fabric adds for modded tabs. */
	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return FabricCreativeModeTab.builder();
	}

	/**
	 * Saved data with no data fixer, as on NeoForge: vanilla can't read a file without one, and Fabric API (as NeoForge
	 * does) reads it as it was saved.
	 */
	@Override
	public <T extends SavedData> SavedDataType<T> savedDataType(Identifier id, Supplier<T> constructor, Codec<T> codec) {
		return new SavedDataType<>(id, constructor, codec, null);
	}
}
