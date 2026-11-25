package com.comedymonkey.postrider.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deliveries through ground that hurts: lava, fire, magma, cactus, dripstone, berry bushes and powder snow. Each
 * trial is judged as {@link Trials} judges every delivery, and on top of that fails the moment the rider or his
 * horse is burnt, set alight, frozen or hurt in any way, saying where and by what. Run with
 * {@code ./gradlew runGameTestServer}; each test is its own batch.
 */
public final class HazardGameTests {
	/** Room to study the map, pick a way round the hazards (and be sent round again), and deliver. */
	private static final int TRIAL_TICKS = 6000;

	private HazardGameTests() {}

	// ---------------------------------------------------------------- keeping him unharmed

	/**
	 * Fails the trial the moment the rider or any of his horses is on fire, freezing (powder snow), or down on
	 * health (lava, fire, magma, cactus, a campfire, a berry bush), and says where he was and what did it.
	 */
	private static final class Unharmed {
		private final String trial;
		private final Scene scene;
		private final Watch watch;
		private final Map<UUID, Float> health = new HashMap<>();

		Unharmed(String trial, Scene scene, Watch watch) {
			this.trial = trial;
			this.scene = scene;
			this.watch = watch;
			scene.helper.onEachTick(this::look);
		}

		private void look() {
			PostmanEntity rider = watch.rider;
			if (rider == null || !rider.isAlive())
				return;
			check(rider, "rider");
			for (PostmanHorse horse : scene.level.getEntitiesOfClass(PostmanHorse.class, rider.getBoundingBox().inflate(64)))
				check(horse, "horse");
		}

		private void check(LivingEntity who, String what) {
			Float before = health.put(who.getUUID(), who.getHealth());
			// A horse's health is settled a tick after it appears (its spawn picks a maximum): only a drop that
			// comes with something to blame is harm
			DamageSource source = who.getLastDamageSource();
			String harm = who.isOnFire() ? "is on fire"
				: who.getTicksFrozen() > 0 ? "is freezing"
				: before != null && who.getHealth() < before && source != null ? "lost " + (before - who.getHealth()) + " health to " + source.getMsgId()
				: null;
			if (harm == null)
				return;
			BlockPos feet = who.blockPosition();
			Trials.record(trial, watch, false);
			throw new GameTestAssertException(trial + ": the " + what + " " + harm + " at " + scene.helper.relativeVec(who.position())
				+ ", feet in " + scene.level.getBlockState(feet) + " on " + scene.level.getBlockState(feet.below()) + " (" + watch.run + ")");
		}
	}

	/** A delivery to the far box, judged as every trial is and failing at once if he or his horse comes to harm. */
	private static void deliverUnharmed(String trial, Scene scene, BlockPos to, Watch watch) {
		new Unharmed(trial, scene, watch);
		Trials.deliver(trial, scene, to, watch, () -> {});
	}

	// ---------------------------------------------------------------- building the hazards

	/** A pool of lava at ground level, on a stone bed. */
	private static void lava(Scene scene, int x0, int z0, int x1, int z1) {
		scene.fill(x0, -1, z0, x1, -1, z1, Blocks.STONE.defaultBlockState());
		scene.fill(x0, 0, z0, x1, 0, z1, Blocks.LAVA.defaultBlockState());
	}

	/** A patch of netherrack burning at ground level: fire that never goes out. */
	private static void burning(Scene scene, int x0, int z0, int x1, int z1) {
		scene.fill(x0, 0, z0, x1, 0, z1, Blocks.NETHERRACK.defaultBlockState());
		scene.fill(x0, 1, z0, x1, 1, z1, Blocks.FIRE.defaultBlockState());
	}

	/** A stone wall three high (too high to step up, too high for the horse to fit under anything). */
	private static void wall(Scene scene, int x0, int z0, int x1, int z1) {
		scene.fill(x0, 1, z0, x1, 3, z1, Blocks.STONE.defaultBlockState());
	}

	/** A drift of powder snow lying on the ground. */
	private static void drift(Scene scene, int x0, int z0, int x1, int z1) {
		scene.fill(x0, 1, z0, x1, 1, z1, Blocks.POWDER_SNOW.defaultBlockState());
	}

