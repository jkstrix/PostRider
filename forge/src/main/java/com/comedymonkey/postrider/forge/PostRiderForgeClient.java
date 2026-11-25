package com.comedymonkey.postrider.forge;

import java.util.List;

import com.comedymonkey.postrider.client.PostRiderClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Post Rider's client on Forge: connects Forge's client events to the mod (the NeoForge module's PostRiderNeoForgeClient).
 * Called by PostRiderForge on the client only.
 */
final class PostRiderForgeClient {
	private PostRiderForgeClient() {}

	static void init(BusGroup modBus) {
		PostRiderClientEvents.init();
		// After each entity's tick on the client, passengers too: mixin.client.ClientLevelMixin and mixin.client.EntityMixin
		// NeoForge only fires its client tick once the game has finished loading; Forge fires it from the first tick
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.isGameLoadFinished())
				PostRiderClientEvents.clientTickEnd(minecraft);
		});
		// Forge has no menu screen event: MenuScreens.register is opened up by Forge's own access transformer, and called
		// on the main thread in client setup
		FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(() ->
			PostRiderClientEvents.registerScreens((menu, screen) -> MenuScreens.register(menu, screen::create))));
		EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(event -> PostRiderClientEvents.registerLayers(event::registerLayerDefinition));
		EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> PostRiderClientEvents.registerRenderers(new PostRiderClientEvents.RendererSink() {
			@Override
			public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> renderer) {
				event.registerEntityRenderer(type, renderer);
			}
		}));
		RegisterColorHandlersEvent.Block.BUS.addListener(event ->
			PostRiderClientEvents.registerBlockTints((block, tint) -> event.register(List.of(state -> tint), block)));
		// (Dailies' compat is NeoForge only: Dailies is a NeoForge mod)
	}
}
