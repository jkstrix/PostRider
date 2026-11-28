package com.comedymonkey.postrider.forge;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

import com.comedymonkey.postrider.PostRiderEvents;
import com.comedymonkey.postrider.client.PostRiderClientEvents;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModRegistries;
import com.comedymonkey.postrider.registry.Registrar;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;

/**
 * Starts the game for the unit tests, before the first test class runs, as NeoForge's unitTest does by loading the mod
 * in FML. Forge 52 has no way to run JUnit inside FML, so this takes the mod through the same steps FML does, in the same
 * order, on the plain classpath (where the Minecraft jar already has the access transformers applied):
 * <ol>
 * <li>the game's own bootstrap (Forge's patch to it snapshots and locks the vanilla registries);</li>
 * <li>what PostRiderForge's constructor does that the tests can see (the mod's init, its config, its packets), and on
 * the client side, as NeoForge's unit tests are, what PostRiderForgeClient does (the client's init);</li>
 * <li>Forge's registry phase: the registries unfrozen ({@code GameData.unfreezeData}), every entry of
 * {@code ModRegistries.ALL} registered as Forge's RegisterEvent registers them (through the Forge registry, opened for
 * it and closed again, where there is one; into the vanilla registry otherwise), then the entities' attributes, as
 * {@code ForgeHooks.modifyAttributes} gathers them through EntityAttributeCreationEvent;</li>
 * <li>the mod's common setup;</li>
 * <li>the registries frozen, as when FML finishes loading ({@code GameData.freezeData}, which also rebuilds each block
 * state's cached properties, as in the game).</li>
 * </ol>
 * Forge 52's mod bus events go to the mods FML has loaded, and here there are none, so the mod's listeners aren't
 * registered: each step calls what its listener does. The chunk ticket callback isn't set (no level loads in the unit
 * tests). The mixins are not applied (they need Forge's class loader); the shared tests don't reach the code they
 * change. The config isn't loaded, so its defaults apply, as in NeoForge's unit tests. Only Post Rider is set up, not
 * Forge's own mod (its fluid types and the like).
 */
public final class ForgeTestGame {
	private static boolean started;

	private ForgeTestGame() {}

	public static synchronized void start() throws ReflectiveOperationException {
		if (started)
			return;
		SharedConstants.tryDetectVersion();
		// Forge 52 fires an event for every item stack made (ForgeHooks.gatherItemComponents), and its mod loader wants
		// the list of mods FML found, which no one has made outside a real launch: an empty one will do
		Field modList = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("loadingModList");
		modList.setAccessible(true);
		if (modList.get(null) == null) {
			var none = net.minecraftforge.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), null);
			none.setBrokenFiles(java.util.List.of());
			modList.set(null, none);
		}
		// It also asks for the game's module layer, which on the plain classpath is the boot layer
		Field layers = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("moduleLayerManager");
		layers.setAccessible(true);
		if (layers.get(null) == null)
			layers.set(null, (cpw.mods.modlauncher.api.IModuleLayerManager) layer -> java.util.Optional.of(ModuleLayer.boot()));
		Bootstrap.bootStrap();

		// PostRiderForge's constructor
		// (Building the config spec hands its values to PostRiderConfig; unloaded, they read as the defaults)
		Objects.requireNonNull(ForgeConfig.SPEC);
		// (Not ForgeNetwork: Forge's network channel listens on an event bus, and EventBus 6 can only list listeners for
		// event classes its transformer has rewritten as they load, which needs Forge's launcher. No test sends packets)
		PostRiderEvents.init();
		// PostRiderForgeClient: the rest of it only registers listeners for client loading events that never fire here
		PostRiderClientEvents.init();

		// Forge's registry phase (RegisterEvent for each registry, then ForgeHooks.modifyAttributes)
		GameData.unfreezeData();
		registerForgeAttributes();
		for (Registrar<?> registrar : ModRegistries.ALL)
			register(registrar);
		registerAttributes();

		// FMLCommonSetupEvent
		PostRiderEvents.commonSetup();

		// FML has finished loading
		GameData.freezeData();
		started = true;
	}

	private static <R> void register(Registrar<R> registrar) {
		ResourceKey<? extends Registry<R>> key = registrar.registry();
		ForgeRegistry<R> forge = RegistryManager.ACTIVE.getRegistry(key);
		if (forge != null) {
			forge.unfreeze();
			registrar.registerAll((entry, value) -> forge.register(entry.location(), value));
			forge.freeze();
			return;
		}
		@SuppressWarnings({ "unchecked", "rawtypes" })
		Registry<R> vanilla = (Registry<R>) BuiltInRegistries.REGISTRY.getOrThrow((ResourceKey) key);
		registrar.registerAll((entry, value) -> Registry.register(vanilla, entry, value));
	}

	/**
	 * Forge's own attributes (swim speed, name tag distance), which Forge 52 gives every living entity: in the game Forge
	 * registers them as a mod when RegisterEvent opens the attribute registry, which no one does here, so their entries
	 * are put in by hand and their holders bound, as DeferredRegister does.
	 */
	@SuppressWarnings("unchecked")
	private static void registerForgeAttributes() throws ReflectiveOperationException {
		Field registerField = net.minecraftforge.common.ForgeMod.class.getDeclaredField("ATTRIBUTES");
		registerField.setAccessible(true);
		Object deferred = registerField.get(null);
		Field entriesField = net.minecraftforge.registries.DeferredRegister.class.getDeclaredField("entries");
		entriesField.setAccessible(true);
		var entries = (Map<net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute>,
			java.util.function.Supplier<? extends net.minecraft.world.entity.ai.attributes.Attribute>>) entriesField.get(deferred);
		ForgeRegistry<net.minecraft.world.entity.ai.attributes.Attribute> attributes = RegistryManager.ACTIVE.getRegistry(net.minecraft.core.registries.Registries.ATTRIBUTE);
		attributes.unfreeze();
		entries.forEach((holder, value) -> attributes.register(holder.getId(), value.get()));
		attributes.freeze();
		java.lang.reflect.Method bind = net.minecraftforge.registries.RegistryObject.class.getDeclaredMethod("updateReference", net.minecraftforge.registries.IForgeRegistry.class);
		bind.setAccessible(true);
		for (var holder : entries.keySet())
			bind.invoke(holder, attributes);
	}

	/**
	 * The entities' attributes, put where Forge keeps modded ones (ForgeHooks' attribute map, which the game reads them
	 * from), as ForgeHooks.modifyAttributes does by posting EntityAttributeCreationEvent with that map to each mod.
	 */
	private static void registerAttributes() throws ReflectiveOperationException {
		Field field = ForgeHooks.class.getDeclaredField("FORGE_ATTRIBUTES");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<EntityType<? extends LivingEntity>, AttributeSupplier> attributes = (Map<EntityType<? extends LivingEntity>, AttributeSupplier>) field.get(null);
		EntityAttributeCreationEvent event = new EntityAttributeCreationEvent(attributes);
		ModEntities.registerAttributes(event::put);
	}
}
