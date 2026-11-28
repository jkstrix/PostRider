package com.comedymonkey.postrider.testutil;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Since 26.1 much of what a block or item is comes with a world's data rather than with the game: whether a block
 * stops a rider is its vanilla tag (as the game's heightmaps read it), and an item's default components (its stack
 * size, its food, the mod's own ones...) are bound to it as the registries load, so no ItemStack can be made before.
 * Unit tests load no world, so before the first test class that asks for it runs, this loads vanilla's data pack the
 * way a server does (WorldLoader): the tags of the game's own registries, the world's data-driven registries with
 * theirs, and then the item components, bound against all of that. A test class that makes items or reads tags asks
 * for it with {@code @ExtendWith(LoadedGameData.class)}. (JUnit can't pick it up for every class by itself: its
 * service lookup runs outside the game's class loader.) It uses vanilla's RegistryDataLoader.load, so it works on every
 * loader (NeoForge's own overload only adds load conditions, which vanilla's data has none of).
 */
public final class LoadedGameData implements BeforeAllCallback {
	private static boolean loaded;

	@Override
	public void beforeAll(ExtensionContext context) {
		load();
	}

	public static synchronized void load() {
		if (loaded)
			return;
		ResourceManager vanilla = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
		LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
		List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(vanilla, layers.getLayer(RegistryLayer.STATIC));
		List<HolderLookup.RegistryLookup<?>> context = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLD), staticTags);
		RegistryAccess.Frozen world = RegistryDataLoader.load(vanilla, context, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
		List<Registry.PendingTags<?>> worldTags = TagLoader.loadTagsForExistingRegistries(vanilla, world);
		HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.concat(context.stream(), TagLoader.buildUpdatedLookups(world, worldTags).stream()));
		staticTags.forEach(Registry.PendingTags::apply);
		worldTags.forEach(Registry.PendingTags::apply);
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(DataComponentInitializers.PendingComponents::apply);
		loaded = true;
	}
}
