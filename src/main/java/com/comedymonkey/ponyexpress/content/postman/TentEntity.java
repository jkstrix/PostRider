package com.comedymonkey.ponyexpress.content.postman;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The rider's tent for the night. It's an entity, not blocks, so it never changes the land; it can't be hurt, pushed
 * or walked through, and packs itself away (in a puff of smoke) if its rider is no longer there.
 */
public class TentEntity extends Entity {
	/** How often it checks that its rider is still about. */
	private static final int CHECK_INTERVAL = 20;

	@Nullable
	private UUID rider;

	public TentEntity(EntityType<? extends TentEntity> type, Level level) {
		super(type, level);
	}

	public void setRider(UUID rider) {
		this.rider = rider;
	}

	@Nullable
	public UUID getRider() {
		return rider;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && tickCount % CHECK_INTERVAL == 0) {
			Entity owner = rider != null ? server.getEntity(rider) : null;
			if (!(owner instanceof PostmanEntity postman) || !postman.isAlive())
				packAway(server);
		}
	}

	/** Gone in a puff of smoke. */
	public void packAway(ServerLevel level) {
		PostmanEffects.puff(level, this);
		discard();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		rider = tag.hasUUID("rider") ? tag.getUUID("rider") : null;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (rider != null)
			tag.putUUID("rider", rider);
	}
}
