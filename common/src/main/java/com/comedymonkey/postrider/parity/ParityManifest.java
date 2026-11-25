package com.comedymonkey.postrider.parity;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.comedymonkey.postrider.PostRider;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Writes down everything the mod has put into the game, as this mod loader registered it, so the loaders' builds can be
 * compared (loader-parity-check). Only when the game is started with -Dparity.manifest=&lt;file&gt;; never in play.
 * <p>
 * It's plain Minecraft code, the same on every loader, so a difference in what it writes is a difference in what the
 * loader registered. The payloads and config keys are recorded by each loader's own registration, as it registers them.
 */
public final class ParityManifest {
	public static final String PROPERTY = "parity.manifest";

	/** The mod's own root commands, written out in full. */
	private static final List<String> COMMAND_ROOTS = List.of("postrider");
	/** Root commands a mod loader adds itself, left out. */
	private static final List<String> LOADER_COMMANDS = List.of("neoforge", "forge", "fabric");

	private static final Map<String, String> PAYLOADS = new TreeMap<>();
	private static final Map<String, String> CONFIG = new TreeMap<>();

	private ParityManifest() {}

	/** Called by each loader's payload registration for every payload it registers. */
	public static void recordPayload(ResourceLocation id, String direction) {
		PAYLOADS.put(id.toString(), direction);
	}

	/** Called by each loader's config for every setting it builds. */
	public static void recordConfig(String key, Object defaultValue, Object min, Object max) {
		CONFIG.put(key, defaultValue + " in " + min + ".." + max);
	}

