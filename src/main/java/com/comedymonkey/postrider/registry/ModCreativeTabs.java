package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PostRider.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
		.title(Component.translatable("itemGroup.postrider"))
		.icon(() -> ModItems.mailbox(DyeColor.BLUE).toStack())
		.displayItems((params, output) -> {
			for (DyeColor color : DyeColor.values())
				output.accept(ModItems.mailbox(color));
			output.accept(ModItems.LETTER);
			output.accept(ModItems.ICE_HOPPER);
		})
		.build());

	private ModCreativeTabs() {}
}
