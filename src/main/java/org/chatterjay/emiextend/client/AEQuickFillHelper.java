package org.chatterjay.emiextend.client;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class AEQuickFillHelper {
    private static Boolean available;
    private static Class<?> fakeSlotClass;
    private static Field fakeSlotIndexField;
    private static Method fakeSlotIndexMethod;
    private static Constructor<?> packetConstructor;
    private static Object setFilterAction;
    private static Method sendToServerMethod;

    private AEQuickFillHelper() {
    }

    static boolean tryFillFakeSlot(Slot slot, EmiStack emiStack) {
        if (!initialize() || !fakeSlotClass.isInstance(slot)) return false;
        try {
            ItemStack itemStack = toFilterStack(emiStack);
            if (itemStack.isEmpty()) return false;

            int slotIndex = fakeSlotIndexField != null
                    ? fakeSlotIndexField.getInt(slot)
                    : (int) fakeSlotIndexMethod.invoke(slot);
            Object packet = packetConstructor.newInstance(setFilterAction, slotIndex, itemStack.copy());
            sendToServerMethod.invoke(null, packet);
            ModLogger.debug("QuickFillSlot: set AE fake slot {} with {}", slotIndex, emiStack.getId());
            return true;
        } catch (ReflectiveOperationException | ClassCastException error) {
            ModLogger.debug("QuickFillSlot: AE2 filter update failed: {}", error.toString());
            return false;
        }
    }

    private static boolean initialize() {
        if (available != null) return available;
        if (!AE2Proxy.isLoaded()) {
            available = false;
            return false;
        }
        try {
            fakeSlotClass = Class.forName("appeng.menu.slot.FakeSlot");
            try {
                fakeSlotIndexField = fakeSlotClass.getField("index");
            } catch (NoSuchFieldException ignored) {
                fakeSlotIndexMethod = fakeSlotClass.getMethod("getSlotIndex");
            }

            Class<?> actionClass = Class.forName("appeng.helpers.InventoryAction");
            for (Object action : actionClass.getEnumConstants()) {
                if ("SET_FILTER".equals(action.toString())) {
                    setFilterAction = action;
                    break;
                }
            }
            Class<?> packetClass = Class.forName("appeng.core.network.serverbound.InventoryActionPacket");
            packetConstructor = packetClass.getConstructor(actionClass, int.class, ItemStack.class);
            Class<?> distributorClass = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            for (Method method : distributorClass.getMethods()) {
                if ("sendToServer".equals(method.getName()) && method.getParameterCount() == 1) {
                    sendToServerMethod = method;
                    break;
                }
            }
            available = setFilterAction != null && sendToServerMethod != null;
        } catch (ReflectiveOperationException error) {
            available = false;
            ModLogger.debug("QuickFillSlot: AE2 reflection unavailable: {}", error.toString());
        }
        return available;
    }

    private static ItemStack toFilterStack(EmiStack emiStack) throws ReflectiveOperationException {
        ItemStack itemStack = emiStack.getItemStack();
        if (!itemStack.isEmpty()) return itemStack;

        Class<?> helperClass = Class.forName("appeng.integration.modules.emi.EmiStackHelper");
        Method toGenericStack = helperClass.getMethod("toGenericStack", EmiStack.class);
        Object genericStack = toGenericStack.invoke(null, emiStack);
        if (genericStack == null) return ItemStack.EMPTY;

        Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
        Object wrapped = genericStackClass.getMethod("wrapInItemStack", genericStackClass)
                .invoke(null, genericStack);
        return wrapped instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }
}
