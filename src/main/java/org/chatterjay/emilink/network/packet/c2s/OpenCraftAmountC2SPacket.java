package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * Opens the CraftAmount screen for any AE2 terminal (wired or wireless).
 * Uses the currently open menu's MenuLocator to open the sub-screen.
 */
public class OpenCraftAmountC2SPacket {
    private final ItemStack stack;

    public OpenCraftAmountC2SPacket(ItemStack stack) {
        this.stack = stack;
    }

    public static void encode(OpenCraftAmountC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static OpenCraftAmountC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenCraftAmountC2SPacket(buf.readItem());
    }

    public static void handle(OpenCraftAmountC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> handleServer(context, msg));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, OpenCraftAmountC2SPacket msg) {
        ServerPlayer player = context.getSender();
        if (player == null || msg.stack == null || msg.stack.isEmpty() || !AE2Proxy.isLoaded()) return;

        try {
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(player.containerMenu)) {
                ModLogger.warn("OpenCraftAmount: player menu is not AEBaseMenu: {}",
                        player.containerMenu.getClass().getName());
                return;
            }

            Object what = Class.forName("appeng.api.stacks.AEItemKey")
                    .getMethod("of", ItemStack.class)
                    .invoke(null, msg.stack);
            if (what == null) return;

            Field locatorField = aeBaseMenuClass.getDeclaredField("locator");
            locatorField.setAccessible(true);
            Object locator = locatorField.get(player.containerMenu);
            if (locator != null) {
                Class<?> craftAmountMenuClass = Class.forName("appeng.menu.me.crafting.CraftAmountMenu");
                Class<?> menuLocatorClass = Class.forName("appeng.menu.locator.MenuLocator");
                Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                craftAmountMenuClass.getMethod("open", ServerPlayer.class, menuLocatorClass, aeKeyClass, int.class)
                        .invoke(null, player, locator, what, 1);
                ModLogger.info("OpenCraftAmount: opened for {} via {}",
                        msg.stack.getHoverName().getString(), locator.getClass().getSimpleName());
            } else {
                ModLogger.warn("OpenCraftAmount: locator is null");
            }
        } catch (Exception e) {
            ModLogger.warn("OpenCraftAmount: error accessing locator: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
