package com.comedymonkey.postrider.client;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.gui.MailboxScreen;
import com.comedymonkey.postrider.content.mailbox.MailboxInfoCache;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * For development only: started with the system property {@code postrider.screenshots} (the {@code runScreenshots}
 * Gradle task), the game makes a flat world of its own, opens the mailbox screen with awkward things to show (the
 * longest names and addresses, every kind of message), saves a screenshot of each to screenshots/postrider, and
 * quits. Lets the screens be checked by eye without anyone clicking through them.
 */
public final class DevScreenshots {
	public static final String PROPERTY = "postrider.screenshots";
	private static final String WORLD = "postrider_screenshots";
	/** Ticks to let the world settle before the first screenshot, and between opening a screen and taking it. */
	private static final int SETTLE_TICKS = 60, SHOW_TICKS = 6;
	private static final BlockPos BOX = new BlockPos(0, 64, 0);
	private static final String LONG_ADDRESS = "Steve's Ranch and Stable";
	private static final String LONG_OWNER = "WWWWWWWWWWWWWWWW";
	private static final List<String> ADDRESSES = List.of(LONG_ADDRESS, "Alex's Inn", "The Old Mill at Willow Bend", "Post Office", "WWWWWWWWWWWWWWWWWWWWWWWW");

	/** One screen to photograph: which tab, the mailbox's own name, and the message showing (if any). */
	private record Shot(String file, int tab, String name, String owner, Component notice, boolean good, boolean pickAddress) {}

	private static final Queue<Shot> SHOTS = new ArrayDeque<>(List.of(
		new Shot("inbox", MailboxMenu.TAB_INBOX, LONG_ADDRESS, LONG_OWNER, null, true, false),
		new Shot("send_unnamed", MailboxMenu.TAB_SEND, "", LONG_OWNER, null, true, false),
		new Shot("send_picked", MailboxMenu.TAB_SEND, LONG_ADDRESS, LONG_OWNER, null, true, true),
		new Shot("send_waiting", MailboxMenu.TAB_SEND, LONG_ADDRESS, LONG_OWNER,
			Component.translatable("message.postrider.mailbox.queued", LONG_ADDRESS), true, true),
		new Shot("send_waiting_letter", MailboxMenu.TAB_SEND, LONG_ADDRESS, LONG_OWNER,
			Component.translatable("message.postrider.mailbox.queued_letter", LONG_ADDRESS), true, true),
		new Shot("send_later", MailboxMenu.TAB_SEND, LONG_ADDRESS, LONG_OWNER,
			Component.translatable("message.postrider.mailbox.queued_later", LONG_ADDRESS, "12pm"), true, true),
		new Shot("send_gone", MailboxMenu.TAB_SEND, LONG_ADDRESS, LONG_OWNER,
			Component.translatable("message.postrider.mailbox.unknown_address", LONG_ADDRESS), false, true),
		new Shot("send_other_dimension", MailboxMenu.TAB_SEND, LONG_ADDRESS, LONG_OWNER,
			Component.translatable("message.postrider.mailbox.other_dimension", LONG_ADDRESS), false, true),
		new Shot("settings", MailboxMenu.TAB_SETTINGS, LONG_ADDRESS, LONG_OWNER, null, true, false),
		new Shot("settings_taken", MailboxMenu.TAB_SETTINGS, LONG_ADDRESS, LONG_OWNER,
			Component.translatable("message.postrider.mailbox.name_taken", LONG_ADDRESS), false, false),
		new Shot("settings_bad_character", MailboxMenu.TAB_SETTINGS, LONG_ADDRESS, LONG_OWNER,
			Component.translatable("message.postrider.mailbox.name_bad_character"), false, false)));

	private enum Step { START, JOINING, SETTLING, SHOWING, DONE }

	private static Step step = Step.START;
	private static int ticks;

	private DevScreenshots() {}

	/** Switches the run on if the game was started for it. */
	public static void registerIfWanted() {
		if (System.getProperty(PROPERTY) == null)
			return;
		PostRider.LOGGER.info("Post Rider screenshot run: the game will take its screenshots and quit");
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
				// No tutorial hints over the screens
				minecraft.getTutorial().setStep(TutorialSteps.NONE);
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
					try {
						Files.createDirectories(minecraft.gameDirectory.toPath().resolve("screenshots").resolve("postrider"));
					} catch (IOException e) {
						PostRider.LOGGER.error("Couldn't make the screenshot folder", e);
					}
					step = Step.SETTLING;
					ticks = 0;
				}
			}
			case SETTLING -> {
				if (ticks >= SETTLE_TICKS) {
					step = Step.SHOWING;
					ticks = 0;
					show(minecraft, SHOTS.peek());
				}
			}
			case SHOWING -> {
				if (ticks < SHOW_TICKS)
					return;
				Shot shot = SHOTS.poll();
				Screenshot.grab(minecraft.gameDirectory, "postrider/" + shot.file() + ".png", minecraft.getMainRenderTarget(),
					message -> PostRider.LOGGER.info("Screenshot {}: {}", shot.file(), message.getString()));
				ticks = 0;
				if (SHOTS.isEmpty()) {
					step = Step.DONE;
				} else {
					show(minecraft, SHOTS.peek());
				}
			}
			case DONE -> {
				// Give the last screenshot time to be written before quitting
				if (ticks >= SHOW_TICKS * 4) {
					PostRider.LOGGER.info("Post Rider screenshot run: done");
					minecraft.stop();
				}
			}
		}
	}

	/** Opens the mailbox screen as the server would, with this shot's names, addresses, tab and message. */
	private static void show(Minecraft minecraft, Shot shot) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), minecraft.level.registryAccess());
		buf.writeBlockPos(BOX);
		buf.writeUtf(shot.name());
		MailboxMenu menu = new MailboxMenu(100, minecraft.player.getInventory(), buf);
		menu.setTab(shot.tab());
		MailboxInfoCache.set(BOX, shot.name(), shot.owner(), ADDRESSES);
		if (shot.notice() != null)
			MailboxInfoCache.notice(shot.notice(), shot.good());
		else
			MailboxInfoCache.clearNotice();
		MailboxScreen screen = new MailboxScreen(menu, minecraft.player.getInventory(), Component.translatable("container.postrider.mailbox"));
		minecraft.setScreen(screen);
		if (shot.pickAddress())
			screen.pickAddress(ADDRESSES.get(0));
	}
}
