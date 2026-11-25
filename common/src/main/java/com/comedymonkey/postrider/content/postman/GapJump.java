package com.comedymonkey.postrider.content.postman;

/**
 * How far a galloping horse leaps. Where his plan crosses a gap (see the route planner), the horse jumps it from the
 * take-off straight at the landing, and nowhere else.
 */
public final class GapJump {
	/** How many blocks across a horse will leap; vanilla horses at full gallop clear about this. */
	public static final int MAX_GAP = 3;

	private GapJump() {}
}
