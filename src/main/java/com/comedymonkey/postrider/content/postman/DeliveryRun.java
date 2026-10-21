package com.comedymonkey.postrider.content.postman;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.lost.LostCause;
import com.comedymonkey.postrider.content.lost.LostParcels;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.post.Mail;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.post.Notices;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.post.PostOfficeHours;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * One rider's delivery from start to finish: picking the mail up, calling his horse, riding there (resting at
 * night, freeing himself when stuck), walking up to the mailbox and delivering, or leaving a Lost Parcel when it
 * goes wrong.
 */
public class DeliveryRun {
	public enum Stage {
		PICKING_UP,
		CALLING_HORSE,
		RIDING,
		CAMPING,
		FLEEING,
		WALKING_TO_BOX,
		DELIVERING,
		DONE
	}

	/** Ticks into the pickup animation when the parcel goes in the satchel, and when it's over. */
	public static final int SATCHEL_FILLS_AT = 32;
	public static final int PICKUP_TICKS = 44;
	/** Ticks into the deliver animation when the mail goes into the box, and when it's over. */
	public static final int MAIL_IN_AT = 26;
	public static final int DELIVER_TICKS = 48;
	/** How long he waits for his horse before setting off on foot. */
	public static final int HORSE_WAIT_TICKS = 80;
	/** How long he tries to walk up to the mailbox before stepping up to it. */
	public static final int WALK_UP_TICKS = 200;
	private static final int AIM_INTERVAL = 40;
	/** On foot he runs as fast as a sprinting player; he only walks the last few steps to the mailbox. */
	public static final double RUN_SPEED = 1.2;

	private final PendingDelivery delivery;
	@Nullable
	private final GlobalPos destination;
	private Stage stage = Stage.PICKING_UP;
	private int stageTicks;
	@Nullable
	private ChunkPos loadedChunk;
	private final Journey.Progress progress = new Journey.Progress();
	private int aimCooldown;

	public DeliveryRun(PendingDelivery delivery, @Nullable GlobalPos destination) {
		this.delivery = delivery;
		this.destination = destination;
	}

	/** Plans a run to wherever the address is now; if it no longer exists, the run will end undeliverable. */
	public static DeliveryRun plan(ServerLevel level, PendingDelivery delivery) {
		MailboxDirectory.Entry entry = MailboxDirectory.get(level.getServer()).find(delivery.toName());
		return new DeliveryRun(delivery, entry != null ? entry.pos() : null);
	}

	public PendingDelivery delivery() {
		return delivery;
	}

	public Stage stage() {
		return stage;
	}

	public boolean isOver() {
		return stage == Stage.DONE;
	}

	private void enter(Stage next) {
		stage = next;
		stageTicks = 0;
	}

	// ---------------------------------------------------------------- where things are

	/**
	 * How far from the middle of the mailbox's post he stands, so his hands meet the door: the pickup animation
	 * reaches further than the deliver one.
	 */
	public static final double PICKUP_STAND_OFF = 1.35;
	public static final double DELIVER_STAND_OFF = 1.1;

	/** Where to stand at a mailbox: square in front of its door, this far from the post, on the mailbox's own level. */
	public static Vec3 standingSpot(BlockPos mailbox, Direction facing, double standOff) {
		return Vec3.atBottomCenterOf(mailbox).add(facing.getStepX() * standOff, 0, facing.getStepZ() * standOff);
	}

	public static Vec3 standingSpot(ServerLevel level, BlockPos mailbox, double standOff) {
		BlockState state = level.getBlockState(mailbox);
		Direction facing = state.getBlock() instanceof MailboxBlock ? state.getValue(MailboxBlock.FACING) : Direction.NORTH;
		return standingSpot(mailbox, facing, standOff);
	}

	/** The way to face to look straight at a point, as a Minecraft yaw. */
	public static float yawToward(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90;
	}

	/** Turns him, body and head, square to the mailbox's door, and keeps him from wandering off it. */
	private static void faceMailbox(PostmanEntity rider, BlockPos mailbox) {
		float yaw = yawToward(rider.position(), Vec3.atBottomCenterOf(mailbox));
		rider.getNavigation().stop();
		rider.setYRot(yaw);
		rider.setYBodyRot(yaw);
		rider.setYHeadRot(yaw);
		rider.getLookControl().setLookAt(Vec3.atCenterOf(mailbox.above()));
	}

