package com.comedymonkey.ponyexpress.content.mailbox;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.ponyexpress.post.MailboxDirectory;
import com.comedymonkey.ponyexpress.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A two-block-tall mailbox in one of the 16 dye colours. The lower half holds the {@link MailboxBlockEntity}; the
 * upper half is only there for its shape. Only the owner can open or break it (operators can also break it).
 */
public class MailboxBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<MailboxBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		DyeColor.CODEC.fieldOf("color").forGetter(MailboxBlock::getColor),
		propertiesCodec()
	).apply(i, MailboxBlock::new));

	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	/** Up when the inbox has mail in it. */
	public static final BooleanProperty FLAG = BooleanProperty.create("flag");

	private final DyeColor color;

	public MailboxBlock(DyeColor color, Properties properties) {
		super(properties);
		this.color = color;
		registerDefaultState(stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(HALF, DoubleBlockHalf.LOWER)
			.setValue(FLAG, false));
	}

	public DyeColor getColor() {
		return color;
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, FLAG);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return MailboxShapes.get(state.getValue(FACING), state.getValue(HALF));
	}

	// ---------------------------------------------------------------- placing and the two halves

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context))
			return null;
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		if (level.isClientSide || !(placer instanceof ServerPlayer player))
			return;
		if (level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox)
			mailbox.setOwner(player);
		if (!level.canSeeSky(pos.above(2)))
			player.sendSystemMessage(Component.translatable("message.ponyexpress.mailbox.no_sky").withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("message.ponyexpress.mailbox.placed"));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(HALF) == DoubleBlockHalf.LOWER)
			return true;
		BlockState below = level.getBlockState(pos.below());
		return below.getBlock() instanceof MailboxBlock && below.getValue(HALF) == DoubleBlockHalf.LOWER;
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
			BlockPos pos, BlockPos neighborPos) {
		DoubleBlockHalf half = state.getValue(HALF);
		if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
			boolean otherHalf = neighbor.getBlock() instanceof MailboxBlock && neighbor.getValue(HALF) != half;
			return otherHalf ? state : Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// Breaking the top in creative would otherwise drop the mailbox from the bottom half
		if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos lowerPos = pos.below();
			BlockState lower = level.getBlockState(lowerPos);
			if (lower.getBlock() instanceof MailboxBlock && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
				level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, lowerPos, Block.getId(lower));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		// Recolouring swaps one mailbox block for another and keeps everything; anything else empties it
		if (state.getValue(HALF) == DoubleBlockHalf.LOWER && !(newState.getBlock() instanceof MailboxBlock)
				&& !level.isClientSide && level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox) {
			mailbox.dropContents();
			if (level.getServer() != null)
				MailboxDirectory.get(level.getServer()).remove(GlobalPos.of(level.dimension(), pos));
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	// ---------------------------------------------------------------- ownership

	/** The position of the half that holds the block entity. */
	public static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	@Nullable
	public static MailboxBlockEntity mailboxAt(BlockGetter level, BlockState state, BlockPos pos) {
		return level.getBlockEntity(lowerPos(state, pos)) instanceof MailboxBlockEntity mailbox ? mailbox : null;
	}

	/** The owner can break their mailbox, and so can operators. */
	public static boolean canBreak(Player player, BlockGetter level, BlockState state, BlockPos pos) {
		MailboxBlockEntity mailbox = mailboxAt(level, state, pos);
		return mailbox == null || mailbox.isOwner(player) || player.hasPermissions(2);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return canBreak(player, level, state, pos) ? super.getDestroyProgress(state, player, level, pos) : 0;
	}

	// ---------------------------------------------------------------- using it

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		MailboxBlockEntity mailbox = mailboxAt(level, state, pos);
		if (mailbox == null)
			return InteractionResult.PASS;
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		if (!mailbox.isOwner(player)) {
			player.displayClientMessage(Component.translatable("message.ponyexpress.mailbox.not_owner", mailbox.getOwnerName()), true);
			return InteractionResult.CONSUME;
		}
		if (player instanceof ServerPlayer serverPlayer)
			mailbox.openFor(serverPlayer);
		return InteractionResult.CONSUME;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof DyeItem dye) || dye.getDyeColor() == color)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		MailboxBlockEntity mailbox = mailboxAt(level, state, pos);
		if (mailbox == null || !mailbox.isOwner(player))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide) {
			recolour(level, lowerPos(state, pos), ModBlocks.mailbox(dye.getDyeColor()).get());
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1);
			stack.consume(1, player);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Swaps both halves for the mailbox of another colour, keeping its name, owner and mail. */
	static void recolour(Level level, BlockPos lowerPos, MailboxBlock target) {
		BlockState lower = level.getBlockState(lowerPos);
		BlockState upper = level.getBlockState(lowerPos.above());
		CompoundTag data = level.getBlockEntity(lowerPos) instanceof MailboxBlockEntity old
			? old.saveWithoutMetadata(level.registryAccess()) : null;
		// Known-shape updates stop each half from seeing the other as a foreign block mid-swap
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		level.setBlock(lowerPos, recoloured(lower, target), flags);
		if (upper.getBlock() instanceof MailboxBlock)
			level.setBlock(lowerPos.above(), recoloured(upper, target), flags);
		if (data != null && level.getBlockEntity(lowerPos) instanceof MailboxBlockEntity mailbox) {
			mailbox.loadCustomOnly(data, level.registryAccess());
			mailbox.setChanged();
			level.sendBlockUpdated(lowerPos, mailbox.getBlockState(), mailbox.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** The same mailbox state (facing, half, flag) on another colour's block. */
	public static BlockState recoloured(BlockState state, MailboxBlock target) {
		return target.defaultBlockState()
			.setValue(FACING, state.getValue(FACING))
			.setValue(HALF, state.getValue(HALF))
			.setValue(FLAG, state.getValue(FLAG));
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new MailboxBlockEntity(pos, state) : null;
	}
}
