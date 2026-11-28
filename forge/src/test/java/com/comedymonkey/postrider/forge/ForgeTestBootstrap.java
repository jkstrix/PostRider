package com.comedymonkey.postrider.forge;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Starts the game for the unit tests before the first test class runs (ForgeTestGame), as NeoForge's unitTest does.
 * <p>
 * JUnit finds this through its extension auto-detection (META-INF/services, switched on in forge/build.gradle) and loads
 * it with its own class loader, which need not be the one the tests run in. So, as in the Fabric module, it starts the
 * game in the class loader the test class was loaded with, where the tests will see it. A start that fails is reported
 * once, then again to every later test class, rather than tried again (half started, it would register everything twice).
 */
public final class ForgeTestBootstrap implements BeforeAllCallback {
	private static final String GAME = "com.comedymonkey.postrider.forge.ForgeTestGame";
	private static boolean tried;
	private static RuntimeException failure;

	@Override
	public void beforeAll(ExtensionContext context) {
		start(context.getRequiredTestClass().getClassLoader());
	}

	private static synchronized void start(ClassLoader game) {
		if (!tried) {
			tried = true;
			try {
				Class.forName(GAME, true, game).getMethod("start").invoke(null);
			} catch (ReflectiveOperationException e) {
				Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
				failure = new IllegalStateException("The game could not be started for the unit tests", cause);
			} catch (LinkageError e) {
				failure = new IllegalStateException("The game could not be started for the unit tests", e);
			}
		}
		if (failure != null)
			throw failure;
	}
}
