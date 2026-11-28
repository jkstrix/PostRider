package com.comedymonkey.postrider.content.postman;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rider's campfire: a real block, since only blocks give off light, but one that only exists while he's camped
 * beside it. It burns at light 15 when he lights it and dies down steadily to embers (light 3) by 3am, keeping
 * monsters from spawning close by through the evening. It's harmless (no burning, no spreading, no cooking), can't be
 * broken, put out or blown up, and goes out by itself if it's ever left without a camp.
 */
public class CampfireBlock extends Block {
	public static final MapCodec<CampfireBlock> CODEC = simpleCodec(CampfireBlock::new);
	public static final int BRIGHTEST = 15;
	public static final int EMBERS = 3;
	public static final IntegerProperty LIGHT = IntegerProperty.create("light", EMBERS, BRIGHTEST);
	/** 3am, by when it has burnt down to embers. */
	public static final long EMBERS_AT = 21000L;
	/** A fire with no tent this close has been left behind, and goes out. */
	public static final double CAMP_RANGE = 6.0;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 7, 16);

	public CampfireBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIGHT, BRIGHTEST));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIGHT);
	}

	/**
	 * How bright it burns at a time of day, having been lit at another: 15 when lit, down a little at a time to embers
	 * at 3am, and embers until it's put out in the morning.
	 */
	public static int lightAt(long timeOfDay, long litAt) {
		if (timeOfDay < litAt || timeOfDay >= EMBERS_AT || litAt >= EMBERS_AT)
			return EMBERS;
		double burnt = (double) (timeOfDay - litAt) / (EMBERS_AT - litAt);
		return (int) Math.round(BRIGHTEST - (BRIGHTEST - EMBERS) * burnt);
	}

	/** Where he lights it: the block just in front of his seat, if there's nothing there and solid ground under it. */
	public static BlockPos spotFor(CampSites.Site site) {
		return site.seat().relative(site.facing());
	}

	public static boolean canLightAt(Level level, BlockPos pos) {
		return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
			&& !isIce(level.getBlockState(pos.below()));
	}

	/** Ice of any kind: no fire is lit on it (it would melt through), and a camp on it is a cold one. */
	public static boolean isIce(BlockState ground) {
		return Crossing.isIce(ground);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	/** Left without a camp (the server stopped mid-night, say): it goes out. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getEntitiesOfClass(TentEntity.class, new AABB(pos).inflate(CAMP_RANGE)).isEmpty())
			level.removeBlock(pos, false);
	}

	/** Smoke and crackling while it burns; now and then a wisp from the embers. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5, y = pos.getY() + 0.4, z = pos.getZ() + 0.5;
		if (state.getValue(LIGHT) > EMBERS) {
			if (random.nextFloat() < 0.4F)
				level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x + random.nextDouble() * 0.3 - 0.15, y + 0.6,
					z + random.nextDouble() * 0.3 - 0.15, 0, 0.07, 0);
			if (random.nextInt(10) == 0)
				level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.5F + random.nextFloat(),
					random.nextFloat() * 0.7F + 0.6F, false);
		} else if (random.nextFloat() < 0.15F) {
			level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
		}
	}

	/** Nothing a blast does puts it out: it goes when the camp does. */
	@Override
	public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {}

	/**
	 * Nor does it drop anything to one, as the campfire that stays put mustn't leave a copy of itself behind (the game drops
	 * a block's loot before it takes the block away).
	 */
	@Override
	public boolean dropFromExplosion(Explosion explosion) {
		return false;
	}
}