	@Nullable
	private Vec3 destinationSpot(ServerLevel level) {
		if (destination == null)
			return null;
		level.getChunkAt(destination.pos());
		return standingSpot(level, destination.pos(), DELIVER_STAND_OFF);
	}

	// ---------------------------------------------------------------- each tick

	public void tick(PostmanEntity rider, ServerLevel level) {
		if (stage == Stage.DONE)
			return;
		keepChunksLoaded(rider, level);
		logChanges(rider);
		stageTicks++;
		switch (stage) {
			case PICKING_UP -> pickUp(rider, level);
			case CALLING_HORSE -> callHorse(rider, level);
			case RIDING -> ride(rider, level);
			case CAMPING -> camp(rider, level);
			case FLEEING -> flee(rider, level);
			case WALKING_TO_BOX -> walkUp(rider, level);
			case DELIVERING -> deliver(rider, level);
			default -> {}
		}
	}

	@Nullable
	private Stage loggedStage;
	@Nullable
	private CampStep loggedStep;

	/** Notes in the log each time he moves on to the next part of the job, for working out what went wrong. */
	private void logChanges(PostmanEntity rider) {
		CampStep step = stage == Stage.CAMPING ? campStep : null;
		if (stage != loggedStage || step != loggedStep) {
			PostRider.LOGGER.info("Rider {} with mail for {}: {}{}", rider.getName().getString(), delivery.toName(), stage,
				step != null ? " (" + step + ")" : "");
			loggedStage = stage;
			loggedStep = step;
		}
	}

	/** What he's up to, for /postrider status. */
	public String describe(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destination != null ? Vec3.atCenterOf(destination.pos()) : null;
		String distance = target != null ? (int) Journey.horizontalDistance(rider.position(), target) + " blocks" : "unknown";
		return rider.getName().getString() + ": " + stage + (stage == Stage.CAMPING ? " (" + campStep + ")" : "")
			+ " for " + stageTicks + " ticks, activity " + rider.getActivity() + (rider.isInTent() ? ", in his tent" : "")
			+ ", " + (int) rider.getHealth() + " health and " + rider.getPotions() + " potions"
			+ ", " + distance + " to " + delivery.toName() + ", time of day " + PostOfficeHours.timeOfDay(level.getDayTime());
	}

	private void keepChunksLoaded(PostmanEntity rider, ServerLevel level) {
		ChunkPos now = rider.chunkPosition();
		if (!now.equals(loadedChunk)) {
			RiderChunks.move(level, rider.getUUID(), loadedChunk, now);
			loadedChunk = now;
		}
	}

	private void pickUp(PostmanEntity rider, ServerLevel level) {
		faceMailbox(rider, delivery.from().pos());
		if (stageTicks == 1)
			rider.playGesture(PostmanEntity.Gesture.PICKUP);
		if (stageTicks == SATCHEL_FILLS_AT)
			rider.setCarrying(true);
		if (stageTicks >= PICKUP_TICKS) {
			if (destination == null) {
				fail(rider, level, LostCause.UNDELIVERABLE, rider.getName().getString() + " found that " + delivery.toName()
					+ " no longer has a mailbox.", null, delivery.from().pos());
				return;
			}
			enter(Stage.CALLING_HORSE);
		}
	}

	private void callHorse(PostmanEntity rider, ServerLevel level) {
		if (rider.getHorse() != null) {
			enter(Stage.RIDING);
			return;
		}
		if (stageTicks == 1)
			rider.whistleForHorse();
		// No room for a horse here: he sets off on foot and tries again later
		if (stageTicks >= HORSE_WAIT_TICKS)
			enter(Stage.RIDING);
	}

