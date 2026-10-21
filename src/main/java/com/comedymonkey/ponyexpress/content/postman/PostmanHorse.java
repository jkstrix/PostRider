package com.comedymonkey.ponyexpress.content.postman;

import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The rider's horse: a vanilla horse with a random coat, markings and name, wearing the mochila. It's always saddled
 * and only answers to its rider; players can't ride it, open it, feed it or breed it, and it leaves nothing behind.
 */
public class PostmanHorse extends Horse {
	/** Roughly how many blocks per second a horse covers for each point of its movement speed. */
	public static final double BLOCKS_PER_SECOND_PER_SPEED = 42.16;
	public static final double MIN_BLOCKS_PER_SECOND = 7.0;
	public static final double MIN_SPEED = MIN_BLOCKS_PER_SECOND / BLOCKS_PER_SECOND_PER_SPEED;
	private static final int SPEED_ROLLS = 32;

	public PostmanHorse(EntityType<? extends Horse> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	/** A vanilla-style random horse speed, rolled again until it's at least {@link #MIN_SPEED}. */
	public static double rollSpeed(DoubleSupplier random) {
		for (int i = 0; i < SPEED_ROLLS; i++) {
			double speed = generateSpeed(random);
			if (speed >= MIN_SPEED)
				return speed;
		}
		return MIN_SPEED;
	}

	public static double blocksPerSecond(double speed) {
		return speed * BLOCKS_PER_SECOND_PER_SPEED;
	}

	@Override
	protected void randomizeAttributes(RandomSource random) {
		super.randomizeAttributes(random);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(rollSpeed(random::nextDouble));
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, type, data);
		setTamed(true);
		equipSaddle(new ItemStack(Items.SADDLE), null);
		setCustomName(Component.literal(PostmanNames.horse(getRandom())));
		return result;
	}

	/** Creates a ready-saddled horse at a spot, without adding it to the world yet. */
	@Nullable
	public static PostmanHorse create(EntityType<PostmanHorse> type, ServerLevel level, double x, double y, double z, float yaw) {
		PostmanHorse horse = type.create(level);
		if (horse == null)
			return null;
		horse.moveTo(x, y, z, yaw, 0);
		EventHooks.finalizeMobSpawn(horse, level, level.getCurrentDifficultyAt(horse.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
		return horse;
	}

	/** Within this many blocks of where he's steering, the horse eases off so it doesn't overshoot. */
	public static final double EASE_OFF_DISTANCE = 3.0;

	/**
	 * How hard to push forward. Vanilla gives a mob-steered horse its own speed as the forward push, which makes it
	 * crawl compared with a player riding the same horse; the rider pushes flat out instead, easing off close to his
	 * mark, so the horse gallops at its real speed.
	 */
	public static double gallopInput(double forward, double distanceToMark) {
		if (forward <= 0)
			return forward;
		return Math.max(forward, Math.min(1.0, distanceToMark / EASE_OFF_DISTANCE));
	}

	@Override
	public void travel(Vec3 input) {
		// The move control has already handed over its push by now (and stopped "wanting"), but it keeps its mark
		if (getControllingPassenger() instanceof PostmanEntity && input.z > 0) {
			double dx = getMoveControl().getWantedX() - getX(), dz = getMoveControl().getWantedZ() - getZ();
			input = new Vec3(input.x, input.y, gallopInput(input.z, Math.sqrt(dx * dx + dz * dz)));
		}
		super.travel(input);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean canMate(Animal other) {
		return false;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	protected void dropEquipment() {
		// The saddle and mochila go with the horse
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
