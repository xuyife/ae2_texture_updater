package net.xuyifei.ae2_texture_updater.utils;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;

public interface TranslatableEnum {
    String getTranslationKey();

    default String getDisplayName() {
        return I18n.get(getTranslationKey());
    }

    default MutableComponent getDisplayComponent() {
        return Component.translatable(getTranslationKey());
    }

    static String generateKey(String categoryPath, String enumName) {
        return String.format("text.autoconfig.%s.option.%s.enum.%s",
                AE2TextureUpdater.MODID, categoryPath, enumName);
    }
}