	// ---------------------------------------------------------------- the trials

	@GameTest(skyAccess = true, template = "gametest/arena_64x48", batch = "hazard_lava_pools", timeoutTicks = TRIAL_TICKS)
	public static void heRidesBetweenLavaPoolsWithoutABurn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 63, 47);
		// Small pools (narrow enough for a horse to leap) and wide ones scattered over the way, with dry ground
		// at least three wide between them; the wide ones are big enough to pass for water from afar
		lava(scene, 16, 22, 18, 24);
		lava(scene, 26, 20, 35, 28);
		lava(scene, 40, 22, 41, 25);
		lava(scene, 46, 27, 48, 29);
		lava(scene, 50, 18, 52, 20);
		lava(scene, 20, 30, 22, 32);
		lava(scene, 36, 14, 38, 16);
		lava(scene, 42, 34, 55, 43);
		lava(scene, 10, 4, 21, 13);
		String fromName = Scene.address("GT Pools A"), toName = Scene.address("GT Pools B");
		BlockPos from = scene.mailbox(4, 24, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(59, 24, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliverUnharmed("Lava pools", scene, to, watch);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_48x32", batch = "hazard_lava_moat", timeoutTicks = TRIAL_TICKS)
	public static void heCrossesALavaMoatByItsOneNarrowBridge(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 47, 31);
		// Six wide right across the way (too wide to leap, and wide enough to pass for water from afar on half the
		// ways it can line up with the long-range plan's cells), stone at both ends so there's no way round, and a
		// stone bridge two wide well off to one side
		lava(scene, 2, 13, 45, 18);
		wall(scene, 0, 13, 1, 18);
		wall(scene, 46, 13, 47, 18);
		scene.fill(40, 0, 13, 41, 0, 18, Blocks.STONE.defaultBlockState());
		String fromName = Scene.address("GT Moat A"), toName = Scene.address("GT Moat B");
		BlockPos from = scene.mailbox(24, 4, Direction.SOUTH, fromName);
		BlockPos to = scene.mailbox(24, 28, Direction.NORTH, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliverUnharmed("Lava moat", scene, to, watch);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_64x48", batch = "hazard_fire_field", timeoutTicks = TRIAL_TICKS)
	public static void heThreadsAFieldOfFireAndCampfires(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 63, 47);
		// Burning patches, two long walls of fire with a gate three wide between them, two patches a block apart
		// (too narrow: he must go round), and a row of campfires with single gaps (likewise)
		burning(scene, 14, 18, 17, 21);
		burning(scene, 14, 27, 17, 30);
		burning(scene, 22, 10, 25, 24);
		burning(scene, 22, 28, 25, 40);
		burning(scene, 32, 20, 35, 23);
		burning(scene, 32, 25, 35, 28);
		burning(scene, 44, 30, 47, 33);
		burning(scene, 50, 14, 53, 17);
		BlockState campfire = Blocks.CAMPFIRE.defaultBlockState();
		for (int z = 18; z <= 30; z += 2)
			helper.setBlock(new BlockPos(40, 1, z), campfire);
		helper.setBlock(new BlockPos(46, 1, 22), campfire);
		helper.setBlock(new BlockPos(46, 1, 26), campfire);
		helper.setBlock(new BlockPos(50, 1, 24), campfire);
		String fromName = Scene.address("GT Fire A"), toName = Scene.address("GT Fire B");
		BlockPos from = scene.mailbox(4, 24, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(59, 24, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliverUnharmed("Fire field", scene, to, watch);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_48x32", batch = "hazard_spikes", timeoutTicks = TRIAL_TICKS)
	public static void heKeepsToTheMiddleOfACorridorLinedWithSpikes(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 47, 31);
		// A wall right across, with one corridor through it: berry bushes along its walls, then cactus and pointed
		// dripstone (turn about: cactus breaks beside anything solid), leaving three clear blocks down the middle
		wall(scene, 0, 13, 20, 18);
		wall(scene, 28, 13, 47, 18);
		BlockState bush = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
		scene.fill(21, 1, 13, 21, 1, 18, bush);
		scene.fill(27, 1, 13, 27, 1, 18, bush);
		for (int x : new int[] {22, 26})
			for (int z = 13; z <= 18; z++) {
				if (z % 2 == 1) {
					helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE.defaultBlockState());
					helper.setBlock(new BlockPos(x, 0, z), Blocks.SAND.defaultBlockState());
					helper.setBlock(new BlockPos(x, 1, z), Blocks.CACTUS.defaultBlockState());
				} else {
					helper.setBlock(new BlockPos(x, 1, z), Blocks.POINTED_DRIPSTONE.defaultBlockState());
				}
			}
		String fromName = Scene.address("GT Spike A"), toName = Scene.address("GT Spike B");
		BlockPos from = scene.mailbox(24, 4, Direction.SOUTH, fromName);
		BlockPos to = scene.mailbox(24, 28, Direction.NORTH, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliverUnharmed("Spike corridor", scene, to, watch);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_64x48", batch = "hazard_magma_snow", timeoutTicks = TRIAL_TICKS)
	public static void heGoesRoundAMagmaCausewayAndThePowderSnowDrifts(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 63, 47);
		// A pit six wide and four deep (too wide to leap, too deep to climb out of) from the south edge to within
		// eight blocks of the north, bridged only by a causeway of magma blocks; powder snow drifts on the way
		// round, with gaps three wide
		scene.fill(28, -3, 8, 33, 0, 47, Blocks.AIR.defaultBlockState());
		scene.fill(28, -3, 24, 33, -1, 25, Blocks.STONE.defaultBlockState());
		scene.fill(28, 0, 24, 33, 0, 25, Blocks.MAGMA_BLOCK.defaultBlockState());
		drift(scene, 20, 0, 21, 4);
		drift(scene, 38, 3, 39, 7);
		drift(scene, 46, 10, 47, 14);
		drift(scene, 12, 12, 13, 14);
		drift(scene, 50, 30, 52, 32);
		drift(scene, 24, 36, 26, 38);
		String fromName = Scene.address("GT Magma A"), toName = Scene.address("GT Magma B");
		BlockPos from = scene.mailbox(4, 24, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(59, 24, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliverUnharmed("Magma causeway", scene, to, watch);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_64x48", batch = "hazard_lava_lane", timeoutTicks = TRIAL_TICKS)
	public static void heWalksALaneThroughALavaFieldAndWhistlesHisHorseOver(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 63, 47);
		// A lava field twelve wide right across (it passes for water from afar whichever way it lines up), stone
		// at both ends, and one dry lane a single block wide: only someone on foot fits, so he has to get down,
		// walk it, and whistle the horse up on the far side (never onto the lava)
		lava(scene, 2, 18, 61, 29);
		wall(scene, 0, 18, 1, 29);
		wall(scene, 62, 18, 63, 29);
		scene.fill(30, 0, 18, 30, 0, 29, Blocks.STONE.defaultBlockState());
		String fromName = Scene.address("GT Lane A"), toName = Scene.address("GT Lane B");
		BlockPos from = scene.mailbox(32, 6, Direction.SOUTH, fromName);
		BlockPos to = scene.mailbox(32, 42, Direction.NORTH, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliverUnharmed("Lava lane", scene, to, watch);
	}

	@GameTest(skyAccess = true, template = "gametest/arena_96x96", batch = "hazard_lava_pocket", timeoutTicks = TRIAL_TICKS)
	public static void aLavaPocketOnTheStraightWayIsBackedOutOfOnce(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.ground(0, 0, 95, 95);
		// Lava four wide (too wide to leap, too narrow to pass for water from afar) in a horseshoe open towards
		// him, right across the straight line to the far box: a stretch planned to a point inside it is fine, the
		// next has to lead him back out and round
		lava(scene, 40, 26, 75, 29);
		lava(scene, 40, 67, 75, 70);
		lava(scene, 72, 26, 75, 70);
		String fromName = Scene.address("GT Pocket A"), toName = Scene.address("GT Pocket B");
		BlockPos from = scene.mailbox(6, 48, Direction.EAST, fromName);
		BlockPos to = scene.mailbox(89, 48, Direction.WEST, toName);
		Watch watch = new Watch(scene, scene.post(from, fromName, toName));
		deliverUnharmed("Lava pocket", scene, to, watch);
	}
}
