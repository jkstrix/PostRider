package com.comedymonkey.postrider.content.postman;

import java.util.ArrayList;
import java.util.List;

import com.comedymonkey.postrider.PostRiderConfig;

import net.minecraft.world.phys.Vec3;

/** The sums for getting a rider from A to B: where to aim next, and whether he's making progress. */
public final class Journey {
	/**
	 * How far ahead each stretch of riding aims, and pathfinding plans the way round things, by default (the server can
	 * change it: {@link PostRiderConfig#lookahead}); inside the chunks he keeps loaded.
	 */
	public static final double STRETCH = 48.0;

	/** The stretch in use on this server. */
	public static double stretch() {
		return PostRiderConfig.lookahead();
	}
	/** How close to the mailbox he gets down from his horse, and how close he walks before delivering. */
	public static final double DISMOUNT_DISTANCE = 6.0;
	/** ...and no further above or below it than this: a box on a tower top is reached by the ramp, not from its foot. */
	public static final double DISMOUNT_HEIGHT = 3.0;
	public static final double DELIVER_DISTANCE = 1.8;
	/** Turns tried, in degrees, when the way straight ahead is water: small swerves first, either side. */
	private static final float[] SWERVES = {0, 25, -25, 50, -50, 80, -80};

	private Journey() {}

	/** Whether he's close enough to the mailbox, across and up or down, to get off his horse and walk the rest. */
	public static boolean closeEnoughToWalkUp(Vec3 from, Vec3 mailbox) {
		return horizontalDistance(from, mailbox) <= DISMOUNT_DISTANCE && Math.abs(mailbox.y - from.y) <= DISMOUNT_HEIGHT;
	}

	/** The straight-line distance, height included: climbing counts as progress. */
	public static double distance(Vec3 from, Vec3 to) {
		return from.distanceTo(to);
	}

