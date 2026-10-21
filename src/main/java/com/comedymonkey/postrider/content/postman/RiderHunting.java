package com.comedymonkey.postrider.content.postman;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Hostile mobs hunt riders the way they hunt players (spiders only in the dark); neutral ones (endermen, piglins...)
 * leave them be. Creepers
 * never stalk him: one only goes off if he comes right up close to it in plain sight, and never while he's in his
 * tent.
 */
public final class RiderHunting {
	/** How close a rider has to come for a creeper to go off. */
	public static final double CREEPER_NOTICE_RANGE = 3.0;

	private RiderHunting() {}

	/** Mobs that go looking for riders to attack: hostile ones, except neutral mobs and creepers. */
	public static boolean huntsRiders(Class<? extends Entity> type) {
		return Monster.class.isAssignableFrom(type) && !NeutralMob.class.isAssignableFrom(type) && !Creeper.class.isAssignableFrom(type);
	}

	/** Whether a creeper this far away (squared) goes off: only right up close, and never while he's in his tent. */
	public static boolean creeperNotices(double distanceSqr, boolean inTent) {
		return !inTent && distanceSqr <= CREEPER_NOTICE_RANGE * CREEPER_NOTICE_RANGE;
	}

	/** Spiders, like with players, only go looking for riders in the dark; in daylight they leave them be. */
	public static boolean huntsOnlyInTheDark(Class<? extends Entity> type) {
		return Spider.class.isAssignableFrom(type);
	}

	/** Spider light rules: a light level under half (the same as vanilla's spider targeting). */
	public static final float SPIDER_LIGHT = 0.5F;

	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster monster) || !huntsRiders(monster.getClass()))
			return;
		if (huntsOnlyInTheDark(monster.getClass()))
			monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, PostmanEntity.class, true) {
				@Override
				public boolean canUse() {
					return monster.getLightLevelDependentMagicValue() < SPIDER_LIGHT && super.canUse();
				}
			});
		else
			monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, PostmanEntity.class, true));
	}

	/** Lights the fuse of any creeper the rider has come right up to, in plain sight. */
	public static void startleCreepers(PostmanEntity rider, ServerLevel level) {
		for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, rider.getBoundingBox().inflate(CREEPER_NOTICE_RANGE)))
			if (!creeper.isIgnited() && creeperNotices(creeper.distanceToSqr(rider), rider.isInTent()) && creeper.hasLineOfSight(rider))
				creeper.ignite();
	}
}
