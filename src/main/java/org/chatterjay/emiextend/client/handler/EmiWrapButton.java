package org.chatterjay.emiextend.client.handler;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import org.chatterjay.emiextend.config.EmiLinkConfig;
import java.util.List;

public class EmiWrapButton extends Widget {
    private final int x, y, width, height;

    public EmiWrapButton(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        EmiDrawContext ctx = EmiDrawContext.wrap(guiGraphics);
        boolean enabled = EmiLinkConfig.ENABLE_WRAP_BOOK.get();
        boolean active = enabled && WrapAsBookHandler.isActive();

        int bgColor = active ? 0xFF555555 : (enabled ? 0xFF222222 : 0xFF111111);
        ctx.fill(x, y, x + width, y + height, bgColor);

        if (active) {
            ctx.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x40FFFF00);
        }

        ctx.fill(x, y, x + width, y + 1, 0xFF888888);
        ctx.fill(x, y + height - 1, x + width, y + height, 0xFF888888);
        ctx.fill(x, y, x + 1, y + height, 0xFF888888);
        ctx.fill(x + width - 1, y, x + width, y + height, 0xFF888888);

        Font font = Minecraft.getInstance().font;
        String label = active ? "书" : "WB";
        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - font.lineHeight) / 2 + 1;
        ctx.drawTextWithShadow(Component.literal(label), textX, textY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && getBounds().contains(mouseX, mouseY) && EmiLinkConfig.ENABLE_WRAP_BOOK.get()) {
            WrapAsBookHandler.toggle();
            return true;
        }
        return false;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        if (getBounds().contains(mouseX, mouseY)) {
            if (!EmiLinkConfig.ENABLE_WRAP_BOOK.get()) {
                return List.of();
            }
            boolean active = WrapAsBookHandler.isActive();
            var key = active ? "emilink.tooltip.wrap_as_book.on" : "emilink.tooltip.wrap_as_book.off";
            return List.of(ClientTooltipComponent.create(
                    Component.translatable(key).getVisualOrderText()));
        }
        return List.of();
    }
}
