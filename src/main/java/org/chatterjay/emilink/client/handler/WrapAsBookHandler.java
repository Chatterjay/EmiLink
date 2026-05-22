package org.chatterjay.emilink.client.handler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.network.NetworkHandler;
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

import org.chatterjay.emilink.util.ModLogger;

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
        ModLogger.info("WrapAsBookHandler: toggle -> {}", wrapRequested.get());
    }

    public static void clear() {
        wrapRequested.set(false);
        ModLogger.info("WrapAsBookHandler: cleared");
    }

    public static void applyWrap(PatternEncodingTermMenu menu, List<GenericStack> outputs) {
        boolean requested = wrapRequested.get();
        ModLogger.info("WrapAsBookHandler: applyWrap called, requested={}, outputs={}",
                requested, outputs == null ? 0 : outputs.size());

        if (!wrapRequested.getAndSet(false)) return;
        if (outputs == null || outputs.isEmpty()) return;

        GenericStack output = outputs.get(0);
        if (output == null) return;
        if (!(output.what() instanceof AEItemKey itemKey)) {
            ModLogger.info("WrapAsBookHandler: output.what() is not AEItemKey: {}",
                    output.what() == null ? "null" : output.what().getClass().getName());
            return;
        }

        ItemStack original = itemKey.toStack();
        original.setCount(1);
        ItemStack book = createWrittenBook(original);

        var outSlots = menu.getProcessingOutputSlots();
        if (outSlots.length > 0) {
            NetworkHandler.instance().sendToServer(new InventoryActionPacket(
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
            NetworkHandler.instance().sendToServer(new InventoryActionPacket(
                    InventoryAction.SET_FILTER, inSlots[target].index, original));
        }
    }

    private static ItemStack createWrittenBook(ItemStack original) {
        var mc = Minecraft.getInstance();
        String title = original.getHoverName().getString()
                + Component.translatable("emilink.suffix.pattern").getString();
        String author = mc.player != null ? mc.player.getName().getString() : "EmiLink";
        var page = Component.literal("Crafted from: " + title);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = new CompoundTag();
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putInt("generation", 0);
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        tag.put("pages", pages);
        tag.putByte("resolved", (byte) 1);
        book.setTag(tag);
        return book;
    }
}
