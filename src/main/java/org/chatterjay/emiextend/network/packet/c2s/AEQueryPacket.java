package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;

public record AEQueryPacket(ItemStack stack) implements CustomPacketPayload {
    public static final Type<AEQueryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEQueryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    AEQueryPacket::stack,
                    AEQueryPacket::new
            );

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
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

            // ---- Query item count (try multiple API patterns) ----
            count = queryItemCount(grid, aeKey);

            // ---- Query craftability ----
            craftable = queryCraftability(grid, aeKey);

        } catch (Exception e) {
            ModLogger.debug("AEQuery: handler error ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }

        sendResponse(player, count, craftable);
    }

    /** Try multiple patterns to get item count from the grid. */
    private static long queryItemCount(Object grid, Object aeKey) {
        var errors = new ArrayList<String>();
        long result = 0;

        // Pattern A: getStorageService() → getInventory() → getAvailableStacks() → KeyCounter.get()
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
                            ModLogger.debug("AEQuery: count via Pattern A = {}", result);
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Pattern A: " + e.getMessage());
        }

        // Pattern B: getStorageService() → getCachedAvailableStacks()
        try {
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc != null) {
                Object cached = callMethodOnBestMatch(storageSvc, "getCachedAvailableStacks", "getAvailableStacks");
                if (cached != null) {
                    Class<?> kc = Class.forName("appeng.api.stacks.KeyCounter");
                    Object raw = tryCallMethod(kc, cached, "get", aeKey);
                    if (raw instanceof Number n) {
                        result = n.longValue();
                        ModLogger.debug("AEQuery: count via Pattern B = {}", result);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Pattern B: " + e.getMessage());
        }

        // Log all methods on grid for debugging
        if (!errors.isEmpty()) {
            ModLogger.debug("AEQuery: count query failed: {}", errors);
        }
        return 0;
    }

    /** Try multiple patterns to check craftability from the grid. */
    private static boolean queryCraftability(Object grid, Object aeKey) {
        var errors = new ArrayList<String>();
        boolean result = false;

        // Pattern A: getCraftingService().isCraftable(AEKey)
        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid", "getService");
            if (craftingSvc != null) {
                Object raw = tryCallMethod(craftingSvc.getClass(), craftingSvc, "isCraftable", aeKey);
                if (raw instanceof Boolean b) {
                    result = b;
                    ModLogger.debug("AEQuery: craftable via Pattern A = {}", result);
                    return result;
                }
            }
        } catch (Exception e) {
            errors.add("Pattern A: " + e.getMessage());
        }

        // Pattern B: craftingService.isCraftable(AEKey) via interface method lookup
        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid");
            if (craftingSvc != null) {
                for (var iface : getAllInterfaces(craftingSvc.getClass())) {
                    if (!iface.getName().contains("Crafting")) continue;
                    // Try exact param class, then superclasses
                    for (var paramCls = aeKey.getClass(); paramCls != null; paramCls = paramCls.getSuperclass()) {
                        try {
                            Object raw = iface.getMethod("isCraftable", paramCls).invoke(craftingSvc, aeKey);
                            if (raw instanceof Boolean b) {
                                result = b;
                                ModLogger.debug("AEQuery: craftable via Pattern B = {}", result);
                                return result;
                            }
                        } catch (NoSuchMethodException e) { /* try next param class */ }
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Pattern B: " + e.getMessage());
        }

        ModLogger.debug("AEQuery: craftable errors: {}", errors);
        return false;
    }

    /** Try calling methods by name, returning first non-null result. */
    private static Object callMethodOnBestMatch(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (NoSuchMethodException e) {
                // try next
            } catch (Exception e) {
                ModLogger.debug("AEQuery: {}.{}() failed: {}", target.getClass().getSimpleName(), name, e.getMessage());
            }
        }
        return null;
    }

    /** Try calling a method on a target with fallback parameter types. */
    private static Object tryCallMethod(Class<?> clazz, Object target, String methodName, Object arg) {
        // Exact match
        try {
            return clazz.getMethod(methodName, arg.getClass()).invoke(target, arg);
        } catch (NoSuchMethodException e) { /* fall through */ }
          catch (Exception e) {
            ModLogger.debug("AEQuery: {}.{}({}) exact failed: {}", clazz.getSimpleName(), methodName, arg.getClass().getSimpleName(), e.getMessage());
        }

        // Try superclass chain (AEItemKey → AEKey → Object)
        for (var cls = arg.getClass().getSuperclass(); cls != null; cls = cls.getSuperclass()) {
            try {
                return clazz.getMethod(methodName, cls).invoke(target, arg);
            } catch (NoSuchMethodException e2) { /* continue up */ }
              catch (Exception e2) { /* continue up */ }
        }

        // Try with each interface of arg's class
        for (var iface : getAllInterfaces(arg.getClass())) {
            try {
                return clazz.getMethod(methodName, iface).invoke(target, arg);
            } catch (NoSuchMethodException e2) { /* try next */ }
              catch (Exception e2) { /* try next */ }
        }

        // Last resort: Object
        try {
            return clazz.getMethod(methodName, Object.class).invoke(target, arg);
        } catch (Exception e2) { /* give up */ }

        return null;
    }

    private void sendResponse(Player player, long count, boolean craftable) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new AEQueryResponsePacket(stack, count, craftable));
        }
    }

    /**
     * Resolve IGrid from AEBaseMenu via reflection.
     * Primary path: getActionHost() → IActionHost.getGridNode() → IGridNode.getGrid()
     */
    private static Object resolveGrid(Class<?> menuClass, Object menu) throws Exception {
        var errors = new ArrayList<String>();

        // Path 1: getActionHost() → IActionHost.getGridNode() → getGrid()
        try {
            var getActionHost = menuClass.getDeclaredMethod("getActionHost");
            getActionHost.setAccessible(true);
            Object actionHost = getActionHost.invoke(menu);
            if (actionHost != null) {
                ModLogger.debug("AEQuery: actionHost = {}", actionHost.getClass().getName());
                // Search all interfaces in the class hierarchy for IActionHost
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
                                } catch (Exception e) { /* try next method */ }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            errors.add("actionHost: " + e.getMessage());
        }

        // Path 2: getActionSource() → getMachineSource() → grid
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
            errors.add("actionSource: " + e.getMessage());
        }

        // Path 3: getBlockEntity() → interfaces → gridNode
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
            errors.add("blockEntity: " + e.getMessage());
        }

        ModLogger.debug("AEQuery: all paths failed: {}", errors);
        return null;
    }

    /** Get all interfaces implemented by a class, including superinterfaces. */
    private static java.util.Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        var interfaces = new LinkedHashSet<Class<?>>();
        while (clazz != null) {
            for (var iface : clazz.getInterfaces()) {
                collectInterfaces(iface, interfaces);
            }
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> iface, java.util.Set<Class<?>> acc) {
        if (acc.add(iface)) {
            for (var parent : iface.getInterfaces()) {
                collectInterfaces(parent, acc);
            }
        }
    }

    public static void handle(final AEQueryPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
