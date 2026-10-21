package com.comedymonkey.ponyexpress.post;

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

	/** From when monsters come out until 6am: a camping rider is in his tent rather than playing outside it. */
	public static boolean isBedtime(long dayTime) {
		return timeOfDay(dayTime) >= MOBS_COME_OUT;
	}

	/** How many ticks until 6am, or 0 if it's already daytime. */
	public static long ticksUntilOpen(long dayTime) {
		return isOpen(dayTime) ? 0 : DAY_LENGTH - timeOfDay(dayTime);
	}
}
