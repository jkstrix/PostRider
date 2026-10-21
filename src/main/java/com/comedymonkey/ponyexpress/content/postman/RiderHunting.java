package com.comedymonkey.ponyexpress.content.postman;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Hostile mobs hunt riders the way they hunt players; neutral ones (endermen, piglins...) leave them be. */
public final class RiderHunting {
	private RiderHunting() {}

	public static boolean huntsRiders(Class<? extends Entity> type) {
		return Monster.class.isAssignableFrom(type) && !NeutralMob.class.isAssignableFrom(type);
	}

	public static void onJoin(EntityJoinLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.getEntity() instanceof Monster monster && huntsRiders(monster.getClass()))
			monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, PostmanEntity.class, true));
	}
}
