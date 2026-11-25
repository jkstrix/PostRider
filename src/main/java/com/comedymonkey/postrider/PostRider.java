package com.comedymonkey.postrider;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * Post Rider. The mod itself is loader-free; each mod loader's module starts it and connects that loader's events
 * to {@link PostRiderEvents} (on NeoForge: neoforge/PostRiderNeoForge).
 */
public final class PostRider {
	public static final String MOD_ID = "postrider";
	public static final Logger LOGGER = LogUtils.getLogger();

	private PostRider() {}

	public static Identifier asResource(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
