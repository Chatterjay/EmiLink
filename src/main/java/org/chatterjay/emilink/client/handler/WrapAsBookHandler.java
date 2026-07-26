package org.chatterjay.emilink.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.chatterjay.emilink.Config;
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
        ModLogger.debug("WrapAsBookHandler: toggle -> {}", wrapRequested.get());
    }

    public static void clear() {
        wrapRequested.set(false);
        ModLogger.debug("WrapAsBookHandler: cleared");
    }

    public static void applyWrap(Object menu, List<?> outputs) {
        boolean requested = wrapRequested.get();
        ModLogger.debug("WrapAsBookHandler: applyWrap called, requested={}, outputs={}",
                requested, outputs == null ? 0 : outputs.size());

        if (!wrapRequested.getAndSet(false)) return;
        if (outputs == null || outputs.isEmpty()) return;

        Object output = outputs.get(0);
        if (output == null) return;
        Object itemKey = getGenericStackWhat(output);
        if (!isAEItemKey(itemKey)) {
            ModLogger.debug("WrapAsBookHandler: output.what() is not AEItemKey: {}",
                    itemKey == null ? "null" : itemKey.getClass().getName());
            return;
        }

        ItemStack original = toItemStack(itemKey);
        if (original.isEmpty()) return;
        original.setCount(1);
        ItemStack book = createWrittenBook(original);

        Object[] outSlots = getSlots(menu, "getProcessingOutputSlots");
        if (outSlots.length > 0) {
            sendSetFilter(getSlotIndex(outSlots[0]), book);
        }

        if (Config.WB_FILL_INPUT_GRID.get()) {
            Object[] inSlots = getSlots(menu, "getProcessingInputSlots");
            int target = -1;
            for (int i = 0; i < inSlots.length; i++) {
                if (getSlotItem(inSlots[i]).isEmpty()) {
                    target = i;
                    break;
                }
            }
            if (target < 0 && inSlots.length > 0) target = 0;

            if (target >= 0) {
                sendSetFilter(getSlotIndex(inSlots[target]), original);
            }
        }
    }

    private static Object getGenericStackWhat(Object genericStack) {
        try {
            return genericStack.getClass().getMethod("what").invoke(genericStack);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isAEItemKey(Object what) {
        try {
            return what != null && Class.forName("appeng.api.stacks.AEItemKey").isInstance(what);
        } catch (Exception e) {
            return false;
        }
    }

    private static ItemStack toItemStack(Object itemKey) {
        try {
            Object stack = itemKey.getClass().getMethod("toStack").invoke(itemKey);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (Exception e) {
            ModLogger.debug("WrapAsBookHandler: toStack failed: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static Object[] getSlots(Object menu, String methodName) {
        try {
            Object slots = menu.getClass().getMethod(methodName).invoke(menu);
            return slots instanceof Object[] array ? array : new Object[0];
        } catch (Exception e) {
            ModLogger.debug("WrapAsBookHandler: {} failed: {}", methodName, e.getMessage());
            return new Object[0];
        }
    }

    private static ItemStack getSlotItem(Object slot) {
        try {
            Object stack = slot.getClass().getMethod("getItem").invoke(slot);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static int getSlotIndex(Object slot) {
        try {
            return slot.getClass().getField("index").getInt(slot);
        } catch (Exception e) {
            try {
                return (int) slot.getClass().getMethod("getSlotIndex").invoke(slot);
            } catch (Exception ignored) {
                return -1;
            }
        }
    }

    private static void sendSetFilter(int slotIndex, ItemStack stack) {
        if (slotIndex < 0 || stack.isEmpty()) return;
        try {
            Class<?> inventoryActionClass = Class.forName("appeng.helpers.InventoryAction");
            Object setFilter = null;
            for (Object action : inventoryActionClass.getEnumConstants()) {
                if ("SET_FILTER".equals(action.toString())) {
                    setFilter = action;
                    break;
                }
            }
            if (setFilter == null) return;

            Class<?> packetClass = Class.forName("appeng.core.sync.packets.InventoryActionPacket");
            Object packet = packetClass.getConstructor(inventoryActionClass, int.class, ItemStack.class)
                    .newInstance(setFilter, slotIndex, stack.copy());

            Class<?> networkHandlerClass = Class.forName("appeng.core.sync.network.NetworkHandler");
            Object handler = networkHandlerClass.getMethod("instance").invoke(null);
            Class<?> basePacketClass = Class.forName("appeng.core.sync.BasePacket");
            handler.getClass().getMethod("sendToServer", basePacketClass).invoke(handler, packet);
        } catch (Exception e) {
            ModLogger.debug("WrapAsBookHandler: sendSetFilter failed: {}", e.getMessage());
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