	private void ride(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destinationSpot(level);
		if (target == null)
			return;
		if (!PostOfficeHours.isOpen(level.getDayTime()) && !rider.isInWater()) {
			enter(Stage.CAMPING);
			return;
		}
		double distance = Journey.horizontalDistance(rider.position(), target);
		if (distance <= Journey.DISMOUNT_DISTANCE) {
			rider.dismissHorse();
			rider.setSprinting(false);
			enter(Stage.WALKING_TO_BOX);
			return;
		}
		// On foot because there was no room for the horse: whistle again every so often
		if (rider.getHorse() == null && stageTicks % 200 == 0)
			rider.whistleForHorse();
		if (checkStuck(rider, level, distance, target))
			return;
		boolean onFoot = rider.getHorse() == null;
		rider.setSprinting(onFoot);
		if (!onFoot)
			jumpGaps(rider.getHorse(), level);
		if (--aimCooldown <= 0 || rider.getNavigation().isDone()) {
			aimCooldown = AIM_INTERVAL;
			Vec3 aim = chooseAim(level, rider.position(), target);
			rider.getNavigation().moveTo(aim.x, aim.y, aim.z, onFoot ? RUN_SPEED : 1.0);
		}
	}

	/** The first stretch ahead that ends on safe, dry ground, or straight ahead if every way is water or worse. */
	static Vec3 chooseAim(ServerLevel level, Vec3 from, Vec3 target) {
		Vec3 safe = safeAim(level, from, target);
		return safe != null ? safe : surface(level, Journey.nextAim(from, target));
	}

	/** The first stretch ahead ending somewhere safe to stand, or null if there's none. */
	@Nullable
	static Vec3 safeAim(ServerLevel level, Vec3 from, Vec3 target) {
		for (Vec3 aim : Journey.aims(from, target)) {
			Vec3 ground = surface(level, aim);
			if (!Hazards.isDangerous(level, BlockPos.containing(ground)))
				return ground;
		}
		return null;
	}

