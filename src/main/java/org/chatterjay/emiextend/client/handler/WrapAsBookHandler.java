package org.chatterjay.emiextend.client.handler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.network.EmiLinkNetwork;

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

        GenericStack output = outputs.get(0);
        if (output == null) return;
        if (!(output.what() instanceof AEItemKey itemKey)) return;

        ItemStack original = itemKey.toStack().copyWithCount(1);
        ItemStack book = createWrittenBook(original);

        var outSlots = menu.getProcessingOutputSlots();
        if (outSlots.length > 0) {
            EmiLinkNetwork.sendAEPacketToServer(new InventoryActionPacket(
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
                EmiLinkNetwork.sendAEPacketToServer(new InventoryActionPacket(
                        InventoryAction.SET_FILTER, inSlots[target].index, original));
            }
        }
    }

    /** 1.20.1 NBT-based written book creation (replaces DataComponents API). */
    private static ItemStack createWrittenBook(ItemStack original) {
        var mc = Minecraft.getInstance();
        String title = original.getHoverName().getString()
                + Component.translatable("emilink.suffix.pattern").getString();
        String author = mc.player != null ? mc.player.getName().getString() : "EmiLink";
        Component page = Component.literal("Crafted from: " + title);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putByte("resolved", (byte) 1);
        tag.putInt("generation", 0);

        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        tag.put("pages", pages);

        return book;
    }
}
