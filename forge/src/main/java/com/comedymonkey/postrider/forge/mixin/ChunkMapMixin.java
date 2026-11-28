package com.comedymonkey.postrider.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.comedymonkey.postrider.forge.ForgeRiderTickets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * A chunk a rider holds with natural spawning (his ticking square) spawns mobs even with no player close enough, as
 * NeoForge's natural-spawning tickets make it (its patch to the same check in ChunkMap.collectSpawningChunks).
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@WrapOperation(method = "collectSpawningChunks", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawningInternal(Lnet/minecraft/world/level/ChunkPos;)Z"))
	private boolean postrider$riderForcesNaturalSpawning(ChunkMap chunkMap, ChunkPos pos, Operation<Boolean> original) {
		return original.call(chunkMap, pos) || ForgeRiderTickets.forcesNaturalSpawning(level, pos);
	}
}
