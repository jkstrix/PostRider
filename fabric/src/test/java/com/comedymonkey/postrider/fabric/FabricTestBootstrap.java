package com.comedymonkey.postrider.fabric;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Starts the game for the unit tests before the first test class runs (FabricTestGame), as NeoForge's unitTest does.
 * <p>
 * JUnit finds this through its extension auto-detection (META-INF/services, switched on in fabric/build.gradle), and
 * loads it with its own class loader, not Fabric Loader's (and that's the thread's context class loader too, by now):
 * the game it would start from here is a second copy the tests never see. So it only reaches into the class loader
 * fabric-loader-junit loaded the test class with (Fabric Loader's) and starts the game there. A start that fails is reported once, then again to every later test class,
 * rather than tried again (half started, it would register everything twice).
 */
public final class FabricTestBootstrap implements BeforeAllCallback {
	private static final String GAME = "com.comedymonkey.postrider.fabric.FabricTestGame";
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
			}
		}
		if (failure != null)
			throw failure;
	}
}
