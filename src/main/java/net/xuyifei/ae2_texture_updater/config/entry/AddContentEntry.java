package net.xuyifei.ae2_texture_updater.config.entry;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class AddContentEntry {
    @ConfigEntry.Gui.Tooltip
    private String path;

    @ConfigEntry.Gui.Tooltip
    private String addContent;

    public AddContentEntry() {
    }

    public AddContentEntry(String path, String addContent) {
        this.path = path;
        this.addContent = addContent;
    }

    public String getAddContent() {
        return addContent;
    }

    public String getPath() {
        return path;
    }
}
