package org.chatterjay.emiextend.client;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.chatterjay.emiextend.util.ModLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists AE network cache to disk using LZ4-compressed binary format.
 *
 * File layout:
 *   [4 bytes magic "EmCc"]
 *   [1 byte version 0x01]
 *   [LZ4 frame containing serialized data]
 */
public final class DiskCacheIO {

    private static final byte[] MAGIC = {'E', 'm', 'C', 'c'};
    private static final int VERSION = 1;

    private DiskCacheIO() {}

    /**
     * Write {@code data} to {@code path} with LZ4 compression and header.
     */
    public static void save(Path path, byte[] data) {
        try {
            byte[] compressed;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length)) {
                try (LZ4FrameOutputStream lz4 = new LZ4FrameOutputStream(baos)) {
                    lz4.write(data);
                }
                compressed = baos.toByteArray();
            }

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
                out.write(MAGIC);
                out.write(VERSION);
                out.write(compressed);
            }
        } catch (Exception e) {
            ModLogger.warn("DiskCache: save failed — {}", e.getMessage());
        }
    }

    /**
     * Read and decompress data previously stored at {@code path}.
     * Returns {@code null} if the file doesn't exist, is corrupt, or uses
     * an incompatible format.
     */
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
                 LZ4FrameInputStream lz4 = new LZ4FrameInputStream(bais)) {
                return lz4.readAllBytes();
            }
        } catch (Exception e) {
            ModLogger.warn("DiskCache: load failed — {}", e.getMessage());
            return null;
        }
    }
}
