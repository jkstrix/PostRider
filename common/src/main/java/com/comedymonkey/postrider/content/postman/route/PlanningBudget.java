package com.comedymonkey.postrider.content.postman.route;

import net.minecraft.server.MinecraftServer;

import com.comedymonkey.postrider.content.postman.RiderChunks;


/**
 * How much route planning the server does each tick, shared by every rider, so working out routes never makes the
 * game lag: each rider takes a share, and whoever finds it used up carries on next tick.
 */
public final class PlanningBudget {
	/** Places the local planners can look at each tick, all riders together, and the most one rider takes. */
	public static final int LOCAL_PER_TICK = 6000;
	public static final int LOCAL_PER_RIDER = 2000;
	/** Cells the long-range planners can look at each tick, all riders together, and the most one rider takes. */
	public static final int COARSE_PER_TICK = 10_000;
	public static final int COARSE_PER_RIDER = 4000;

	private static int local = LOCAL_PER_TICK;
	private static int coarse = COARSE_PER_TICK;

	private PlanningBudget() {}

	public static void onServerTick(MinecraftServer server) {
		local = LOCAL_PER_TICK;
		coarse = COARSE_PER_TICK;
		TerrainSurvey.tickAll(server);
	}

	public static void onServerStopped(MinecraftServer server) {
		TerrainSurvey.clear();
		RiderChunks.clear();
	}

	/** One rider's share of this tick's local planning (none if it's all gone). */
	public static int takeLocal() {
		int share = Math.min(LOCAL_PER_RIDER, local);
		local -= share;
		return share;
	}

	/** Gives back what a rider didn't use. */
	public static void giveBackLocal(int unused) {
		local += Math.max(0, unused);
	}

	public static int takeCoarse() {
		int share = Math.min(COARSE_PER_RIDER, coarse);
		coarse -= share;
		return share;
	}
}
