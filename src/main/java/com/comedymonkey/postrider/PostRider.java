package com.comedymonkey.postrider;

import com.comedymonkey.postrider.command.PostRiderCommand;
import com.comedymonkey.postrider.content.PostalBlockEvents;
import com.comedymonkey.postrider.content.postman.RiderChunks;
import com.comedymonkey.postrider.content.postman.RiderHunting;
import com.comedymonkey.postrider.post.PostDispatcher;
import com.comedymonkey.postrider.network.EditLetterPayload;
import com.comedymonkey.postrider.network.MailboxInfoPayload;
import com.comedymonkey.postrider.network.MailboxNoticePayload;
import com.comedymonkey.postrider.network.RenameMailboxPayload;
import com.comedymonkey.postrider.network.SendParcelPayload;
import com.comedymonkey.postrider.registry.ModBlockEntities;
import com.comedymonkey.postrider.registry.ModBlocks;
import com.comedymonkey.postrider.registry.ModCreativeTabs;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModItems;
import com.comedymonkey.postrider.registry.ModMenus;
import com.comedymonkey.postrider.registry.ModSounds;
import com.comedymonkey.postrider.registry.ModTriggers;
import com.comedymonkey.postrider.content.postman.route.PlanningBudget;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(PostRider.MOD_ID)
public class PostRider {
	public static final String MOD_ID = "postrider";
	public static final Logger LOGGER = LogUtils.getLogger();

	public PostRider(IEventBus modBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.SERVER, PostRiderConfig.SPEC);
		ModDataComponents.COMPONENTS.register(modBus);
		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModBlockEntities.BLOCK_ENTITIES.register(modBus);
		ModMenus.MENUS.register(modBus);
		ModCreativeTabs.TABS.register(modBus);
		ModEntities.ENTITIES.register(modBus);
		ModSounds.SOUNDS.register(modBus);
		ModTriggers.TRIGGERS.register(modBus);
		ModTriggers.STATS.register(modBus);
		modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ModTriggers::registerStatFormats));
		modBus.addListener(this::registerPayloads);
		modBus.addListener(ModEntities::registerAttributes);
		modBus.addListener((RegisterTicketControllersEvent event) -> event.register(RiderChunks.CONTROLLER));

		NeoForge.EVENT_BUS.addListener(PostalBlockEvents::onBreak);
		NeoForge.EVENT_BUS.addListener(PostDispatcher::onServerTick);
		NeoForge.EVENT_BUS.addListener(PlanningBudget::onServerTick);
		NeoForge.EVENT_BUS.addListener(PlanningBudget::onServerStopped);
		NeoForge.EVENT_BUS.addListener(RiderHunting::onJoin);
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PostRiderCommand.register(event.getDispatcher()));
		DevHeadless.registerIfWanted();
	}

	public static ResourceLocation asResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar("1");
		registrar.playToClient(MailboxInfoPayload.TYPE, MailboxInfoPayload.STREAM_CODEC, MailboxInfoPayload::handle);
		registrar.playToClient(MailboxNoticePayload.TYPE, MailboxNoticePayload.STREAM_CODEC, MailboxNoticePayload::handle);
		registrar.playToServer(RenameMailboxPayload.TYPE, RenameMailboxPayload.STREAM_CODEC, RenameMailboxPayload::handle);
		registrar.playToServer(SendParcelPayload.TYPE, SendParcelPayload.STREAM_CODEC, SendParcelPayload::handle);
		registrar.playToServer(EditLetterPayload.TYPE, EditLetterPayload.STREAM_CODEC, EditLetterPayload::handle);
	}
}
