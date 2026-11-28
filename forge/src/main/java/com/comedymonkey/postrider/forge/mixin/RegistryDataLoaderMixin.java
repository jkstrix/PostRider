package com.comedymonkey.postrider.forge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.comedymonkey.postrider.forge.ForgeGameTests;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.RegistryOps;

/**
 * As a load of the data-driven registries starts, the tasks loading the test environments and tests are noted, so the
 * in-world tests can be put in them once their data has loaded (RegistryLoadTaskMixin): where NeoForge fires
 * RegisterGameTestsEvent in RegistryDataLoader.load. Forge 66 registers test instances from data packs only.
 */
@Mixin(RegistryDataLoader.class)
abstract class RegistryDataLoaderMixin {
	@Inject(method = "createContext", at = @At("HEAD"))
	private static void postrider$noteTestRegistries(List<HolderLookup.RegistryLookup<?>> contextRegistries, List<RegistryLoadTask<?>> tasks,
			CallbackInfoReturnable<RegistryOps.RegistryInfoLookup> info) {
		ForgeGameTests.loading(tasks);
	}
}
