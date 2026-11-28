package com.comedymonkey.postrider.forge;

import com.comedymonkey.postrider.client.PostRiderClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Post Rider's client on Forge: connects Forge's client events to the mod (the NeoForge module's PostRiderNeoForgeClient).
 * Called by PostRiderForge on the client only.
 */
final class PostRiderForgeClient {
	private PostRiderForgeClient() {}

	static void init(IEventBus modBus) {
		PostRiderClientEvents.init();
		// After each entity's tick on the client, passengers too: mixin.client.ClientLevelMixin and mixin.client.EntityMixin
		// (Forge 52 fires its client tick at the end of Minecraft.tick from the first tick, as NeoForge 21.1 does)
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> PostRiderClientEvents.clientTickEnd(Minecraft.getInstance()));
		// Forge has no menu screen event: MenuScreens.register is opened up by Forge's own access transformer, and called
		// on the main thread in client setup
		modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
			PostRiderClientEvents.registerScreens((menu, screen) -> MenuScreens.register(menu, screen::create))));
		modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> PostRiderClientEvents.registerLayers(event::registerLayerDefinition));
		modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> PostRiderClientEvents.registerRenderers(new PostRiderClientEvents.RendererSink() {
			@Override
			public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> renderer) {
				event.registerEntityRenderer(type, renderer);
			}
		}));
		modBus.addListener((RegisterColorHandlersEvent.Block event) ->
			PostRiderClientEvents.registerBlockColors((block, color) -> event.register(color, block)));
		modBus.addListener((RegisterColorHandlersEvent.Item event) ->
			PostRiderClientEvents.registerItemColors((item, color) -> event.register(color, item)));
		// (Dailies' compat is NeoForge only: Dailies is a NeoForge mod)
	}
}
