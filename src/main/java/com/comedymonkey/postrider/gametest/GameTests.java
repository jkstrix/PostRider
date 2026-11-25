package com.comedymonkey.postrider.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;

/**
 * Registers the in-world tests: each method marked {@link GameTest} in the classes below becomes a test named after the
 * method in lower case ({@code heopensthedoorsonhiswayandleavesthemashefoundthem}), built in the mod's structure
 * {@code postrider:<template>}, in the batch it names. {@code /test run <name>} runs one, {@code /test runall} all of
 * them, and the game test server runs them all. The loader hands {@link #all} to the game's test registry (it's a
 * {@link GameTestGenerator}). Test-only.
 */
public final class GameTests {
	/** The classes holding the tests. */
	static final List<Class<?>> HOLDERS = List.of(BridgeAndMazeGameTests.class, CaveGameTests.class, ClimbingGameTests.class,
		DoorGameTests.class, HazardGameTests.class, IceHopperGameTests.class, MineGameTests.class, ParcelGameTests.class,
		RiderGameTests.class, RiverGameTests.class, WoodlandGameTests.class);

	private GameTests() {}

	/**
	 * Every test, by name, made the way NeoForge made them from a class marked as the mod's test holder with unprefixed
	 * templates: named after the method, the structure in the mod's namespace, everything else from the annotation.
	 */
	@GameTestGenerator
	public static Collection<TestFunction> all() {
		List<TestFunction> tests = new ArrayList<>();
		for (Class<?> holder : HOLDERS)
			for (Method method : holder.getDeclaredMethods()) {
				GameTest settings = method.getAnnotation(GameTest.class);
				if (settings == null)
					continue;
				if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != GameTestHelper.class)
					throw new IllegalStateException("The test " + holder.getSimpleName() + "." + method.getName() + " should be static and take only a GameTestHelper");
				String name = method.getName().toLowerCase(Locale.ROOT);
				String structure = PostRider.MOD_ID + ":" + (settings.template().isEmpty() ? name : settings.template());
				// The attempts and successes go in the order the game's own registry passes them
				tests.add(new TestFunction(settings.batch(), name, structure, StructureUtils.getRotationForRotationSteps(settings.rotationSteps()),
					settings.timeoutTicks(), settings.setupTicks(), settings.required(), settings.manualOnly(), settings.requiredSuccesses(),
					settings.attempts(), settings.skyAccess(), run(method)));
			}
		tests.sort(Comparator.comparing(TestFunction::testName));
		return tests;
	}

	/** Runs the test's method, failing the test with what it threw, as the game's own registry does. */
	private static Consumer<GameTestHelper> run(Method method) {
		return helper -> {
			try {
				method.invoke(null, helper);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof RuntimeException failure)
					throw failure;
				throw new RuntimeException(e.getCause());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		};
	}
}
