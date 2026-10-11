package net.xuyifei.ae2_texture_updater.config.entry;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class ModifyContentEntry {
    @ConfigEntry.Gui.Tooltip
    private String path;

    @ConfigEntry.Gui.Tooltip
    private String modifyContent;

    public ModifyContentEntry() {
    }

    public ModifyContentEntry(String path, String modifyContent) {
        this.path = path;
        this.modifyContent = modifyContent;
    }

    public String getModifyContent() {
        return modifyContent;
    }

    public String getPath() {
        return path;
    }
}
