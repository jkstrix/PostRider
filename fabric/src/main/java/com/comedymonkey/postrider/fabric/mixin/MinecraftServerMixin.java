package com.comedymonkey.postrider.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.comedymonkey.postrider.fabric.FabricRiderTickets;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * As the server loads its levels, the riders' saved chunk tickets are sorted out and held again, right after vanilla
 * holds its own forced chunks again and before the server's last wait for chunks: where NeoForge's
 * MinecraftServer.prepareLevels hands its saved tickets to the ticket controllers' validation and reinstates them.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
	@Shadow
	public abstract Iterable<ServerLevel> getAllLevels();

	@Inject(method = "prepareLevels",
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;updateChunkForced(Lnet/minecraft/world/level/ChunkPos;Z)V")),
		at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getNanos()J", ordinal = 0))
	private void postrider$reinstateRiderTickets(CallbackInfo info) {
		for (ServerLevel level : getAllLevels())
			FabricRiderTickets.reinstate(level);
	}
}
