package net.xuyifei.ae2_texture_updater.message;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LogScreen extends Screen {
    private static final String[] FILTER_LEVELS = {"all", "error", "warning", "info", "success"};

    public final Screen lastScreen;
    private LogList list;
    private JsonArray allMessages;
    private Set<String> hiddenLevels = Sets.newHashSet();
    private List<Button> filterButtons = new ArrayList<>();

    public LogScreen(Screen lastScreen) {
        super(Component.translatable("screen.ae2_texture_updater.log_screen.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.allMessages = UserMessageManager.loadMessages();
        this.list = new LogList(this, this.minecraft, this.width, this.height, 32, this.height - 32, 16);
        this.addWidget(this.list);
        buildFilterButtons();
        this.list.refreshList(allMessages, hiddenLevels);
        this.addRenderableWidget(Button.builder(
                Component.literal("<"),
                pButton -> onClose()
        ).bounds(5, 5, 25, 20).build());
        buildBottomButtons();
    }

    private void buildBottomButtons() {
        int btnWidth = 100;
        int btnHeight = 20;
        int spacing = 5;
        int totalWidth = 4 * btnWidth + 3 * spacing;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - 25;

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.ae2_texture_updater.log_screen.button.copy_all"),
                button -> copyAllLogs()
        ).bounds(startX, y, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.ae2_texture_updater.log_screen.button.export"),
                button -> exportLogs()
        ).bounds(startX + btnWidth + spacing, y, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.ae2_texture_updater.log_screen.button.clear_archive"),
                button -> clearArchive()
        ).bounds(startX + 2 * (btnWidth + spacing), y, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.ae2_texture_updater.log_screen.button.send_feedback"),
                button -> sendFeedback()
        ).bounds(startX + 3 * (btnWidth + spacing), y, btnWidth, btnHeight).build());
    }

    private void copyAllLogs() {
        minecraft.keyboardHandler.setClipboard(buildLogsString().toString());
        showToast("screen.ae2_texture_updater.log_screen.message.copy_success");
    }

    private void exportLogs() {
        StringBuilder header = new StringBuilder();
        header.append("=== AE2 Texture Updater Logs ===\n");
        header.append("Generated (UTC): ").append(Instant.now()).append("\n");
        header.append("Generated (Local): ").append(ZonedDateTime.now()).append("\n");
        header.append("================================\n\n");

        StringBuilder logContent = buildLogsString();

        StringBuilder fullLog = header.append(logContent);

        Path exportPath = FMLPaths.GAMEDIR.get().resolve("export_logs");
        try {
            if (!Files.exists(exportPath)) {
                Files.createDirectories(exportPath);
            }
            if (!Files.isDirectory(exportPath)) {
                Files.delete(exportPath);
            }
            String fileName = "ae2_texture_updater_logs_" + Instant.now().toString().replace(":", "-") + ".log";
            Files.writeString(exportPath.resolve(fileName), fullLog.toString());
            showToast("screen.ae2_texture_updater.log_screen.message.export_success");
            Util.getPlatform().openUri(exportPath.resolve(fileName).toUri());
        } catch (IOException ignored) {
        }
    }

    private void clearArchive() {
        minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        UserMessageManager.cleanAllOldLogs();
                        showToast("screen.ae2_texture_updater.confirm_clear_archive.message.success");
                    }
                    minecraft.setScreen(this);
                },
                Component.translatable("screen.ae2_texture_updater.confirm_clear_archive.title"),
                Component.translatable("screen.ae2_texture_updater.confirm_clear_archive.message")
        ));
    }

    private void sendFeedback() {
        try {
            Util.getPlatform().openUri(new URI("https://github.com/xuyife/ae2_texture_updater/issues"));
        } catch (URISyntaxException ignored) {
        }
    }

    private @NotNull StringBuilder buildLogsString() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < allMessages.size(); i++) {
            JsonObject jo = allMessages.get(i).getAsJsonObject();
            String timestamp = jo.get("timestamp").getAsString();
            String type = jo.get("type").getAsString();
            String key = jo.get("key").getAsString();

            Object[] args = parseArgs(jo);
            sb.append("[").append(timestamp).append("] [").append(type.toUpperCase()).append("] ")
                    .append(Component.translatable(key, args).getString()).append("\n");
        }
        return sb;
    }

    /** Shared with {@link Entry}: converts the stored JSON args back into format arguments. */
    static Object @NotNull [] parseArgs(JsonObject message) {
        JsonArray argsArray = message.getAsJsonArray("args");
        if (argsArray == null || argsArray.isEmpty()) {
            return new Object[0];
        }
        Object[] args = new Object[argsArray.size()];
        for (int i = 0; i < argsArray.size(); i++) {
            JsonElement element = argsArray.get(i);
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    args[i] = primitive.getAsNumber();
                } else if (primitive.isBoolean()) {
                    args[i] = primitive.getAsBoolean();
                } else {
                    args[i] = primitive.getAsString();
                }
            } else {
                args[i] = element.getAsString();
            }
        }
        return args;
    }

    private void buildFilterButtons() {
        filterButtons.forEach(this::removeWidget);
        filterButtons.clear();

        int[] counts = countMessages();

        int btnWidth = 50;
        int btnHeight = 20;
        int totalWidth = FILTER_LEVELS.length * (btnWidth + 5) - 5;
        int startX = this.width - totalWidth - 10;
        int y = 10;

        for (int i = 0; i < FILTER_LEVELS.length; i++) {
            String level = FILTER_LEVELS[i];
            String displayName = Component.translatable(
                    "screen.ae2_texture_updater.log_screen.button." + level).getString();
            int count = counts[i];
            boolean isHidden = hiddenLevels.contains(level);

            String btnText = displayName + "(" + count + ")";
            Button button = this.addRenderableWidget(Button.builder(
                    Component.literal(btnText),
                    btn -> toggleLevel(level)
            ).bounds(startX + i * (btnWidth + 5), y, btnWidth, btnHeight).build());

            button.active = !isHidden;

            filterButtons.add(button);
        }
    }

    private void toggleLevel(@NotNull String level) {
        if (level.equals("all")) {
            hiddenLevels.clear();
            buildFilterButtons();
            this.list.refreshList(allMessages, hiddenLevels);
            return;
        }

        if (hiddenLevels.contains(level)) {
            hiddenLevels.remove(level);
        } else {
            hiddenLevels.add(level);
        }
        buildFilterButtons();
        this.list.refreshList(allMessages, hiddenLevels);
    }

    private void showToast(String key) {
        minecraft.execute(() -> {
            ToastComponent toastComponent = minecraft.getToasts();
            SystemToast.add(toastComponent, SystemToast.SystemToastIds.TUTORIAL_HINT, Component.translatable(key), Component.empty());
        });
    }

    private int @NotNull [] countMessages() {
        int[] counts = new int[FILTER_LEVELS.length];

        for (int i = 0; i < allMessages.size(); i++) {
            JsonObject obj = allMessages.get(i).getAsJsonObject();
            String type = obj.get("type").getAsString();
            counts[0]++;
            for (int j = 1; j < FILTER_LEVELS.length; j++) {
                if (FILTER_LEVELS[j].equals(type)) {
                    counts[j]++;
                    break;
                }
            }
        }
        return counts;
    }

    @Override
    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        if (this.list == null) {
            this.init();
        }
        if (this.list != null) {
            this.list.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }
        pGuiGraphics.drawString(this.font, Component.translatable("screen.ae2_texture_updater.log_screen.title"), 35, 12, 0xFFFFFF);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(this.lastScreen);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        private final LogList logList;
        private final Minecraft minecraft;
        private final Component logLine;

        public Entry(Minecraft minecraft, JsonObject line, LogList logList) {
            this.minecraft = minecraft;
            this.logList = logList;

            Object[] args = parseArgs(line);
            String type = line.get("type").getAsString();

            this.logLine = Component.literal("[" + line.get("timestamp").getAsString() + "]")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" "))
                    .append(Component.literal("[" + type.toUpperCase() + "]")
                            .withStyle(style -> switch (type) {
                                case "error" -> style.withColor(ChatFormatting.RED);
                                case "warning" -> style.withColor(ChatFormatting.GOLD);
                                case "info" -> style.withColor(ChatFormatting.BLUE);
                                case "success" -> style.withColor(ChatFormatting.GREEN);
                                default -> style.withColor(ChatFormatting.GRAY);
                            }))
                    .append(Component.literal(" "))
                    .append(Component.translatable(line.get("key").getAsString(), args).withStyle(ChatFormatting.WHITE));
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.empty();
        }

        @Override
        public void render(@NotNull GuiGraphics pGuiGraphics, int pIndex, int pTop, int pLeft, int pWidth, int pHeight, int pMouseX, int pMouseY, boolean pHovering, float pPartialTick) {
            pGuiGraphics.drawString(minecraft.font, logLine, pLeft, pTop, 0xFFFFFF);

            if (pHovering) {
                logList.thisScreen.setTooltipForNextRenderPass(Component.translatable("screen.ae2_texture_updater.log_screen.message.click_to_copy").withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
            String content = logLine.getString();
            minecraft.keyboardHandler.setClipboard(content);
            logList.thisScreen.showToast("screen.ae2_texture_updater.log_screen.message.copy_success");
            return true;
        }
    }

    public static class LogList extends ObjectSelectionList<Entry> {
        public final LogScreen thisScreen;

        public LogList(LogScreen screen, Minecraft pMinecraft, int pWidth, int pHeight, int pY0, int pY1, int pItemHeight) {
            super(pMinecraft, pWidth, pHeight, pY0, pY1, pItemHeight);

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

        public void refreshList(JsonArray messages, Set<String> hiddenLevels) {
            this.clearEntries();
            boolean showAll = hiddenLevels.isEmpty();

            for (int i = 0; i < messages.size(); i++) {
                JsonObject obj = messages.get(i).getAsJsonObject();
                String type = obj.get("type").getAsString();

                boolean shouldShow = showAll || !hiddenLevels.contains(type);
                if (shouldShow) {
                    this.addEntry(new LogScreen.Entry(minecraft, obj, this));
                }
            }
        }
    }
}
