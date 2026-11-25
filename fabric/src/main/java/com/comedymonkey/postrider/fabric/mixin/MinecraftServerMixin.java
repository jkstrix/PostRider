package com.comedymonkey.postrider.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.comedymonkey.postrider.fabric.FabricRiderTickets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ServerLevel;

/**
 * As each level loads, the riders' saved chunk tickets are sorted out and held again, right after vanilla brings back
 * its own saved tickets and before the server waits for the level's first chunks to load: where NeoForge's
 * MinecraftServer.prepareLevels hands its saved tickets to the ticket controllers' validation.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
	@WrapOperation(method = "prepareLevels", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ChunkLoadCounter;track(Lnet/minecraft/server/level/ServerLevel;Ljava/lang/Runnable;)V"))
	private void postrider$activateRiderTickets(ChunkLoadCounter counter, ServerLevel level, Runnable scheduler, Operation<Void> original) {
		original.call(counter, level, (Runnable) () -> {
			scheduler.run();
			FabricRiderTickets.activate(level);
		});
	}
}
