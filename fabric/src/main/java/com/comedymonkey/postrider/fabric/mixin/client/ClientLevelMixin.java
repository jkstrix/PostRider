package com.comedymonkey.postrider.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.comedymonkey.postrider.client.PostRiderClientEvents;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * After each entity's own tick on the client (one that isn't riding anything), where NeoForge fires EntityTickEvent.Post
 * in ClientLevel.tickNonPassenger. Passengers: EntityMixin.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	@Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
	private void postrider$entityTickEnd(Entity entity, CallbackInfo info) {
		PostRiderClientEvents.entityTickEnd(entity);
	}
}