	public static double horizontalDistance(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Where to aim the next stretch: up to {@link #STRETCH} blocks towards the destination, or the destination
	 * itself if it's closer. Height is left to the caller, who knows where the ground is.
	 */
	public static Vec3 nextAim(Vec3 from, Vec3 destination) {
		return aims(from, destination).get(0);
	}

	/** The straight-ahead aim, then swerves left and right of it, for when straight ahead is no good. */
	public static List<Vec3> aims(Vec3 from, Vec3 destination) {
		return aims(from, destination, stretch());
	}

	/**
	 * Places to jump to when he's stuck, best first: the usual aims, then half as far the same ways, then a ring all
	 * round him (even back the way he came) for when everything ahead is water or worse.
	 */
	public static List<Vec3> escapes(Vec3 from, Vec3 destination) {
		double stretch = stretch();
		List<Vec3> escapes = new ArrayList<>(aims(from, destination, stretch));
		escapes.addAll(aims(from, destination, stretch / 2));
		for (int degrees = 0; degrees < 360; degrees += 30) {
			double angle = Math.toRadians(degrees);
			escapes.add(new Vec3(from.x + Math.cos(angle) * stretch / 2, from.y, from.z + Math.sin(angle) * stretch / 2));
		}
		return escapes;
	}

	private static List<Vec3> aims(Vec3 from, Vec3 destination, double stretch) {
		double distance = horizontalDistance(from, destination);
		List<Vec3> aims = new ArrayList<>();
		if (distance <= stretch) {
			aims.add(new Vec3(destination.x, from.y, destination.z));
			return aims;
		}
		double dx = (destination.x - from.x) / distance, dz = (destination.z - from.z) / distance;
		for (float degrees : SWERVES) {
			double angle = Math.toRadians(degrees);
			double rx = dx * Math.cos(angle) - dz * Math.sin(angle);
			double rz = dx * Math.sin(angle) + dz * Math.cos(angle);
			aims.add(new Vec3(from.x + rx * stretch, from.y, from.z + rz * stretch));
		}
		return aims;
	}

	/**
	 * Notices a rider getting nowhere: if he hasn't got {@link #MIN_PROGRESS} blocks closer within
	 * {@link #PATIENCE} ticks, he's stuck. Counts how many times in a row that has happened.
	 */
	public static final class Progress {
		public static final int PATIENCE = 30 * 20;
		public static final double MIN_PROGRESS = 4.0;
		public static final int MAX_TRIES = 5;
		/** Not moving this far in this long means he's wedged somewhere, however close he is. */
		public static final double STALL_DISTANCE = 2.0;
		public static final int STALL_TICKS = 5 * 20;
		/** This many routes redone without his getting any nearer is being stuck too, however busy the map is. */
		public static final int MAX_REDOS = 6;

		private double bestDistance = Double.MAX_VALUE;
		private long bestSince;
		private int triesInARow;
		private int redosWithoutProgress;
		private double stallX = Double.NaN, stallY, stallZ;
		private long stallSince;

		/** Records how far he is from the destination now; true if he's been stuck too long. */
		public boolean isStuck(double distance, long tick) {
			if (distance < bestDistance - MIN_PROGRESS || bestDistance == Double.MAX_VALUE) {
				if (bestDistance != Double.MAX_VALUE)
					triesInARow = 0;
				bestDistance = distance;
				bestSince = tick;
				redosWithoutProgress = 0;
				return false;
			}
			return tick - bestSince >= PATIENCE || redosWithoutProgress >= MAX_REDOS;
		}

		/** Records where he is now; true if he's hardly moved, up or down included, for {@link #STALL_TICKS}. */
		public boolean isStalled(double x, double y, double z, long tick) {
			return isStalled(x, y, z, tick, STALL_TICKS);
		}

		/** As above, with a patience of its own (a barge pressed against a bank takes a while to back off and turn). */
		public boolean isStalled(double x, double y, double z, long tick, int patience) {
			if (Double.isNaN(stallX) || Math.sqrt((x - stallX) * (x - stallX) + (y - stallY) * (y - stallY) + (z - stallZ) * (z - stallZ)) > STALL_DISTANCE) {
				stallX = x;
				stallY = y;
				stallZ = z;
				stallSince = tick;
				return false;
			}
			return tick - stallSince >= patience;
		}

		/** He has jumped free once more; the clocks start again from where he lands. */
		/** How he's doing, for the trace: how long since he last got nearer, and since he last moved. */
		public String describe(long tick) {
			return "best " + (bestDistance == Double.MAX_VALUE ? "none" : Math.round(bestDistance) + " " + (tick - bestSince) + " ticks ago")
				+ ", still " + (Double.isNaN(stallX) ? "unset" : (tick - stallSince) + " ticks") + ", tries " + triesInARow;
		}

		/**
		 * He has been jumped (or pushed) free once more; the clocks start again. Put down further off than he was,
		 * coming back to where he was stuck isn't getting nearer: he has to get past it.
		 */
		public void unstuck(double distance, long tick) {
			triesInARow++;
			bestDistance = Math.min(bestDistance, distance);
			bestSince = tick;
			stallX = Double.NaN;
			redosWithoutProgress = 0;
		}

		/** The way has changed (the route redone): what counts as nearer is measured afresh from here, and no try is counted. */
		public void rebase(double distance, long tick) {
			bestDistance = distance;
			bestSince = tick;
			redosWithoutProgress++;
		}

		/** Time didn't pass for him (camping at night), so don't count it. */
		public void pause(long tick) {
			bestSince = tick;
			stallX = Double.NaN;
		}

		/**
		 * The way ahead was struck off and the route is being redone on purpose: that redo isn't one more without
		 * progress, or the next check would find him stuck again at once and strike again, over and over.
		 */
		public void struckOff() {
			redosWithoutProgress = 0;
		}

		/** This many ticks were spent busy (the route being redone, say): they don't count, but the clocks keep what they had. */
		public void excuse(int ticks) {
			bestSince += ticks;
			if (!Double.isNaN(stallX))
				stallSince += ticks;
		}

		public boolean outOfTries() {
			return triesInARow >= MAX_TRIES;
		}

		public int triesInARow() {
			return triesInARow;
		}
	}
}
