package com.comedymonkey.postrider.post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.comedymonkey.postrider.platform.Services;

/** Every named mailbox on the server, by address. Kept with the server's saved data. */
public class MailboxDirectory extends SavedData {
	/** The file name worlds from before Minecraft 26.1 kept it under. */
	public static final String ID = "postrider_mailboxes";

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

	public static final Codec<MailboxDirectory> CODEC = RecordCodecBuilder.create(i -> i.group(
		Entry.CODEC.listOf().optionalFieldOf("mailboxes", List.of()).forGetter(d -> List.copyOf(d.byKey.values()))
	).apply(i, MailboxDirectory::new));
	public static final SavedDataType<MailboxDirectory> TYPE =
		Services.PLATFORM.savedDataType(PostRider.asResource("mailboxes"), MailboxDirectory::new, CODEC);

	private final Map<String, Entry> byKey = new HashMap<>();

	public MailboxDirectory() {}

	private MailboxDirectory(List<Entry> entries) {
		for (Entry entry : entries)
			byKey.put(MailboxNames.key(entry.name()), entry);
	}

	public static MailboxDirectory get(MinecraftServer server) {
		return LegacySavedData.get(server, TYPE, ID);
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
}
