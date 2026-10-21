package com.comedymonkey.postrider;

import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.Journey;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's settings (config/postrider-server.toml). Until the config has loaded (and in unit tests) every setting
 * reads as its default.
 */
public final class PostRiderConfig {
	public static final ModConfigSpec SPEC;
	private static final ModConfigSpec.IntValue MAX_RIDERS;
	private static final ModConfigSpec.IntValue LOOKAHEAD;
	private static final ModConfigSpec.IntValue POTIONS_PER_DAY;
	private static final ModConfigSpec.DoubleValue BARGE_SPEED;
	private static final ModConfigSpec.IntValue FIRST_COLLECTION;
	private static final ModConfigSpec.IntValue LAST_COLLECTION;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		builder.push("riders");
		MAX_RIDERS = builder
			.comment("How many riders can be out on deliveries at once. Each is one of the crew of " + Roster.ALL.size() + ", so that's the most.")
			.defineInRange("maxRiders", Roster.ALL.size(), 1, Roster.ALL.size());
		LOOKAHEAD = builder
			.comment("How far ahead (in blocks) a rider plans his route, so he can find his way round ravines and lakes.",
				"Each rider keeps enough chunks loaded to see this far (48 blocks: 7x7 chunks, of which only the middle 3x3 tick).",
				"Lower it if all those riders' chunks weigh on the server.")
			.defineInRange("lookahead", (int) Journey.STRETCH, 16, 64);
		POTIONS_PER_DAY = builder
			.comment("Potions of Instant Health II each rider sets out with every day (restocked at 6am), drunk below half health.")
			.defineInRange("potionsPerDay", PostmanEntity.POTIONS_PER_DAY, 0, 10);
		BARGE_SPEED = builder
			.comment("How fast the ferry barge is rowed across water, in blocks per second (a boat manages about 8).")
			.defineInRange("bargeSpeed", BargeEntity.BLOCKS_PER_SECOND, 2.0, 16.0);
		builder.pop();
		builder.push("collection");
		FIRST_COLLECTION = builder
			.comment("The hour (0-23) riders start setting out with the day's mail. Riders on the road still camp from dusk to 6am.")
			.defineInRange("firstCollection", 6, 0, 23);
		LAST_COLLECTION = builder
			.comment("The hour (1-24) after which no more riders set out that day; 18 is dusk.")
			.defineInRange("lastCollection", 18, 1, 24);
		builder.pop();
		SPEC = builder.build();
	}

	private PostRiderConfig() {}

	private static int value(ModConfigSpec.IntValue setting) {
		return SPEC.isLoaded() ? setting.get() : setting.getDefault();
	}

	public static int maxRiders() {
		return value(MAX_RIDERS);
	}

	public static double lookahead() {
		return value(LOOKAHEAD);
	}

	public static int potionsPerDay() {
		return value(POTIONS_PER_DAY);
	}

	public static double bargeBlocksPerSecond() {
		return SPEC.isLoaded() ? BARGE_SPEED.get() : BARGE_SPEED.getDefault();
	}

	public static int firstCollection() {
		return value(FIRST_COLLECTION);
	}

	public static int lastCollection() {
		return value(LAST_COLLECTION);
	}
}
