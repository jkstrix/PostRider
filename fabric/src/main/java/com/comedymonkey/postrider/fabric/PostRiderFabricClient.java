package com.comedymonkey.postrider.fabric;

import com.comedymonkey.postrider.client.PostRiderClientEvents;
import com.comedymonkey.postrider.network.ModPayloads;
import com.comedymonkey.postrider.network.PacketContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/** Post Rider's client on Fabric: connects Fabric's client hooks to the mod (the NeoForge module's PostRiderNeoForgeClient). */
public class PostRiderFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PostRiderClientEvents.init();
		// After each entity's tick on the client, passengers too: mixin.client.ClientLevelMixin and mixin.client.EntityMixin
		ClientTickEvents.END_CLIENT_TICK.register(PostRiderClientEvents::clientTickEnd);
		// MenuScreens.register is opened up by Fabric API's transitive access wideners
		PostRiderClientEvents.registerScreens((menu, screen) -> MenuScreens.register(menu, screen::create));
		PostRiderClientEvents.registerLayers((layer, definition) -> EntityModelLayerRegistry.registerModelLayer(layer, definition::get));
		PostRiderClientEvents.registerRenderers(new PostRiderClientEvents.RendererSink() {
			@Override
			public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> renderer) {
				EntityRendererRegistry.register(type, renderer);
			}
		});
		PostRiderClientEvents.registerBlockColors((block, color) -> ColorProviderRegistry.BLOCK.register(color, block));
		PostRiderClientEvents.registerItemColors((item, color) -> ColorProviderRegistry.ITEM.register(color, item));
		registerPayloadHandlers();
		// (Dailies' compat is NeoForge only: Dailies is a NeoForge mod)
	}

	private static void registerPayloadHandlers() {
		for (ModPayloads.Entry<?> entry : ModPayloads.ALL)
			if (entry.direction() == ModPayloads.Direction.TO_CLIENT)
				register(entry);
	}

	private static <T extends CustomPacketPayload> void register(ModPayloads.Entry<T> entry) {
		ClientPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) ->
			entry.handler().handle(payload, context(context.player(), context.client())));
	}

	/** A packet's context on the client. Fabric hands packets over on the render thread, as NeoForge's main-thread handlers. */
	private static PacketContext context(Player player, Minecraft minecraft) {
		return new PacketContext() {
			@Override
			public Player player() {
				return player;
			}

			@Override
			public void enqueueWork(Runnable work) {
				// As NeoForge: straight away when already on the render thread, otherwise queued for it
				if (minecraft.isSameThread())
					work.run();
				else
					minecraft.execute(work);
			}
		};
	}
}
