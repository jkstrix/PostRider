package com.comedymonkey.postrider.content.postman.route;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.comedymonkey.postrider.content.postman.Journey;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The doors on his way. He opens any door (wooden or iron, no button needed: he's a rider on his rounds, not a mob
 * rattling the handle) as he comes up to it, and once he's through and clear of it puts it back as he found it:
 * shut again if it was shut, left open if it was open. Only on foot: no horse goes through a doorway.
 */
public final class Doors {
	/** A door this near (to its middle) is about to be gone through, or still being come through. */
	public static final double REACH = 2.0;
	/** How many steps past the one he's heading for are looked at for doors. */
	public static final int LOOK_AHEAD = 2;

	/** Each door he's opened (its lower half) and whether it was open before he came. */
	private final Map<BlockPos, Boolean> opened = new LinkedHashMap<>();

	/** The door steps just ahead: from the one before the one he's heading for, a couple on. */
	public static List<BlockPos> ahead(List<LocalPlanner.Step> steps, int next, int last, Predicate<BlockPos> isDoor) {
		List<BlockPos> doors = new ArrayList<>();
		for (int i = Math.max(0, next - 1); i <= Math.min(last, next + LOOK_AHEAD) && i < steps.size(); i++) {
			LocalPlanner.Step step = steps.get(i);
			if (step.mounted())
				continue;
			BlockPos at = new BlockPos(step.x(), step.y(), step.z());
			if (isDoor.test(at) && !doors.contains(at))
				doors.add(at);
		}
		return doors;
	}

	/** Whether he's far enough from a door to shut it without it swinging on him. */
	public static boolean clearOf(Vec3 body, BlockPos door) {
		return Journey.horizontalDistance(body, new Vec3(door.getX() + 0.5, body.y, door.getZ() + 0.5)) > REACH;
	}

	/** Whether there's a door (either half) at this block. */
	public static boolean isDoor(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getBlock() instanceof DoorBlock;
	}

	/** Opens the doors just ahead of him on these steps, and puts back any he's through and clear of. */
	public void tick(ServerLevel level, Entity body, List<LocalPlanner.Step> steps, int next, int last) {
		List<BlockPos> coming = next < 0 ? List.of() : ahead(steps, next, last, at -> isDoor(level, at));
		for (BlockPos at : coming) {
			BlockPos lower = lowerHalf(level, at);
			if (opened.containsKey(lower))
				continue;
			BlockState state = level.getBlockState(lower);
			if (!(state.getBlock() instanceof DoorBlock door))
				continue;
			boolean wasOpen = state.getValue(DoorBlock.OPEN);
			opened.put(lower, wasOpen);
			if (!wasOpen)
				door.setOpen(body, level, state, lower, true);
		}
		for (Iterator<Map.Entry<BlockPos, Boolean>> it = opened.entrySet().iterator(); it.hasNext();) {
			Map.Entry<BlockPos, Boolean> entry = it.next();
			BlockPos lower = entry.getKey();
			if (coming.stream().anyMatch(at -> lowerHalf(level, at).equals(lower)) || !clearOf(body.position(), lower))
				continue;
			putBack(level, lower, entry.getValue());
			it.remove();
		}
	}

	/** Every door he's opened goes back as it was, wherever he is (he's leaving: delivered, camping, moved). */
	public void putAllBack(ServerLevel level) {
		opened.forEach((lower, wasOpen) -> putBack(level, lower, wasOpen));
		opened.clear();
	}

	/** How many doors he's opened and not yet put back. */
	public int open() {
		return opened.size();
	}

	private static void putBack(ServerLevel level, BlockPos lower, boolean wasOpen) {
		BlockState state = level.getBlockState(lower);
		if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.OPEN) != wasOpen)
			door.setOpen(null, level, state, lower, wasOpen);
	}

	private static BlockPos lowerHalf(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? at.below() : at;
	}
}
