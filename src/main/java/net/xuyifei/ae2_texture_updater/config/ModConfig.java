package net.xuyifei.ae2_texture_updater.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.entry.*;
import net.xuyifei.ae2_texture_updater.utils.TranslatableEnum;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@Config(name = AE2TextureUpdater.MODID)
@Config.Gui.CategoryBackground(background = "minecraft:textures/block/cherry_planks.png", category = "__ae2_config_marker")
public class ModConfig implements ConfigData {
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("__ae2_config_marker")
    public General general = new General();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("__ae2_config_marker")
    public Download download = new Download();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("__ae2_config_marker")
    public Modify modify = new Modify();

    public static class General {
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        @ConfigEntry.Gui.Tooltip
        public TriggerMode triggerMode = TriggerMode.ON_MOD_CHANGE;

        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        @ConfigEntry.Gui.Tooltip(count = 2)
        public DownloadSource downloadSource = DownloadSource.CURSEFORGE;

        @ConfigEntry.Gui.Tooltip(count = 2)
        public boolean useApiKey = false;

        @ConfigEntry.Gui.Tooltip
        public String curseForgeApiKey = "";

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(count = 2)
        public ModList modsToUpdate = new ModList();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(count = 2)
        public ModIdMappingList modIdMapping = new ModIdMappingList();

        @ConfigEntry.Gui.Tooltip
        public boolean debugMode = false;
    }

    public static class Download {
        @ConfigEntry.BoundedDiscrete(min = 1, max = 32)
        @ConfigEntry.Gui.Tooltip
        public int threads = 5;

        @ConfigEntry.Gui.Tooltip
        public int connect_timeout_seconds = 20;

        @ConfigEntry.Gui.Tooltip
        public int download_timeout_seconds = 80;

        @ConfigEntry.Gui.Tooltip
        public int retry_count = 3;

        @ConfigEntry.Gui.Tooltip
        public int retry_delay_ms = 1000;

        @ConfigEntry.Gui.Tooltip
        public int speed_limit_kb = 0;

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        public Proxy proxy = new Proxy();

        @ConfigEntry.Gui.Tooltip
        public boolean check_connectivity = true;
    }

    public static class Modify {
        @ConfigEntry.Gui.PrefixText
        @ConfigEntry.Gui.Tooltip(count = 2)
        @ConfigEntry.Gui.CollapsibleObject
        public AddContentList addContentList = new AddContentList();

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.CollapsibleObject
        public ModifyContentList modifyContentList = new ModifyContentList();

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.CollapsibleObject
        public DeleteContentList deleteContentList = new DeleteContentList();
    }

    public static class ModList {
        public List<ModEntry> entries = new ArrayList<>();

