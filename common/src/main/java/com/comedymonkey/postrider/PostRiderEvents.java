package com.comedymonkey.postrider;

import com.comedymonkey.postrider.command.PostRiderCommand;
import com.comedymonkey.postrider.content.PostalBlockEvents;
import com.comedymonkey.postrider.content.postman.RiderHunting;
import com.comedymonkey.postrider.content.postman.route.PlanningBudget;
import com.comedymonkey.postrider.parity.ParityGameTests;
import com.comedymonkey.postrider.parity.ParityManifest;
import com.comedymonkey.postrider.post.PostDispatcher;
import com.comedymonkey.postrider.registry.ModTriggers;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;

/**
 * Everything the mod does when the game tells it something happened. Each mod loader calls these from its own events,
 * at the point named; the order inside each is the order the mod has always run them in.
 */
public final class PostRiderEvents {
	/** Whether this server was started for a headless trip (checked once, when the mod starts). */
	private static boolean headless;

	private PostRiderEvents() {}

	/** When the mod is constructed, before registries are filled. */
	public static void init() {
		ParityGameTests.installReporterIfRequested();
		headless = DevHeadless.wanted();
	}

	/** Once registries are filled (the loader's common setup), on the main thread. */
	public static void commonSetup() {
		ModTriggers.registerStatFormats();
	}

	/** At the start of every server tick, before the levels tick. */
	public static void serverTickStart(MinecraftServer server) {
		PlanningBudget.onServerTick(server);
	}

	/** At the end of every server tick, after the levels have ticked. */
	public static void serverTickEnd(MinecraftServer server) {
		PostDispatcher.onServerTick(server);
		RiderWatch.onServerTick(server);
		if (headless)
			DevHeadless.onTick(server);
	}

	/** Once the server has started and its levels are loaded. */
	public static void serverStarted(MinecraftServer server) {
		ParityManifest.dumpIfRequested(server);
		if (headless)
			DevHeadless.onStarted(server);
	}

	/** Once the server has stopped. */
	public static void serverStopped(MinecraftServer server) {
		PlanningBudget.onServerStopped(server);
		RiderWatch.onServerStopped(server);
	}

	/** When an entity joins a level (loaded or spawned), on both sides. */
	public static void entityJoinedLevel(Entity entity, Level level) {
		RiderHunting.onJoin(entity, level);
	}

	/**
	 * When a player is about to break a block.
	 *
	 * @return true if the break is refused: the loader cancels it and sends the block back to the player's client
	 */
	public static boolean breakRefused(Player player, LevelAccessor level, BlockPos pos, BlockState state) {
		return PostalBlockEvents.onBreak(player, level, pos, state);
	}

	/** When the server's commands are registered. */
	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		PostRiderCommand.register(dispatcher);
	}
}
