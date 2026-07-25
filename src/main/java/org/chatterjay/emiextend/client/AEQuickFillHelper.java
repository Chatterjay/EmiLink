package org.chatterjay.emiextend.client;

import appeng.api.stacks.GenericStack;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.slot.FakeSlot;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.util.ModLogger;

final class AEQuickFillHelper {
    private AEQuickFillHelper() {
    }

    static boolean tryFillFakeSlot(Slot slot, EmiStack emiStack) {
        if (!(slot instanceof FakeSlot fakeSlot)) {
            return false;
        }

        ItemStack itemStack = emiStack.getItemStack();
        if (itemStack.isEmpty()) {
            var genericStack = EmiStackHelper.toGenericStack(emiStack);
            if (genericStack == null) {
                return false;
            }
            itemStack = GenericStack.wrapInItemStack(genericStack);
            if (itemStack.isEmpty()) {
                return false;
            }
        }

        PacketDistributor.sendToServer(
                new InventoryActionPacket(InventoryAction.SET_FILTER, fakeSlot.index, itemStack.copy()));
        ModLogger.debug("QuickFillSlot: set AE fake slot {} with {}", fakeSlot.index, emiStack.getId());
        return true;
    }
}
