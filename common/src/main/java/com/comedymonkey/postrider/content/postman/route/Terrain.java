package com.comedymonkey.postrider.content.postman.route;

/**
 * What the local planner needs to know about the blocks round a rider: what each one is like to stand in, on or
 * under. Kept apart from the world so plans can be worked out (and tested) on made-up ground.
 */
public interface Terrain {
	enum Kind {
		/** Nothing in the way: air, grass, flowers. */
		OPEN,
		/** A floor you stand in rather than on, too thin to matter overhead: a carpet, a thin layer of snow, a trapdoor. */
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
		/** A door, either half, open or shut, wooden or iron: he opens it and goes through on foot; no horse does. */
		DOOR,
		/** Somewhere the planner can't see (the chunk isn't loaded). */
		UNKNOWN,
		/**
		 * A floor you stand in that lifts you a good part of a block (more than {@link #THIN_FLOOR}, up to half): a bed, a
		 * bottom slab, deep snow. Stood on it, a rider on foot reaches into the second block above, so that must be clear
		 * too (under a tent's roof, a straw bed is no way through).
		 */
		RAISED;

		/** The highest a floor can rise and leave a rider on foot (1.8 tall) inside the two blocks it's in. */
		public static final double THIN_FLOOR = 0.2;

		/** Whether this is a floor stood in, low or raised. */
		public boolean isFloor() {
			return this == LOW || this == RAISED;
		}

		/** Whether a body on foot can be in this block. */
		public boolean isClear() {
			return this == OPEN || this == CLIMB || this == DOOR;
		}

		/** Whether a rider's feet can be in this block (as well as clear blocks, a low floor). */
		public boolean feetFit() {
			return isClear() || isFloor();
		}

		/** Whether a horse can be in this block: as on foot, but never a doorway. */
		public boolean horseClear() {
			return this == OPEN || this == CLIMB;
		}

		/** Whether a horse's feet can be in this block. */
		public boolean horseFeetFit() {
			return horseClear() || isFloor();
		}
	}

	Kind kind(int x, int y, int z);

	/** Whether a place stands on ice (sea ice, a frozen lake): walkable, but bad going that a way keeps off where it can. */
	default boolean icy(int x, int y, int z) {
		return false;
	}

	/** Whether there's a roof over this spot (underground, or indoors): a little dearer, as monsters lurk there. */
	boolean covered(int x, int y, int z);
}
