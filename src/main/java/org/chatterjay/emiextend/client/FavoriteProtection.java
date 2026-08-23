package org.chatterjay.emiextend.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import dev.emi.emi.api.stack.EmiIngredient;
import net.neoforged.fml.loading.FMLPaths;
import org.chatterjay.emiextend.util.ModLogger;

/** Persists the EMI favorites protected by EmiLink's sidebar selection. */
public final class FavoriteProtection {
    private static final String FILE_NAME = "emilink_favorites_protected.json";
    private static final Set<String> PROTECTED = new HashSet<>();
    private static boolean loaded = false;

    private FavoriteProtection() {}

    public static boolean isProtected(EmiIngredient stack) {
        if (stack == null || stack.isEmpty()) return false;
        load();
        String id = getId(stack);
        return id != null && PROTECTED.contains(id);
    }

    public static void protect(EmiIngredient stack) {
        String id = getId(stack);
        if (id == null) return;
        load();
        if (PROTECTED.add(id)) {
            save();
            ModLogger.debug("FavoriteProtection: protected {}", id);
        }
    }

    public static void unprotect(EmiIngredient stack) {
        String id = getId(stack);
        if (id == null) return;
        load();
        if (PROTECTED.remove(id)) {
            save();
            ModLogger.debug("FavoriteProtection: unprotected {}", id);
        }
    }

    private static String getId(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return null;
        if (ingredient instanceof dev.emi.emi.runtime.EmiFavorite favorite) {
            ingredient = favorite.getStack();
        }
        var stacks = ingredient.getEmiStacks();
        if (stacks == null || stacks.size() != 1) return null;
        var id = stacks.get(0).getId();
        return id == null ? null : id.toString();
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path file = file();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start < 0 || end <= start) return;
            for (String part : json.substring(start + 1, end).split(",")) {
                String entry = part.trim().replace("\"", "");
                if (!entry.isEmpty()) PROTECTED.add(entry);
            }
            ModLogger.debug("FavoriteProtection: loaded {} entries", PROTECTED.size());
        } catch (IOException e) {
            ModLogger.warn("FavoriteProtection: failed to read {}: {}", FILE_NAME, e.getMessage());
        }
    }

    private static synchronized void save() {
        Path file = file();
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            StringBuilder sb = new StringBuilder("{\"protected\":[");
            boolean first = true;
            for (String entry : PROTECTED) {
                if (!first) sb.append(',');
                sb.append('\"').append(entry).append('\"');
                first = false;
            }
            sb.append("]}");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModLogger.warn("FavoriteProtection: failed to write {}: {}", FILE_NAME, e.getMessage());
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
