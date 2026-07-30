package org.chatterjay.emilink.client.handler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.BasePacket;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.concurrent.atomic.AtomicReference;

public final class WrapAsBookHandler {
    private static final AtomicBoolean wrapRequested = new AtomicBoolean(false);
    private static final AtomicReference<ItemStack> lastRecipeOutput = new AtomicReference<>(ItemStack.EMPTY);

    private WrapAsBookHandler() {}

    public static boolean isActive() {
        return wrapRequested.get();
    }

    public static void toggle() {
        wrapRequested.set(!wrapRequested.get());
        ModLogger.debug("WB_FLOW Toggle: active={} cached={}", wrapRequested.get(), describeStack(lastRecipeOutput.get()));
    }

    public static void clear() {
        wrapRequested.set(false);
        ModLogger.debug("WB_FLOW Clear: active=false cached={}", describeStack(lastRecipeOutput.get()));
    }

    public static void rememberRecipeOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            ModLogger.debug("WB_FLOW RecipeCache: ignored empty stack stack={}", describeStack(stack));
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        lastRecipeOutput.set(copy);
        ModLogger.debug("WB_FLOW RecipeCache: stored {}", describeStack(copy));
    }

    public static void applyWrap(PatternEncodingTermMenu menu, List<List<GenericStack>> inputs, List<GenericStack> outputs) {
        boolean requested = wrapRequested.get();
        ModLogger.debug("WB_FLOW Apply: enter requested={} menu={} inputRows={} outputs={} configWrap={} configFillInput={} cached={}",
                requested, menu == null ? "null" : menu.getClass().getName(), inputs == null ? "null" : inputs.size(),
                outputs == null ? "null" : outputs.size(), safeConfigWrap(), safeConfigFillInput(), describeStack(lastRecipeOutput.get()));
        if (menu == null) {
            ModLogger.debug("WB_FLOW Apply: abort because menu is null");
            return;
        }

        FakeSlot[] outSlots = menu.getProcessingOutputSlots();
        debugDumpState("apply-before-consume", menu, outputs);
        if (!wrapRequested.getAndSet(false)) {
            ModLogger.debug("WB_FLOW Apply: no pending request, skip");
            return;
        }
        ModLogger.debug("WB_FLOW Apply: request consumed, activeNow=false");

        ItemStack original = resolveOriginalOutput(inputs, outputs, outSlots);
        if (original.isEmpty()) {
            ModLogger.debug("WB_FLOW Apply: abort no usable output stack");
            debugDumpState("apply-empty-output", menu, outputs);
            return;
        }
        original = original.copy();
        original.setCount(1);
        ModLogger.debug("WB_FLOW Apply: original resolved {}", describeStack(original));
        ItemStack book = createWrittenBook(original);
        ModLogger.debug("WB_FLOW Apply: written book prepared {} tag={}", describeStack(book), book.getTag());

        if (outSlots != null && outSlots.length > 0) {
            ModLogger.debug("WB_FLOW Apply: writing book to first output slot idx={} before={}",
                    outSlots[0].index, describeStack(outSlots[0].getItem()));
            sendSetFilter(outSlots[0].index, book);
        } else {
            ModLogger.debug("WB_FLOW Apply: no processing output slots available");
        }

        if (Config.WB_FILL_INPUT_GRID.get()) {
            FakeSlot[] inSlots = menu.getProcessingInputSlots();
            int target = -1;
            if (inSlots != null) {
                for (int i = 0; i < inSlots.length; i++) {
                    if (inSlots[i].getItem().isEmpty()) {
                        target = i;
                        break;
                    }
                }
                if (target < 0 && inSlots.length > 0) target = 0;
            }

            if (target >= 0) {
                ModLogger.debug("WB_FLOW Apply: writing original to input slot local={} idx={} before={}",
                        target, inSlots[target].index, describeStack(inSlots[target].getItem()));
                sendSetFilter(inSlots[target].index, original);
            } else {
                ModLogger.debug("WB_FLOW Apply: input fill enabled but no input slots available");
            }
        } else {
            ModLogger.debug("WB_FLOW Apply: input grid fill disabled by config");
        }
        debugDumpState("apply-after-send", menu, outputs);
    }

    private static ItemStack resolveOriginalOutput(List<List<GenericStack>> inputs, List<GenericStack> outputs, FakeSlot[] outSlots) {
        ModLogger.debug("WB_FLOW Resolve: start outputs={} outSlots={} cached={}",
                outputs == null ? "null" : outputs.size(), outSlots == null ? "null" : outSlots.length,
                describeStack(lastRecipeOutput.get()));
        if (outputs != null && !outputs.isEmpty()) {
            for (int i = 0; i < outputs.size(); i++) {
                GenericStack output = outputs.get(i);
                ModLogger.debug("WB_FLOW Resolve: output[{}]={}", i, describeGenericStack(output));
                if (output != null && output.what() instanceof AEItemKey itemKey) {
                    ItemStack stack = itemKey.toStack();
                    ModLogger.debug("WB_FLOW Resolve: selected AE output[{}] {}", i, describeStack(stack));
                    return stack;
                }
            }
            ModLogger.debug("WB_FLOW Resolve: no AEItemKey found in outputs");
        } else {
            ModLogger.debug("WB_FLOW Resolve: outputs are empty, checking output slots");
        }

        if (outSlots != null) {
            for (int i = 0; i < outSlots.length; i++) {
                ItemStack stack = outSlots[i].getItem();
                ModLogger.debug("WB_FLOW Resolve: outSlot[{}] idx={} stack={}", i, outSlots[i].index, describeStack(stack));
                if (!stack.isEmpty()) {
                    ModLogger.debug("WB_FLOW Resolve: selected output slot[{}] {}", i, describeStack(stack));
                    return stack;
                }
            }
        }

        ItemStack targetFromInputs = resolveTargetFromInputs(inputs);
        if (!targetFromInputs.isEmpty()) {
            ModLogger.debug("WB_FLOW Resolve: selected target from input row 0 {}", describeStack(targetFromInputs));
            return targetFromInputs;
        }

        ItemStack cached = lastRecipeOutput.get();
        if (cached != null && !cached.isEmpty()) {
            ModLogger.debug("WB_FLOW Resolve: selected cached recipe output {}", describeStack(cached));
            return cached.copy();
        }

        ModLogger.debug("WB_FLOW Resolve: failed, no output source available");
        return ItemStack.EMPTY;
    }

    private static ItemStack resolveTargetFromInputs(List<List<GenericStack>> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            ModLogger.debug("WB_FLOW ResolveInputTarget: inputs are empty");
            return ItemStack.EMPTY;
        }
        List<GenericStack> firstRow = inputs.get(0);
        if (firstRow == null || firstRow.isEmpty()) {
            ModLogger.debug("WB_FLOW ResolveInputTarget: input row 0 is empty");
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < firstRow.size(); i++) {
            GenericStack candidate = firstRow.get(i);
            ModLogger.debug("WB_FLOW ResolveInputTarget: row0[{}]={}", i, describeGenericStack(candidate));
            if (candidate != null && candidate.what() instanceof AEItemKey itemKey) {
                ItemStack stack = itemKey.toStack();
                if (!stack.isEmpty()) {
                    stack = stack.copy();
                    stack.setCount(1);
                    return stack;
                }
            }
        }
        ModLogger.debug("WB_FLOW ResolveInputTarget: no item target in input row 0");
        return ItemStack.EMPTY;
    }

    private static void sendSetFilter(int slotIndex, ItemStack stack) {
        if (slotIndex < 0 || stack.isEmpty()) {
            ModLogger.debug("WB_FLOW Packet: skip SET_FILTER slot={} stack={}", slotIndex, describeStack(stack));
            return;
        }
        try {
            BasePacket packet = new InventoryActionPacket(InventoryAction.SET_FILTER, slotIndex, stack.copy());
            NetworkHandler handler = NetworkHandler.instance();
            ModLogger.debug("WB_FLOW Packet: send SET_FILTER slot={} stack={} packet={} handler={}",
                    slotIndex, describeStack(stack), packet.getClass().getName(), handler == null ? "null" : handler.getClass().getName());
            handler.sendToServer(packet);
            ModLogger.debug("WB_FLOW Packet: sent SET_FILTER slot={} stack={}", slotIndex, describeStack(stack));
        } catch (Exception e) {
            ModLogger.debug("WB_FLOW Packet: sendSetFilter failed slot={} stack={} error={}:{}",
                    slotIndex, describeStack(stack), e.getClass().getName(), e.getMessage());
            ModLogger.debug("WB_FLOW Packet: sendSetFilter stacktrace", e);
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
        ModLogger.debug("WB_FLOW Book: created original={} title='{}' author='{}' pages={} tag={}",
                describeStack(original), title, author, pages.size(), tag);
        return book;
    }

    public static void debugDumpState(String stage, PatternEncodingTermMenu menu, List<GenericStack> outputs) {
        try {
            ModLogger.debug("WB_FLOW State[{}]: active={} menu={} outputs={} cached={} configWrap={} configFillInput={}",
                    stage, wrapRequested.get(), menu == null ? "null" : menu.getClass().getName(),
                    outputs == null ? "null" : outputs.size(), describeStack(lastRecipeOutput.get()),
                    safeConfigWrap(), safeConfigFillInput());
            if (outputs != null) {
                for (int i = 0; i < outputs.size(); i++) {
                    ModLogger.debug("WB_FLOW State[{}]: output[{}]={}", stage, i, describeGenericStack(outputs.get(i)));
                }
            }
            if (menu != null) {
                FakeSlot[] outSlots = menu.getProcessingOutputSlots();
                ModLogger.debug("WB_FLOW State[{}]: outputSlots={}", stage, outSlots == null ? "null" : outSlots.length);
                if (outSlots != null) {
                    for (int i = 0; i < outSlots.length; i++) {
                        ModLogger.debug("WB_FLOW State[{}]: outSlot[{}] idx={} stack={}",
                                stage, i, outSlots[i].index, describeStack(outSlots[i].getItem()));
                    }
                }
                FakeSlot[] inSlots = menu.getProcessingInputSlots();
                ModLogger.debug("WB_FLOW State[{}]: inputSlots={}", stage, inSlots == null ? "null" : inSlots.length);
                if (inSlots != null) {
                    for (int i = 0; i < inSlots.length; i++) {
                        ModLogger.debug("WB_FLOW State[{}]: inSlot[{}] idx={} stack={}",
                                stage, i, inSlots[i].index, describeStack(inSlots[i].getItem()));
                    }
                }
            }
        } catch (Throwable e) {
            ModLogger.debug("WB_FLOW State[{}]: dump failed {}:{}", stage, e.getClass().getName(), e.getMessage());
            ModLogger.debug("WB_FLOW State[{}]: dump stacktrace", stage, e);
        }
    }

    private static boolean safeConfigWrap() {
        try {
            return Config.ENABLE_WRAP_BOOK.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean safeConfigFillInput() {
        try {
            return Config.WB_FILL_INPUT_GRID.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String describeGenericStack(GenericStack stack) {
        if (stack == null) return "null";
        Object what = stack.what();
        String whatClass = what == null ? "null" : what.getClass().getName();
        String item = "not-item";
        if (what instanceof AEItemKey itemKey) {
            item = describeStack(itemKey.toStack());
        }
        return "amount=" + stack.amount() + ", whatClass=" + whatClass + ", item=" + item;
    }

    private static String describeStack(ItemStack stack) {
        if (stack == null) return "null";
        if (stack.isEmpty()) return "empty";
        String id;
        try {
            id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable e) {
            id = stack.getItem().getClass().getName();
        }
        String name;
        try {
            name = stack.getHoverName().getString();
        } catch (Throwable e) {
            name = "<name-error:" + e.getClass().getSimpleName() + ">";
        }
        return id + " x" + stack.getCount() + " name='" + name + "' tag=" + stack.getTag();
    }
}
