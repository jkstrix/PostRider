package com.comedymonkey.postrider.forge;

import java.util.Objects;
import java.util.function.Consumer;

import com.comedymonkey.postrider.PostRiderEvents;
import com.comedymonkey.postrider.client.PostRiderClientEvents;
import com.comedymonkey.postrider.gametest.GameTests;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModRegistries;
import com.comedymonkey.postrider.registry.Registrar;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;

/**
 * Starts the game for the unit tests, before the first test class runs, as NeoForge's unitTest does by loading the mod
 * in FML. Forge 66 has no way to run JUnit inside FML, so this takes the mod through the same steps FML does, in the same
 * order, on the plain classpath (where the Minecraft jar already has the access transformers applied):
 * <ol>
 * <li>the game's own bootstrap (Forge's patch to it snapshots and locks the vanilla registries);</li>
 * <li>what PostRiderForge's constructor does (the mod's init, its config, its listeners, its packets), and on the
 * client side, as NeoForge's unit tests are, what PostRiderForgeClient does that the tests can see (the client's
 * init);</li>
 * <li>Forge's registry phase: the registries unfrozen ({@code GameData.unfreezeData}), every entry of
 * {@code ModRegistries.ALL} and the test functions registered as Forge's RegisterEvent registers them (through the Forge
 * registry, opened for it and closed again, where there is one; into the vanilla registry otherwise), then the
 * entities' attributes ({@code ForgeHooks.modifyAttributes}, which fires EntityAttributeCreationEvent);</li>
 * <li>the mod's common setup;</li>
 * <li>the registries frozen, as when FML finishes loading ({@code GameData.freezeData}).</li>
 * </ol>
 * The mixins are not applied here (they need Forge's class loader); the shared tests don't reach the code they change.
 * The config isn't loaded, so its defaults apply, as in NeoForge's unit tests. Only Post Rider is set up, not Forge's own
 * mod (its fluid types and the like).
 */
public final class ForgeTestGame {
	private static boolean started;

	private ForgeTestGame() {}

	public static synchronized void start() {
		if (started)
			return;
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		// PostRiderForge's constructor
		// (Building the config spec hands its values to PostRiderConfig; unloaded, they read as the defaults)
		Objects.requireNonNull(ForgeConfig.SPEC);
		EntityAttributeCreationEvent.BUS.addListener(event -> ModEntities.registerAttributes(event::put));
		ForgeNetwork.init();
		PostRiderEvents.init();
		// PostRiderForgeClient: the rest of it only registers listeners for client loading events that never fire here
		PostRiderClientEvents.init();

		// Forge's registry phase (ForgeStatesProvider's UNFREEZE_DATA and LOAD_REGISTRIES)
		GameData.unfreezeData();
		for (Registrar<?> registrar : ModRegistries.ALL)
			register(registrar);
		registerTestFunctions();
		ForgeHooks.modifyAttributes();

		// FMLCommonSetupEvent
		PostRiderEvents.commonSetup();

		// FML has finished loading (FREEZE_DATA)
		GameData.freezeData();
		started = true;
	}

	private static <R> void register(Registrar<R> registrar) {
		registrar.registerAll(sink(registrar.registry()));
		closeForgeRegistry(registrar.registry());
	}

	private static void registerTestFunctions() {
		Registrar.Sink<Consumer<GameTestHelper>> sink = sink(Registries.TEST_FUNCTION);
		GameTests.registerFunctions((id, function) -> sink.register(ResourceKey.create(Registries.TEST_FUNCTION, id), function));
		closeForgeRegistry(Registries.TEST_FUNCTION);
	}

	/**
	 * Where RegisterEvent puts a registry's entries: into its Forge registry, opened for the purpose, if it has one, or
	 * into the vanilla registry. A Forge registry opened here is closed again by {@link #closeForgeRegistry}.
	 */
	private static <R> Registrar.Sink<R> sink(ResourceKey<? extends Registry<R>> key) {
		ForgeRegistry<R> forge = RegistryManager.ACTIVE.getRegistry(key);
		if (forge != null) {
			forge.unfreeze();
			return (entry, value) -> {
				forge.register(entry.identifier(), value);
			};
		}
		@SuppressWarnings({ "unchecked", "rawtypes" })
		Registry<R> vanilla = (Registry<R>) BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) key);
		return (entry, value) -> Registry.register(vanilla, entry, value);
	}

	private static void closeForgeRegistry(ResourceKey<? extends Registry<?>> key) {
		ForgeRegistry<?> forge = RegistryManager.ACTIVE.getRegistry(key.identifier());
		if (forge != null)
			forge.freeze();
	}
}
