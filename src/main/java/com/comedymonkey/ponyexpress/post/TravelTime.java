package com.comedymonkey.ponyexpress.post;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

/** How long mail took to arrive, in game time: a game day is 24000 ticks, an hour 1000. */
public final class TravelTime {
	public static final long TICKS_PER_DAY = 24000L;
	public static final long TICKS_PER_HOUR = 1000L;

	private TravelTime() {}

	/** {days, hours, minutes} in a span of game ticks. */
	public static long[] split(long ticks) {
		long days = ticks / TICKS_PER_DAY;
		long hours = ticks % TICKS_PER_DAY / TICKS_PER_HOUR;
		// Whole-number sums: a fractional ticks-per-minute turns 30 minutes into 29.999...
		long minutes = ticks % TICKS_PER_HOUR * 60 / TICKS_PER_HOUR;
		return new long[] {days, hours, minutes};
	}

	/** "Took 1 day, 3 hours", using at most the two largest units that aren't zero. */
	public static Component describe(long ticks) {
		long[] parts = split(Math.max(0, ticks));
		String[] units = {"day", "hour", "minute"};
		List<Component> shown = new ArrayList<>();
		for (int i = 0; i < parts.length && shown.size() < 2; i++)
			if (parts[i] > 0)
				shown.add(Component.translatable("time.ponyexpress." + units[i] + (parts[i] == 1 ? "" : "s"), parts[i]));
		if (shown.isEmpty())
			return Component.translatable("time.ponyexpress.took_moments");
		Component span = shown.size() == 1 ? shown.get(0) : Component.translatable("time.ponyexpress.pair", shown.get(0), shown.get(1));
		return Component.translatable("time.ponyexpress.took", span);
	}
}
