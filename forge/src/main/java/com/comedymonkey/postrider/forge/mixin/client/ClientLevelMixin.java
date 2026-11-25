package com.comedymonkey.postrider.forge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.comedymonkey.postrider.client.PostRiderClientEvents;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * After each entity's own tick on the client (one that isn't riding anything), where NeoForge fires EntityTickEvent.Post
 * in ClientLevel.tickNonPassenger. Passengers: EntityMixin. Forge has no entity tick event (its LivingTickEvent fires
 * before a living entity's tick, and for living entities only); its patch here only skips the tick for an entity that
 * can't update, and then this doesn't fire either, as NeoForge's doesn't for a cancelled tick.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	@Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
	private void postrider$entityTickEnd(Entity entity, CallbackInfo info) {
		PostRiderClientEvents.entityTickEnd(entity);
	}
}
