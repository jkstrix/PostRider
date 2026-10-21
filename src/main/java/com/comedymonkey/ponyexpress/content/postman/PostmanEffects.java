package com.comedymonkey.ponyexpress.content.postman;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** The puff of smoke the rider and his horse arrive and leave in. */
public final class PostmanEffects {
	private PostmanEffects() {}

	/** A cloud of smoke around an entity, sized to it, with a soft hiss. */
	public static void puff(ServerLevel level, Entity entity) {
		double x = entity.getX(), y = entity.getY() + entity.getBbHeight() / 2, z = entity.getZ();
		double spreadX = entity.getBbWidth() / 2 + 0.2, spreadY = entity.getBbHeight() / 2;
		int amount = (int) Math.ceil(entity.getBbWidth() * entity.getBbHeight() * 12);
		level.sendParticles(ParticleTypes.POOF, x, y, z, amount, spreadX, spreadY, spreadX, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, amount / 2, spreadX, spreadY, spreadX, 0.01);
		level.sendParticles(ParticleTypes.CLOUD, x, entity.getY() + 0.1, z, amount / 2, spreadX, 0.05, spreadX, 0.02);
		level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6F, 1.5F);
	}
}
