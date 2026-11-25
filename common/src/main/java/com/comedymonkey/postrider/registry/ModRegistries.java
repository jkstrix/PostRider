package com.comedymonkey.postrider.registry;

import java.util.List;

/** Every registry the mod adds to, in the order they're registered (a registry before those that use it). */
public final class ModRegistries {
	public static final List<Registrar<?>> ALL = List.of(
		ModDataComponents.COMPONENTS,
		ModBlocks.BLOCKS,
		ModItems.ITEMS,
		ModBlockEntities.BLOCK_ENTITIES,
		ModMenus.MENUS,
		ModCreativeTabs.TABS,
		ModEntities.ENTITIES,
		ModSounds.SOUNDS,
		ModTriggers.TRIGGERS,
		ModTriggers.STATS);

	private ModRegistries() {}
}