	private static Vec3 surface(ServerLevel level, Vec3 aim) {
		BlockPos column = BlockPos.containing(aim);
		return new Vec3(aim.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), aim.z);
	}

	// ---------------------------------------------------------------- jumping gaps

	/** How fast the horse must already be going before it will leap, so it never jumps from a standstill. */
	private static final double JUMP_MIN_SPEED = 0.15;

	/** When the ground ahead drops into a gap with safe ground just beyond, the horse leaps it. */
	private static void jumpGaps(PostmanHorse horse, ServerLevel level) {
		Vec3 motion = horse.getDeltaMovement();
		double speed = motion.horizontalDistance();
		if (!horse.onGround() || speed < JUMP_MIN_SPEED)
			return;
		Vec3 heading = new Vec3(motion.x / speed, 0, motion.z / speed);
		int here = horse.getBlockY();
		int[] ahead = new int[GapJump.MAX_GAP + 1];
		boolean[] safe = new boolean[ahead.length];
		for (int i = 0; i < ahead.length; i++) {
			Vec3 spot = horse.position().add(heading.scale(i + 1.2));
			BlockPos column = BlockPos.containing(spot.x, here, spot.z);
			ahead[i] = floorAt(level, column);
			safe[i] = !Hazards.isDangerous(level, column.atY(ahead[i]));
		}
		int landing = GapJump.landingDistance(here, ahead, safe);
		if (landing == 0)
			return;
		horse.getJumpControl().jump();
		double push = 0.35 + 0.12 * landing;
		horse.setDeltaMovement(heading.x * push, motion.y, heading.z * push);
	}

	/** The height he'd stand at in this column, searching a little above and well below where he is. */
	private static int floorAt(ServerLevel level, BlockPos near) {
		for (int y = near.getY() + 1; y >= near.getY() - 6; y--) {
			BlockPos feet = near.atY(y);
			if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
					&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty())
				return y;
		}
		return near.getY() - 7;
	}

	/** How long he hammers in the tent pegs before the tent goes up. */
	public static final int HAMMER_TICKS = 100;
	/** How far behind him the middle of the tent stands, with its open front facing him. */
	public static final double TENT_DISTANCE = 2.2;
	/** The tent runs this far either side of its middle, from the open door to the back wall (18 pixels). */
	public static final double TENT_HALF_LENGTH = 18 / 16.0;
	/** Lying flat he's this long from his soles to the top of his head. */
	public static final double SLEEPER_LENGTH = 2.0;
	/** How far past the door his boots stick out. */
	public static final double BOOTS_OUT = 0.3;
	/** How long he walks towards his pitch before stepping onto it. */
	public static final int WALK_TO_CAMP_TICKS = 200;
	/** How far players hear the harmonica, for stopping it when he turns in. */
	private static final double HEARING_RANGE = 48;

	/** The evening, step by step: find a pitch, walk to it, hammer in the pegs, play, sleep. */
	public enum CampStep {
		FINDING,
		WALKING,
		HAMMERING,
		PLAYING,
		SLEEPING
	}

	private CampStep campStep = CampStep.FINDING;
	private int stepTicks;
	@Nullable
	private CampSites.Site campSite;
	@Nullable
	private UUID tentId;

	private void step(CampStep next) {
		campStep = next;
		stepTicks = 0;
	}

	/**
	 * Dusk: he finds a flat pitch with room for the tent, walks to it and hammers in the pegs; the tent goes up behind
	 * him in a puff of smoke and he sits playing the harmonica until monsters come out, then turns in, hidden from all
	 * but the closest mobs. At 6am he packs up and rides on. With nowhere to pitch, he plays and sleeps in the open.
	 */
	private void camp(PostmanEntity rider, ServerLevel level) {
		progress.pause(level.getGameTime());
		if (PostOfficeHours.isOpen(level.getDayTime())) {
			breakCamp(rider, level);
			enter(Stage.CALLING_HORSE);
			return;
		}
		stepTicks++;
		switch (campStep) {
			case FINDING -> {
				rider.getNavigation().stop();
				rider.dismissHorse();
				rider.setSprinting(false);
				BlockPos here = rider.blockPosition();
				campSite = CampSites.find(here, (x, z) -> CampSites.floorIn(level, x, z, here.getY()));
				if (campSite != null)
					step(CampStep.WALKING);
				else
					startEvening(rider, level);
			}
			case WALKING -> {
				Vec3 seat = campSite.seatPos();
				if (Journey.horizontalDistance(rider.position(), seat) < 0.6 || stepTicks >= WALK_TO_CAMP_TICKS) {
					rider.getNavigation().stop();
					rider.moveTo(seat.x, seat.y, seat.z, campSite.yaw() + 180, 0);
					face(rider, campSite.yaw() + 180);
					rider.setActivity(PostmanEntity.Activity.CAMP_HAMMER);
					step(CampStep.HAMMERING);
				} else if (stepTicks % 20 == 1) {
					rider.getNavigation().moveTo(seat.x, seat.y, seat.z, 1.0);
				}
			}
			case HAMMERING -> {
				// Facing the tent as he knocks the pegs in, then he turns round to sit at its door
				face(rider, campSite.yaw() + 180);
				if (stepTicks >= HAMMER_TICKS) {
					pitchTent(rider, level);
					face(rider, campSite.yaw());
					startEvening(rider, level);
				}
			}
			case PLAYING -> {
				if (PostOfficeHours.isBedtime(level.getDayTime())) {
					stopHarmonica(rider, level);
					turnIn(rider, level);
				}
			}
			case SLEEPING -> {}
		}
	}

	/** The harmonica until monsters come out; straight to bed if they already have. */
	private void startEvening(PostmanEntity rider, ServerLevel level) {
		if (PostOfficeHours.isBedtime(level.getDayTime())) {
			turnIn(rider, level);
			return;
		}
		rider.setActivity(PostmanEntity.Activity.HARMONICA);
		level.playSound(null, rider, ModSounds.HARMONICA.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		step(CampStep.PLAYING);
	}

	private static void face(PostmanEntity rider, float yaw) {
		rider.getNavigation().stop();
		rider.setYRot(yaw);
		rider.setYBodyRot(yaw);
		rider.setYHeadRot(yaw);
	}

	/** The tent goes up on the pitch behind him, open end towards him. */
	private void pitchTent(PostmanEntity rider, ServerLevel level) {
		TentEntity tent = campSite == null ? null : ModEntities.TENT.get().create(level);
		if (tent == null)
			return;
		Vec3 spot = campSite.tentPos();
		tent.moveTo(spot.x, spot.y, spot.z, campSite.yaw(), 0);
		tent.setRider(rider.getUUID());
		level.addFreshEntity(tent);
		PostmanEffects.puff(level, tent);
		tentId = tent.getUUID();
	}

	/** Into the tent for the night, flat on his back with his boots out of the door (or, with no tent, dozing where he sits). */
	private void turnIn(PostmanEntity rider, ServerLevel level) {
		if (tentId != null && level.getEntity(tentId) instanceof TentEntity tent) {
			Vec3 soles = sleepingSpot(tent.position(), tent.getYRot());
			rider.moveTo(soles.x, soles.y, soles.z, tent.getYRot(), 0);
			face(rider, tent.getYRot());
			rider.setInTent(true);
			rider.setActivity(PostmanEntity.Activity.SLEEP);
		} else {
			rider.setActivity(PostmanEntity.Activity.CAMP_REST);
		}
		step(CampStep.SLEEPING);
	}

	/** Where his soles go when he lies down in a tent: just out of the door, so his head is inside by the back wall. */
	public static Vec3 sleepingSpot(Vec3 tent, float tentYaw) {
		return tent.add(Vec3.directionFromRotation(0, tentYaw).scale(TENT_HALF_LENGTH + BOOTS_OUT));
	}

	private void breakCamp(PostmanEntity rider, ServerLevel level) {
		stopHarmonica(rider, level);
		if (rider.isInTent() && campSite != null) {
			Vec3 seat = campSite.seatPos();
			rider.moveTo(seat.x, seat.y, seat.z, rider.getYRot(), 0);
		}
		rider.setInTent(false);
		rider.setActivity(PostmanEntity.Activity.NONE);
		if (tentId != null && level.getEntity(tentId) instanceof TentEntity tent)
			tent.packAway(level);
		tentId = null;
		campSite = null;
		step(CampStep.FINDING);
	}

	// ---------------------------------------------------------------- fleeing

	/** How far he runs from whatever attacked him each time he picks somewhere to run to. */
	private static final int FLEE_DISTANCE = 16;
	/** He keeps running from anything hostile this close. */
	private static final double THREAT_RANGE = 16;

	@Nullable
	private UUID attacker;

	/** Something hurt him. At camp, he leaves everything and runs for it on foot until daylight. */
	public void attacked(PostmanEntity rider, ServerLevel level, LivingEntity by) {
		if (stage != Stage.CAMPING && stage != Stage.FLEEING)
			return;
		attacker = by.getUUID();
		if (stage == Stage.CAMPING) {
			breakCamp(rider, level);
			enter(Stage.FLEEING);
		}
	}

	private void flee(PostmanEntity rider, ServerLevel level) {
		progress.pause(level.getGameTime());
		if (PostOfficeHours.isOpen(level.getDayTime())) {
			rider.setSprinting(false);
			attacker = null;
			enter(Stage.CALLING_HORSE);
			return;
		}
		if (stageTicks % 20 != 1 && !rider.getNavigation().isDone())
			return;
		Entity threat = attacker != null ? level.getEntity(attacker) : null;
		if (threat == null || !threat.isAlive() || threat.distanceTo(rider) > THREAT_RANGE * 2)
			threat = level.getNearestEntity(Monster.class, TargetingConditions.forCombat().range(THREAT_RANGE), rider,
				rider.getX(), rider.getY(), rider.getZ(), rider.getBoundingBox().inflate(THREAT_RANGE));
		if (threat == null) {
			rider.setSprinting(false);
			return;
		}
		Vec3 away = DefaultRandomPos.getPosAway(rider, FLEE_DISTANCE, 7, threat.position());
		if (away != null) {
			rider.setSprinting(true);
			rider.getNavigation().moveTo(away.x, away.y, away.z, RUN_SPEED);
		}
	}

	/** Stops the tune for everyone who could hear it. */
	private static void stopHarmonica(PostmanEntity rider, ServerLevel level) {
		ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(ModSounds.HARMONICA.getId(), SoundSource.NEUTRAL);
		for (ServerPlayer player : level.players())
			if (player.distanceToSqr(rider) < HEARING_RANGE * HEARING_RANGE)
				player.connection.send(stop);
	}

	private void walkUp(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destinationSpot(level);
		if (target == null)
			return;
		double distance = Journey.horizontalDistance(rider.position(), target);
		if (distance <= Journey.DELIVER_DISTANCE || stageTicks >= WALK_UP_TICKS) {
			// The last step up to the door, so his hands reach into the box
			rider.getNavigation().stop();
			rider.teleportTo(target.x, target.y, target.z);
			enter(Stage.DELIVERING);
			return;
		}
		if (stageTicks % 20 == 1)
			rider.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
	}

	private void deliver(PostmanEntity rider, ServerLevel level) {
		if (destination == null)
			return;
		BlockPos box = destination.pos();
		faceMailbox(rider, box);
		if (stageTicks == 1)
			rider.playGesture(PostmanEntity.Gesture.DELIVER);
		if (stageTicks == MAIL_IN_AT && !postMail(rider, level, box))
			return;
		if (stageTicks >= DELIVER_TICKS) {
			finish(rider, level);
			rider.vanish();
		}
	}

	/** Puts the mail in the box; if the box is gone, renamed or full, leaves a Lost Parcel instead. */
	private boolean postMail(PostmanEntity rider, ServerLevel level, BlockPos box) {
		MailboxDirectory.Entry entry = MailboxDirectory.get(level.getServer()).find(delivery.toName());
		boolean sameBox = entry != null && entry.pos().equals(destination);
		MailboxBlockEntity mailbox = sameBox && level.getBlockEntity(box) instanceof MailboxBlockEntity found ? found : null;
		String name = rider.getName().getString();
		if (mailbox == null) {
			fail(rider, level, LostCause.UNDELIVERABLE, name + " found no mailbox called " + delivery.toName() + " there any more.", null, box);
			return false;
		}
		long travelTicks = level.getGameTime() - delivery.sentAt();
		if (!mailbox.deliver(Mail.wrap(delivery, Mail.day(level.getDayTime()), travelTicks))) {
			fail(rider, level, LostCause.UNDELIVERABLE, name + " found the mailbox at " + delivery.toName() + " too full to take it.", null, box);
			return false;
		}
		rider.setCarrying(false);
		Notices.delivered(level.getServer(), delivery, name, entry.owner());
		return true;
	}

	// ---------------------------------------------------------------- getting stuck

	private boolean checkStuck(PostmanEntity rider, ServerLevel level, double distance, Vec3 target) {
		if (stageTicks % 20 != 0 || !progress.isStuck(distance, level.getGameTime()))
			return false;
		if (progress.outOfTries()) {
			fail(rider, level, LostCause.STUCK, rider.getName().getString()
				+ " lost his way in the wilds and had to turn back.", null, rider.blockPosition());
			return true;
		}
		Vec3 free = freeSpot(level, rider.position(), target, rider.level().canSeeSky(rider.blockPosition()));
		// Nowhere safe to jump to this time (lava all round, say); it still counts as a try
		if (free == null) {
			progress.unstuck(distance, level.getGameTime());
			return true;
		}
		Entity mover = rider.getVehicle() != null ? rider.getVehicle() : rider;
		PostmanEffects.puff(level, mover);
		mover.teleportTo(free.x, free.y, free.z);
		PostmanEffects.puff(level, mover);
		rider.getNavigation().stop();
		aimCooldown = 0;
		progress.unstuck(Journey.horizontalDistance(free, target), level.getGameTime());
		return true;
	}

	/**
	 * Out of a cave: straight up to the surface; otherwise a stretch ahead, on top of whatever is there. Never onto
	 * lava, fire and the like: null if there's nowhere safe.
	 */
	@Nullable
	static Vec3 freeSpot(ServerLevel level, Vec3 from, Vec3 target, boolean underOpenSky) {
		Vec3 aim = underOpenSky ? safeAim(level, from, target) : surface(level, from);
		if (aim == null)
			return null;
		BlockPos column = BlockPos.containing(aim);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
		if (Hazards.isDangerous(level, feet))
			return underOpenSky ? null : freeSpot(level, from, target, true);
		return new Vec3(column.getX() + 0.5, y, column.getZ() + 0.5);
	}

	// ---------------------------------------------------------------- ending

	/** He gives up and turns back, as if he'd got hopelessly stuck (for testing failures). */
	public void giveUp(PostmanEntity rider, ServerLevel level) {
		if (stage != Stage.DONE)
			fail(rider, level, LostCause.STUCK, rider.getName().getString() + " lost his way in the wilds and had to turn back.",
				null, rider.blockPosition());
	}

	/** He was killed on the road. */
	public void killed(PostmanEntity rider, ServerLevel level, DamageSource source) {
		Entity killer = source.getEntity();
		UUID killerId = killer instanceof Player ? killer.getUUID() : null;
		String killerName = killer != null ? killer.getName().getString() : "";
		String reason = source.getLocalizedDeathMessage(rider).getString() + ".";
		fail(rider, level, LostCause.KILLED, reason, killerId, killerName, rider.blockPosition());
	}

	private void fail(PostmanEntity rider, ServerLevel level, LostCause cause, String reason, @Nullable UUID killer, BlockPos near) {
		fail(rider, level, cause, reason, killer, "", near);
	}

	private void fail(PostmanEntity rider, ServerLevel level, LostCause cause, String reason, @Nullable UUID killer,
			String killerName, BlockPos near) {
		MailboxDirectory.Entry entry = MailboxDirectory.get(level.getServer()).find(delivery.toName());
		UUID recipient = entry != null ? entry.owner() : null;
		BlockPos at = LostParcels.place(level, near, delivery, cause, recipient, killer, killerName);
		Notices.failed(level.getServer(), delivery, reason, at != null ? at : near, at != null, Mail.day(level.getDayTime()));
		rider.setCarrying(false);
		finish(rider, level);
		if (cause != LostCause.KILLED)
			rider.vanish();
	}

	private void finish(PostmanEntity rider, ServerLevel level) {
		enter(Stage.DONE);
		PostOffice.get(level.getServer()).finish(delivery.id());
		if (loadedChunk != null)
			RiderChunks.release(level, rider.getUUID(), loadedChunk);
		loadedChunk = null;
	}

	// ---------------------------------------------------------------- saving

	public CompoundTag save(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		PendingDelivery.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), delivery).result()
			.ifPresent(d -> tag.put("delivery", d));
		if (destination != null)
			GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, destination).result().ifPresent(d -> tag.put("destination", d));
		tag.putString("stage", stage.name());
		tag.putInt("stage_ticks", stageTicks);
		if (loadedChunk != null)
			tag.putLong("loaded_chunk", loadedChunk.toLong());
		tag.putString("camp_step", campStep.name());
		tag.putInt("step_ticks", stepTicks);
		if (campSite != null) {
			tag.putLong("camp_seat", campSite.seat().asLong());
			tag.putString("camp_facing", campSite.facing().getName());
		}
		if (tentId != null)
			tag.putUUID("tent", tentId);
		if (attacker != null)
			tag.putUUID("attacker", attacker);
		return tag;
	}

	@Nullable
	public static DeliveryRun load(CompoundTag tag, HolderLookup.Provider registries) {
		PendingDelivery delivery = PendingDelivery.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("delivery"))
			.result().orElse(null);
		if (delivery == null)
			return null;
		GlobalPos destination = tag.contains("destination")
			? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("destination")).result().orElse(null) : null;
		DeliveryRun run = new DeliveryRun(delivery, destination);
		try {
			run.stage = Stage.valueOf(tag.getString("stage"));
		} catch (IllegalArgumentException e) {
			run.stage = Stage.RIDING;
		}
		run.stageTicks = tag.getInt("stage_ticks");
		if (tag.contains("loaded_chunk"))
			run.loadedChunk = new ChunkPos(tag.getLong("loaded_chunk"));
		try {
			run.campStep = CampStep.valueOf(tag.getString("camp_step"));
		} catch (IllegalArgumentException e) {
			run.campStep = CampStep.FINDING;
		}
		run.stepTicks = tag.getInt("step_ticks");
		Direction facing = Direction.byName(tag.getString("camp_facing"));
		if (tag.contains("camp_seat") && facing != null)
			run.campSite = new CampSites.Site(BlockPos.of(tag.getLong("camp_seat")), facing);
		// A pitch that didn't survive saving: start the evening over
		if (run.campSite == null && (run.campStep == CampStep.WALKING || run.campStep == CampStep.HAMMERING))
			run.campStep = CampStep.FINDING;
		if (tag.hasUUID("tent"))
			run.tentId = tag.getUUID("tent");
		if (tag.hasUUID("attacker"))
			run.attacker = tag.getUUID("attacker");
		return run;
	}
}
