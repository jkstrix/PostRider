package com.comedymonkey.postrider.registry;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * One thing the mod registers. Its id is known from the start; its value exists once the mod loader has registered it
 * ({@link Registrar#registerAll}), and asking for it before then is a mistake.
 */
public class RegistryEntry<R, T extends R> implements Supplier<T> {
	private final ResourceKey<R> key;
	private final Identifier id;
	private final Function<Identifier, ? extends T> factory;
	private T value;

	RegistryEntry(ResourceKey<R> key, Identifier id, Function<Identifier, ? extends T> factory) {
		this.key = key;
		this.id = id;
		this.factory = factory;
	}

	public ResourceKey<R> key() {
		return key;
	}

	public Identifier getId() {
		return id;
	}

	@Override
	public T get() {
		if (value == null)
			throw new IllegalStateException(key + " has not been registered yet");
		return value;
	}

	public boolean isBound() {
		return value != null;
	}

	/** Makes the value, once, for the loader to register. */
	T create() {
		if (value != null)
			throw new IllegalStateException(key + " is registered twice");
		value = factory.apply(id);
		return value;
	}
}
