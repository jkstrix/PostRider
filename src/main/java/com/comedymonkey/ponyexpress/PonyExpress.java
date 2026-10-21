package com.comedymonkey.ponyexpress;

import com.comedymonkey.ponyexpress.command.PonyExpressCommand;
import com.comedymonkey.ponyexpress.content.PostalBlockEvents;
import com.comedymonkey.ponyexpress.content.postman.RiderChunks;
import com.comedymonkey.ponyexpress.content.postman.RiderHunting;
import com.comedymonkey.ponyexpress.post.PostDispatcher;
import com.comedymonkey.ponyexpress.network.EditLetterPayload;
import com.comedymonkey.ponyexpress.network.MailboxInfoPayload;
import com.comedymonkey.ponyexpress.network.RenameMailboxPayload;
import com.comedymonkey.ponyexpress.network.SendParcelPayload;
import com.comedymonkey.ponyexpress.registry.ModBlockEntities;
import com.comedymonkey.ponyexpress.registry.ModBlocks;
import com.comedymonkey.ponyexpress.registry.ModCreativeTabs;
import com.comedymonkey.ponyexpress.registry.ModDataComponents;
import com.comedymonkey.ponyexpress.registry.ModEntities;
import com.comedymonkey.ponyexpress.registry.ModItems;
import com.comedymonkey.ponyexpress.registry.ModMenus;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(PonyExpress.MOD_ID)
public class PonyExpress {
	public static final String MOD_ID = "ponyexpress";
	public static final Logger LOGGER = LogUtils.getLogger();

	public PonyExpress(IEventBus modBus, ModContainer container) {
		ModDataComponents.COMPONENTS.register(modBus);
		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModBlockEntities.BLOCK_ENTITIES.register(modBus);
		ModMenus.MENUS.register(modBus);
		ModCreativeTabs.TABS.register(modBus);
		ModEntities.ENTITIES.register(modBus);
		modBus.addListener(this::registerPayloads);
		modBus.addListener(ModEntities::registerAttributes);
		modBus.addListener((RegisterTicketControllersEvent event) -> event.register(RiderChunks.CONTROLLER));

		NeoForge.EVENT_BUS.addListener(PostalBlockEvents::onBreak);
		NeoForge.EVENT_BUS.addListener(PostDispatcher::onServerTick);
		NeoForge.EVENT_BUS.addListener(RiderHunting::onJoin);
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PonyExpressCommand.register(event.getDispatcher()));
	}

	public static ResourceLocation asResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar("1");
		registrar.playToClient(MailboxInfoPayload.TYPE, MailboxInfoPayload.STREAM_CODEC, MailboxInfoPayload::handle);
		registrar.playToServer(RenameMailboxPayload.TYPE, RenameMailboxPayload.STREAM_CODEC, RenameMailboxPayload::handle);
		registrar.playToServer(SendParcelPayload.TYPE, SendParcelPayload.STREAM_CODEC, SendParcelPayload::handle);
		registrar.playToServer(EditLetterPayload.TYPE, EditLetterPayload.STREAM_CODEC, EditLetterPayload::handle);
	}
}
