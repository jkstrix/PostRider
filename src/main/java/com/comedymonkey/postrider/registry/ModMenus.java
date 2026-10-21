package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PostRider.MOD_ID);

	public static final DeferredHolder<MenuType<?>, MenuType<MailboxMenu>> MAILBOX =
		MENUS.register("mailbox", () -> IMenuTypeExtension.create(MailboxMenu::new));

	private ModMenus() {}
}
