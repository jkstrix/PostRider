package com.comedymonkey.postrider.content.lost;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Lost Parcel lost on the water: the soggy parcel lashed to the planks of the wrecked barge, floating at the surface
 * where the rider went down (with his hat and skull, or his map, as on land). It sits in the water block, so the water
 * stays round it, and leaves water behind when it's taken.
 */
public class LostParcelWreckBlock extends LostParcelBlock implements SimpleWaterloggedBlock {
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** The raft riding at the water line, with the parcel on it. */
	private static final VoxelShape SHAPE = Block.box(0, 13, 0, 16, 16, 16);

	public LostParcelWreckBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (state.getValue(WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected BlockState emptied(BlockState state) {
		return state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : super.emptied(state);
	}
}
