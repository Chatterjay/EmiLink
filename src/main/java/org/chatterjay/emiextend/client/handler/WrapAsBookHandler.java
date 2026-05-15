package org.chatterjay.emiextend.client.handler;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WrapAsBookHandler {
    private static final AtomicBoolean wrapRequested = new AtomicBoolean(false);

    private WrapAsBookHandler() {}

    public static boolean isActive() {
        return wrapRequested.get();
    }

    public static void toggle() {
        wrapRequested.set(!wrapRequested.get());
    }

    public static void clear() {
        wrapRequested.set(false);
    }

    /**
     * After recipe transfer, if wrap mode is active, override the output slot
     * with a Written Book and move the original output to an input slot.
     * Only applies to processing recipes.
     */
    public static void applyWrap(PatternEncodingTermMenu menu, EmiStack originalOutput, boolean doTransfer) {
        if (!wrapRequested.getAndSet(false) || !doTransfer) return;
        if (originalOutput == null || originalOutput.isEmpty()) return;

        ItemStack original = originalOutput.getItemStack();
        if (original == null || original.isEmpty()) return;
        original = original.copyWithCount(1);

        ItemStack book = createWrittenBook(original);

        var outSlots = menu.getProcessingOutputSlots();
        if (outSlots.length > 0) {
            PacketDistributor.sendToServer(new InventoryActionPacket(
                    InventoryAction.SET_FILTER, outSlots[0].index, book));
        }

        var inSlots = menu.getProcessingInputSlots();
        int target = -1;
        for (int i = 0; i < inSlots.length; i++) {
            if (inSlots[i].getItem().isEmpty()) {
                target = i;
                break;
            }
        }
        if (target < 0 && inSlots.length > 0) target = 0;

        if (target >= 0) {
            PacketDistributor.sendToServer(new InventoryActionPacket(
                    InventoryAction.SET_FILTER, inSlots[target].index, original));
        }
    }

    private static ItemStack createWrittenBook(ItemStack original) {
        var mc = Minecraft.getInstance();
        String title = original.getHoverName().getString()
                + net.minecraft.network.chat.Component.translatable("emilink.suffix.pattern").getString();
        String author = mc.player != null ? mc.player.getName().getString() : "EmiLink";
        Component page = Component.literal("Crafted from: " + title);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                List.of(Filterable.passThrough(page)),
                true
        ));
        return book;
    }
}
