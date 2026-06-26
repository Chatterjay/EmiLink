package org.chatterjay.emilink.client;

import org.chatterjay.emilink.util.ModLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persists AE network cache to disk using GZIP-compressed binary format.
 *
 * File layout:
 *   [4 bytes magic "EmCc"]
 *   [1 byte version 0x01]
 *   [GZIP frame containing serialized data]
 */
public final class DiskCacheIO {

    private static final byte[] MAGIC = {'E', 'm', 'C', 'c'};
    private static final int VERSION = 1;

    private DiskCacheIO() {}

    public static void save(Path path, byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(data);
            }
            byte[] compressed = baos.toByteArray();

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
                out.write(MAGIC);
                out.write(VERSION);
                out.write(compressed);
            }
        } catch (Exception e) {
            ModLogger.warn("DiskCache: save failed — {}", e.getMessage());
        }
    }

    public static byte[] load(Path path) {
        if (!Files.exists(path)) return null;

        try {
            byte[] fileBytes = Files.readAllBytes(path);
            if (fileBytes.length < 5) {
                ModLogger.warn("DiskCache: file too small ({})", fileBytes.length);
                return null;
            }

            for (int i = 0; i < 4; i++) {
                if (fileBytes[i] != MAGIC[i]) {
                    ModLogger.warn("DiskCache: bad magic — expected EmCc");
                    return null;
                }
            }

            if (fileBytes[4] != VERSION) {
                ModLogger.warn("DiskCache: unsupported version {}", fileBytes[4]);
                return null;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes, 5, fileBytes.length - 5);
                 GZIPInputStream gzip = new GZIPInputStream(bais)) {
                return gzip.readAllBytes();
            }
        } catch (Exception e) {
            ModLogger.warn("DiskCache: load failed — {}", e.getMessage());
            return null;
        }
    }
}