        public ModList() {
            entries.add(new ModEntry("ae2",
                    "223794",
                    "7027323",
                    "appliedenergistics2-19.2.17.jar",
                    "XxWD5pD3"));

            entries.add(new ModEntry("ae2additions",
                    "493962",
                    "7661276",
                    "AEAdditions-1.21.1-6.0.2.jar",
                    "BaiPFaoT"));

            entries.add(new ModEntry("ae2wtlib",
                    "459929",
                    "7693114",
                    "ae2wtlib-19.4.1.jar",
                    "pNabrMMw"));

            entries.add(new ModEntry("merequester",
                    "688367",
                    "7844592",
                    "merequester-neoforge-1.21.1-1.4.2.jar",
                    "E6BFl96N"));

            entries.add(new ModEntry("ae2netanalyser",
                    "961856",
                    "7622554",
                    "AE2NetworkAnalyzer-1.21-2.1.5-neoforge.jar",
                    ""));

            entries.add(new ModEntry("ae2insertexportcard",
                    "982512",
                    "7579936",
                    "ae2importexportcard-1.21.1-1.4.3.jar",
                    "qelfSMnn"));

            entries.add(new ModEntry("eup2p",
                    "1065433",
                    "6907095",
                    "eup2p-1.21.1-1.0.0.jar",
                    "yoL9UPAM"));

            entries.add(new ModEntry("expatternprovider",
                    "892005",
                    "7727948",
                    "ExtendedAE-1.21-2.2.30-neoforge.jar",
                    "JiOqfoFM"));

            entries.add(new ModEntry("advanced_ae",
                    "1084104",
                    "7849217",
                    "AdvancedAE-1.6.11-1.21.1.jar",
                    "rxYaglEe"));

            entries.add(new ModEntry("extendedae_plus",
                    "1337639",
                    "7816546",
                    "extendedae_plus-1.21.1-1.5.3.jar",
                    "xr109llC"));

            entries.add(new ModEntry("megacells",
                    "622112",
                    "7489211",
                    "megacells-4.10.2.jar",
                    "jjuIRIVr"));

            entries.add(new ModEntry("appmek",
                    "574300",
                    "7096962",
                    "Applied-Mekanistics-1.6.3.jar",
                    "IiATswDj"));

            entries.add(new ModEntry("appflux",
                    "965012",
                    "7429565",
                    "AppliedFlux-1.21-2.1.4-neoforge.jar",
                    "oMgZ004U"));

            entries.add(new ModEntry("appliede",
                    "1009940",
                    "6500056",
                    "appliede-1.0.8-beta.jar",
                    "SyKS54UY"));

            entries.add(new ModEntry("ae2things",
                    "609977",
                    "5637783",
                    "AE2-Things-1.4.2-beta.jar",
                    "veunMwU3"));

            entries.add(new ModEntry("arseng",
                    "905641",
                    "6203425",
                    "arseng-2.1.1-beta.jar",
                    "IJI3QuK8"));

            entries.add(new ModEntry("appbot",
                    "610632",
                    "7234122",
                    "appbot-1.6.0-alpha.1.jar",
                    "545hUrw9"));

            entries.add(new ModEntry("ae2_mega_things",
                    "1150075",
                    "6203833",
                    "AE2MEGAThings-1.21.1-2.0.4.jar",
                    ""));

            entries.add(new ModEntry("aeinfinitybooster",
                    "559313",
                    "7402808",
                    "aeinfinitybooster-neoforge-1.21.1-1.0.0.54.jar",
                    "VQhDBNs8"));
        }
    }

    public static class ModIdMappingList {
        public List<ModIdMappingEntry> entries = new ArrayList<>();

        public ModIdMappingList() {
            entries.add(new ModIdMappingEntry("ae2insertexportcard", "ae2importexportcard"));

            entries.add(new ModIdMappingEntry("expatternprovider", "extendedae"));
        }
    }

    public static class Proxy {
        @ConfigEntry.Gui.Tooltip
        public boolean enabled = false;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public ProxyType proxyType = ProxyType.HTTP;

        @ConfigEntry.Gui.Tooltip
        public String host = "";

        @ConfigEntry.Gui.Tooltip
        public int port = 8080;

        @ConfigEntry.Gui.Tooltip
        public boolean useAuthorization = false;

        @ConfigEntry.Gui.Tooltip
        public String username = "";

        @ConfigEntry.Gui.Tooltip
        public String password = "";
    }

    public static class AddContentList {
        public List<AddContentEntry> entries = new ArrayList<>();

        public AddContentList() {

        }
    }

    public static class ModifyContentList {
        public List<ModifyContentEntry> entries = new ArrayList<>();

        public ModifyContentList() {

        }
    }

    public static class DeleteContentList {
        public List<DeleteContentEntry> entries = new ArrayList<>();

        public DeleteContentList() {
            entries.add(new DeleteContentEntry("assets/advanced_ae/models/item/quantum_infusion_bucket.json"));
        }
    }

    public enum TriggerMode implements TranslatableEnum {
        NEVER,
        ON_STARTUP,
        ON_MOD_CHANGE;

        private final String translationKey;

        TriggerMode() {
            this.translationKey = TranslatableEnum.generateKey(
                    "general.triggerMode",
                    this.name()
            );
        }

        @Override
        public String toString() {
            return translatedOrDefault();
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    public enum DownloadSource implements TranslatableEnum {
        CURSEFORGE,
        MODRINTH;

        private final String translationKey;

        DownloadSource() {
            this.translationKey = TranslatableEnum.generateKey(
                    "general.downloadSource",
                    this.name()
            );
        }

        @Override
        public String toString() {
            return translatedOrDefault();
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    public enum ProxyType implements TranslatableEnum {
        HTTP,
        SOCKS;

        private final String translationKey;

        ProxyType() {
            this.translationKey = TranslatableEnum.generateKey(
                    "download.proxyType",
                    this.name()
            );
        }

        @Override
        public String toString() {
            return translatedOrDefault();
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }
}
