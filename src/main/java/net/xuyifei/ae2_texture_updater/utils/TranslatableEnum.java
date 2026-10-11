package net.xuyifei.ae2_texture_updater.utils;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;

public interface TranslatableEnum {
    String getTranslationKey();

    /**
     * Client-safe display name: translated on the client, plain enum name on
     * any other dist or when translation fails (e.g. missing lang entries).
     */
    default String translatedOrDefault() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                return Component.translatable(getTranslationKey()).getString();
            } catch (Exception | NoSuchMethodError e) {
                return enumName();
            }
        }
        return enumName();
    }

    private String enumName() {
        return ((Enum<?>) this).name();
    }

    static String generateKey(String categoryPath, String enumName) {
        return String.format("text.autoconfig.%s.option.%s.enum.%s",
                AE2TextureUpdater.MODID, categoryPath, enumName);
    }
}