	/** Once the server has started: writes the manifest if the game was started for it. */
	public static void dumpIfRequested(MinecraftServer server) {
		String out = System.getProperty(PROPERTY);
		if (out == null || out.isBlank())
			return;
		Path file = Path.of(out).toAbsolutePath();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(build(server), writer);
			}
			PostRider.LOGGER.info("Post Rider parity manifest written to {}", file);
		} catch (IOException | RuntimeException e) {
			PostRider.LOGGER.error("Post Rider parity manifest could not be written to {}", file, e);
		}
	}

	static JsonObject build(MinecraftServer server) {
		RegistryAccess access = server.registryAccess();
		DynamicOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
		JsonObject root = new JsonObject();
		root.add("registries", registries(access));
		root.add("items", items(ops));
		root.add("blocks", blocks());
		root.add("entities", entities());
		root.add("recipes", recipes(server, ops));
		root.add("loot_tables", ids(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream()
			.filter(ParityManifest::ours).toList()));
		root.add("advancements", ids(server.getAdvancements().getAllAdvancements().stream().map(holder -> holder.id())
			.filter(ParityManifest::ours).toList()));
		root.add("tags", tags(access));
		root.add("commands", commands(server));
		root.add("payloads", map(PAYLOADS));
		root.add("config", map(CONFIG));
		return root;
	}

	private static boolean ours(ResourceLocation id) {
		return id.getNamespace().equals(PostRider.MOD_ID);
	}

	private static JsonObject registries(RegistryAccess access) {
		JsonObject registries = new JsonObject();
		access.registries().forEach(entry -> {
			List<ResourceLocation> ids = entry.value().keySet().stream().filter(ParityManifest::ours).toList();
			if (!ids.isEmpty())
				registries.add(entry.key().location().toString(), ids(ids));
		});
		return registries;
	}

	private static JsonObject items(DynamicOps<JsonElement> ops) {
		JsonObject items = new JsonObject();
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (!ours(id))
				continue;
			JsonObject entry = new JsonObject();
			entry.addProperty("class", item.getClass().getName());
			entry.addProperty("description", item.getDescriptionId());
			entry.add("components", encode(DataComponentMap.CODEC, item.components(), ops));
			items.add(id.toString(), entry);
		}
		return items;
	}

	private static JsonObject blocks() {
		JsonObject blocks = new JsonObject();
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (!ours(id))
				continue;
			BlockState state = block.defaultBlockState();
			JsonObject entry = new JsonObject();
			entry.addProperty("class", block.getClass().getName());
			entry.addProperty("description", block.getDescriptionId());
			entry.addProperty("destroy_time", block.defaultDestroyTime());
			entry.addProperty("explosion_resistance", block.getExplosionResistance());
			entry.addProperty("friction", block.getFriction());
			entry.addProperty("speed_factor", block.getSpeedFactor());
			entry.addProperty("jump_factor", block.getJumpFactor());
			entry.addProperty("default_state", state.toString());
			entry.addProperty("light", state.getLightEmission());
			entry.addProperty("block_entity", state.hasBlockEntity());
			entry.addProperty("sound", String.valueOf(BuiltInRegistries.SOUND_EVENT.getKey(state.getSoundType().getBreakSound())));
			JsonObject properties = new JsonObject();
			for (Property<?> property : block.getStateDefinition().getProperties())
				properties.add(property.getName(), strings(property.getPossibleValues().stream().map(Object::toString).toList()));
			entry.add("properties", properties);
			entry.addProperty("states", block.getStateDefinition().getPossibleStates().size());
			blocks.add(id.toString(), entry);
		}
		return blocks;
	}

	@SuppressWarnings("unchecked")
	private static JsonObject entities() {
		JsonObject entities = new JsonObject();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (!ours(id))
				continue;
			JsonObject entry = new JsonObject();
			EntityDimensions dimensions = type.getDimensions();
			entry.addProperty("category", type.getCategory().getName());
			entry.addProperty("width", dimensions.width());
			entry.addProperty("height", dimensions.height());
			entry.addProperty("eye_height", dimensions.eyeHeight());
			entry.addProperty("tracking_range", type.clientTrackingRange());
			entry.addProperty("update_interval", type.updateInterval());
			entry.addProperty("fire_immune", type.fireImmune());
			entry.addProperty("summonable", type.canSummon());
			if (DefaultAttributes.hasSupplier(type)) {
				AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
				JsonObject attributes = new JsonObject();
				BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
					if (supplier.hasAttribute(holder))
						attributes.addProperty(holder.key().location().toString(), supplier.getBaseValue(holder));
				});
				entry.add("attributes", attributes);
			}
			entities.add(id.toString(), entry);
		}
		return entities;
	}

	private static JsonObject recipes(MinecraftServer server, DynamicOps<JsonElement> ops) {
		JsonObject recipes = new JsonObject();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			ResourceLocation id = holder.id();
			if (ours(id))
				recipes.add(id.toString(), ParityManifest.<Recipe<?>>encode(Recipe.CODEC, holder.value(), ops));
		}
		return recipes;
	}

	private static JsonObject tags(RegistryAccess access) {
		JsonObject tags = new JsonObject();
		access.registries().forEach(entry -> addTags(tags, entry.key().location(), entry.value()));
		return tags;
	}

	private static <T> void addTags(JsonObject tags, ResourceLocation registry, Registry<T> values) {
		values.getTags().forEach(pair -> {
			HolderSet.Named<T> tag = pair.getSecond();
			List<String> members = tag.stream().map(Holder::unwrapKey).flatMap(Optional::stream)
				.map(key -> key.location().toString()).sorted().toList();
			ResourceLocation name = tag.key().location();
			if (ours(name) || members.stream().anyMatch(member -> member.startsWith(PostRider.MOD_ID + ":")))
				tags.add(registry + " #" + name, strings(members));
		});
	}

	private static JsonObject commands(MinecraftServer server) {
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		JsonObject commands = new JsonObject();
		List<String> roots = new ArrayList<>();
		for (CommandNode<CommandSourceStack> node : dispatcher.getRoot().getChildren())
			if (LOADER_COMMANDS.stream().noneMatch(node.getName()::startsWith))
				roots.add(node.getName());
		commands.add("roots", strings(roots.stream().sorted().toList()));
		for (String name : COMMAND_ROOTS) {
			CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(name);
			if (node != null)
				commands.add(name, strings(List.of(dispatcher.getAllUsage(node, server.createCommandSourceStack(), false))));
		}
		return commands;
	}

	private static <T> JsonElement encode(Codec<T> codec, T value, DynamicOps<JsonElement> ops) {
		return codec.encodeStart(ops, value).result().orElseGet(() -> new JsonPrimitive("<unencodable>"));
	}

	private static JsonArray ids(List<ResourceLocation> ids) {
		return strings(ids.stream().map(ResourceLocation::toString).sorted().toList());
	}

	private static JsonArray strings(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}

	private static JsonObject map(Map<String, String> values) {
		JsonObject object = new JsonObject();
		values.forEach(object::addProperty);
		return object;
	}
}
