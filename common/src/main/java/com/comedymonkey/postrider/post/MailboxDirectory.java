package com.comedymonkey.postrider.post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import com.comedymonkey.postrider.platform.Services;

/** Every named mailbox on the server, by address. Kept with the overworld's saved data. */
public class MailboxDirectory extends SavedData {
	public static final String ID = "postrider_mailboxes";
	private static final SavedData.Factory<MailboxDirectory> FACTORY =
		Services.PLATFORM.savedDataFactory(MailboxDirectory::new, MailboxDirectory::load);

	public record Entry(String name, GlobalPos pos, UUID owner) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Entry::name),
			GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner)
		).apply(i, Entry::new));
	}

	public enum Result {
		OK,
		TAKEN,
		INVALID
	}

	private final Map<String, Entry> byKey = new HashMap<>();

	public static MailboxDirectory get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
	}

	/**
	 * Gives the mailbox at this position the name, replacing any name it had. Fails if the name breaks the
	 * {@link MailboxNames} rules or another mailbox already uses it.
	 */
	public Result register(String rawName, GlobalPos pos, UUID owner) {
		String name = MailboxNames.clean(rawName);
		if (MailboxNames.check(name) != null)
			return Result.INVALID;
		String key = MailboxNames.key(name);
		Entry existing = byKey.get(key);
		if (existing != null && !existing.pos().equals(pos))
			return Result.TAKEN;
		byKey.values().removeIf(e -> e.pos().equals(pos));
		byKey.put(key, new Entry(name, pos, owner));
		setDirty();
		return Result.OK;
	}

	/** Frees the name of the mailbox at this position, if it had one. */
	public void remove(GlobalPos pos) {
		if (byKey.values().removeIf(e -> e.pos().equals(pos)))
			setDirty();
	}

	@Nullable
	public Entry find(String name) {
		return byKey.get(MailboxNames.key(name));
	}

	@Nullable
	public Entry at(GlobalPos pos) {
		for (Entry entry : byKey.values())
			if (entry.pos().equals(pos))
				return entry;
		return null;
	}

	/** The addresses of every mailbox in this dimension, sorted alphabetically. */
	public List<String> namesIn(ResourceKey<Level> dimension) {
		List<String> names = new ArrayList<>();
		for (Entry entry : byKey.values())
			if (entry.pos().dimension().equals(dimension))
				names.add(entry.name());
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public int size() {
		return byKey.size();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Entry entry : byKey.values())
			Entry.CODEC.encodeStart(NbtOps.INSTANCE, entry).result().ifPresent(list::add);
		tag.put("mailboxes", list);
		return tag;
	}

	public static MailboxDirectory load(CompoundTag tag, HolderLookup.Provider registries) {
		MailboxDirectory directory = new MailboxDirectory();
		for (Tag entryTag : tag.getList("mailboxes", Tag.TAG_COMPOUND))
			Entry.CODEC.parse(NbtOps.INSTANCE, entryTag).result()
				.ifPresent(e -> directory.byKey.put(MailboxNames.key(e.name()), e));
		return directory;
	}
}
