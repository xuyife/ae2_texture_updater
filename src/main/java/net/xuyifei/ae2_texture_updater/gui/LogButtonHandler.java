package net.xuyifei.ae2_texture_updater.gui;

import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.message.LogScreen;
import org.apache.commons.compress.utils.Lists;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = AE2TextureUpdater.MODID, value = Dist.CLIENT)
public class LogButtonHandler {
    @SubscribeEvent
    public static void onGuiInit(ScreenEvent.Init event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractConfigScreen configScreen) {
            if (isAE2ConfigScreen(configScreen)) {
                Button button = Button.builder(
                                Component.translatable("screen.ae2_texture_updater.log_screen.button.open"),
                                btn -> Minecraft.getInstance().setScreen(
                                        new LogScreen(screen)
                                )
                        )
                        .bounds(screen.width - 70, 5, 60, 20)
                        .build();

                event.addListener(button);
            }
        }
    }

    private static boolean isAE2ConfigScreen(AbstractConfigScreen configScreen) {
        try {
            Method method = AbstractConfigScreen.class.getDeclaredMethod("getCategorizedEntries");
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<Component, List<AbstractConfigEntry<?>>> categorizedEntries =
                    (Map<Component, List<AbstractConfigEntry<?>>>) method.invoke(configScreen);

            List<Component> components = Lists.newArrayList(categorizedEntries.keySet().iterator());

            return components.contains(Component.translatable("text.autoconfig.ae2_texture_updater.category.__ae2_config_marker"));
        } catch (Exception ignored) {
        }
        return false;
    }
}
