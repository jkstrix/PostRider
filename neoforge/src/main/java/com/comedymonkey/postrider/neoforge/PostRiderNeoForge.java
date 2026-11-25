package com.comedymonkey.postrider.neoforge;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.PostRiderEvents;
import com.comedymonkey.postrider.gametest.GameTests;
import com.comedymonkey.postrider.network.ModPayloads;
import com.comedymonkey.postrider.network.PacketContext;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModRegistries;
import com.comedymonkey.postrider.registry.Registrar;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Post Rider on NeoForge: connects NeoForge's events and registries to the mod. */
@Mod(PostRider.MOD_ID)
public class PostRiderNeoForge {
	public PostRiderNeoForge(IEventBus modBus, ModContainer container) {
		// Synced is what 26.x calls a server config; the file keeps its old name, so a server's settings carry over
		container.registerConfig(ModConfig.Type.SYNCED, NeoForgeConfig.SPEC, PostRiderConfig.FILE_NAME);
		modBus.addListener(PostRiderNeoForge::registerAll);
		modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(PostRiderEvents::commonSetup));
		modBus.addListener(PostRiderNeoForge::registerPayloads);
		modBus.addListener((EntityAttributeCreationEvent event) -> ModEntities.registerAttributes(event::put));
		modBus.addListener((RegisterTicketControllersEvent event) -> event.register(NeoForgePlatform.TICKETS));
		modBus.addListener(PostRiderNeoForge::registerTestFunctions);
		modBus.addListener(PostRiderNeoForge::registerTests);

		NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
			if (PostRiderEvents.breakRefused(event.getPlayer(), event.getLevel(), event.getPos(), event.getState())) {
				event.setCanceled(true);
				event.setNotifyClient(true);
			}
		});
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> PostRiderEvents.serverTickEnd(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> PostRiderEvents.serverTickStart(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> PostRiderEvents.serverStopped(event.getServer()));
		NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> PostRiderEvents.entityJoinedLevel(event.getEntity(), event.getLevel()));
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PostRiderEvents.registerCommands(event.getDispatcher()));
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> PostRiderEvents.serverStarted(event.getServer()));
		PostRiderEvents.init();
	}

	private static void registerAll(RegisterEvent event) {
		for (Registrar<?> registrar : ModRegistries.ALL)
			register(event, registrar);
	}

	private static <R> void register(RegisterEvent event, Registrar<R> registrar) {
		event.register(registrar.registry(), helper -> registrar.registerAll(helper::register));
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(ModPayloads.PROTOCOL);
		for (ModPayloads.Entry<?> entry : ModPayloads.ALL)
			register(registrar, entry);
	}

	private static <T extends CustomPacketPayload> void register(PayloadRegistrar registrar, ModPayloads.Entry<T> entry) {
		if (entry.direction() == ModPayloads.Direction.TO_CLIENT)
			registrar.playToClient(entry.type(), entry.codec(), (payload, context) -> entry.handler().handle(payload, wrap(context)));
		else
			registrar.playToServer(entry.type(), entry.codec(), (payload, context) -> entry.handler().handle(payload, wrap(context)));
	}

	private static PacketContext wrap(IPayloadContext context) {
		return new PacketContext() {
			@Override
			public Player player() {
				return context.player();
			}

			@Override
			public void enqueueWork(Runnable work) {
				context.enqueueWork(work);
			}
		};
	}

	private static void registerTestFunctions(RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, registry -> GameTests.registerFunctions(registry::register));
	}

	private static void registerTests(RegisterGameTestsEvent event) {
		GameTests.registerTests(event::registerEnvironment, event::registerTest);
	}
}
