package com.comedymonkey.postrider.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.comedymonkey.postrider.PostRiderEvents;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/**
 * An entity joining a server level, spawned or loaded from disk, just before it's added: where NeoForge fires
 * EntityJoinLevelEvent. (Fabric API's ServerEntityEvents.ENTITY_LOAD fires each time an entity starts being tracked,
 * which can be more than once for the same entity, so it isn't used.) The client's levels aren't hooked: the mod's
 * handler ignores the client side.
 */
@Mixin(PersistentEntitySectionManager.class)
abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {
	@Inject(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z", at = @At("HEAD"))
	private void postrider$entityJoinedLevel(T entity, boolean loaded, CallbackInfoReturnable<Boolean> info) {
		if (entity instanceof Entity joined)
			PostRiderEvents.entityJoinedLevel(joined, joined.level());
	}
}
