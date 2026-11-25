package com.comedymonkey.postrider.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * What the mod puts into one of the game's registries, in order. The entries are declared here, loader-free; each mod
 * loader registers them its own way and at its own time ({@link #registerAll}), which is when they get their values.
 */
public final class Registrar<R> {
	private final ResourceKey<? extends Registry<R>> registry;
	private final List<RegistryEntry<R, ?>> entries = new ArrayList<>();

	private Registrar(ResourceKey<? extends Registry<R>> registry) {
		this.registry = registry;
	}

	public static <R> Registrar<R> create(ResourceKey<? extends Registry<R>> registry) {
		return new Registrar<>(registry);
	}

	public ResourceKey<? extends Registry<R>> registry() {
		return registry;
	}

	public List<RegistryEntry<R, ?>> entries() {
		return Collections.unmodifiableList(entries);
	}

	public <T extends R> RegistryEntry<R, T> register(String name, Supplier<? extends T> factory) {
		return add(new RegistryEntry<>(key(name), PostRider.asResource(name), id -> factory.get()));
	}

	/** An item, made from properties with its id already set, as NeoForge's item register makes them. */
	@SuppressWarnings("unchecked")
	public <I extends Item> ItemEntry<I> registerItem(String name, Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
		ResourceKey<Item> key = (ResourceKey<Item>) (ResourceKey<?>) key(name);
		return (ItemEntry<I>) add((RegistryEntry<R, ?>) (RegistryEntry<?, ?>) new ItemEntry<I>(key, PostRider.asResource(name),
			id -> factory.apply(properties.apply(new Item.Properties()).setId(key))));
	}

	/** A block, made from properties with its id already set, as NeoForge's block register makes them. */
	@SuppressWarnings("unchecked")
	public <B extends Block> RegistryEntry<Block, B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
			UnaryOperator<BlockBehaviour.Properties> properties) {
		ResourceKey<Block> key = (ResourceKey<Block>) (ResourceKey<?>) key(name);
		return (RegistryEntry<Block, B>) add((RegistryEntry<R, ?>) (RegistryEntry<?, ?>) new RegistryEntry<Block, B>(key, PostRider.asResource(name),
			id -> factory.apply(properties.apply(BlockBehaviour.Properties.of()).setId(key))));
	}

	/** A data component type, from a builder. */
	@SuppressWarnings("unchecked")
	public <D> RegistryEntry<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name, UnaryOperator<DataComponentType.Builder<D>> builder) {
		return (RegistryEntry<DataComponentType<?>, DataComponentType<D>>) (RegistryEntry<?, ?>)
			register(name, () -> (R) builder.apply(DataComponentType.builder()).build());
	}

	/**
	 * Hands every entry, in the order declared, to the loader's registration: the loader puts the value it's given
	 * under that id.
	 */
	public void registerAll(Sink<R> sink) {
		for (RegistryEntry<R, ?> entry : entries)
			sink.register(entry.key(), entry.create());
	}

	@FunctionalInterface
	public interface Sink<R> {
		void register(ResourceKey<R> key, R value);
	}

	private ResourceKey<R> key(String name) {
		return ResourceKey.create(registry, PostRider.asResource(name));
	}

	private <E extends RegistryEntry<R, ?>> E add(E entry) {
		entries.add(entry);
		return entry;
	}
}
