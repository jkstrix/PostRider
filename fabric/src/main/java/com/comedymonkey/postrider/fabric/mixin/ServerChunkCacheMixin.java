package com.comedymonkey.postrider.fabric.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.comedymonkey.postrider.fabric.FabricRiderTickets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * A chunk a rider holds ticking (his ticking square) gets its chunk tick (random ticks, natural spawning) even with no
 * player close enough, as NeoForge's forceTicks tickets make it: its patch to ServerChunkCache.tickChunks turns
 * {@code spawningAllowed && playerClose} into {@code (spawningAllowed && playerClose) || forced}; each half is or'ed with
 * {@code forced} here, which comes to the same.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
	@Shadow
	@Final
	ServerLevel level;

	@WrapOperation(method = "tickChunks", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z"))
	private boolean postrider$riderForcesSpawningAllowed(ServerLevel serverLevel, ChunkPos pos, Operation<Boolean> original) {
		return original.call(serverLevel, pos) || FabricRiderTickets.forcesTicks(level, pos);
	}

	@WrapOperation(method = "tickChunks", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"))
	private boolean postrider$riderForcesPlayerClose(ChunkMap chunkMap, ChunkPos pos, Operation<Boolean> original) {
		return original.call(chunkMap, pos) || FabricRiderTickets.forcesTicks(level, pos);
	}
}
