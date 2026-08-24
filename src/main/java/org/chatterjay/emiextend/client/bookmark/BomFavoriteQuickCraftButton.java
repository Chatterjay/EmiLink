package org.chatterjay.emiextend.client.bookmark;

import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.chatterjay.emiextend.client.InputEvents;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.List;

public final class BomFavoriteQuickCraftButton {
    private static final int HITBOX_WIDTH = 10;
    private static final int HITBOX_OUTSIDE_WIDTH = 8;
    private static final int BRACKET_WIDTH = 5;
    private static final int SLOT_SIZE = 18;
    private static final int GROUP_CHAIN_COLOR = 0xFF45DA75;
    private static final int HOVER_FRAME_COLOR = 0xFFFFFFFF;
    private static final List<Button> BUTTONS = new ArrayList<>();

    private BomFavoriteQuickCraftButton() {
    }

    public static void clearFrame() {
        BUTTONS.clear();
    }

    public static void render(GuiGraphics guiGraphics, int slotX, int slotY, int mouseX, int mouseY,
                              EmiFavorite.Synthetic synthetic) {
        if (!shouldShow(synthetic)) {
            return;
        }

        int x = slotX - HITBOX_OUTSIDE_WIDTH;
        int y = slotY;
        Button button = new Button(x, y, synthetic);
        BUTTONS.add(button);

        boolean hovered = button.contains(mouseX, mouseY);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 220);
        drawBracket(guiGraphics, slotX - BRACKET_WIDTH - 1, y, GROUP_CHAIN_COLOR);

        var font = Minecraft.getInstance().font;
        if (hovered) {
            drawHoverFrame(guiGraphics, x, y, HITBOX_OUTSIDE_WIDTH, SLOT_SIZE, HOVER_FRAME_COLOR);
            guiGraphics.renderTooltip(font,
                    List.of(Component.translatable("emilink.tooltip.favorite_bom_quick_craft")),
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY);
        }
        guiGraphics.pose().popPose();
    }

    private static void drawBracket(GuiGraphics guiGraphics, int x, int y, int color) {
        int top = y + SLOT_SIZE / 4;
        int bottom = y + SLOT_SIZE - SLOT_SIZE / 4;
        guiGraphics.fill(x, top, x + BRACKET_WIDTH, top + 1, color);
        guiGraphics.fill(x, top, x + 1, bottom, color);
        guiGraphics.fill(x, bottom - 1, x + BRACKET_WIDTH, bottom, color);
    }

    private static void drawHoverFrame(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Button hit = hit(mouseX, mouseY);
        if (hit == null) {
            return false;
        }
        boolean started = InputEvents.tryQuickCraftFavoriteSyntheticFromButton(hit.synthetic());
        ModLogger.debug("BOM_TREE favorite quick-craft panel clicked started={} xy=({}, {})",
                started, (int) mouseX, (int) mouseY);
        return started;
    }

    public static boolean isMouseOver(double mouseX, double mouseY) {
        return hit(mouseX, mouseY) != null;
    }

    private static boolean shouldShow(EmiFavorite.Synthetic synthetic) {
        return EmiLinkConfig.isAutomaticWorkbenchCraftingEnabled()
                && EmiLinkConfig.ENABLE_QUICK_CRAFT_TAB.get()
                && synthetic != null
                && BomTreePageHelper.isFinalSynthetic(synthetic);
    }

    private static Button hit(double mouseX, double mouseY) {
        if (!EmiLinkConfig.isAutomaticWorkbenchCraftingEnabled()
                || !EmiLinkConfig.ENABLE_QUICK_CRAFT_TAB.get()) {
            return null;
        }
        for (Button button : BUTTONS) {
            if (button.contains(mouseX, mouseY)) {
                return button;
            }
        }
        return null;
    }

    private record Button(int x, int y, EmiFavorite.Synthetic synthetic) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + HITBOX_WIDTH
                    && mouseY >= y && mouseY < y + SLOT_SIZE;
        }
    }
}
