package net.xuyifei.ae2_texture_updater.config.entry;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class DeleteContentEntry {
    @ConfigEntry.Gui.Tooltip
    private String path;

    public DeleteContentEntry() {
    }

    public DeleteContentEntry(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
