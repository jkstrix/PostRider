package com.comedymonkey.ponyexpress.content.lost;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.ponyexpress.content.HorizontalShapes;
import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A parcel a rider failed to deliver, left where it happened. It never despawns; only the sender, the addressee,
 * whoever killed the rider (or an operator) can open or break it. Anyone else can read its label.
 */
public class LostParcelBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<LostParcelBlock> CODEC = simpleCodec(LostParcelBlock::new);
	public static final EnumProperty<LostCause> CAUSE = EnumProperty.create("cause", LostCause.class);

	// Boxes for the model as drawn, with the LOST tag facing south
	private static final double[] PARCEL = {0.5, 0, 3, 11.5, 9, 13.5};
	private static final double[] SKULL = {11.5, 0, 7.75, 16, 4.5, 13};
	private static final double[] MAP = {11.5, 0, 8, 16, 1.5, 14.5};
	private static final Map<LostCause, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(LostCause.class);

	static {
		SHAPES.put(LostCause.KILLED, HorizontalShapes.build(new double[][] {PARCEL, SKULL}, Direction.SOUTH));
		SHAPES.put(LostCause.STUCK, HorizontalShapes.build(new double[][] {PARCEL, MAP}, Direction.SOUTH));
		SHAPES.put(LostCause.UNDELIVERABLE, HorizontalShapes.build(new double[][] {PARCEL}, Direction.SOUTH));
	}

	public LostParcelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(CAUSE, LostCause.UNDELIVERABLE));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, CAUSE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(CAUSE)).get(state.getValue(FACING));
	}

	public static boolean canBreak(Player player, BlockGetter level, BlockPos pos) {
		return !(level.getBlockEntity(pos) instanceof LostParcelBlockEntity parcel) || parcel.canOpen(player);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return canBreak(player, level, pos) ? super.getDestroyProgress(state, player, level, pos) : 0;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof LostParcelBlockEntity parcel))
			return InteractionResult.PASS;
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		player.sendSystemMessage(label(parcel));
		if (!parcel.canOpen(player)) {
			player.sendSystemMessage(Component.translatable("message.ponyexpress.lost_parcel.not_yours").withStyle(ChatFormatting.GRAY));
			return InteractionResult.CONSUME;
		}
		for (ItemStack item : parcel.takeContents())
			player.getInventory().placeItemBackInInventory(item);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1, 0.7F);
		player.sendSystemMessage(Component.translatable("message.ponyexpress.lost_parcel.recovered").withStyle(ChatFormatting.DARK_GREEN));
		return InteractionResult.CONSUME;
	}

	static Component label(LostParcelBlockEntity parcel) {
		return Component.translatable("message.ponyexpress.lost_parcel.label", parcel.getSenderName(), parcel.getFromName(),
			parcel.getToName(), parcel.getDay()).withStyle(ChatFormatting.GOLD);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LostParcelBlockEntity parcel)
			parcel.dropContents();
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LostParcelBlockEntity(pos, state);
	}
}
