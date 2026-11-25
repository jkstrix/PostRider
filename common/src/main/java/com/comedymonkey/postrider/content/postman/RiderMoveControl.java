package com.comedymonkey.postrider.content.postman;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * His legs' move control: vanilla's, except that he steps up a stair or a slab rather than hop it. Vanilla has a mob
 * jump whenever its feet are below the top of the block it's standing in, the low half of a stair block, say, though
 * the half block up is within its step. Off the ground there's nothing to slow him, so on a stair one block wide
 * winding round a tower, a hop at a turn carried him on over the edge. A hop is still made where the step is higher
 * than he can step up, and a jump asked for any other way (a leap over a gap) is left alone.
 */
public final class RiderMoveControl extends MoveControl<Mob> {
	private final Jump jump;

	public RiderMoveControl(Mob mob, Jump jump) {
		super(mob);
		this.jump = jump;
	}

	@Override
	public void tick() {
		Operation was = operation;
		boolean jumpAskedAlready = jump.asked();
		super.tick();
		if (was != Operation.MOVE_TO || operation != Operation.JUMPING || !mob.onGround())
			return;
		BlockPos feet = mob.blockPosition();
		BlockState state = mob.level().getBlockState(feet);
		VoxelShape shape = state.getCollisionShape(mob.level(), feet);
		double top = shape.isEmpty() ? mob.getY() : feet.getY() + shape.max(Direction.Axis.Y);
		if (hopNeedless(mob.getY(), top, wantedY, mob.maxUpStep())) {
			if (!jumpAskedAlready)
				jump.cancel();
			operation = Operation.WAIT;
		}
	}

	/**
	 * Whether a hop vanilla would make is needless: the block he's standing in rises no more than he can step up,
	 * and so does the place he's making for.
	 */
	public static boolean hopNeedless(double feetY, double blockTop, double wantedY, double maxUpStep) {
		double slack = 1e-3;
		return blockTop - feetY <= maxUpStep + slack && wantedY - feetY <= maxUpStep + slack;
	}

	/** His jump control, which a needless hop can be taken back from before it's made. */
	public static final class Jump extends JumpControl {
		public Jump(Mob mob) {
			super(mob);
		}

		boolean asked() {
			return jump;
		}

		void cancel() {
			jump = false;
		}
	}
}
