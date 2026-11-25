package com.comedymonkey.postrider.forge;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.forge.mixin.RegistryLoadTaskAccessor;
import com.comedymonkey.postrider.gametest.GameTests;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraftforge.gametest.ForgeGameTestHooks;

/**
 * Puts the in-world tests and their batches' environments into the world's data-driven registries, as NeoForge's
 * RegisterGameTestsEvent does: when game tests are enabled (every development run, and the game test server), when the
 * registries are read from data packs (not when a client receives them from its server, which sends the tests it has),
 * once their data has loaded and before they're frozen. Forge 66 only registers test instances from data packs, so the
 * mixins RegistryDataLoaderMixin and RegistryLoadTaskMixin call this at those points of RegistryDataLoader.load.
 */
public final class ForgeGameTests {
	/** The test environment and test registries of a load in progress, and whether the tests went in. */
	private record Pending(RegistryLoadTask<?> environments, RegistryLoadTask<?> tests, AtomicBoolean registered) {}

	private static final Map<RegistryLoadTask<?>, Pending> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

	private ForgeGameTests() {}

	/** A load of the data-driven registries is starting with these tasks. */
	public static void loading(List<RegistryLoadTask<?>> tasks) {
		if (!ForgeGameTestHooks.isGametestEnabled())
			return;
		RegistryLoadTask<?> environments = find(tasks, Registries.TEST_ENVIRONMENT);
		RegistryLoadTask<?> tests = find(tasks, Registries.TEST_INSTANCE);
		if (environments == null || tests == null)
			return;
		Pending pending = new Pending(environments, tests, new AtomicBoolean());
		PENDING.put(environments, pending);
		PENDING.put(tests, pending);
	}

	/**
	 * A registry of a load is about to be frozen; all of the load's data has been read. The first of the two test
	 * registries to get here has the tests put in both.
	 */
	public static void freezing(RegistryLoadTask<?> task) {
		Pending pending = PENDING.remove(task);
		if (pending == null)
			return;
		PENDING.remove(pending.environments());
		PENDING.remove(pending.tests());
		if (!pending.registered().compareAndSet(false, true))
			return;
		WritableRegistry<TestEnvironmentDefinition<?>> environments = registry(pending.environments());
		WritableRegistry<GameTestInstance> tests = registry(pending.tests());
		GameTests.registerTests(
			(id, definition) -> environments.register(ResourceKey.create(Registries.TEST_ENVIRONMENT, id), definition, RegistrationInfo.BUILT_IN),
			(id, test) -> tests.register(ResourceKey.create(Registries.TEST_INSTANCE, id), test, RegistrationInfo.BUILT_IN));
	}

	/** The task loading this registry from the data packs, if the load has one. */
	@Nullable
	private static RegistryLoadTask<?> find(List<RegistryLoadTask<?>> tasks, ResourceKey<? extends Registry<?>> key) {
		for (RegistryLoadTask<?> task : tasks)
			if (task instanceof ResourceManagerRegistryLoadTask<?> && ((RegistryLoadTaskAccessor) task).postrider$getRegistry().key().equals(key))
				return task;
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> WritableRegistry<T> registry(RegistryLoadTask<?> task) {
		return (WritableRegistry<T>) ((RegistryLoadTaskAccessor) task).postrider$getRegistry();
	}
}
