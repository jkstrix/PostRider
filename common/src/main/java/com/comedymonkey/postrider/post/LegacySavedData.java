package com.comedymonkey.postrider.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Finds the post's saved data on the server. Worlds from before Minecraft 26.1 kept it loose in the world's data
 * folder ({@code data/postrider_mailboxes.dat}); now each file sits under its namespace
 * ({@code data/postrider/mailboxes.dat}). The first time an older world is opened, the old file is read and the data
 * is saved again in the new place.
 */
final class LegacySavedData {
	private LegacySavedData() {}

	static <T extends SavedData> T get(MinecraftServer server, SavedDataType<T> type, String legacyName) {
		SavedDataStorage storage = server.getDataStorage();
		T data = storage.get(type);
		if (data != null)
			return data;
		Path legacy = server.getWorldPath(LevelResource.DATA).resolve(legacyName + ".dat");
		if (Files.exists(legacy)) {
			try {
				CompoundTag tag = storage.readTagFromDisk(legacy, null, SharedConstants.getCurrentVersion().dataVersion().version());
				RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
				Optional<T> loaded = type.codec().parse(ops, tag.getCompoundOrEmpty("data"))
					.resultOrPartial(error -> PostRider.LOGGER.error("Couldn't read the old {}: {}", legacy, error));
				if (loaded.isPresent()) {
					storage.set(type, loaded.get());
					return loaded.get();
				}
			} catch (IOException e) {
				PostRider.LOGGER.error("Couldn't read the old {}", legacy, e);
			}
		}
		return storage.computeIfAbsent(type);
	}
}
