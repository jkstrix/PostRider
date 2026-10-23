package com.comedymonkey.postrider.client;

import java.util.Locale;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.DevHeadless;
import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.RiderWatch;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.post.PendingDelivery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
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
 * watched: all of them, or the one the property names. The player is made a spectator and kept a few blocks off
 * the rider most recently out (see {@link RiderWatch}), and the game doesn't pause when another window is in front.
 * It stays open afterwards (type {@code /test runall} or {@code /test run <name>} for another go; {@code /test clearall}
 * tidies up).
 * <p>
 * With {@code postrider.watchWorld} set to {@code "<world>|<from>|<to>"} instead ({@code runWatch -Pworld=...}), a
 * trip across a real world is watched: one of the player's worlds (copied into the run's saves by Gradle), or a
 * fresh ordinary one for {@code new} ({@code postrider.watchSeed} for the same one again); a parcel is posted from
 * the one box to the other as the headless run posts ({@code spawn}, {@code random:<blocks>}, {@code at:<x>,<z>}),
 * and the player follows the rider the same way, at the game's own pace, day and night.
 */
public final class DevWatch {
	public static final String PROPERTY = "postrider.watch";
	public static final String WORLD_PROPERTY = "postrider.watchWorld";
	public static final String SEED_PROPERTY = "postrider.watchSeed";
	private static final String WORLD = "postrider_watch";

	/** A trip to watch across a real world: which world ({@code new} for a fresh one), and the boxes to post from and to. */
	public record WorldRun(String world, String from, String to) {
		public boolean fresh() {
			return world.equals("new");
		}

		/** The fresh world's folder name, by its seed, so the same seed opens the same world again. */
		public String folder(@Nullable String seed) {
			return "postrider_watch_" + (seed == null || seed.isBlank() ? "random" : seed.trim().replaceAll("[^A-Za-z0-9_-]", "_"));
		}
	}

	/** The trip the property asks for; null if it's unset or isn't three names with bars between. */
	@Nullable
	public static WorldRun worldRun(@Nullable String property) {
		if (property == null || property.isBlank())
			return null;
		String[] parts = property.split("\\|", -1);
		if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank())
			return null;
		return new WorldRun(parts[0].trim(), parts[1].trim(), parts[2].trim());
	}

	/** A world seed as the game takes one: a number as it is, anything else by its hash; a random one if there's none. */
	public static long seed(@Nullable String seed) {
		if (seed == null || seed.isBlank())
			return new java.util.Random().nextLong();
		try {
			return Long.parseLong(seed.trim());
		} catch (NumberFormatException e) {
			return seed.trim().hashCode();
		}
	}
	/** Ticks to let the world settle (the player stood on the ground, chunks round him loaded) before the tests. */
	private static final int SETTLE_TICKS = 60;

	private enum Step { START, JOINING, SETTLING, WATCHING }
	public static final double FOLLOW_DISTANCE = RiderWatch.FOLLOW_DISTANCE;

	private static Step step = Step.START;
	private static int ticks;
	@Nullable
	private static WorldRun worldRun;

	private DevWatch() {}

	/**
	 * The test command for what the property asks for: everything, or one test by its name (the method's, in lower
	 * case, as the tests are registered; a {@code postrider:} in front is dropped).
	 */
	public static String command(String wanted) {
		String name = wanted == null ? "" : wanted.trim().toLowerCase(Locale.ROOT);
		if (name.isEmpty() || name.equals("all") || name.equals("true"))
			return "test runall";
		return "test run " + name.substring(name.indexOf(':') + 1);
	}

	/** Switches the run on if the game was started for it. */
	public static void registerIfWanted() {
		worldRun = worldRun(System.getProperty(WORLD_PROPERTY));
		if (worldRun != null)
			PostRider.LOGGER.info("Post Rider watch run: the game will open the world {} and post from {} to {}", worldRun.world(), worldRun.from(), worldRun.to());
		else if (System.getProperty(PROPERTY) != null)
			PostRider.LOGGER.info("Post Rider watch run: the game will open a world and run the in-world tests ({})", command(System.getProperty(PROPERTY)));
		else
			return;
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
				if (worldRun != null) {
					openWorldToWatch(minecraft, worldRun);
				} else if (minecraft.getLevelSource().levelExists(WORLD)) {
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
				if (worldRun != null) {
					server.execute(() -> postAndWatch(server, worldRun));
					return;
				}
				String command = command(System.getProperty(PROPERTY));
				// On the server's own thread (this is the client's): the tests load chunks and set blocks as they're placed
				server.execute(() -> {
					ServerPlayer player = server.getPlayerList().getPlayers().get(0);
					// Daytime, and staying that way, so the post office is open however long the watching takes
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gamerule doDaylightCycle false");
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "time set day");
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "test clearall");
					// Somewhere fresh each time: a test is built on whatever ground is under the player, and the world keeps
					// what earlier runs built (the ground of a flat world is at -61; he's stood on it)
					long spot = (System.nanoTime() / 1000) % 64;
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "tp @s " + (spot * 2048 + 512) + " -60 " + ((spot * 7919) % 64 * 2048 + 512));
					PostRider.LOGGER.info("Post Rider watch run: /{}", command);
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
					// A spectator, kept off the rider most recently out
					RiderWatch.start(player);
				});
			}
			case WATCHING -> {}
		}
	}

	/** Opens the world a trip is to be watched across: one of the player's (copied in by Gradle), or a fresh ordinary one. */
	private static void openWorldToWatch(Minecraft minecraft, WorldRun run) {
		if (!run.fresh()) {
			if (!minecraft.getLevelSource().levelExists(run.world())) {
				PostRider.LOGGER.error("Post Rider watch run: no world called {} in this run's saves", run.world());
				return;
			}
			minecraft.createWorldOpenFlows().openWorld(run.world(), () -> minecraft.setScreen(new TitleScreen()));
			return;
		}
		String folder = run.folder(System.getProperty(SEED_PROPERTY));
		if (minecraft.getLevelSource().levelExists(folder)) {
			minecraft.createWorldOpenFlows().openWorld(folder, () -> minecraft.setScreen(new TitleScreen()));
			return;
		}
		LevelSettings settings = new LevelSettings(folder, GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(), WorldDataConfiguration.DEFAULT);
		minecraft.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(seed(System.getProperty(SEED_PROPERTY)), true, false),
			access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
			new TitleScreen());
	}

	/** Posts the parcel the run asks for and sets the player following the rider (on the server's own thread). */
	private static void postAndWatch(MinecraftServer server, WorldRun run) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		try {
			PendingDelivery posted = DevHeadless.postNamed(server, new String[] {run.from(), run.to()});
			PostRider.LOGGER.info("Post Rider watch run: posted from {} at {} to {}", posted.fromName(), posted.from().pos().toShortString(), posted.toName());
		} catch (IllegalArgumentException e) {
			PostRider.LOGGER.error("Post Rider watch run: {}", e.getMessage());
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Post Rider watch run: " + e.getMessage()));
			return;
		}
		RiderWatch.start(player);
	}

	/** As {@link RiderWatch#viewpoint}. */
	public static Vec3 viewpoint(Vec3 riderEyes, Vec3 look) {
		return RiderWatch.viewpoint(riderEyes, look);
	}

	/** As {@link RiderWatch#onTheRoad}. */
	public static boolean onTheRoad(PostmanEntity rider) {
		return RiderWatch.onTheRoad(rider);
	}
}
