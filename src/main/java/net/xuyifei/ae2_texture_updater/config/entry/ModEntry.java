package net.xuyifei.ae2_texture_updater.config.entry;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class ModEntry {
    @ConfigEntry.Gui.Tooltip
    private String modId = "";

    @ConfigEntry.Gui.Tooltip
    private String curseForgeProjectId = "";

    @ConfigEntry.Gui.Tooltip
    private String curseForgeFileId = "";

    @ConfigEntry.Gui.Tooltip
    private String curseForgeFileName = "";

    @ConfigEntry.Gui.Tooltip
    private String modrinthId = "";

    public ModEntry() {
    }

    public ModEntry(String modId, String curseForgeProjectId,  String curseForgeFileId, String curseForgeFileName, String modrinthId) {
        this.modId = modId;
        this.curseForgeProjectId = curseForgeProjectId;
        this.curseForgeFileId = curseForgeFileId;
        this.curseForgeFileName = curseForgeFileName;
        this.modrinthId = modrinthId;
    }

    public String getModId() {
        return modId;
    }

    public String getCurseForgeProjectId() {
        return curseForgeProjectId;
    }

    public String getCurseForgeFileId() {
        return curseForgeFileId;
    }

    public String getModrinthId() {
        return modrinthId;
    }

    /** Real file name on CurseForge CDN; needed to build the no-API-key fallback URL. */
    public String getCurseForgeFileName() {
        return curseForgeFileName;
    }
}
