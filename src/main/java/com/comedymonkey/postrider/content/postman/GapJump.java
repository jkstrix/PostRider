package com.comedymonkey.postrider.content.postman;

/**
 * Deciding when a galloping horse should leap: the ground just ahead falls away into a gap, and there's safe ground
 * at about the same height a few blocks further on. Heights are the floor's height at each block ahead of him.
 */
public final class GapJump {
	/** A gap is ground at least this far below him. */
	public static final int GAP_DEPTH = 2;
	/** How many blocks across a horse will leap; vanilla horses at full gallop clear about this. */
	public static final int MAX_GAP = 3;
	/** The far side can be this much higher or lower than where he takes off. */
	public static final int LANDING_STEP = 1;

	private GapJump() {}

	/**
	 * @param here     the floor height he's on
	 * @param ahead    the floor height 1, 2, 3... blocks ahead, as far as {@code MAX_GAP + 1}
	 * @param safe     whether each of those blocks is safe to land on
	 * @return how many blocks ahead the landing is, or 0 if there's nothing to jump
	 */
	public static int landingDistance(int here, int[] ahead, boolean[] safe) {
		if (ahead.length == 0 || here - ahead[0] < GAP_DEPTH)
			return 0;
		for (int i = 1; i < ahead.length && i <= MAX_GAP; i++)
			if (Math.abs(ahead[i] - here) <= LANDING_STEP && safe[i])
				return i + 1;
		return 0;
	}
}
