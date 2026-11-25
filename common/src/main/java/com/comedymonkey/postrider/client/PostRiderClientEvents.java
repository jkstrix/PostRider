package com.comedymonkey.postrider.client;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.comedymonkey.postrider.client.gui.LetterEditScreen;
import com.comedymonkey.postrider.client.gui.LetterReadScreen;
import com.comedymonkey.postrider.client.gui.MailboxScreen;
import com.comedymonkey.postrider.client.model.BargeModel;
import com.comedymonkey.postrider.client.model.IceHopperModel;
import com.comedymonkey.postrider.client.model.MochilaModel;
import com.comedymonkey.postrider.client.model.PostmanModel;
import com.comedymonkey.postrider.client.model.TentModel;
import com.comedymonkey.postrider.client.renderer.BargeRenderer;
import com.comedymonkey.postrider.client.renderer.IceHopperRenderer;
import com.comedymonkey.postrider.client.renderer.PostmanHorseRenderer;
import com.comedymonkey.postrider.client.renderer.PostmanRenderer;
import com.comedymonkey.postrider.client.renderer.TentRenderer;
import com.comedymonkey.postrider.client.sound.HarmonicaSound;
import com.comedymonkey.postrider.content.letter.LetterScreens;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.registry.ModBlocks;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.registry.ModItems;
import com.comedymonkey.postrider.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Everything the mod does on the client when the game tells it something happened. Each mod loader calls these from
 * its own client events; the order inside each is the order the mod has always run them in.
 */
public final class PostRiderClientEvents {
	private static boolean screenshots;
	private static boolean watch;

	private PostRiderClientEvents() {}

	/** When the client side of the mod is constructed. */
	public static void init() {
		LetterScreens.setOpener(new LetterScreens.Opener() {
			@Override
			public void openEditor(Player player, InteractionHand hand, ItemStack letter) {
				Minecraft.getInstance().setScreen(new LetterEditScreen(player, hand, letter));
			}

			@Override
			public void openReader(ItemStack letter) {
				Minecraft.getInstance().setScreen(new LetterReadScreen(letter));
			}
		});
		screenshots = DevScreenshots.wanted();
		watch = DevWatch.wanted();
	}

	/** After every entity's tick on the client (a passenger's too). */
	public static void entityTickEnd(Entity entity) {
		HarmonicaSound.onEntityTick(entity);
	}

	/** At the end of every client tick. */
	public static void clientTickEnd(Minecraft minecraft) {
		if (screenshots)
			DevScreenshots.tick(minecraft);
		if (watch)
			DevWatch.tick(minecraft);
	}

	/** The model layers the mod's entities are drawn with. */
	public static void registerLayers(BiConsumer<ModelLayerLocation, Supplier<LayerDefinition>> sink) {
		sink.accept(PostmanModel.LAYER, PostmanModel::createBodyLayer);
		sink.accept(PostmanModel.SLIM_LAYER, PostmanModel::createSlimBodyLayer);
		sink.accept(MochilaModel.LAYER, MochilaModel::createBodyLayer);
		sink.accept(TentModel.LAYER, TentModel::createBodyLayer);
		sink.accept(BargeModel.LAYER, BargeModel::createBodyLayer);
		sink.accept(IceHopperModel.LAYER, IceHopperModel::createBodyLayer);
	}

	/** A loader's entity renderer registration. */
	public interface RendererSink {
		<T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> renderer);
	}

	public static void registerRenderers(RendererSink sink) {
		sink.register(ModEntities.POSTMAN.get(), PostmanRenderer::new);
		sink.register(ModEntities.POSTMAN_HORSE.get(), PostmanHorseRenderer::new);
		sink.register(ModEntities.TENT.get(), TentRenderer::new);
		sink.register(ModEntities.BARGE.get(), BargeRenderer::new);
		sink.register(ModEntities.ICE_HOPPER.get(), IceHopperRenderer::new);
	}

	/** Makes the mailbox screen for a menu (vanilla's own screen constructor type is private). */
	@FunctionalInterface
	public interface MailboxScreenFactory {
		MailboxScreen create(MailboxMenu menu, Inventory inventory, Component title);
	}

	/** A loader's menu screen registration. */
	public interface ScreenSink {
		void register(MenuType<MailboxMenu> menu, MailboxScreenFactory screen);
	}

	public static void registerScreens(ScreenSink sink) {
		sink.register(ModMenus.MAILBOX.get(), MailboxScreen::new);
	}

	/** The mailbox box faces are tint index 0; one white texture covers every dye colour. Hands each block its colours. */
	public static void registerBlockColors(BiConsumer<MailboxBlock, BlockColor> sink) {
		for (DyeColor color : DyeColor.values()) {
			MailboxBlock block = ModBlocks.mailbox(color).get();
			sink.accept(block, (state, level, pos, tint) -> tint == 0 ? color.getTextureDiffuseColor() : -1);
		}
	}

	/** The mailbox items are tinted the same way. */
	public static void registerItemColors(BiConsumer<Item, ItemColor> sink) {
		for (DyeColor color : DyeColor.values())
			sink.accept(ModItems.mailbox(color).get(), (stack, tint) -> tint == 0 ? color.getTextureDiffuseColor() : -1);
	}
}
