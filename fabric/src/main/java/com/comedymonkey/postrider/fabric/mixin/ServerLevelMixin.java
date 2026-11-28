package com.comedymonkey.postrider.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.comedymonkey.postrider.fabric.FabricRiderTickets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;

/**
 * A level where riders hold chunks keeps ticking its entities and block entities with no player in it, as NeoForge's
 * patch to ServerLevel.tick makes it: vanilla stops them 300 ticks after the last player leaves unless a chunk is
 * force-loaded ({@code !getForcedChunks().isEmpty()}), and NeoForge counts its ticket controllers' chunks there too
 * (ForcedChunkManager.hasForcedChunks). Without this a rider froze 15 s after the last player left his dimension.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;isEmpty()Z"))
	private boolean postrider$noForcedChunksUnlessRidersHoldSome(LongSet forcedChunks, Operation<Boolean> original) {
		return original.call(forcedChunks) && !FabricRiderTickets.holdsAny((ServerLevel) (Object) this);
	}
}
