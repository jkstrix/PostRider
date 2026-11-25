package com.comedymonkey.postrider.neoforge;

import java.util.List;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.PostRiderClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Post Rider's client on NeoForge: connects NeoForge's client events to the mod. */
@Mod(value = PostRider.MOD_ID, dist = Dist.CLIENT)
public class PostRiderNeoForgeClient {
	public PostRiderNeoForgeClient(IEventBus modBus) {
		PostRiderClientEvents.init();
		NeoForge.EVENT_BUS.addListener((EntityTickEvent.Post event) -> PostRiderClientEvents.entityTickEnd(event.getEntity()));
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> PostRiderClientEvents.clientTickEnd(Minecraft.getInstance()));
		modBus.addListener((RegisterMenuScreensEvent event) -> PostRiderClientEvents.registerScreens(event::register));
		modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> PostRiderClientEvents.registerLayers(event::registerLayerDefinition));
		modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> PostRiderClientEvents.registerRenderers(new PostRiderClientEvents.RendererSink() {
			@Override
			public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> renderer) {
				event.registerEntityRenderer(type, renderer);
			}
		}));
		modBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) ->
			PostRiderClientEvents.registerBlockTints((block, tint) -> event.register(List.of(state -> tint), block)));
		registerDailies();
	}

	/**
	 * With Dailies installed, the mailbox, letters being read and letters being written go into its recordings and are
	 * drawn again in playback (client/dailies). Without it nothing of Dailies is touched: the hook class is only loaded
	 * here, after the check. (Dailies is a NeoForge mod, so this is NeoForge only.)
	 */
	private static void registerDailies() {
		if (!ModList.get().isLoaded("dailies"))
			return;
		try {
			com.comedymonkey.postrider.client.dailies.PostRiderDailies.register();
		} catch (LinkageError tooOld) {
			PostRider.LOGGER.warn("Post Rider: this version of Dailies has no compat API, so Post Rider's screens are left out of its recordings", tooOld);
		}
	}
}
