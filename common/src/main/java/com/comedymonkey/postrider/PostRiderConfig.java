package com.comedymonkey.postrider;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.Journey;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;

/**
 * The server's settings (config/postrider-server.toml). Until the config has loaded (and in unit tests) every setting
 * reads as its default. The settings are described here; each mod loader builds its config file from the description
 * and hands the values back through {@link #setValues}.
 */
public final class PostRiderConfig {
	/** The file's name, kept from before 26.x so a server's settings carry over. */
	public static final String FILE_NAME = "postrider-server.toml";

	/**
	 * One setting: the section it's in, its key, its comment lines, its default and its range. A whole-number setting
	 * has Integer bounds, a decimal one Double bounds.
	 */
	public record Setting(String section, String key, List<String> comment, Number defaultValue, Number min, Number max) {
		public boolean isWhole() {
			return defaultValue instanceof Integer;
		}
	}

	public static final Setting MAX_RIDERS = new Setting("riders", "maxRiders",
		List.of("How many riders can be out on deliveries at once. Each is one of the crew of " + Roster.ALL.size() + ", so that's the most."),
		Roster.ALL.size(), 1, Roster.ALL.size());
	public static final Setting LOOKAHEAD = new Setting("riders", "lookahead",
		List.of("How far ahead (in blocks) a rider plans his route, so he can find his way round ravines and lakes.",
			"Each rider keeps enough chunks loaded to see this far (48 blocks: 7x7 chunks, of which only the middle 3x3 tick).",
			"Lower it if all those riders' chunks weigh on the server."),
		(int) Journey.STRETCH, 16, 64);
	public static final Setting POTIONS_PER_DAY = new Setting("riders", "potionsPerDay",
		List.of("Potions of Instant Health II each rider sets out with every day (restocked at 6am), drunk below half health."),
		PostmanEntity.POTIONS_PER_DAY, 0, 10);
	public static final Setting BARGE_SPEED = new Setting("riders", "bargeSpeed",
		List.of("How fast the ferry barge is rowed across water, in blocks per second (a boat manages about 8)."),
		BargeEntity.BLOCKS_PER_SECOND, 2.0, 16.0);
	public static final Setting FIRST_COLLECTION = new Setting("collection", "firstCollection",
		List.of("The hour (0-23) riders start setting out with the day's mail. Riders on the road still camp from dusk to 6am."),
		6, 0, 23);
	public static final Setting LAST_COLLECTION = new Setting("collection", "lastCollection",
		List.of("The hour (1-24) after which no more riders set out that day; 18 is dusk."),
		18, 1, 24);

	/** Every setting, in the order (and sections) they appear in the file. */
	public static final List<Setting> ALL = List.of(MAX_RIDERS, LOOKAHEAD, POTIONS_PER_DAY, BARGE_SPEED, FIRST_COLLECTION, LAST_COLLECTION);

	/** The loaded values, as the mod loader's config reads them. */
	public interface Values {
		boolean isLoaded();

		Number get(Setting setting);
	}

	@Nullable
	private static Values values;

	private PostRiderConfig() {}

	/** Called once by the mod loader's config, when it has been built. */
	public static void setValues(Values loaded) {
		values = loaded;
	}

	private static Number value(Setting setting) {
		return values != null && values.isLoaded() ? values.get(setting) : setting.defaultValue();
	}

	public static int maxRiders() {
		return value(MAX_RIDERS).intValue();
	}

	public static double lookahead() {
		return value(LOOKAHEAD).intValue();
	}

	public static int potionsPerDay() {
		return value(POTIONS_PER_DAY).intValue();
	}

	public static double bargeBlocksPerSecond() {
		return value(BARGE_SPEED).doubleValue();
	}

	public static int firstCollection() {
		return value(FIRST_COLLECTION).intValue();
	}

	public static int lastCollection() {
		return value(LAST_COLLECTION).intValue();
	}
}
