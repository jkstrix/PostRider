package com.comedymonkey.postrider.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

/** How the in-world tests are found and named for registering. */
class GameTestsTest {
	@Test
	void everyTestIsNamedAfterItsMethodInLowerCaseAndNoTwoShareAName() {
		List<GameTests.Test> tests = GameTests.tests();
		assertFalse(tests.isEmpty());
		Set<String> names = new HashSet<>();
		for (GameTests.Test test : tests) {
			assertEquals(test.method().getName().toLowerCase(Locale.ROOT), test.name());
			assertTrue(Identifier.isValidPath(test.name()), test.name() + " should do as a test's id");
			assertTrue(names.add(test.name()), "two tests are called " + test.name());
		}
	}

	@Test
	void everyTestHasABatchAndATimeAndAStructureThatsThere() {
		for (GameTests.Test test : GameTests.tests()) {
			GameTest settings = test.settings();
			assertTrue(Identifier.isValidPath(settings.batch()), test.name() + "'s batch should do as an id");
			assertTrue(settings.timeoutTicks() > 0, test.name() + " should have some time");
			assertNotNull(GameTests.class.getResource("/data/postrider/structure/" + settings.template() + ".nbt"),
				test.name() + "'s structure " + settings.template() + " should be among the resources");
		}
	}

	@Test
	void everyClassWithTestsInItIsListed() throws ClassNotFoundException, URISyntaxException {
		URL folder = GameTests.class.getResource("");
		Assumptions.assumeTrue(folder != null && folder.getProtocol().equals("file"), "the classes aren't in a folder to look through");
		File[] files = new File(folder.toURI()).listFiles((dir, name) -> name.endsWith(".class"));
		assertNotNull(files);
		for (File file : files) {
			String name = GameTests.class.getPackageName() + "." + file.getName().substring(0, file.getName().length() - ".class".length());
			Class<?> type = Class.forName(name, false, GameTests.class.getClassLoader());
			for (Method method : type.getDeclaredMethods())
				if (method.isAnnotationPresent(GameTest.class))
					assertTrue(GameTests.HOLDERS.contains(type), type.getSimpleName() + " has tests in it but isn't in GameTests.HOLDERS");
		}
	}
}
