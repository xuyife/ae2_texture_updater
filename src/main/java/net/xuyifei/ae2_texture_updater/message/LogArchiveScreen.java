package net.xuyifei.ae2_texture_updater.message;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Picker for archived logs. Selecting an entry decompresses it into a temporary
 * file and hands the parsed messages to {@link LogScreen}, which then displays
 * them with its normal logic. The temporary file is deleted when the log screen
 * is closed.
 */
public class LogArchiveScreen extends Screen {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Screen lastScreen;
    private List<UserMessageManager.ArchivedLog> archives = List.of();
    private ArchiveList list;

    public LogArchiveScreen(Screen lastScreen) {
        super(Component.translatable("screen.ae2_texture_updater.log_archive.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.archives = UserMessageManager.listArchivedLogs();
        this.list = new ArchiveList(this, this.minecraft, this.width, this.height, 32, this.height - 32, 20);
        this.addWidget(this.list);
        this.list.refreshList(this.archives);

        this.addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> this.minecraft.setScreen(this.lastScreen)
        ).bounds(5, 5, 25, 20).build());

        if (this.archives.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("screen.ae2_texture_updater.log_archive.button.back"),
                    button -> this.minecraft.setScreen(this.lastScreen)
            ).bounds((this.width - 100) / 2, this.height - 25, 100, 20).build());
        }
    }

    /** Opens a chosen archive: extract, parse, then show it in the normal log screen. */
    void openArchive(UserMessageManager.ArchivedLog entry) {
        Path temp = UserMessageManager.extractArchiveToTemp(entry.file());
        if (temp == null) {
            // Return to the picker so the player can try another entry.
            this.minecraft.setScreen(new LogScreen(this, null,
                    Component.translatable("screen.ae2_texture_updater.log_archive.message.extract_failed")));
            return;
        }
        this.minecraft.setScreen(new LogScreen(this, temp, Component.literal(entry.displayName())));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        if (this.list == null) {
            this.init();
        }
        if (this.list != null) {
            this.list.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.drawString(this.font,
                Component.translatable("screen.ae2_texture_updater.log_archive.title"), 35, 12, 0xFFFFFF);
        if (this.archives.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.ae2_texture_updater.log_archive.message.empty")
                            .withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2 - 10, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        private final LogArchiveScreen screen;
        private final Minecraft minecraft;
        private final UserMessageManager.ArchivedLog archive;
        private final Component line;

        public Entry(Minecraft minecraft, UserMessageManager.ArchivedLog archive, LogArchiveScreen screen) {
            this.minecraft = minecraft;
            this.archive = archive;
            this.screen = screen;
            String time = archive.lastModified() > 0
                    ? TIME_FORMAT.format(Instant.ofEpochMilli(archive.lastModified()))
                    : "?";
            this.line = Component.literal("[" + time + "] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(archive.displayName()).withStyle(ChatFormatting.WHITE));
        }

        @Override
        public @NotNull Component getNarration() {
            return this.line;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(minecraft.font, this.line, left, top + 5, 0xFFFFFF);
            if (hovering) {
                screen.setTooltipForNextRenderPass(
                        Component.translatable("screen.ae2_texture_updater.log_archive.message.click_to_view")
                                .withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            screen.openArchive(this.archive);
            return true;
        }
    }

    public static class ArchiveList extends ObjectSelectionList<Entry> {
        private final LogArchiveScreen thisScreen;

        public ArchiveList(LogArchiveScreen screen, Minecraft minecraft, int width, int height,
                           int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
            this.thisScreen = screen;
            setRenderBackground(false);
        }

        @Override
        public int getRowLeft() {
            return 10;
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 6;
        }

        // Fully qualified: inside an ObjectSelectionList subclass the simple name
        // "Entry" resolves to the inherited AbstractSelectionList.Entry, which is abstract.
        public void refreshList(List<UserMessageManager.ArchivedLog> archives) {
            this.clearEntries();
            for (UserMessageManager.ArchivedLog archive : archives) {
                this.addEntry(new LogArchiveScreen.Entry(minecraft, archive, this.thisScreen));
            }
        }
    }
}
