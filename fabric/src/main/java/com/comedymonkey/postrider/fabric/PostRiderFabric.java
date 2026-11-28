package com.comedymonkey.postrider.fabric;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.PostRiderEvents;
import com.comedymonkey.postrider.gametest.GameTests;
import com.comedymonkey.postrider.network.ModPayloads;
import com.comedymonkey.postrider.network.PacketContext;
import com.comedymonkey.postrider.parity.ParityManifest;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModRegistries;
import com.comedymonkey.postrider.registry.Registrar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.modprotocol.v1.ModProtocolRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.neoforged.fml.config.ModConfig;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Post Rider on Fabric: connects Fabric's events and registries to the mod (the NeoForge module's PostRiderNeoForge). */
public class PostRiderFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		// What NeoForge's mod constructor does first; on Fabric the registries fill straight after, below
		PostRiderEvents.init();
		// Forge Config API Port's SERVER type is what NeoForge 26.x calls SYNCED: config/postrider-server.toml, read
		// when a server starts (a copy in the world's serverconfig folder overrides it) and synced to the clients
		ConfigRegistry.INSTANCE.register(PostRider.MOD_ID, ModConfig.Type.SERVER, FabricConfig.SPEC, PostRiderConfig.FILE_NAME);

		for (Registrar<?> registrar : ModRegistries.ALL)
			register(registrar);
		// The test functions are registered in every run, as NeoForge's RegisterEvent for TEST_FUNCTION does
		GameTests.registerFunctions((id, function) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, id, function));
		ModEntities.registerAttributes(FabricDefaultAttributeRegistry::register);
		registerPayloads();
		// NeoForge's common setup (after the registries, on the main thread): onInitialize is both
		PostRiderEvents.commonSetup();

		// The tests and their batches' environments go into the world's data-driven registries as they load, in
		// development runs only (NeoForge registers them when its game tests are enabled, which is every development run)
		if (FabricLoader.getInstance().isDevelopmentEnvironment())
			DynamicRegistrySetupCallback.EVENT.register(view -> view.getOptional(Registries.TEST_ENVIRONMENT).ifPresent(environments ->
				view.getOptional(Registries.TEST_INSTANCE).ifPresent(tests ->
					registerTests((WritableRegistry<TestEnvironmentDefinition<?>>) environments, (WritableRegistry<GameTestInstance>) tests))));

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!PostRiderEvents.breakRefused(player, level, pos, state))
				return true;
			// NeoForge's setNotifyClient: the client may have broken the block already, so it's sent the block back
			if (player instanceof ServerPlayer serverPlayer)
				serverPlayer.connection.send(new ClientboundBlockUpdatePacket(pos, state));
			return false;
		});
		ServerTickEvents.END_SERVER_TICK.register(PostRiderEvents::serverTickEnd);
		ServerTickEvents.START_SERVER_TICK.register(PostRiderEvents::serverTickStart);
		ServerLifecycleEvents.SERVER_STOPPED.register(PostRiderEvents::serverStopped);
		// Entities joining a level: mixin.PersistentEntitySectionManagerMixin
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> PostRiderEvents.registerCommands(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(PostRiderEvents::serverStarted);
	}

	private static <R> void register(Registrar<R> registrar) {
		@SuppressWarnings("unchecked")
		Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) registrar.registry());
		registrar.registerAll((key, value) -> Registry.register(registry, key, value));
	}

	private static void registerPayloads() {
		// NeoForge's payload registrar(ModPayloads.PROTOCOL): a client and server must both have Post Rider, on the same
		// protocol, to connect
		ModProtocolRegistry.register(PostRider.asResource("network"), "Post Rider", ModPayloads.PROTOCOL, ModPayloads.PROTOCOL);
		for (ModPayloads.Entry<?> entry : ModPayloads.ALL)
			register(entry);
	}

	private static <T extends CustomPacketPayload> void register(ModPayloads.Entry<T> entry) {
		ParityManifest.recordPayload(entry.type().id(), entry.direction().name());
		if (entry.direction() == ModPayloads.Direction.TO_CLIENT) {
			// Handled on the client (PostRiderFabricClient.registerPayloadHandlers)
			PayloadTypeRegistry.clientboundPlay().register(entry.type(), entry.codec());
		} else {
			PayloadTypeRegistry.serverboundPlay().register(entry.type(), entry.codec());
			ServerPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) ->
				entry.handler().handle(payload, context(context.player(), context.server())));
		}
	}

	/** A packet's context on the server. Fabric hands packets over on the server thread, as NeoForge's main-thread handlers. */
	private static PacketContext context(ServerPlayer player, MinecraftServer server) {
		return new PacketContext() {
			@Override
			public Player player() {
				return player;
			}

			@Override
			public void enqueueWork(Runnable work) {
				// As NeoForge: straight away when already on the server thread, otherwise queued for it
				if (server.isSameThread())
					work.run();
				else
					server.execute(work);
			}
		};
	}

	private static void registerTests(WritableRegistry<TestEnvironmentDefinition<?>> environments, WritableRegistry<GameTestInstance> tests) {
		GameTests.registerTests(
			(id, definition) -> environments.register(ResourceKey.create(Registries.TEST_ENVIRONMENT, id), definition, RegistrationInfo.BUILT_IN),
			(id, test) -> tests.register(ResourceKey.create(Registries.TEST_INSTANCE, id), test, RegistrationInfo.BUILT_IN));
	}
}
