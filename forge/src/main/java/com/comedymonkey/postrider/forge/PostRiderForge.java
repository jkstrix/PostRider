package com.comedymonkey.postrider.forge;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.PostRiderEvents;
import com.comedymonkey.postrider.gametest.GameTests;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModRegistries;
import com.comedymonkey.postrider.registry.Registrar;

import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Post Rider on Forge: connects Forge's events and registries to the mod (the NeoForge module's PostRiderNeoForge).
 * Forge 52 uses EventBus 6, as NeoForge 21.1 does: the mod's loading events are on the mod's own bus, the game's on
 * MinecraftForge.EVENT_BUS.
 */
@Mod(PostRider.MOD_ID)
public class PostRiderForge {
	public PostRiderForge(FMLJavaModLoadingContext context) {
		IEventBus modBus = context.getModEventBus();
		// Forge's SERVER config, as NeoForge's: synced to clients, read as the server is about to start. Forge keeps it
		// in each world's serverconfig/postrider-server.toml (made from defaultconfigs/ when there's one there)
		context.registerConfig(ModConfig.Type.SERVER, ForgeConfig.SPEC, PostRiderConfig.FILE_NAME);
		modBus.addListener(PostRiderForge::registerAll);
		modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(PostRiderEvents::commonSetup));
		// NeoForge's payload registration, while the mod loads
		ForgeNetwork.init();
		modBus.addListener((EntityAttributeCreationEvent event) -> ModEntities.registerAttributes(event::put));
		// NeoForge's ticket controller postrider:rider: Forge's own chunk tickets for the mod, validated by the same
		// callback when a level loads (set in common setup, as Forge asks)
		modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
			ForgeChunkManager.setForcedChunkLoadingCallback(PostRider.MOD_ID, ForgePlatform::validateTickets)));
		modBus.addListener((RegisterGameTestsEvent event) -> event.register(GameTests.class));

		MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
			// NeoForge refuses a break of a game master block by a player who can't use one before any listener sees it,
			// as it does a tool that can't break the block and adventure mode. Forge pre-refuses those two too (so this
			// listener isn't called for them, as there), but only refuses the first after the event: it's skipped here
			if (event.getState().getBlock() instanceof GameMasterBlock && !event.getPlayer().canUseGameMasterBlocks())
				return;
			// Refused: Forge sends the block (and its block entity) back to the player's client itself, as NeoForge does
			if (PostRiderEvents.breakRefused(event.getPlayer(), event.getLevel(), event.getPos(), event.getState()))
				event.setCanceled(true);
		});
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> PostRiderEvents.serverTickEnd(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Pre event) -> PostRiderEvents.serverTickStart(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> PostRiderEvents.serverStopped(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> PostRiderEvents.entityJoinedLevel(event.getEntity(), event.getLevel()));
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PostRiderEvents.registerCommands(event.getDispatcher()));
		MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> PostRiderEvents.serverStarted(event.getServer()));
		PostRiderEvents.init();

		// NeoForge's client entry (a @Mod for the client dist), straight after this one
		if (FMLEnvironment.dist == Dist.CLIENT)
			PostRiderForgeClient.init(modBus);
	}

	private static void registerAll(RegisterEvent event) {
		for (Registrar<?> registrar : ModRegistries.ALL)
			register(event, registrar);
	}

	private static <R> void register(RegisterEvent event, Registrar<R> registrar) {
		event.register(registrar.registry(), helper -> registrar.registerAll(helper::register));
	}
}
