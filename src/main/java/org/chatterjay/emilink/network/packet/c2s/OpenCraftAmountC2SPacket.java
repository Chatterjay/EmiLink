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
    private final Object aeKey;

    public OpenCraftAmountC2SPacket(ItemStack stack) {
        this.aeKey = toAeItemKey(stack);
    }

    /** Used by the client for both AEItemKey and AEFluidKey without linking AE2 at class load time. */
    public OpenCraftAmountC2SPacket(Object aeKey) {
        this.aeKey = aeKey;
    }

    public static void encode(OpenCraftAmountC2SPacket msg, FriendlyByteBuf buf) {
        writeAeKey(buf, msg.aeKey);
    }

    public static OpenCraftAmountC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenCraftAmountC2SPacket(readAeKey(buf));
    }

    public static void handle(OpenCraftAmountC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> handleServer(context, msg));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, OpenCraftAmountC2SPacket msg) {
        ServerPlayer player = context.getSender();
        if (player == null || msg.aeKey == null || !AE2Proxy.isLoaded()) return;

        try {
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(player.containerMenu)) {
                ModLogger.warn("OpenCraftAmount: player menu is not AEBaseMenu: {}",
                        player.containerMenu.getClass().getName());
                return;
            }

            Field locatorField = aeBaseMenuClass.getDeclaredField("locator");
            locatorField.setAccessible(true);
            Object locator = locatorField.get(player.containerMenu);
            if (locator != null) {
                Class<?> craftAmountMenuClass = Class.forName("appeng.menu.me.crafting.CraftAmountMenu");
                Class<?> menuLocatorClass = Class.forName("appeng.menu.locator.MenuLocator");
                Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                craftAmountMenuClass.getMethod("open", ServerPlayer.class, menuLocatorClass, aeKeyClass, int.class)
                        .invoke(null, player, locator, msg.aeKey, getInitialAmount(msg.aeKey));
                ModLogger.debug("OpenCraftAmount: opened for {} via {}",
                        describeAeKey(msg.aeKey), locator.getClass().getSimpleName());
            } else {
                ModLogger.warn("OpenCraftAmount: locator is null");
            }
        } catch (Exception e) {
            ModLogger.warn("OpenCraftAmount: error accessing locator: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static int getInitialAmount(Object aeKey) {
        try {
            return Math.max(1, (int) aeKey.getClass().getMethod("getAmountPerUnit").invoke(aeKey));
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private static Object toAeItemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            return Class.forName("appeng.api.stacks.AEItemKey")
                    .getMethod("of", ItemStack.class).invoke(null, stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeAeKey(FriendlyByteBuf buf, Object aeKey) {
        if (aeKey == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        try {
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            aeKeyClass.getMethod("writeKey", FriendlyByteBuf.class, aeKeyClass)
                    .invoke(null, buf, aeKey);
        } catch (Throwable error) {
            ModLogger.warn("OpenCraftAmount: failed to encode AE key: {}", error.toString());
        }
    }

    private static Object readAeKey(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        try {
            return Class.forName("appeng.api.stacks.AEKey")
                    .getMethod("readKey", FriendlyByteBuf.class).invoke(null, buf);
        } catch (Throwable error) {
            ModLogger.warn("OpenCraftAmount: failed to decode AE key: {}", error.toString());
            return null;
        }
    }

    private static String describeAeKey(Object aeKey) {
        try {
            Object name = aeKey.getClass().getMethod("getDisplayName").invoke(aeKey);
            return name == null ? aeKey.toString() : name.toString();
        } catch (Throwable ignored) {
            return aeKey.toString();
        }
    }
}
