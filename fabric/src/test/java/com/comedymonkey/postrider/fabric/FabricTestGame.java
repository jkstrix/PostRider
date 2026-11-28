package com.comedymonkey.postrider.fabric;

import com.comedymonkey.postrider.PostRider;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

/**
 * Starts the game for the unit tests, before the first test class runs, as NeoForge's unitTest does by loading the mod
 * in FML: the game's own registries, then Post Rider's entry points (PostRiderFabric, which registers everything, and
 * PostRiderFabricClient), then the registries frozen, as the game freezes them once the mods are in (Fabric API holds
 * the freeze back until then). Only Post Rider's own entry points are run, not other mods'.
 * <p>
 * Loaded and run inside Fabric Loader's class loader (by FabricTestBootstrap), where the tests and the game run, so the
 * mixins and the access widener apply and the game it starts is the one the tests use.
 */
public final class FabricTestGame {
	private static boolean started;

	private FabricTestGame() {}

	public static synchronized void start() {
		if (started)
			return;
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		for (EntrypointContainer<ModInitializer> entrypoint : FabricLoader.getInstance().getEntrypointContainers("main", ModInitializer.class))
			if (ours(entrypoint))
				entrypoint.getEntrypoint().onInitialize();
		// The tests run on the client side (fabric-loader-junit's default, and NeoForge's unit tests construct the client
		// mod too), so the client entry point (PostRiderFabricClient) as well
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
			for (EntrypointContainer<ClientModInitializer> entrypoint : FabricLoader.getInstance().getEntrypointContainers("client", ClientModInitializer.class))
				if (ours(entrypoint))
					entrypoint.getEntrypoint().onInitializeClient();
		BuiltInRegistries.bootStrap();
		started = true;
	}

	private static boolean ours(EntrypointContainer<?> entrypoint) {
		return entrypoint.getProvider().getMetadata().getId().equals(PostRider.MOD_ID);
	}
}
