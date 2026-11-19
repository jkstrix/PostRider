package com.comedymonkey.postrider.client.dailies;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.gui.LetterEditScreen;
import com.comedymonkey.postrider.client.gui.LetterReadScreen;
import com.comedymonkey.postrider.client.gui.MailboxScreen;
import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.registry.ModDataComponents;

import comedymonkey.dailies.api.compat.Claims;
import comedymonkey.dailies.api.compat.DailiesCompat;
import comedymonkey.dailies.api.compat.DailiesCompats;
import comedymonkey.dailies.api.compat.DisplayTest;
import comedymonkey.dailies.api.compat.ScreenPart;
import comedymonkey.dailies.api.compat.WindowExtras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Post Rider's screens in Dailies' recordings: the compat, written into the mod itself. This class and the others in
 * this package that implement Dailies' interfaces are only touched when Dailies is loaded (see PostRiderClient), so Post
 * Rider runs as before without it; it is compiled against Dailies' API jar in libs/, which is never packed in.
 * <ul>
 * <li>The mailbox is a window the server opens, so Dailies records and draws it already, slots and all. What its
 * screen holds that the server never sent is added here: the tab open, the address picked in the ledger and its scroll,
 * and the address box. Without it, playback would only ever show the inbox.</li>
 * <li>Reading a letter is a screen only the player's game knows. When the letter is in one of the reader's hands, as it
 * always is when it was opened by using it, the recording already holds it as their held item, so only the hand and
 * the page are kept, with a check that it is the same letter; a letter that isn't is copied.</li>
 * <li>Writing a letter: the pages as they stand, the page open, the cursor and whether the seal is being confirmed, a
 * few times a second while it changes. Drawn read only.</li>
 * </ul>
 */
public final class PostRiderDailies implements DailiesCompat {
	public static final String ID = PostRider.MOD_ID + ":screens";
	/** Raise when a state in DailiesStates changes shape; the parts below must still read every version before it. */
	static final int VERSION = 1;
	/** Typing is written at most five times a second. */
	private static final int TYPING_TICKS = 4;

	private PostRiderDailies() {}

	/** Registers the compat. Only call it when Dailies is loaded. */
	public static void register() {
		DailiesCompats.register(new PostRiderDailies());
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int version() {
		return VERSION;
	}

	@Override
	public void claim(Claims claims) {
		claims.windowExtras("mailbox", PostRider.MOD_ID + ":mailbox", new WindowExtras() {
			@Override
			@Nullable
			public byte[] capture(AbstractContainerScreen<?> screen) {
				if (!(screen instanceof MailboxScreen mailbox))
					return null;
				return DailiesStates.mailbox(new DailiesStates.Mailbox(mailbox.getMenu().getTab(), mailbox.selectedAddress(), mailbox.scrollRows(),
					mailbox.typedAddress()));
			}

			@Override
			public void apply(AbstractContainerScreen<?> screen, byte[] state, int version) {
				if (screen instanceof MailboxScreen mailbox) {
					DailiesStates.Mailbox recorded = DailiesStates.readMailbox(state);
					mailbox.showRecorded(recorded.tab(), recorded.selected(), recorded.scroll(), recorded.typed());
				}
			}

			@Override
			public int ticksBetweenWrites() {
				return TYPING_TICKS;
			}
		});

		claims.screen("letter_read", LetterReadScreen.class, new ScreenPart() {
			@Override
			public byte[] capture(Screen screen) {
				LetterReadScreen reading = (LetterReadScreen) screen;
				LetterContent letter = reading.letter();
				Player player = Minecraft.getInstance().player;
				if (player != null)
					for (InteractionHand hand : InteractionHand.values())
						if (letter.equals(player.getItemInHand(hand).get(ModDataComponents.LETTER.get())))
							return DailiesStates.reading(new DailiesStates.Reading(hand, DailiesStates.check(letter), null, reading.page()));
				return DailiesStates.reading(new DailiesStates.Reading(null, 0, letter, reading.page()));
			}

			@Override
			@Nullable
			public Screen build(byte[] state, int version) {
				DailiesStates.Reading recorded = DailiesStates.readReading(state);
				LetterContent letter = recorded.copy();
				if (letter == null) {
					// The letter in the recorder's hand, as the recording shows them holding it
					Player recorder = DailiesCompats.recorder();
					letter = recorder == null ? null : recorder.getItemInHand(recorded.hand()).get(ModDataComponents.LETTER.get());
					if (letter == null || DailiesStates.check(letter) != recorded.check())
						return null;
				}
				return new LetterReadScreen(letter, recorded.page());
			}

			@Override
			public int[] corner(Screen screen) {
				LetterReadScreen reading = (LetterReadScreen) screen;
				return new int[] {reading.pageX(), reading.pageY()};
			}
		});

		claims.screen("letter_write", LetterEditScreen.class, new ScreenPart() {
			@Override
			public byte[] capture(Screen screen) {
				LetterEditScreen writing = (LetterEditScreen) screen;
				return DailiesStates.writing(new DailiesStates.Writing(writing.pagesNow(), writing.page(), writing.cursor(), writing.confirmingSeal()));
			}

			@Override
			public Screen build(byte[] state, int version) {
				DailiesStates.Writing recorded = DailiesStates.readWriting(state);
				return LetterEditScreen.readOnly(recorded.pages(), recorded.page(), recorded.cursor(), recorded.confirming());
			}

			@Override
			public int[] corner(Screen screen) {
				LetterEditScreen writing = (LetterEditScreen) screen;
				return new int[] {writing.pageX(), writing.pageY()};
			}

			@Override
			public int ticksBetweenWrites() {
				return TYPING_TICKS;
			}
		});
	}

	@Override
	public DisplayTest displayTest() {
		return new PostRiderDisplayTest();
	}
}
