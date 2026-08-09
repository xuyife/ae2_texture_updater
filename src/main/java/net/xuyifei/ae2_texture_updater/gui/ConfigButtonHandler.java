package net.xuyifei.ae2_texture_updater.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import org.apache.commons.lang3.mutable.MutableObject;

@Mod.EventBusSubscriber(modid = AE2TextureUpdater.MODID, value = Dist.CLIENT)
public class ConfigButtonHandler {
    @SubscribeEvent
    public static void onGuiInit(ScreenEvent.Init event) {
        MutableObject<GuiEventListener> toAdd = new MutableObject<>(null);
        if (event.getScreen() instanceof TitleScreen) {
            event.getListenersList()
                    .stream()
                    .filter(w -> w instanceof AbstractWidget)
                    .map(w -> (AbstractWidget) w)
                    .findFirst()
                    .ifPresent(w -> toAdd
                            .setValue(new ConfigButton(w.getX() - 48, w.getY() + 84)));
        }
        if (toAdd.getValue() != null)
            event.addListener(toAdd.getValue());
    }
}
