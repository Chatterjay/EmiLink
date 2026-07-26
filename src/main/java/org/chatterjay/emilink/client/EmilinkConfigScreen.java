package org.chatterjay.emilink.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.chatterjay.emilink.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EmilinkConfigScreen extends Screen {
    private final Screen parent;
    private final List<ConfigRow> rows = new ArrayList<>();
    private final List<SaveAction> saveActions = new ArrayList<>();
    private int scrollOffset = 0;

    private EmilinkConfigScreen(Screen parent) {
        super(Component.literal("EmiLink Config"));
        this.parent = parent;
    }

    public static Screen create(Screen parent) {
        return new EmilinkConfigScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        saveActions.clear();
        scrollOffset = 0;

        int leftCol = this.width / 2 - 155;
        int rightCol = leftCol + 160;
        int startY = 40;
        int spacing = 24;

        int idx = 0;
        idx = addToggle(leftCol, rightCol, startY, spacing, idx, "Debug Mode", Config.DEBUG_MODE);
        idx = addLongField(leftCol, rightCol, startY, spacing, idx, "Cache TTL (ms)", Config.CACHE_TTL_MS);
        idx = addLongField(leftCol, rightCol, startY, spacing, idx, "Batch Flush (ms)", Config.BATCH_FLUSH_MS);
        idx = addToggle(leftCol, rightCol, startY, spacing, idx, "Wrap Book", Config.ENABLE_WRAP_BOOK);
        idx = addToggle(leftCol, rightCol, startY, spacing, idx, "WB Fill Input Grid", Config.WB_FILL_INPUT_GRID);
        idx = addToggle(leftCol, rightCol, startY, spacing, idx, "AE Deposit", Config.ENABLE_AE_DEPOSIT);
        idx = addToggle(leftCol, rightCol, startY, spacing, idx, "Network Badges", Config.ENABLE_NETWORK_BADGES);
        idx = addToggle(leftCol, rightCol, startY, spacing, idx, "Bulk Transfer", Config.ENABLE_BULK_TRANSFER);
        idx = addToggle(leftCol, rightCol, startY, spacing, idx, "Drag Fill", Config.ENABLE_DRAG_FILL);
        idx = addSearchHistoryPositionCycle(leftCol, rightCol, startY, spacing, idx, "Search History",
                Config.SEARCH_HISTORY_POSITION);
        idx = addEnumCycle(leftCol, rightCol, startY, spacing, idx, "Extract Modifier",
                Config.EXTRACT_MODIFIER);
        idx = addEnumCycle(leftCol, rightCol, startY, spacing, idx, "Deposit Batch Modifier",
                Config.DEPOSIT_BATCH_MODIFIER);
    }

    private int addToggle(int leftCol, int rightCol, int startY, int spacing, int idx,
                          String label, ForgeConfigSpec.BooleanValue cfg) {
        int y = startY + idx * spacing;
        var btn = Button.builder(
                Component.literal(cfg.get() ? "ON" : "OFF"),
                b -> {
                    cfg.set(!cfg.get());
                    b.setMessage(Component.literal(cfg.get() ? "ON" : "OFF"));
                }
        ).bounds(rightCol, y - scrollOffset, 70, 20).build();
        rows.add(new ConfigRow(Component.literal(label), btn, y));
        addRenderableWidget(btn);
        return idx + 1;
    }

    private int addEnumCycle(int leftCol, int rightCol, int startY, int spacing, int idx,
                             String label, ForgeConfigSpec.ConfigValue<String> cfg) {
        int y = startY + idx * spacing;
        var btn = Button.builder(
                Component.literal(cfg.get()),
                b -> {
                    String current = cfg.get().toUpperCase(Locale.ROOT);
                    String next;
                    switch (current) {
                        case "SHIFT" -> next = "CONTROL";
                        case "CONTROL" -> next = "ALT";
                        case "ALT" -> next = "OFF";
                        default -> next = "SHIFT";
                    }
                    cfg.set(next);
                    b.setMessage(Component.literal(next));
                }
        ).bounds(rightCol, y - scrollOffset, 70, 20).build();
        rows.add(new ConfigRow(Component.literal(label), btn, y));
        addRenderableWidget(btn);
        return idx + 1;
    }

    private int addSearchHistoryPositionCycle(int leftCol, int rightCol, int startY, int spacing, int idx,
                                               String label, ForgeConfigSpec.EnumValue<Config.SearchHistoryPosition> cfg) {
        int y = startY + idx * spacing;
        var btn = Button.builder(
                Component.literal(cfg.get().name()),
                b -> {
                    Config.SearchHistoryPosition current = cfg.get();
                    Config.SearchHistoryPosition[] values = Config.SearchHistoryPosition.values();
                    Config.SearchHistoryPosition next = values[(current.ordinal() + 1) % values.length];
                    cfg.set(next);
                    b.setMessage(Component.literal(next.name()));
                }
        ).bounds(rightCol, y - scrollOffset, 90, 20).build();
        rows.add(new ConfigRow(Component.literal(label), btn, y));
        addRenderableWidget(btn);
        return idx + 1;
    }

    private int addLongField(int leftCol, int rightCol, int startY, int spacing, int idx,
                             String label, ForgeConfigSpec.LongValue cfg) {
        int y = startY + idx * spacing;
        var editBox = new EditBox(this.font, rightCol, y - scrollOffset, 70, 20, Component.literal(label));
        editBox.setValue(String.valueOf(cfg.get()));
        editBox.setFilter(s -> s.matches("-?\\d*"));
        rows.add(new ConfigRow(Component.literal(label), editBox, y));
        addRenderableWidget(editBox);
        saveActions.add(new SaveAction(editBox, cfg));
        return idx + 1;
    }

    @Override
    public void tick() {
        super.tick();
        for (var row : rows) {
            if (row.widget() instanceof EditBox eb) {
                eb.tick();
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int contentHeight = rows.size() * 24 + 40;
        int maxScroll = Math.max(0, contentHeight - (this.height - 20));
        int oldOffset = scrollOffset;
        scrollOffset = (int) Math.max(0, Math.min(scrollOffset - delta * 24, maxScroll));
        if (scrollOffset != oldOffset) {
            for (var row : rows) {
                row.widget().setY(row.baseY() - scrollOffset);
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Draw the title
        guiGraphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 15, 0xFFFFFF);

        // Draw row labels and clip widgets outside viewport
        int leftCol = this.width / 2 - 155;
        int viewTop = 30;
        int viewBottom = this.height - 10;

        guiGraphics.enableScissor(0, viewTop, this.width, viewBottom);

        for (var row : rows) {
            int y = row.baseY() - scrollOffset + 6;
            if (y + 10 > viewTop && y - 10 < viewBottom) {
                guiGraphics.drawString(this.font, row.label(), leftCol, y, 0xFFFFFF);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.disableScissor();
    }

    @Override
    public void onClose() {
        for (var action : saveActions) {
            action.save();
        }
        Config.SPEC.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private record ConfigRow(Component label, AbstractWidget widget, int baseY) {}

    private static class SaveAction {
        private final EditBox editBox;
        private final ForgeConfigSpec.LongValue cfg;

        SaveAction(EditBox editBox, ForgeConfigSpec.LongValue cfg) {
            this.editBox = editBox;
            this.cfg = cfg;
        }

        void save() {
            try {
                cfg.set(Long.parseLong(editBox.getValue()));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
