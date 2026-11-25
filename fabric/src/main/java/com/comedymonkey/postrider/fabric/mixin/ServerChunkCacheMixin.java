package com.comedymonkey.postrider.fabric.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.comedymonkey.postrider.fabric.FabricRiderTickets;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;

/**
 * As the server closes a level's tickets on stopping, the riders' are put aside to be saved, as NeoForge's patch to
 * TicketStorage.deactivateTicketsOnClosing does for its ticket controllers.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "deactivateTicketsOnClosing", at = @At("TAIL"))
	private void postrider$deactivateRiderTickets(CallbackInfo info) {
		FabricRiderTickets.deactivate(level);
	}
}
