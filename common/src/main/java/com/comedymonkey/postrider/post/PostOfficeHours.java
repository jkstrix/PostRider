package com.comedymonkey.postrider.post;

/**
 * When riders work. Mail posted after dark waits until 6am to be collected, and riders on the road camp through the
 * same hours. Times are Minecraft day time, where 0 is 6am and 12000 is 6pm.
 */
public final class PostOfficeHours {
	public static final long DAY_LENGTH = 24000L;
	/** 6am. */
	public static final long OPENS = 0L;
	/** 6pm, when dusk falls. */
	public static final long CLOSES = 12000L;
	/** About 7:11pm: the surface is dark enough for monsters, and a camping rider turns in. */
	public static final long MOBS_COME_OUT = 13188L;

	private PostOfficeHours() {}

	public static long timeOfDay(long dayTime) {
		return Math.floorMod(dayTime, DAY_LENGTH);
	}

	/** Daytime, from 6am until dusk: riders can set out and keep riding. */
	public static boolean isOpen(long dayTime) {
		long time = timeOfDay(dayTime);
		return time >= OPENS && time < CLOSES;
	}

	/** The day time of an hour of the clock (0-24): 6am is 0, noon 6000, midnight 18000. */
	public static long dayTimeOf(int hour) {
		return Math.floorMod((hour - 6) * 1000L, DAY_LENGTH);
	}

	/**
	 * Whether riders are setting out: from the first collection hour until the last (by default 6am to dusk). A last
	 * collection at or before the first runs on past midnight.
	 */
	public static boolean isCollecting(long dayTime, int firstHour, int lastHour) {
		long time = timeOfDay(dayTime), from = dayTimeOf(firstHour), until = lastHour == 6 ? DAY_LENGTH : dayTimeOf(lastHour);
		return from < until ? time >= from && time < until : time >= from || time < until;
	}

	/** An hour of the clock (0-24) as it would be written: "6am", "noon", "6pm", "midnight". */
	public static String clock(int hour) {
		int h = Math.floorMod(hour, 24);
		if (h == 0)
			return "midnight";
		if (h == 12)
			return "noon";
		return h < 12 ? h + "am" : (h - 12) + "pm";
	}

	/** From when monsters come out until 6am: a camping rider is in his tent rather than playing outside it. */
	public static boolean isBedtime(long dayTime) {
		return timeOfDay(dayTime) >= MOBS_COME_OUT;
	}

	/** How many ticks until 6am, or 0 if it's already daytime. */
	public static long ticksUntilOpen(long dayTime) {
		return isOpen(dayTime) ? 0 : DAY_LENGTH - timeOfDay(dayTime);
	}
}
