package com.comedymonkey.postrider.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.comedymonkey.postrider.client.PostRiderClientEvents;

import net.minecraft.world.entity.Entity;

/**
 * After a passenger's tick on the client (a rider on his horse), before he's put back in his seat: where NeoForge fires
 * EntityTickEvent.Post in Entity.rideTick. Only for the client's entities (in single player the server's entities ride
 * through here too; NeoForge hands those to the handler as well, and it ignores them).
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "rideTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
	private void postrider$passengerTickEnd(CallbackInfo info) {
		Entity entity = (Entity) (Object) this;
		if (entity.level().isClientSide())
			PostRiderClientEvents.entityTickEnd(entity);
	}
}
