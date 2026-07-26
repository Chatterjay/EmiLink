package org.chatterjay.emilink.client.search;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.EmiSearchWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.integration.BDProxy;
import org.chatterjay.emilink.util.ModLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SearchHistoryOverlay {
    private static final int MAX_HISTORY = 36;
    private static final int VISIBLE_EXPANDED = 6;
    private static final int ENTRY_HEIGHT = 12;
    private static final int PADDING = 3;
    private static final List<Entry> HISTORY = new ArrayList<>();
    private static int scrollOffset = 0;
    private static boolean loaded = false;
    private static boolean expanded = false;
    private static String observedSearch = "";
    private static String lastRememberedSearch = "";
    private static long observedSince = 0;
    private static Bounds lastRenderedBounds = null;
    private static boolean suppressUnderlyingHover = false;

    private SearchHistoryOverlay() {
    }

    public static void remember(String text) {
        remember(text, ItemStack.EMPTY);
    }

    public static void remember(String text, ItemStack icon) {
        if (!isEnabled()) {
            return;
        }
        load();
        text = normalize(text);
        if (text.isEmpty()) {
            return;
        }
        ItemStack normalizedIcon = normalizeIcon(icon);
        Entry old = remove(text);
        if (normalizedIcon.isEmpty() && old != null) {
            normalizedIcon = old.icon();
        }
        HISTORY.add(0, new Entry(text, normalizedIcon));
        scrollOffset = 0;
        while (HISTORY.size() > MAX_HISTORY) {
            HISTORY.remove(HISTORY.size() - 1);
        }
        lastRememberedSearch = text;
        save();
    }

    public static void applySearch(String text) {
        applySearch(text, ItemStack.EMPTY);
    }

    public static void applySearch(String text, ItemStack icon) {
        text = normalize(text);
        if (text.isEmpty()) {
            return;
        }
        remember(text, icon);
        EmiApi.setSearchText(text);

        var mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null || screen instanceof ChatScreen) {
            return;
        }

        if (AE2Proxy.isMEStorageScreen(screen) && setAESearchText(screen, text)) {
            return;
        }

        if (BDProxy.isBDNetGUI(screen) && setBDSearchText(screen, text)) {
            return;
        }

        if (screen.getFocused() instanceof EditBox focusedEditBox) {
            setEditBoxValue(focusedEditBox, text);
            return;
        }

        for (var child : screen.children()) {
            if (child instanceof EditBox editBox) {
                setEditBoxValue(editBox, text);
                return;
            }
        }

        for (var fieldName : new String[]{"searchBox", "searchField", "search"}) {
            var field = findScreenField(screen, fieldName);
            if (field == null) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object widget = field.get(screen);
                if (widget == null) {
                    continue;
                }
                var setValue = widget.getClass().getMethod("setValue", String.class);
                setValue.invoke(widget, text);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    public static void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isEnabled()) {
            expanded = false;
            lastRenderedBounds = null;
            return;
        }
        load();
        observeExternalSearch();
        if (HISTORY.isEmpty() || EmiScreenManager.isDisabled()) {
            expanded = false;
            lastRenderedBounds = null;
            return;
        }
        Bounds bounds = bounds(mouseX, mouseY);
        if (bounds == null) {
            lastRenderedBounds = null;
            return;
        }
        lastRenderedBounds = bounds;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 500);
        var font = Minecraft.getInstance().font;
        int rows = bounds.expanded() ? Math.min(visibleExpandedRows(), HISTORY.size()) : 1;
        int bg = bounds.expanded() ? 0xee101014 : 0xcc101014;
        int border = bounds.expanded() ? 0xff6da7ff : 0xff50505a;
        guiGraphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.w(), bounds.y() + bounds.h(), bg);
        guiGraphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.w(), bounds.y() + 1, border);
        guiGraphics.fill(bounds.x(), bounds.y() + bounds.h() - 1, bounds.x() + bounds.w(), bounds.y() + bounds.h(), border);
        guiGraphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.h(), border);
        guiGraphics.fill(bounds.x() + bounds.w() - 1, bounds.y(), bounds.x() + bounds.w(), bounds.y() + bounds.h(), border);

        for (int row = 0; row < rows; row++) {
            int index = bounds.expanded() ? scrollOffset + row : 0;
            if (index < 0 || index >= HISTORY.size()) {
                break;
            }
            Entry entry = HISTORY.get(index);
            int y = bounds.y() + PADDING + row * ENTRY_HEIGHT;
            boolean hovered = bounds.expanded() && mouseX >= bounds.x() && mouseX < bounds.x() + bounds.w()
                    && mouseY >= y - 1 && mouseY < y + ENTRY_HEIGHT - 1;
            if (hovered) {
                guiGraphics.fill(bounds.x() + 1, y - 1, bounds.x() + bounds.w() - 1, y + ENTRY_HEIGHT - 1, 0x553b78ff);
            }
            int textX = bounds.x() + PADDING;
            if (!entry.icon().isEmpty()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(0.5f, 0.5f, 1.0f);
                guiGraphics.renderItem(entry.icon(), (bounds.x() + 3) * 2, (y + 2) * 2);
                guiGraphics.pose().popPose();
                textX += 11;
            }
            String text = font.plainSubstrByWidth(entry.text(), bounds.x() + bounds.w() - textX - PADDING);
            guiGraphics.drawString(font, text, textX, y + 1, 0xfff4f4f4, false);
        }
        guiGraphics.pose().popPose();
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled()) {
            return false;
        }
        load();
        if (EmiScreenManager.isDisabled() || (button != 0 && button != 1 && button != 2)) {
            return false;
        }
        Bounds bounds = bounds((int) mouseX, (int) mouseY);
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (!bounds.expanded()) {
            if (button == 1 || button == 2) {
                HISTORY.remove(0);
                save();
            } else {
                applySearch(HISTORY.get(0).text());
            }
            return true;
        }

        int row = ((int) mouseY - bounds.y() - PADDING) / ENTRY_HEIGHT;
        int index = scrollOffset + row;
        if (row < 0 || index < 0 || index >= HISTORY.size()) {
            return true;
        }
        if (button == 1 || button == 2) {
            HISTORY.remove(index);
            if (scrollOffset > maxScroll()) {
                scrollOffset = maxScroll();
            }
            save();
        } else {
            applySearch(HISTORY.get(index).text());
        }
        return true;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isEnabled()) {
            return false;
        }
        load();
        if (EmiScreenManager.isDisabled()) {
            return false;
        }
        Bounds bounds = bounds((int) mouseX, (int) mouseY);
        if (bounds == null || !bounds.expanded() || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (amount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (amount < 0) {
            scrollOffset = Math.min(maxScroll(), scrollOffset + 1);
        }
        return true;
    }

    public static boolean isMouseOver(double mouseX, double mouseY) {
        if (!isEnabled()) {
            return false;
        }
        load();
        if (HISTORY.isEmpty() || EmiScreenManager.isDisabled()) {
            return false;
        }
        if (lastRenderedBounds != null && lastRenderedBounds.contains(mouseX, mouseY)) {
            return true;
        }
        Bounds bounds = bounds((int) mouseX, (int) mouseY);
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    public static void setSuppressUnderlyingHover(boolean suppress) {
        suppressUnderlyingHover = suppress;
    }

    public static boolean shouldSuppressUnderlyingHover() {
        return suppressUnderlyingHover;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = historyPath();
            if (!Files.isRegularFile(path)) {
                return;
            }
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                Entry entry = parseEntry(line);
                if (!entry.text().isEmpty() && find(entry.text()) == null) {
                    HISTORY.add(entry);
                }
                if (HISTORY.size() >= MAX_HISTORY) {
                    break;
                }
            }
        } catch (Exception e) {
            ModLogger.debug("SEARCH_HISTORY load failed: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Path path = historyPath();
            Files.createDirectories(path.getParent());
            Files.write(path, HISTORY.stream().map(SearchHistoryOverlay::serializeEntry).toList(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ModLogger.debug("SEARCH_HISTORY save failed: {}", e.toString());
        }
    }

    private static Path historyPath() {
        return FMLPaths.CONFIGDIR.get().resolve("emilink-search-history.txt");
    }

    private static void observeExternalSearch() {
        try {
            String current = normalize(EmiApi.getSearchText());
            long now = System.currentTimeMillis();
            if (!current.equals(observedSearch)) {
                observedSearch = current;
                observedSince = now;
                return;
            }
            if (!current.isEmpty()
                    && !current.equals(lastRememberedSearch)
                    && now - observedSince >= 700) {
                remember(current);
            }
        } catch (Exception ignored) {
        }
    }

    private static void setEditBoxValue(EditBox editBox, String text) {
        editBox.setValue(text);
        editBox.moveCursorToEnd();
    }

    private static boolean setBDSearchText(Screen screen, String text) {
        try {
            var field = findScreenField(screen, "searchField");
            if (field == null) {
                return false;
            }
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof EditBox editBox) {
                setEditBoxValue(editBox, text);
                return true;
            }
        } catch (Exception e) {
            ModLogger.debug("SEARCH_HISTORY BD sync failed: {}", e.toString());
        }
        return false;
    }

    private static boolean setAESearchText(Screen screen, String text) {
        try {
            var field = findScreenField(screen, "searchField");
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof EditBox editBox) {
                    setEditBoxValue(editBox, text);
                }
            }
            var setSearchText = findScreenMethod(screen, "setSearchText", String.class);
            if (setSearchText != null) {
                setSearchText.setAccessible(true);
                setSearchText.invoke(screen, text);
                return true;
            }
        } catch (Exception e) {
            ModLogger.debug("SEARCH_HISTORY AE sync failed: {}", e.toString());
        }
        return false;
    }

    private static java.lang.reflect.Method findScreenMethod(Screen screen, String name, Class<?>... parameterTypes) {
        Class<?> cls = screen.getClass();
        while (cls != null && cls != Screen.class) {
            try {
                return cls.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static java.lang.reflect.Field findScreenField(Screen screen, String name) {
        Class<?> cls = screen.getClass();
        while (cls != null && cls != Screen.class) {
            try {
                return cls.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static Bounds bounds(int mouseX, int mouseY) {
        EmiSearchWidget search = EmiScreenManager.search;
        if (search == null || !search.visible || HISTORY.isEmpty()) {
            expanded = false;
            return null;
        }

        int compactWidth = compactWidth(search);
        int expandedWidth = expandedWidth(search);
        Bounds compact = rawBounds(search, compactWidth, 1, false);
        Bounds expandedBounds = rawBounds(search, expandedWidth, Math.min(visibleExpandedRows(), HISTORY.size()), true);
        if (expanded) {
            expanded = expandedBounds.contains(mouseX, mouseY) || compact.contains(mouseX, mouseY);
        } else {
            expanded = compact.contains(mouseX, mouseY);
        }
        int rows = expanded ? Math.min(visibleExpandedRows(), HISTORY.size()) : 1;
        return expanded ? rawBounds(search, expandedWidth, rows, true) : compact;
    }

    private static int compactWidth(EmiSearchWidget search) {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int maxWidth = guiScale >= 4.0 ? 52 : guiScale >= 3.0 ? 64 : 84;
        int minWidth = guiScale >= 4.0 ? 36 : 48;
        return Math.max(minWidth, Math.min(search.getWidth(), maxWidth));
    }

    private static int expandedWidth(EmiSearchWidget search) {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int minWidth = guiScale >= 4.0 ? 64 : 84;
        int maxWidth = guiScale >= 4.0 ? 84 : guiScale >= 3.0 ? 104 : 132;
        return Math.max(minWidth, Math.min(search.getWidth(), maxWidth));
    }

    private static int visibleExpandedRows() {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (guiScale >= 4.0) {
            return 4;
        }
        if (guiScale >= 3.0) {
            return 5;
        }
        return VISIBLE_EXPANDED;
    }

    private static Bounds rawBounds(EmiSearchWidget search, int width, int rows, boolean expanded) {
        int height = PADDING * 2 + rows * ENTRY_HEIGHT;
        return switch (searchHistoryPosition()) {
            case ABOVE -> boundsAbove(search, width, height, expanded);
            case LEFT -> boundsBeside(search, width, height, expanded, true);
            case RIGHT -> boundsBeside(search, width, height, expanded, false);
            case AUTO, OFF -> boundsAuto(search, width, height, expanded);
        };
    }

    private static Bounds boundsAuto(EmiSearchWidget search, int width, int height, boolean expanded) {
        int x = xPosition(search, width);
        int y = search.getY() - height - 3;
        if (isHorizontalDock(search)) {
            y = clampY(search.getY(), height);
        } else if (y < 4) {
            y = search.getY() + search.getHeight() + 3;
        }
        return new Bounds(x, y, width, height, expanded);
    }

    private static Bounds boundsAbove(EmiSearchWidget search, int width, int height, boolean expanded) {
        int x = clampX(search.getX(), width);
        int y = search.getY() - height - 3;
        if (y < 4) {
            y = search.getY() + search.getHeight() + 3;
        }
        return new Bounds(x, clampY(y, height), width, height, expanded);
    }

    private static Bounds boundsBeside(EmiSearchWidget search, int width, int height, boolean expanded, boolean left) {
        Screen screen = Minecraft.getInstance().screen;
        int screenWidth = screen == null ? search.getX() + search.getWidth() : screen.width;
        int preferred = left ? search.getX() - width - 4 : search.getX() + search.getWidth() + 4;
        int fallback = left ? search.getX() + search.getWidth() + 4 : search.getX() - width - 4;
        int x = preferred;
        if (x < 4 || x + width > screenWidth - 4) {
            x = fallback;
        }
        return new Bounds(clampX(x, width), clampY(search.getY(), height), width, height, expanded);
    }

    private static int xPosition(EmiSearchWidget search, int width) {
        Screen screen = Minecraft.getInstance().screen;
        int screenWidth = screen == null ? search.getX() + search.getWidth() : screen.width;
        int searchX = search.getX();
        int searchWidth = search.getWidth();
        if (!isSideSearch(search) && Minecraft.getInstance().getWindow().getGuiScale() >= 4.0) {
            int leftSide = searchX - width - 4;
            if (leftSide >= 4) {
                return leftSide;
            }
            int rightSide = searchX + searchWidth + 4;
            if (rightSide + width <= screenWidth - 4) {
                return rightSide;
            }
        }
        if (isSideSearch(search)) {
            int rightSide = searchX + searchWidth + 4;
            if (rightSide + width <= screenWidth - 4) {
                return rightSide;
            }
            int leftSide = searchX - width - 4;
            if (leftSide >= 4) {
                return leftSide;
            }
            return Math.max(4, Math.min(screenWidth - width - 4, searchX + searchWidth - width));
        }
        return Math.max(4, Math.min(screenWidth - width - 4, searchX));
    }

    private static boolean isHorizontalDock(EmiSearchWidget search) {
        return isSideSearch(search) || Minecraft.getInstance().getWindow().getGuiScale() >= 4.0;
    }

    private static boolean isSideSearch(EmiSearchWidget search) {
        return search.getWidth() > 180;
    }

    private static Config.SearchHistoryPosition searchHistoryPosition() {
        try {
            return Config.SEARCH_HISTORY_POSITION.get();
        } catch (Exception ignored) {
            return Config.SearchHistoryPosition.AUTO;
        }
    }

    private static boolean isEnabled() {
        return searchHistoryPosition() != Config.SearchHistoryPosition.OFF;
    }

    private static int clampX(int x, int width) {
        Screen screen = Minecraft.getInstance().screen;
        int screenWidth = screen == null ? x + width : screen.width;
        return Math.max(4, Math.min(screenWidth - width - 4, x));
    }

    private static int clampY(int y, int height) {
        Screen screen = Minecraft.getInstance().screen;
        int screenHeight = screen == null ? y + height : screen.height;
        return Math.max(4, Math.min(screenHeight - height - 4, y));
    }

    private static int maxScroll() {
        return Math.max(0, HISTORY.size() - visibleExpandedRows());
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private static Entry remove(String text) {
        for (int i = 0; i < HISTORY.size(); i++) {
            if (HISTORY.get(i).text().equals(text)) {
                return HISTORY.remove(i);
            }
        }
        return null;
    }

    private static Entry find(String text) {
        for (Entry entry : HISTORY) {
            if (entry.text().equals(text)) {
                return entry;
            }
        }
        return null;
    }

    private static ItemStack normalizeIcon(ItemStack icon) {
        if (icon == null || icon.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = icon.copy();
        copy.setCount(1);
        return copy;
    }

    private static Entry parseEntry(String line) {
        String raw = line == null ? "" : line;
        int tab = raw.indexOf('\t');
        if (tab < 0) {
            return new Entry(normalize(raw), ItemStack.EMPTY);
        }
        String id = raw.substring(0, tab);
        String text = normalize(raw.substring(tab + 1));
        ItemStack icon = ItemStack.EMPTY;
        try {
            ResourceLocation location = new ResourceLocation(id);
            Item item = BuiltInRegistries.ITEM.get(location);
            if (item != Items.AIR) {
                icon = new ItemStack(item);
            }
        } catch (Exception ignored) {
        }
        return new Entry(text, icon);
    }

    private static String serializeEntry(Entry entry) {
        String text = entry.text().replace('\t', ' ');
        if (entry.icon().isEmpty()) {
            return text;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.icon().getItem());
        return id + "\t" + text;
    }

    private record Bounds(int x, int y, int w, int h, boolean expanded) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Entry(String text, ItemStack icon) {
    }
}
