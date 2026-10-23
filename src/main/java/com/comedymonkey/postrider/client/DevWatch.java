package com.comedymonkey.postrider.client;

import java.util.Locale;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.content.postman.PostmanEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * For development only: started with the system property {@code postrider.watch} (the {@code runWatch} Gradle
 * task), the game opens a flat creative world of its own and runs the in-world tests in front of the player, to be
 * watched: all of them, or the one the property names. The player is made a spectator and set to follow the first
 * rider out, then the one most recently out once that one's delivered, and so on; shift lets go of a rider (a click on any rider
 * follows him again), and the game doesn't pause when another window is in front. It stays open
 * afterwards (type {@code /test runall} or {@code /test run postrider:<name>} for another go; {@code /test clearall}
 * tidies up).
 */
public final class DevWatch {
	public static final String PROPERTY = "postrider.watch";
	private static final String WORLD = "postrider_watch";
	/** Ticks to let the world settle (the player stood on the ground, chunks round him loaded) before the tests. */
	private static final int SETTLE_TICKS = 60;

	private enum Step { START, JOINING, SETTLING, WATCHING }
	/** How often (in ticks) the spectator is given the next rider to follow, once his is done. */
	private static final int FOLLOW_CHECK = 10;

	private static Step step = Step.START;
	private static int ticks;
	/** The rider the spectator was last set to follow (the player lets go with shift; nobody is forced back on). */
	@Nullable
	private static PostmanEntity followed;

	private DevWatch() {}

	/** The test command for what the property asks for: everything, or one test by its name (namespace or not). */
	public static String command(String wanted) {
		String name = wanted == null ? "" : wanted.trim().toLowerCase(Locale.ROOT);
		if (name.isEmpty() || name.equals("all") || name.equals("true"))
			return "test runall";
		return "test run " + (name.contains(":") ? name : PostRider.MOD_ID + ":" + name);
	}

	/** Switches the run on if the game was started for it. */
	public static void registerIfWanted() {
		if (System.getProperty(PROPERTY) == null)
			return;
		PostRider.LOGGER.info("Post Rider watch run: the game will open a world and run the in-world tests ({})", command(System.getProperty(PROPERTY)));
		Consumer<ClientTickEvent.Post> tick = event -> tick(Minecraft.getInstance());
		NeoForge.EVENT_BUS.addListener(tick);
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		switch (step) {
			case START -> {
				// A new game folder opens on the accessibility welcome first: skip it
				if (minecraft.screen instanceof AccessibilityOnboardingScreen) {
					minecraft.options.onboardAccessibility = false;
					minecraft.options.save();
					minecraft.setScreen(new TitleScreen());
					return;
				}
				if (!(minecraft.screen instanceof TitleScreen))
					return;
				minecraft.getTutorial().setStep(TutorialSteps.NONE);
				// The game keeps going when it's not the window in front (the tests are being watched from elsewhere too)
				minecraft.options.pauseOnLostFocus = false;
				minecraft.options.save();
				step = Step.JOINING;
				if (minecraft.getLevelSource().levelExists(WORLD)) {
					minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.setScreen(new TitleScreen()));
				} else {
					LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
						WorldDataConfiguration.DEFAULT);
					minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
						access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
						new TitleScreen());
				}
			}
			case JOINING -> {
				if (minecraft.player != null && minecraft.level != null) {
					step = Step.SETTLING;
					ticks = 0;
				}
			}
			case SETTLING -> {
				MinecraftServer server = minecraft.getSingleplayerServer();
				if (ticks < SETTLE_TICKS || server == null || server.getPlayerList().getPlayers().isEmpty())
					return;
				step = Step.WATCHING;
				ticks = 0;
				String command = command(System.getProperty(PROPERTY));
				// On the server's own thread (this is the client's): the tests load chunks and set blocks as they're placed
				server.execute(() -> {
					ServerPlayer player = server.getPlayerList().getPlayers().get(0);
					// Daytime, and staying that way, so the post office is open however long the watching takes
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gamerule doDaylightCycle false");
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "time set day");
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "test clearall");
					PostRider.LOGGER.info("Post Rider watch run: /{}", command);
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
					// A spectator: through walls and roofs to see, and a click on a rider to follow him
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gamemode spectator");
				});
			}
			case WATCHING -> {
				MinecraftServer server = minecraft.getSingleplayerServer();
				if (ticks % FOLLOW_CHECK == 0 && server != null)
					server.execute(() -> follow(server));
			}
		}
	}

	/** Puts the spectator's eyes on a rider still on the road, once the one he was following has finished. */
	private static void follow(MinecraftServer server) {
		if (server.getPlayerList().getPlayers().isEmpty())
			return;
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		if (!player.isSpectator())
			return;
		if (followed != null && onTheRoad(followed))
			return;
		Entity camera = player.getCamera();
		if (camera != player && camera instanceof PostmanEntity rider && onTheRoad(rider)) {
			followed = rider;
			return;
		}
		PostmanEntity next = null;
		for (Entity entity : server.overworld().getAllEntities())
			// The one most recently out, for the whole of his trip
			if (entity instanceof PostmanEntity rider && onTheRoad(rider) && (next == null || rider.tickCount < next.tickCount))
				next = rider;
		if (next == null || next == followed)
			return;
		followed = next;
		player.setCamera(next);
		PostRider.LOGGER.info("Post Rider watch run: following {}", next.getName().getString());
	}

	/** Whether a rider is out on a delivery (not finished, not gone). */
	public static boolean onTheRoad(PostmanEntity rider) {
		DeliveryRun run = rider.getRun();
		return rider.isAlive() && run != null && run.stage() != DeliveryRun.Stage.DONE;
	}
}
