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

    public void setModId(String modId) {
        this.modId = modId;
    }

    public String getCurseForgeProjectId() {
        return curseForgeProjectId;
    }

    public void setCurseForgeProjectId(String curseForgeProjectId) {
        this.curseForgeProjectId = curseForgeProjectId;
    }


    public String getCurseForgeFileId() {
        return curseForgeFileId;
    }

    public void setCurseForgeFileId(String curseForgeFileId) {
        this.curseForgeFileId = curseForgeFileId;
    }

    public String getModrinthId() {
        return modrinthId;
    }

    public void setModrinthId(String modrinthId) {
        this.modrinthId = modrinthId;
    }

    public String getCurseForgeFileName() {
        return curseForgeFileName;
    }

    public void setCurseForgeFileName(String curseForgeFileName) {
        this.curseForgeFileName = curseForgeFileName;
    }

    public boolean isValid() {
        return modId != null && !modId.isEmpty() && ((curseForgeFileId != null && !curseForgeFileId.isEmpty()) || (modrinthId != null && !modrinthId.isEmpty()));
    }
}
