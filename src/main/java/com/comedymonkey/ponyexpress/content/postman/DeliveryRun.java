package com.comedymonkey.ponyexpress.content.postman;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.ponyexpress.content.lost.LostCause;
import com.comedymonkey.ponyexpress.content.lost.LostParcels;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxBlock;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.ponyexpress.post.Mail;
import com.comedymonkey.ponyexpress.post.MailboxDirectory;
import com.comedymonkey.ponyexpress.post.Notices;
import com.comedymonkey.ponyexpress.post.PendingDelivery;
import com.comedymonkey.ponyexpress.post.PostOffice;
import com.comedymonkey.ponyexpress.post.PostOfficeHours;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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

	/** Where to stand in front of a mailbox (two blocks out from its door), on the mailbox's own level. */
	public static BlockPos standingSpot(ServerLevel level, BlockPos mailbox) {
		BlockState state = level.getBlockState(mailbox);
		Direction facing = state.getBlock() instanceof MailboxBlock ? state.getValue(MailboxBlock.FACING) : Direction.NORTH;
		return mailbox.relative(facing, 2);
	}

	@Nullable
	private Vec3 destinationSpot(ServerLevel level) {
		if (destination == null)
			return null;
		level.getChunkAt(destination.pos());
		return Vec3.atBottomCenterOf(standingSpot(level, destination.pos()));
	}

	// ---------------------------------------------------------------- each tick

	public void tick(PostmanEntity rider, ServerLevel level) {
		if (stage == Stage.DONE)
			return;
		keepChunksLoaded(rider, level);
		stageTicks++;
		switch (stage) {
			case PICKING_UP -> pickUp(rider, level);
			case CALLING_HORSE -> callHorse(rider, level);
			case RIDING -> ride(rider, level);
			case CAMPING -> camp(rider, level);
			case WALKING_TO_BOX -> walkUp(rider, level);
			case DELIVERING -> deliver(rider, level);
			default -> {}
		}
	}

	private void keepChunksLoaded(PostmanEntity rider, ServerLevel level) {
		ChunkPos now = rider.chunkPosition();
		if (!now.equals(loadedChunk)) {
			RiderChunks.move(level, rider.getUUID(), loadedChunk, now);
			loadedChunk = now;
		}
	}

	private void pickUp(PostmanEntity rider, ServerLevel level) {
		if (stageTicks == 1) {
			rider.getLookControl().setLookAt(Vec3.atCenterOf(delivery.from().pos().above()));
			rider.playGesture(PostmanEntity.Gesture.PICKUP);
		}
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
			enter(Stage.WALKING_TO_BOX);
			return;
		}
		// On foot because there was no room for the horse: whistle again every so often
		if (rider.getHorse() == null && stageTicks % 200 == 0)
			rider.whistleForHorse();
		if (checkStuck(rider, level, distance, target))
			return;
		if (--aimCooldown <= 0 || rider.getNavigation().isDone()) {
			aimCooldown = AIM_INTERVAL;
			Vec3 aim = chooseAim(level, rider.position(), target);
			rider.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0);
		}
	}

	/** The first stretch ahead that ends on dry ground, or straight ahead if every way is water. */
	static Vec3 chooseAim(ServerLevel level, Vec3 from, Vec3 target) {
		List<Vec3> aims = Journey.aims(from, target);
		Vec3 first = null;
		for (Vec3 aim : aims) {
			BlockPos column = BlockPos.containing(aim);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			Vec3 ground = new Vec3(aim.x, y, aim.z);
			if (first == null)
				first = ground;
			if (level.getFluidState(new BlockPos(column.getX(), y - 1, column.getZ())).isEmpty())
				return ground;
		}
		return first;
	}

	private void camp(PostmanEntity rider, ServerLevel level) {
		progress.pause(level.getGameTime());
		if (stageTicks == 1) {
			rider.getNavigation().stop();
			rider.dismissHorse();
			rider.setActivity(PostmanEntity.Activity.CAMP_REST);
		}
		if (PostOfficeHours.isOpen(level.getDayTime())) {
			rider.setActivity(PostmanEntity.Activity.NONE);
			enter(Stage.CALLING_HORSE);
		}
	}

	private void walkUp(PostmanEntity rider, ServerLevel level) {
		Vec3 target = destinationSpot(level);
		if (target == null)
			return;
		double distance = Journey.horizontalDistance(rider.position(), target);
		if (distance <= Journey.DELIVER_DISTANCE || stageTicks >= WALK_UP_TICKS) {
			if (distance > Journey.DELIVER_DISTANCE)
				rider.teleportTo(target.x, target.y, target.z);
			rider.getNavigation().stop();
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
		if (stageTicks == 1) {
			rider.getLookControl().setLookAt(Vec3.atCenterOf(box.above()));
			rider.playGesture(PostmanEntity.Gesture.DELIVER);
		}
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
		if (!mailbox.deliver(Mail.wrap(delivery, Mail.day(level.getDayTime())))) {
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
		Entity mover = rider.getVehicle() != null ? rider.getVehicle() : rider;
		PostmanEffects.puff(level, mover);
		mover.teleportTo(free.x, free.y, free.z);
		PostmanEffects.puff(level, mover);
		rider.getNavigation().stop();
		aimCooldown = 0;
		progress.unstuck(Journey.horizontalDistance(free, target), level.getGameTime());
		return true;
	}

	/** Out of a cave: straight up to the surface; otherwise a stretch ahead, on top of whatever is there. */
	static Vec3 freeSpot(ServerLevel level, Vec3 from, Vec3 target, boolean underOpenSky) {
		Vec3 aim = underOpenSky ? chooseAim(level, from, target) : from;
		BlockPos column = BlockPos.containing(aim);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
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
		return run;
	}
}
