package com.comedymonkey.postrider.fabric;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.PostRiderEvents;
import com.comedymonkey.postrider.network.ModPayloads;
import com.comedymonkey.postrider.network.PacketContext;
import com.comedymonkey.postrider.parity.ParityManifest;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModRegistries;
import com.comedymonkey.postrider.registry.Registrar;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.config.ModConfig;

/** Post Rider on Fabric: connects Fabric's events and registries to the mod (the NeoForge module's PostRiderNeoForge). */
public class PostRiderFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		// NeoForge 21.1's SERVER config, as Forge Config API Port provides it: the world's serverconfig/postrider-server.toml
		// (made from defaultconfigs/ or the defaults), read when a server starts and synced to the clients
		NeoForgeConfigRegistry.INSTANCE.register(PostRider.MOD_ID, ModConfig.Type.SERVER, FabricConfig.SPEC, PostRiderConfig.FILE_NAME);
		// What NeoForge's mod constructor does last, before the registries fill; on Fabric they fill straight after, below
		PostRiderEvents.init();

		for (Registrar<?> registrar : ModRegistries.ALL)
			register(registrar);
		ModEntities.registerAttributes(FabricDefaultAttributeRegistry::register);
		registerPayloads();
		// NeoForge's common setup (after the registries, on the main thread): onInitialize is both
		PostRiderEvents.commonSetup();
		// The in-world tests: FabricGameTests, Fabric API's game test entry point (fabric.mod.json)

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!PostRiderEvents.breakRefused(player, level, pos, state))
				return true;
			// As NeoForge's break event does when it's cancelled: the client may have broken the block already, so it's
			// sent the block back
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
		Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getOrThrow((ResourceKey) registrar.registry());
		registrar.registerAll((key, value) -> Registry.register(registry, key, value));
	}

	private static void registerPayloads() {
		for (ModPayloads.Entry<?> entry : ModPayloads.ALL)
			register(entry);
	}

	private static <T extends CustomPacketPayload> void register(ModPayloads.Entry<T> entry) {
		ParityManifest.recordPayload(entry.type().id(), entry.direction().name());
		if (entry.direction() == ModPayloads.Direction.TO_CLIENT) {
			// Handled on the client (PostRiderFabricClient.registerPayloadHandlers)
			PayloadTypeRegistry.playS2C().register(entry.type(), entry.codec());
		} else {
			PayloadTypeRegistry.playC2S().register(entry.type(), entry.codec());
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
}
