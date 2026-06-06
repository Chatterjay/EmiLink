package org.chatterjay.emiextend.client.handler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.config.EmiLinkConfig;

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

    public static void applyWrap(PatternEncodingTermMenu menu, List<GenericStack> outputs) {
        if (!wrapRequested.getAndSet(false)) return;
        if (outputs == null || outputs.isEmpty()) return;

        GenericStack output = outputs.getFirst();
        if (output == null) return;
        if (!(output.what() instanceof AEItemKey itemKey)) return;

        ItemStack original = itemKey.toStack().copyWithCount(1);
        ItemStack book = createWrittenBook(original);

        var outSlots = menu.getProcessingOutputSlots();
        if (outSlots.length > 0) {
            PacketDistributor.sendToServer(new InventoryActionPacket(
                    InventoryAction.SET_FILTER, outSlots[0].index, book));
        }

        if (EmiLinkConfig.WB_FILL_INPUT_GRID.get()) {
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
    }

    private static ItemStack createWrittenBook(ItemStack original) {
        var mc = Minecraft.getInstance();
        String title = original.getHoverName().getString()
                + net.minecraft.network.chat.Component.translatable("emilink.suffix.pattern").getString();
        String author = mc.player != null ? mc.player.getName().getString() : "EmiLink";
        var page = net.minecraft.network.chat.Component.literal("Crafted from: " + title);

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
