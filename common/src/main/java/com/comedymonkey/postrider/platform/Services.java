package com.comedymonkey.postrider.platform;

import java.util.ServiceLoader;

/** Finds the loader module's implementation of each platform interface (a META-INF/services file in that module). */
public final class Services {
	public static final Platform PLATFORM = load(Platform.class);

	private Services() {}

	public static <T> T load(Class<T> type) {
		return ServiceLoader.load(type, Services.class.getClassLoader())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No mod loader provides " + type.getName()));
	}
}
