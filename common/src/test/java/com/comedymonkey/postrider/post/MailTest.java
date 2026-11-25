package com.comedymonkey.postrider.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.client.gui.PostOfficeStyle;
import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.letter.LetterText;
import com.comedymonkey.postrider.content.parcel.ParcelItem;
import com.comedymonkey.postrider.content.parcel.ParcelLabel;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.comedymonkey.postrider.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

class MailTest {
	private static ItemStack letter(String words) {
		ItemStack stack = new ItemStack(ModItems.WRITTEN_LETTER.get());
		stack.set(ModDataComponents.LETTER.get(), new LetterContent(List.of(words), "Steve", Optional.empty()));
		return stack;
	}

	private static PendingDelivery delivery(ItemStack... items) {
		return new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Steve", "Steve's Ranch",
			GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), "Alex's Inn", List.of(items), 0);
	}

	@Test
	void aLetterOnItsOwnArrivesAsTheLetterWithAPostmark() {
		ItemStack mail = Mail.wrap(delivery(letter("Howdy")), 12);
		assertTrue(mail.is(ModItems.WRITTEN_LETTER.get()));
		LetterContent content = mail.get(ModDataComponents.LETTER.get());
		assertEquals("Howdy", content.pages().get(0));
		assertEquals(new LetterContent.Postmark("Steve's Ranch", 12), content.postmark().orElseThrow());
	}

	@Test
	void aLetterWithAnythingElseArrivesInsideAParcel() {
		ItemStack mail = Mail.wrap(delivery(letter("Here's the iron"), new ItemStack(Items.IRON_INGOT, 20)), 5);
		assertTrue(mail.is(ModItems.PARCEL.get()));
		List<ItemStack> inside = ParcelItem.contents(mail);
		assertEquals(2, inside.size());
		assertTrue(inside.get(0).get(ModDataComponents.LETTER.get()).postmark().isPresent());
		assertEquals(20, inside.get(1).getCount());
	}

	@Test
	void theTimeItTookGoesOnTheLabelAndPostmarks() {
		ItemStack parcel = Mail.wrap(delivery(letter("Hi"), new ItemStack(Items.BREAD)), 7, 27000);
		assertEquals(27000, parcel.get(ModDataComponents.PARCEL_LABEL.get()).travelTicks());
		ItemStack inside = ParcelItem.contents(parcel).get(0);
		assertEquals(27000, inside.get(ModDataComponents.LETTER.get()).postmark().orElseThrow().travelTicks());

		ItemStack alone = Mail.wrap(delivery(letter("Hi")), 7, 1500);
		assertEquals(1500, alone.get(ModDataComponents.LETTER.get()).postmark().orElseThrow().travelTicks());
	}

	@Test
	void twoLettersTravelAsAParcel() {
		assertFalse(Mail.isLetterOnly(List.of(letter("a"), letter("b"))));
		assertTrue(Mail.wrap(delivery(letter("a"), letter("b")), 1).is(ModItems.PARCEL.get()));
	}

	@Test
	void itemsAloneTravelAsAParcelWithItsLabel() {
		ItemStack mail = Mail.wrap(delivery(new ItemStack(Items.BREAD, 3)), 40);
		ParcelLabel label = mail.get(ModDataComponents.PARCEL_LABEL.get());
		assertNotNull(label);
		assertEquals("Steve's Ranch", label.from());
		assertEquals("Alex's Inn", label.to());
		assertEquals("Steve", label.sender());
		assertEquals(40, label.day());
	}

	@Test
	void aBlankLetterIsNotALetterToPost() {
		assertFalse(Mail.isLetterOnly(List.of(new ItemStack(ModItems.LETTER.get()))));
	}

	@Test
	void aForwardedLetterKeepsItsFirstPostmark() {
		ItemStack old = letter("Old news");
		old.set(ModDataComponents.LETTER.get(), old.get(ModDataComponents.LETTER.get()).postmarked(new LetterContent.Postmark("Far Away", 2)));
		ItemStack mail = Mail.wrap(delivery(old), 9);
		assertEquals("Far Away", mail.get(ModDataComponents.LETTER.get()).postmark().orElseThrow().from());
	}

	@Test
	void wrappingDoesNotChangeTheQueuedItems() {
		ItemStack original = letter("Howdy");
		PendingDelivery pending = delivery(original);
		Mail.wrap(pending, 3);
		assertTrue(pending.items().get(0).get(ModDataComponents.LETTER.get()).postmark().isEmpty());
	}

	@Test
	void theFailureLetterSaysWhatHappenedAndWhere() {
		ItemStack letter = Mail.failureLetter(delivery(new ItemStack(Items.BREAD)), "Wyatt Holloway was slain by Zombie.",
			new BlockPos(1204, 71, -388), true, 9);
		LetterContent content = letter.get(ModDataComponents.LETTER.get());
		String text = String.join("\n", content.pages());
		assertTrue(text.contains("Dear Steve"));
		assertTrue(text.contains("parcel to Alex's Inn"));
		assertTrue(text.contains("slain by Zombie"));
		assertTrue(text.contains("X 1204, Y 71, Z -388"));
		assertTrue(text.contains("marked LOST"));
		assertEquals(Mail.COMPANY, content.author());
		assertEquals(9, content.postmark().orElseThrow().day());
	}

	@Test
	void theFailureLetterSaysWhenThereWasNowhereToLeaveIt() {
		ItemStack letter = Mail.failureLetter(delivery(letter("Hi")), "Lost.", BlockPos.ZERO, false, 1);
		String text = String.join("\n", letter.get(ModDataComponents.LETTER.get()).pages());
		assertTrue(text.contains("your letter to"));
		assertTrue(text.contains("scattered"));
	}

	@Test
	void daysCountFromOne() {
		assertEquals(1, Mail.day(0));
		assertEquals(1, Mail.day(23999));
		assertEquals(2, Mail.day(24000));
	}

	@Test
	void theFailureLetterAlwaysFitsItsPagesWithTheLocationUpFront() {
		// The longest names and account there can be, and a generous six pixels a character
		PendingDelivery longest = new PendingDelivery(UUID.randomUUID(), UUID.randomUUID(), "Sixteen_Letters_",
			"Twenty Four Letter Place", GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
			"Another Twenty-Four Spot", List.of(new ItemStack(Items.BREAD)), 0);
		String reason = "\"One-Eyed\" Nell Harper found the mailbox at Another Twenty-Four Spot too full to take it.";
		for (boolean left : new boolean[] {true, false}) {
			LetterContent content = Mail.failureLetter(longest, reason, new BlockPos(-29999984, -64, -29999984), left, 9999)
				.get(ModDataComponents.LETTER.get());
			for (String page : content.pages()) {
				LetterText layout = new LetterText(s -> s.length() * 6, PostOfficeStyle.PAGE_TEXT_WIDTH, 100, 10000);
				layout.setText(page);
				assertTrue(layout.lines().size() <= PostOfficeStyle.PAGE_LINES, "a page runs over: " + page);
			}
			assertTrue(content.pages().get(0).contains("-29999984"), "where to find it is on the first page");
		}
	}

	@Test
	void openingAParcelThrowsOutEverythingThatWasPacked() {
		// Several stacks of the same thing too: these all went missing when parcels unpacked into the hand's slot
		ItemStack parcel = Mail.wrap(delivery(new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), letter("Lunch"),
			new ItemStack(Items.IRON_INGOT, 20)), 3);
		List<ItemStack> contents = ParcelItem.contents(parcel);
		assertEquals(4, contents.size());
		assertEquals(128, contents.stream().filter(item -> item.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum());
		assertEquals(20, contents.get(3).getCount());
		assertTrue(contents.get(2).is(ModItems.WRITTEN_LETTER.get()));
		assertTrue(ParcelItem.contents(new ItemStack(ModItems.PARCEL.get())).isEmpty(), "an empty parcel throws out nothing");
	}
}
