package com.comedymonkey.postrider.forge;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.PostRiderEvents;
import com.comedymonkey.postrider.gametest.GameTests;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModRegistries;
import com.comedymonkey.postrider.registry.Registrar;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Post Rider on Forge: connects Forge's events and registries to the mod (the NeoForge module's PostRiderNeoForge).
 * Forge 66 uses EventBus 7: the mod's loading events are on the mod's bus group, the game's each on its own bus.
 */
@Mod(PostRider.MOD_ID)
public class PostRiderForge {
	public PostRiderForge(FMLJavaModLoadingContext context) {
		BusGroup modBus = context.getModBusGroup();
		// Forge has no synced config type (its SERVER configs live in each world's serverconfig folder), so the server
		// settings are a common config: config/postrider-server.toml, the file and name NeoForge's synced config has
		context.registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC, PostRiderConfig.FILE_NAME);
		RegisterEvent.getBus(modBus).addListener(PostRiderForge::registerAll);
		FMLCommonSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(PostRiderEvents::commonSetup));
		// NeoForge's payload registration, while the mod loads
		ForgeNetwork.init();
		EntityAttributeCreationEvent.BUS.addListener(event -> ModEntities.registerAttributes(event::put));
		// The riders' chunk tickets: ForgeRiderTickets and its mixins (Forge 66 has no ticket controllers)
		RegisterEvent.getBus(modBus).addListener(PostRiderForge::registerTestFunctions);
		// The tests themselves and their environments: ForgeGameTests and its mixins

		BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent event) -> {
			// NeoForge refuses some breaks before any listener sees them (a tool that can't break the block, adventure
			// mode, a game master block the player can't use). Forge refuses the first two after the event and still
			// calls listeners, and the third after the event: the mod's handler is skipped for all three, as there
			if (event.getResult().isDenied()
					|| event.getState().getBlock() instanceof GameMasterBlock && !event.getPlayer().canUseGameMasterBlocks())
				return false;
			if (!PostRiderEvents.breakRefused(event.getPlayer(), event.getLevel(), event.getPos(), event.getState()))
				return false;
			// Refused: Forge sends the block (and its block entity) back to the player's client, as NeoForge's
			// setNotifyClient; cancelling stops later listeners, as NeoForge's setCanceled
			event.setResult(Result.DENY);
			return true;
		});
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> PostRiderEvents.serverTickEnd(event.server()));
		TickEvent.ServerTickEvent.Pre.BUS.addListener(event -> PostRiderEvents.serverTickStart(event.server()));
		ServerStoppedEvent.BUS.addListener(event -> PostRiderEvents.serverStopped(event.getServer()));
		EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event) -> PostRiderEvents.entityJoinedLevel(event.getEntity(), event.getLevel()));
		RegisterCommandsEvent.BUS.addListener(event -> PostRiderEvents.registerCommands(event.getDispatcher()));
		ServerStartedEvent.BUS.addListener(event -> PostRiderEvents.serverStarted(event.getServer()));
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

	private static void registerTestFunctions(RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, helper -> GameTests.registerFunctions(helper::register));
	}
}
