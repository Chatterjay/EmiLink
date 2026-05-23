package org.chatterjay.emilink.network.packet.c2s;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * Opens the CraftAmount screen for any AE2 terminal (wired or wireless).
 * Uses the currently open menu's MenuLocator to open the sub-screen.
 */
public class OpenCraftAmountC2SPacket {
    private final GenericStack stack;

    public OpenCraftAmountC2SPacket(GenericStack stack) {
        this.stack = stack;
    }

    public static void encode(OpenCraftAmountC2SPacket msg, FriendlyByteBuf buf) {
        GenericStack.writeBuffer(msg.stack, buf);
    }

    public static OpenCraftAmountC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenCraftAmountC2SPacket(GenericStack.readBuffer(buf));
    }

    public static void handle(OpenCraftAmountC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> handleServer(context, msg));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, OpenCraftAmountC2SPacket msg) {
        ServerPlayer player = context.getSender();
        if (player == null || msg.stack == null || msg.stack.what() == null) return;
        if (!(player.containerMenu instanceof AEBaseMenu aeMenu)) {
            ModLogger.warn("OpenCraftAmount: player menu is not AEBaseMenu: {}",
                    player.containerMenu.getClass().getName());
            return;
        }

        AEKey what = msg.stack.what();
        if (what == null) return;

        try {
            Field locatorField = AEBaseMenu.class.getDeclaredField("locator");
            locatorField.setAccessible(true);
            MenuLocator locator = (MenuLocator) locatorField.get(aeMenu);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, what, 1);
                ModLogger.info("OpenCraftAmount: opened for {} via {}", what.getDisplayName().getString(),
                        locator.getClass().getSimpleName());
            } else {
                ModLogger.warn("OpenCraftAmount: locator is null");
            }
        } catch (Exception e) {
            ModLogger.warn("OpenCraftAmount: error accessing locator: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
