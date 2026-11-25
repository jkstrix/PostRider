package com.comedymonkey.postrider.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryLoadTask;

/** The registry a data-driven registry load is filling (ForgeGameTests registers the in-world tests into it). */
@Mixin(RegistryLoadTask.class)
public interface RegistryLoadTaskAccessor {
	@Accessor("registry")
	WritableRegistry<?> postrider$getRegistry();
}
