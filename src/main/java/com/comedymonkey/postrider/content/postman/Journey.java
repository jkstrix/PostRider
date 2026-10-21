package com.comedymonkey.postrider.content.postman;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

/** The sums for getting a rider from A to B: where to aim next, and whether he's making progress. */
public final class Journey {
	/** How far ahead each stretch of riding aims; kept inside the chunks he keeps loaded. */
	public static final double STRETCH = 16.0;
	/** How close to the mailbox he gets down from his horse, and how close he walks before delivering. */
	public static final double DISMOUNT_DISTANCE = 6.0;
	public static final double DELIVER_DISTANCE = 1.8;
	/** Turns tried, in degrees, when the way straight ahead is water: small swerves first, either side. */
	private static final float[] SWERVES = {0, 25, -25, 50, -50, 80, -80};

	private Journey() {}

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
		double distance = horizontalDistance(from, destination);
		List<Vec3> aims = new ArrayList<>();
		if (distance <= STRETCH) {
			aims.add(new Vec3(destination.x, from.y, destination.z));
			return aims;
		}
		double dx = (destination.x - from.x) / distance, dz = (destination.z - from.z) / distance;
		for (float degrees : SWERVES) {
			double angle = Math.toRadians(degrees);
			double rx = dx * Math.cos(angle) - dz * Math.sin(angle);
			double rz = dx * Math.sin(angle) + dz * Math.cos(angle);
			aims.add(new Vec3(from.x + rx * STRETCH, from.y, from.z + rz * STRETCH));
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

		private double bestDistance = Double.MAX_VALUE;
		private long bestSince;
		private int triesInARow;

		/** Records how far he is from the destination now; true if he's been stuck too long. */
		public boolean isStuck(double distance, long tick) {
			if (distance < bestDistance - MIN_PROGRESS || bestDistance == Double.MAX_VALUE) {
				if (bestDistance != Double.MAX_VALUE)
					triesInARow = 0;
				bestDistance = distance;
				bestSince = tick;
				return false;
			}
			return tick - bestSince >= PATIENCE;
		}

		/** He has jumped free once more; the clock starts again from where he lands. */
		public void unstuck(double distance, long tick) {
			triesInARow++;
			bestDistance = distance;
			bestSince = tick;
		}

		/** Time didn't pass for him (camping at night), so don't count it. */
		public void pause(long tick) {
			bestSince = tick;
		}

		public boolean outOfTries() {
			return triesInARow >= MAX_TRIES;
		}

		public int triesInARow() {
			return triesInARow;
		}
	}
}
