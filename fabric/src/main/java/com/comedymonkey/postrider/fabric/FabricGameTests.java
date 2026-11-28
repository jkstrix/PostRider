package com.comedymonkey.postrider.fabric;

import java.util.Collection;
import java.util.List;

import com.comedymonkey.postrider.gametest.GameTests;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

/**
 * The in-world tests on Fabric: Fabric API's game test entry point (fabric-gametest in fabric.mod.json) hands this class
 * to the game's test registry, as NeoForge's RegisterGameTestsEvent is handed GameTests, and the registry takes every
 * test from {@link GameTests#all}, so they have the same names, structures, batches and timeouts. Only in development
 * runs, as NeoForge only registers game tests when they're enabled, which they never are outside development. (A class
 * of its own, with a public constructor, because Fabric makes one of its entry point and vanilla makes one of a
 * generator's class, where NeoForge's registry doesn't for a static generator.)
 */
public final class FabricGameTests {
	@GameTestGenerator
	public static Collection<TestFunction> all() {
		return FabricLoader.getInstance().isDevelopmentEnvironment() ? GameTests.all() : List.of();
	}
}
