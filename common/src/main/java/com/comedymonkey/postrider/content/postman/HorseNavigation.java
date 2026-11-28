package com.comedymonkey.postrider.content.postman;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A rider's horse's navigation (his own hands it the way to follow while he's riding): vanilla's, except beside fire
 * and the like. Vanilla counts a step of the way as reached once a horse's middle is within half its width of it
 * (0.7 of a block), and may turn for the step after early, so round a corner it cuts in by most of that; but the
 * planner leaves a horse only about 0.3 of a block beside a hazard, and since 26.1 a horse is hurt by anything it
 * brushes in passing. Rounding the end of a row of campfires it stepped onto the last one. Beside a hazard it has to
 * come within {@link #NEAR_HAZARD_REACH} of each step, so it keeps to the way's corners rather than cut them.
 */
public final class HorseNavigation extends GroundPathNavigation {
	/** How near each step of the way the horse's middle must come, beside a hazard. */
	public static final double NEAR_HAZARD_REACH = 0.25;

	public HorseNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	protected void followThePath() {
		if (path == null || !Hazards.near(level, mob.getBoundingBox())) {
			super.followThePath();
			return;
		}
		Vec3 here = getTempMobPos();
		if (reachedBesideHazard(mob.position(), path.getNextEntityPos(mob), getMaxVerticalDistanceToWaypoint()))
			path.advance();
		doStuckDetection(here);
	}

	/** Whether a horse here has reached this step of the way (where its middle should be), beside a hazard. */
	public static boolean reachedBesideHazard(Vec3 here, Vec3 step, double verticalReach) {
		return Math.abs(here.x - step.x) <= NEAR_HAZARD_REACH && Math.abs(here.z - step.z) <= NEAR_HAZARD_REACH
			&& Math.abs(here.y - step.y) < verticalReach;
	}
}
