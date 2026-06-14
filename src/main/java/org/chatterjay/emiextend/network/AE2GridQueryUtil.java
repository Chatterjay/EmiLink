package org.chatterjay.emiextend.network;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared AE2 grid query logic used by both AEQueryPacket and AEBatchQueryPacket.
 * All methods are static and stateless — safe for concurrent usage.
 */
public final class AE2GridQueryUtil {

    private AE2GridQueryUtil() {}

    /**
     * Resolve IGrid from AEBaseMenu via reflection.
     * Primary path: getActionHost() → IActionHost.getGridNode() → IGridNode.getGrid()
     */
    public static Object resolveGrid(Class<?> menuClass, Object menu) throws Exception {
        // Path 1: getActionHost() → IActionHost.getGridNode() → getGrid()
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
        } catch (Exception e) { /* ignore */ }

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
        } catch (Exception e) { /* ignore */ }

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
        } catch (Exception e) { /* ignore */ }

        return null;
    }

    /**
     * Query item count from the grid via multiple API patterns.
     */
    public static long queryItemCount(Object grid, Object aeKey) {
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
                            if (result > 0) return result;
                        }
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }

        // Pattern B: getStorageService() → getCachedAvailableStacks()
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
        } catch (Exception e) { /* ignore */ }

        return result;
    }

    /**
     * Check craftability from the grid via multiple API patterns.
     */
    public static boolean queryCraftability(Object grid, Object aeKey) {
        // Pattern A: getCraftingService().isCraftable(AEKey)
        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid", "getService");
            if (craftingSvc != null) {
                Object raw = tryCallMethod(craftingSvc.getClass(), craftingSvc, "isCraftable", aeKey);
                if (raw instanceof Boolean b) return b;
            }
        } catch (Exception e) { /* ignore */ }

        // Pattern B: via interface method lookup on crafting service
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
        } catch (Exception e) { /* ignore */ }

        return false;
    }

    /** Try calling methods by name, returning first non-null result. */
    public static Object callMethodOnBestMatch(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Exception e) { /* try next */ }
        }
        return null;
    }

    /** Try calling a method on a target with fallback parameter types. */
    public static Object tryCallMethod(Class<?> clazz, Object target, String methodName, Object arg) {
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

    /** Get all interfaces implemented by a class, including superinterfaces. */
    public static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
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
