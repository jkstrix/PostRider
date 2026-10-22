package com.comedymonkey.postrider.content.postman.route;

/**
 * What the local planner needs to know about the blocks round a rider: what each one is like to stand in, on or
 * under. Kept apart from the world so plans can be worked out (and tested) on made-up ground.
 */
public interface Terrain {
	enum Kind {
		/** Nothing in the way: air, grass, flowers. */
		OPEN,
		/** A floor you stand in rather than on: a carpet, a bottom slab, a thin layer of snow. */
		LOW,
		/** A block you stand on top of. */
		SOLID,
		/** Too tall to step onto or stand on: fences, walls. */
		TALL,
		/** Water or lava, waterlogged blocks included: never entered. */
		LIQUID,
		/** Fire, magma, cactus, berry bushes and the like (see Hazards): never entered or stood on. */
		DANGER,
		/** Ladders and vines: climbable on foot, and not in the way. */
		CLIMB,
		/** A bubble column rising from soul sand: carries someone on foot up to the top of it (the one water he enters). */
		BUBBLE_UP,
		/** A whirlpool over magma: carries someone on foot down to the foot of it. */
		BUBBLE_DOWN,
		/** Somewhere the planner can't see (the chunk isn't loaded). */
		UNKNOWN;

		/** Whether a body can be in this block. */
		public boolean isClear() {
			return this == OPEN || this == CLIMB;
		}

		/** Whether a rider's feet can be in this block (as well as clear blocks, a low floor). */
		public boolean feetFit() {
			return isClear() || this == LOW;
		}
	}

	Kind kind(int x, int y, int z);

	/** Whether there's a roof over this spot (underground, or indoors): a little dearer, as monsters lurk there. */
	boolean covered(int x, int y, int z);
}
