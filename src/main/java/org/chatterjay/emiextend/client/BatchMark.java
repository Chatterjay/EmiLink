package org.chatterjay.emiextend.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.emi.emi.api.stack.EmiIngredient;
import net.neoforged.fml.loading.FMLPaths;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Persists the sidebar stacks marked by EmiLink's batch-mark selection.
 * <p>
 * Marked stacks are filled into filter slots in the order they were selected, so
 * the insertion order of the set is part of the state and is written to disk.
 */
public final class BatchMark {

    private static final String FILE_NAME = "emilink_batch_marks.json";
    private static final Set<String> MARKED = new LinkedHashSet<>();
    private static boolean loaded = false;

    private BatchMark() {}

    public static boolean isMarked(EmiIngredient stack) {
        if (stack == null || stack.isEmpty()) return false;
        load();
        String id = EmiStackId.of(stack);
        return id != null && MARKED.contains(id);
    }

    public static boolean isMarkedId(String id) {
        if (id == null) return false;
        load();
        return MARKED.contains(id);
    }

    public static void markAll(Collection<? extends EmiIngredient> stacks) {
        if (stacks == null || stacks.isEmpty()) return;
        load();
        int changed = 0;
        for (EmiIngredient stack : stacks) {
            String id = EmiStackId.of(stack);
            if (id != null && MARKED.add(id)) changed++;
        }
        if (changed > 0) {
            save();
            ModLogger.debug("BatchMark: marked {} stacks in batch", changed);
        }
    }

    public static void unmarkAll(Collection<? extends EmiIngredient> stacks) {
        if (stacks == null || stacks.isEmpty()) return;
        load();
        int changed = 0;
        for (EmiIngredient stack : stacks) {
            String id = EmiStackId.of(stack);
            if (id != null && MARKED.remove(id)) changed++;
        }
        if (changed > 0) {
            save();
            ModLogger.debug("BatchMark: unmarked {} stacks in batch", changed);
        }
    }

    public static int size() {
        load();
        return MARKED.size();
    }

    /** Remove every mark and return how many were cleared. */
    public static int clear() {
        load();
        int cleared = MARKED.size();
        if (cleared == 0) return 0;
        MARKED.clear();
        save();
        ModLogger.debug("BatchMark: cleared {} marks", cleared);
        return cleared;
    }

    /** Marked ids in selection order, optionally skipping one entry. */
    public static List<String> orderedIds(String skipId) {
        load();
        List<String> ids = new ArrayList<>(MARKED.size());
        for (String id : MARKED) {
            if (skipId != null && skipId.equals(id)) continue;
            ids.add(id);
        }
        return ids;
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
                if (!entry.isEmpty()) MARKED.add(entry);
            }
            ModLogger.debug("BatchMark: loaded {} entries", MARKED.size());
        } catch (IOException e) {
            ModLogger.warn("BatchMark: failed to read {}: {}", FILE_NAME, e.getMessage());
        }
    }

    private static synchronized void save() {
        Path file = file();
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            StringBuilder sb = new StringBuilder("{\"marked\":[");
            boolean first = true;
            for (String entry : MARKED) {
                if (!first) sb.append(',');
                sb.append('"').append(entry).append('"');
                first = false;
            }
            sb.append("]}");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModLogger.warn("BatchMark: failed to write {}: {}", FILE_NAME, e.getMessage());
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
