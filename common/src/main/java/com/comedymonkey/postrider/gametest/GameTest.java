package com.comedymonkey.postrider.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method taking a {@link net.minecraft.gametest.framework.GameTestHelper} as an in-world test, and
 * says how it's set up; {@link GameTests} registers it, named after the method in lower case. Test-only.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface GameTest {
	/** Whether the test is open to the sky (no barrier roof over its structure). */
	boolean skyAccess() default false;

	/** The structure the test is placed in, under data/postrider/structure (e.g. gametest/arena_96x96). */
	String template();

	/** The batch the test runs in; tests in different batches never run side by side. */
	String batch();

	/** How long the test has to succeed before it fails. */
	int timeoutTicks();
}
