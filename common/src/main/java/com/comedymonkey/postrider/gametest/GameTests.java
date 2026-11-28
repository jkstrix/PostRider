package com.comedymonkey.postrider.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * Registers the in-world tests: each method marked {@link GameTest} in the classes below becomes a test function and a
 * test, both named after the method in lower case ({@code postrider:heopensthedoorsonhiswayandleavesthemashefoundthem}),
 * in a batch of the name it gives. {@code /test run postrider:<name>} runs one, {@code /test run postrider:*} all of
 * them, and the game test server runs them all. Test-only.
 */
public final class GameTests {
	/** The classes holding the tests. */
	static final List<Class<?>> HOLDERS = List.of(BridgeAndMazeGameTests.class, CaveGameTests.class, ClimbingGameTests.class,
		DoorGameTests.class, HazardGameTests.class, IceHopperGameTests.class, MineGameTests.class, ParcelGameTests.class,
		RiderGameTests.class, RiverGameTests.class, WoodlandGameTests.class);

	private GameTests() {}

	/** One test: its name, the method that runs it, and how it's set up. */
	record Test(String name, Method method, GameTest settings) {
		Identifier id() {
			return Identifier.fromNamespaceAndPath(PostRider.MOD_ID, name);
		}

		Identifier structure() {
			return Identifier.fromNamespaceAndPath(PostRider.MOD_ID, settings.template());
		}

		void run(GameTestHelper helper) {
			try {
				method.invoke(null, helper);
			} catch (InvocationTargetException e) {
				// What the test threw, as it threw it: an assertion fails the test with its own message
				if (e.getCause() instanceof RuntimeException failure)
					throw failure;
				if (e.getCause() instanceof Error error)
					throw error;
				throw new RuntimeException(e.getCause());
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Can't run the test " + name, e);
			}
		}
	}

	/** Every test, by name. */
	static List<Test> tests() {
		List<Test> tests = new ArrayList<>();
		for (Class<?> holder : HOLDERS)
			for (Method method : holder.getDeclaredMethods()) {
				GameTest settings = method.getAnnotation(GameTest.class);
				if (settings == null)
					continue;
				if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != GameTestHelper.class)
					throw new IllegalStateException("The test " + holder.getSimpleName() + "." + method.getName() + " should be static and take only a GameTestHelper");
				tests.add(new Test(method.getName().toLowerCase(Locale.ROOT), method, settings));
			}
		tests.sort(Comparator.comparing(Test::name));
		return tests;
	}

	/** Hands each test's function to the loader's test-function registration. */
	public static void registerFunctions(BiConsumer<Identifier, Consumer<GameTestHelper>> sink) {
		for (Test test : tests())
			sink.accept(test.id(), test::run);
	}

	/**
	 * Hands each test to the loader's test registration, with its batch: the loader registers each batch's environment
	 * (given its id and definition) and hands back its holder.
	 */
	public static void registerTests(BiFunction<Identifier, TestEnvironmentDefinition<?>, Holder<TestEnvironmentDefinition<?>>> environments,
			BiConsumer<Identifier, GameTestInstance> sink) {
		Map<String, Holder<TestEnvironmentDefinition<?>>> batches = new HashMap<>();
		for (Test test : tests()) {
			GameTest settings = test.settings();
			// A batch is an environment now; ours set nothing up (each test builds its own scene), they only keep tests apart
			Holder<TestEnvironmentDefinition<?>> batch = batches.computeIfAbsent(settings.batch(),
				name -> environments.apply(Identifier.fromNamespaceAndPath(PostRider.MOD_ID, name), new TestEnvironmentDefinition.AllOf(List.of())));
			TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(batch, Level.OVERWORLD, test.structure(), settings.timeoutTicks(),
				0, true, Rotation.NONE, false, 1, 1, settings.skyAccess(), 0);
			sink.accept(test.id(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, test.id()), data));
		}
	}
}
