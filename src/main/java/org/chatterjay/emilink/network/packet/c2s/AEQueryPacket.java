package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.network.PacketRateLimiter;
import org.chatterjay.emilink.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public class AEQueryPacket {
    private final ItemStack stack;

    public AEQueryPacket(ItemStack stack) {
        this.stack = stack;
    }

    public static void encode(AEQueryPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static AEQueryPacket decode(FriendlyByteBuf buf) {
        return new AEQueryPacket(buf.readItem());
    }

    public static void handle(AEQueryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        if (!PacketRateLimiter.allowDebugPacket()) {
            ModLogger.debug("AEQuery rate limited (dropped)");
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> msg.handleInServer(context));
        context.setPacketHandled(true);
    }

    private void handleInServer(NetworkEvent.Context context) {
        Player player = context.getSender();
        if (player == null || stack == null || stack.isEmpty()) return;
        if (!AE2Proxy.isLoaded()) {
            sendResponse(player, 0, false);
            return;
        }

        long count = 0;
        boolean craftable = false;

        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) {
                sendResponse(player, count, craftable);
                return;
            }

            Object grid = resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) {
                sendResponse(player, count, craftable);
                return;
            }

            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
            if (aeKey == null) {
                sendResponse(player, count, craftable);
                return;
            }

            count = queryItemCount(grid, aeKey);
            craftable = queryCraftability(grid, aeKey);
        } catch (Exception e) {
            // ignore
        }

        sendResponse(player, count, craftable);
    }

    static long queryItemCount(Object grid, Object aeKey) {
        long result = 0;

        try {
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid", "getService");
            if (storageSvc != null) {
                Object inventory = callMethodOnBestMatch(storageSvc, "getInventory");
                if (inventory != null) {
                    Object available = callMethodOnBestMatch(inventory, "getAvailableStacks");
                    if (available != null) {
                        Class<?> kc = Class.forName("appeng.api.stacks.KeyCounter");
                        Object raw = kc.getMethod("get", Class.forName("appeng.api.stacks.AEKey")).invoke(available, aeKey);
                        if (raw instanceof Number n) {
                            result = n.longValue();
                            if (result > 0) return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        try {
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc != null) {
                Object cached = callMethodOnBestMatch(storageSvc, "getCachedAvailableStacks", "getAvailableStacks");
                if (cached != null) {
                    Class<?> kc = Class.forName("appeng.api.stacks.KeyCounter");
                    Object raw = kc.getMethod("get", Class.forName("appeng.api.stacks.AEKey")).invoke(cached, aeKey);
                    if (raw instanceof Number n) {
                        result = n.longValue();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return result;
    }

    static boolean queryCraftability(Object grid, Object aeKey) {
        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid", "getService");
            if (craftingSvc != null) {
                Object raw = tryCallMethod(craftingSvc.getClass(), craftingSvc, "isCraftable", aeKey);
                if (raw instanceof Boolean b) return b;
            }
        } catch (Exception e) {
            // ignore
        }

        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid");
            if (craftingSvc != null) {
                for (var iface : getAllInterfaces(craftingSvc.getClass())) {
                    if (!iface.getName().contains("Crafting")) continue;
                    for (var paramCls = aeKey.getClass(); paramCls != null; paramCls = paramCls.getSuperclass()) {
                        try {
                            Object raw = iface.getMethod("isCraftable", paramCls).invoke(craftingSvc, aeKey);
                            if (raw instanceof Boolean b) return b;
                        } catch (NoSuchMethodException e) { /* try next */ }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return false;
    }

    private void sendResponse(Player player, long count, boolean craftable) {
        if (player instanceof ServerPlayer sp) {
            NetworkHandler.sendToPlayer(sp, new AEQueryResponsePacket(stack, count, craftable));
        }
    }

    private static Object callMethodOnBestMatch(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Exception e) {
                // try next
            }
        }
        return null;
    }

    private static Object tryCallMethod(Class<?> clazz, Object target, String methodName, Object arg) {
        try {
            return clazz.getMethod(methodName, arg.getClass()).invoke(target, arg);
        } catch (Exception e) { /* fall through */ }

        for (var cls = arg.getClass().getSuperclass(); cls != null; cls = cls.getSuperclass()) {
            try {
                return clazz.getMethod(methodName, cls).invoke(target, arg);
            } catch (Exception e2) { /* continue */ }
        }

        for (var iface : getAllInterfaces(arg.getClass())) {
            try {
                return clazz.getMethod(methodName, iface).invoke(target, arg);
            } catch (Exception e2) { /* try next */ }
        }

        try {
            return clazz.getMethod(methodName, Object.class).invoke(target, arg);
        } catch (Exception e2) { /* give up */ }

        return null;
    }

    static Object resolveGrid(Class<?> menuClass, Object menu) throws Exception {
        try {
            var getActionHost = menuClass.getDeclaredMethod("getActionHost");
            getActionHost.setAccessible(true);
            Object actionHost = getActionHost.invoke(menu);
            if (actionHost != null) {
                for (var iface : getAllInterfaces(actionHost.getClass())) {
                    if (iface.getName().contains("ActionHost") || iface.getName().contains("IActionHost")) {
                        for (var m : iface.getMethods()) {
                            if (m.getParameterCount() != 0) continue;
                            String retName = m.getReturnType().getName();
                            if (retName.contains("Grid") || retName.contains("Node")) {
                                try {
                                    Object result = m.invoke(actionHost);
                                    if (result != null) {
                                        try {
                                            Object grid = result.getClass().getMethod("getGrid").invoke(result);
                                            if (grid != null) return grid;
                                        } catch (NoSuchMethodException e) { /* result may be grid itself */ }
                                    }
                                } catch (Exception e) { /* try next */ }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        try {
            Object actionSource = menuClass.getMethod("getActionSource").invoke(menu);
            if (actionSource != null) {
                var getMachine = actionSource.getClass().getMethod("getMachineSource");
                Object machineOpt = getMachine.invoke(actionSource);
                if (machineOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object machine = opt.get();
                    for (var m : machine.getClass().getMethods()) {
                        if (m.getParameterCount() != 0) continue;
                        String mn = m.getName();
                        if (!mn.contains("Grid") && !mn.contains("grid") && !mn.contains("Node")) continue;
                        try {
                            Object result = m.invoke(machine);
                            if (result == null) continue;
                            try {
                                Object grid = result.getClass().getMethod("getGrid").invoke(result);
                                if (grid != null) return grid;
                            } catch (NoSuchMethodException e2) {
                                if (m.getReturnType().getName().contains("IGrid")) return result;
                            }
                        } catch (Exception e2) { /* try next */ }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        try {
            Object host = menuClass.getMethod("getBlockEntity").invoke(menu);
            if (host != null) {
                for (var iface : getAllInterfaces(host.getClass())) {
                    if (!iface.getName().contains("Grid") && !iface.getName().contains("Node")) continue;
                    try {
                        Method getNode = iface.getMethod("getGridNode");
                        Object gridNode = getNode.invoke(host);
                        if (gridNode != null) {
                            Object grid = gridNode.getClass().getMethod("getGrid").invoke(gridNode);
                            if (grid != null) return grid;
                        }
                    } catch (NoSuchMethodException e) { /* try next */ }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        var interfaces = new LinkedHashSet<Class<?>>();
        while (clazz != null) {
            for (var iface : clazz.getInterfaces()) {
                collectInterfaces(iface, interfaces);
            }
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> iface, Set<Class<?>> acc) {
        if (acc.add(iface)) {
            for (var parent : iface.getInterfaces()) {
                collectInterfaces(parent, acc);
            }
        }
    }
}
