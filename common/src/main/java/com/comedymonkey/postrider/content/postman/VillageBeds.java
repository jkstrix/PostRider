package com.comedymonkey.postrider.content.postman;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A night in a village bed rather than under canvas: at dusk in a village (the game's own reckoning: beds, a bell or
 * workstations about), a rider looks for a bed nobody is lying in, a villager's included, and sleeps in it. Beds are found
 * by looking through the loaded chunks round him (a straw bed isn't on the villagers' list of homes, so the game's own
 * index of beds won't do), skipping any part of a chunk without a bed in it.
 */
public final class VillageBeds {
	private VillageBeds() {}

	/** How far round him (across) he looks for a bed. */
	public static final int REACH = 48;
	/** How far up or down from him he looks for a bed. */
	public static final int HEIGHT = 8;
	/** How many of the nearest free beds he checks for a way there before giving up and camping. */
	public static final int CANDIDATES = 6;
	/** How far in from the foot end of the bed his soles go, so his boots don't hang off it. */
	public static final double SOLES_IN = 0.1;
	/** The top of a bed, where its shape can't be had (a full-size bed: 9 pixels). */
	public static final double BED_TOP = 9 / 16.0;

	/** Whether this is a bed: a coloured bed, or on 26.3, a straw bed. */
	public static boolean isBed(BlockState state) {
		return state.getBlock() instanceof AbstractBedBlock;
	}

	/** Whether this is the head end of a bed (where a sleeper lies, and what marks it taken) that nobody is lying in. */
	public static boolean isFreeBedHead(BlockState state) {
		return isBed(state) && state.getValue(AbstractBedBlock.PART) == BedPart.HEAD && !state.getValue(AbstractBedBlock.OCCUPIED);
	}

	/** The free beds (their head ends) within reach of this spot, in the chunks already loaded, nearest first. */
	public static List<BlockPos> freeBedsNear(ServerLevel level, BlockPos centre) {
		List<BlockPos> beds = new ArrayList<>();
		int minY = Math.max(level.getMinY(), centre.getY() - HEIGHT), maxY = Math.min(level.getMaxY(), centre.getY() + HEIGHT);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int cx = (centre.getX() - REACH) >> 4; cx <= (centre.getX() + REACH) >> 4; cx++)
			for (int cz = (centre.getZ() - REACH) >> 4; cz <= (centre.getZ() + REACH) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null)
					continue;
				for (int sy = level.getSectionIndex(minY); sy <= level.getSectionIndex(maxY); sy++) {
					LevelChunkSection section = chunk.getSection(sy);
					if (section.hasOnlyAir() || !section.maybeHas(VillageBeds::isBed))
						continue;
					int baseY = level.getSectionYFromSectionIndex(sy) << 4;
					for (int y = Math.max(minY, baseY); y <= Math.min(maxY, baseY + 15); y++)
						for (int x = 0; x < 16; x++)
							for (int z = 0; z < 16; z++) {
								cursor.set((cx << 4) + x, y, (cz << 4) + z);
								if (Math.abs(cursor.getX() - centre.getX()) > REACH || Math.abs(cursor.getZ() - centre.getZ()) > REACH)
									continue;
								if (isFreeBedHead(section.getBlockState(x, y & 15, z)))
									beds.add(cursor.immutable());
							}
				}
			}
		beds.sort(Comparator.comparingDouble(bed -> bed.distSqr(centre)));
		return beds;
	}

	/** The foot end of the bed whose head end is here, facing this way (a bed faces from its foot to its head). */
	public static BlockPos footOf(BlockPos head, Direction facing) {
		return head.relative(facing.getOpposite());
	}

	/**
	 * Where his soles go when he lies down in a bed: at its foot end, a little in, on top of the mattress. Lying on his
	 * back facing the foot (see {@link #yaw}), his head is on the pillow at the other end, as in his tent.
	 */
	public static Vec3 soles(BlockPos head, Direction facing, double top) {
		BlockPos foot = footOf(head, facing);
		double out = 0.5 - SOLES_IN;
		return new Vec3(foot.getX() + 0.5 - facing.getStepX() * out, foot.getY() + top, foot.getZ() + 0.5 - facing.getStepZ() * out);
	}

	/** Which way he faces lying in a bed facing this way: towards the foot, so he lies back with his head to the pillow. */
	public static float yaw(Direction facing) {
		return facing.getOpposite().toYRot();
	}

	/** How high the top of the bed's foot end is above the block's floor. */
	public static double topOf(ServerLevel level, BlockPos foot) {
		VoxelShape shape = level.getBlockState(foot).getCollisionShape(level, foot);
		return shape.isEmpty() ? BED_TOP : shape.max(Direction.Axis.Y);
	}
}
