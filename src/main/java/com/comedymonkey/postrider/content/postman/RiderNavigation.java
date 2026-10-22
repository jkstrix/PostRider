package com.comedymonkey.postrider.content.postman;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/**
 * His legs' navigation: vanilla's, except that a block changing near the way he's on never has it worked out again
 * by vanilla's own pathfinder. The way he follows is the planner's (through doors, up ladders, into bubble columns:
 * places vanilla wouldn't go), and the block that changes is usually one he changed himself, a door he's opening.
 */
public final class RiderNavigation extends GroundPathNavigation {
	public RiderNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	public boolean shouldRecomputePath(BlockPos pos) {
		return false;
	}
}
