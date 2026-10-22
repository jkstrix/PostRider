package com.comedymonkey.postrider.content.postman.route;

import com.comedymonkey.postrider.content.postman.Hazards;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The real world as the local planner sees it, only ever from chunks already loaded (it never loads one). Each block
 * is worked out once per plan; the plan only lasts a few ticks, so changes after that are picked up by the next one.
 */
public final class LevelTerrain implements Terrain {
	private static final Kind[] KINDS = Kind.values();
	private static final byte NOT_SEEN = -1;

	private final ServerLevel level;
	private final Long2ByteOpenHashMap kinds = new Long2ByteOpenHashMap();
	private final Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	public LevelTerrain(ServerLevel level) {
		this.level = level;
		kinds.defaultReturnValue(NOT_SEEN);
	}

	@Override
	public Kind kind(int x, int y, int z) {
		long key = BlockPos.asLong(x, y, z);
		byte seen = kinds.get(key);
		if (seen != NOT_SEEN)
			return KINDS[seen];
		Kind kind = look(x, y, z);
		kinds.put(key, (byte) kind.ordinal());
		return kind;
	}

	@Override
	public boolean covered(int x, int y, int z) {
		LevelChunk chunk = chunk(x, z);
		return chunk != null && chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) >= y;
	}

	private Kind look(int x, int y, int z) {
		if (level.isOutsideBuildHeight(y))
			return y < level.getMinBuildHeight() ? Kind.UNKNOWN : Kind.OPEN;
		LevelChunk chunk = chunk(x, z);
		if (chunk == null)
			return Kind.UNKNOWN;
		cursor.set(x, y, z);
		BlockState state = chunk.getBlockState(cursor);
		return classify(state, state.getCollisionShape(chunk, cursor));
	}

	/** What a block is like to be in or on, from its state and the shape you'd bump into. */
	public static Kind classify(BlockState state, VoxelShape collision) {
		if (Hazards.isDangerous(state))
			return Kind.DANGER;
		if (!state.getFluidState().isEmpty())
			return Kind.LIQUID;
		if (state.is(BlockTags.CLIMBABLE) && !state.is(Blocks.SCAFFOLDING))
			return Kind.CLIMB;
		if (collision.isEmpty())
			return Kind.OPEN;
		double top = collision.max(Direction.Axis.Y);
		if (top > 1.0)
			return Kind.TALL;
		return top <= 0.5 ? Kind.LOW : Kind.SOLID;
	}

	private LevelChunk chunk(int x, int z) {
		long key = ChunkPos.asLong(x >> 4, z >> 4);
		LevelChunk chunk = chunks.get(key);
		if (chunk == null && !chunks.containsKey(key)) {
			chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			chunks.put(key, chunk);
		}
		return chunk;
	}
}
