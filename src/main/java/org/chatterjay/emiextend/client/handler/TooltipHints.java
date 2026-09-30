package org.chatterjay.emiextend.client.handler;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.chatterjay.emiextend.client.BatchMark;
import org.chatterjay.emiextend.client.FavoriteProtection;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Adds the EmiLink selection hints to item tooltips.
 * <p>
 * The EMI sidebar tooltip is built from the vanilla item tooltip lines, so this
 * event covers the sidebar and every other place an item tooltip is shown
 * (inventory, containers, recipe screens). Non-item stacks such as fluids are
 * handled by {@link FavoriteHighlightRenderer#appendSelectionTooltip} instead,
 * because they never reach this event.
 */
public final class TooltipHints {

    private TooltipHints() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get() && !EmiLinkConfig.ENABLE_BATCH_MARK.get()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;

        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        String key = id.toString();

        if (EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get() && FavoriteProtection.isProtectedId(key)) {
            event.getToolTip().add(Component.translatable("emilink.tooltip.favorite_protected")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (EmiLinkConfig.ENABLE_BATCH_MARK.get() && BatchMark.isMarkedId(key)) {
            event.getToolTip().add(Component.translatable("emilink.tooltip.batch_marked")
                    .withStyle(ChatFormatting.AQUA));
        }
        if (ModLogger.isDebugEnabled()) {
            ModLogger.debug("TooltipHints: item={} protected={} marked={}",
                    key, FavoriteProtection.isProtectedId(key), BatchMark.isMarkedId(key));
        }
    }
}
