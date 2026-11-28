package com.comedymonkey.postrider.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.comedymonkey.postrider.forge.ForgeRiderTickets;

import net.minecraft.server.level.ServerChunkCache;

/**
 * As the server closes a level's tickets on stopping, the riders' are put aside to be saved, as NeoForge's patch to
 * TicketStorage.deactivateTicketsOnClosing does for its ticket controllers.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
	@Inject(method = "deactivateTicketsOnClosing", at = @At("TAIL"))
	private void postrider$deactivateRiderTickets(CallbackInfo info) {
		// (The cache's level is a public field)
		ForgeRiderTickets.deactivate(((ServerChunkCache) (Object) this).level);
	}
}
