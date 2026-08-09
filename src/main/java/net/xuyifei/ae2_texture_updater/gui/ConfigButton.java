package net.xuyifei.ae2_texture_updater.gui;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;

public class ConfigButton extends Button {
    public ConfigButton(int x, int y) {
        super(Button.builder(Component.literal("\uE000").withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(AE2TextureUpdater.MODID, "config_button_image_font")).withColor(0xFFFFFF)), ConfigButton::click).pos(x, y).size(20, 20));
    }

    public static void click(Button b) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Screen screen = AutoConfig.getConfigScreen(ModConfig.class, Minecraft.getInstance().screen).get();
            Minecraft.getInstance().setScreen(screen);
        }
    }
}
