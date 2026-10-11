package net.xuyifei.ae2_texture_updater.config.entry;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class ModIdMappingEntry {
    @ConfigEntry.Gui.Tooltip
    private String oldModId = "";

    @ConfigEntry.Gui.Tooltip
    private String newModId = "";

    public ModIdMappingEntry() {
    }

    public ModIdMappingEntry(String oldModId, String newModId) {
        this.oldModId = oldModId;
        this.newModId = newModId;
    }

    public String getOldModId() {
        return oldModId;
    }

    public String getNewModId() {
        return newModId;
    }
}
