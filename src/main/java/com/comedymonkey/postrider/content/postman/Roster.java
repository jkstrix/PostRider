package com.comedymonkey.postrider.content.postman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.horse.Markings;
import net.minecraft.world.entity.animal.horse.Variant;

/**
 * The Post Rider crew: twenty riders, each with their own face (textures/entity/rider/&lt;id&gt;.png, made by
 * tools/rider_skins.js), build and horse. Only one of each can be out at a time, so you get to know them: Chris on
 * Lightning is the quickest, Gus on Molasses the slowest.
 */
public final class Roster {
	/** Which long-hair parts of the model a rider wears, if any. */
	public enum Hair {
		SHORT,
		BUN,
		BRAID,
		PIGTAILS,
		LONG;

		/** The model part that shows this hair, or null for hair that's all on the head. */
		@Nullable
		public String part() {
			return switch (this) {
				case SHORT -> null;
				case BUN -> "hair_bun";
				case BRAID -> "hair_braid";
				case PIGTAILS -> "hair_pigtails";
				case LONG -> "hair_back";
			};
		}
	}

	/** A rider's own horse: always the same coat, markings, name and speed. */
	public record Horse(String name, Variant coat, Markings markings, double blocksPerSecond) {
		/** The movement speed attribute that gives this many blocks per second. */
		public double speed() {
			return blocksPerSecond / PostmanHorse.BLOCKS_PER_SECOND_PER_SPEED;
		}
	}

	public record Rider(String id, String name, boolean she, Hair hair, Horse horse) {
		/** Women ride on the slim model (narrower head and arms). */
		public boolean slim() {
			return she;
		}

		/** "his" or "her". */
		public String possessive() {
			return she ? "her" : "his";
		}

		public String texture() {
			return "textures/entity/rider/" + id + ".png";
		}
	}

	private static Rider rider(String id, String name, boolean she, Hair hair, String horse, Variant coat, Markings markings, double blocksPerSecond) {
		return new Rider(id, name, she, hair, new Horse(horse, coat, markings, blocksPerSecond));
	}

	public static final List<Rider> ALL = List.of(
		rider("chris", "Chris", false, Hair.SHORT, "Lightning", Variant.CHESTNUT, Markings.WHITE, 10.5),
		rider("mattie", "Martha \"Mattie\" Cole", true, Hair.BRAID, "Biscuit", Variant.CREAMY, Markings.WHITE_DOTS, 8.2),
		rider("eli", "Eli Whitaker", false, Hair.SHORT, "Old Sal", Variant.GRAY, Markings.NONE, 7.2),
		rider("rosa", "Rosa Delgado", true, Hair.BUN, "Canela", Variant.CHESTNUT, Markings.WHITE_FIELD, 9.0),
		rider("jeb", "Jeb Stone", false, Hair.SHORT, "Boulder", Variant.DARK_BROWN, Markings.NONE, 7.6),
		rider("tom", "Tom Hale", false, Hair.SHORT, "Duke", Variant.BLACK, Markings.WHITE, 9.4),
		rider("clara", "Clara Bell", true, Hair.PIGTAILS, "Dandelion", Variant.CREAMY, Markings.WHITE, 8.0),
		rider("silas", "Silas Crane", false, Hair.SHORT, "Shadow", Variant.BLACK, Markings.NONE, 9.2),
		rider("hattie", "Hattie Mae Brooks", true, Hair.BUN, "Pepper", Variant.GRAY, Markings.BLACK_DOTS, 7.4),
		rider("wyatt", "Wyatt Kincaid", false, Hair.SHORT, "Ranger", Variant.BROWN, Markings.WHITE_FIELD, 9.6),
		rider("ike", "Ike Delaney", false, Hair.SHORT, "Rusty", Variant.CHESTNUT, Markings.NONE, 8.4),
		rider("lottie", "Lottie Park", true, Hair.LONG, "Pippin", Variant.WHITE, Markings.BLACK_DOTS, 8.8),
		rider("gus", "Gus Tanner", false, Hair.SHORT, "Molasses", Variant.DARK_BROWN, Markings.NONE, 7.0),
		rider("nell", "\"One-Eyed\" Nell Harper", true, Hair.LONG, "Raven", Variant.BLACK, Markings.WHITE, 9.1),
		rider("abe", "Abe Mercer", false, Hair.SHORT, "Sterling", Variant.GRAY, Markings.WHITE, 8.1),
		rider("billy", "Billy Sparrow", false, Hair.SHORT, "Sprout", Variant.CHESTNUT, Markings.WHITE_DOTS, 8.9),
		rider("ada", "Ada Quill", true, Hair.LONG, "Inkwell", Variant.BLACK, Markings.NONE, 8.3),
		rider("hank", "Hank Morrow", false, Hair.SHORT, "Barley", Variant.CREAMY, Markings.NONE, 7.8),
		rider("sadie", "Sadie Fontaine", true, Hair.LONG, "Ruby", Variant.CHESTNUT, Markings.WHITE_FIELD, 9.3),
		rider("juan", "Juan Herrera", false, Hair.SHORT, "Paloma", Variant.WHITE, Markings.NONE, 9.5));

	private Roster() {}

	@Nullable
	public static Rider byId(String id) {
		for (Rider rider : ALL)
			if (rider.id().equals(id))
				return rider;
		return null;
	}

	/** Someone who isn't already out on the road, picked at random; null if the whole crew is out. */
	@Nullable
	public static Rider pickFree(Collection<String> busy, RandomSource random) {
		List<Rider> free = new ArrayList<>();
		for (Rider rider : ALL)
			if (!busy.contains(rider.id()))
				free.add(rider);
		return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
	}
}
