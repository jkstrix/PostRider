package com.comedymonkey.postrider.forge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.comedymonkey.postrider.forge.ForgeGameTests;

import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;

/**
 * Just before a data-driven registry is frozen, every registry of the load having loaded its data: the in-world tests
 * go into the test environment and test registries noted by RegistryDataLoaderMixin, where NeoForge registers them
 * (after the load, before any registry is frozen).
 */
@Mixin(RegistryLoadTask.class)
abstract class RegistryLoadTaskMixin {
	@Inject(method = "freezeRegistry", at = @At("HEAD"))
	private void postrider$registerGameTests(Map<ResourceKey<?>, Exception> loadingErrors, CallbackInfoReturnable<Boolean> info) {
		ForgeGameTests.freezing((RegistryLoadTask<?>) (Object) this);
	}
}
