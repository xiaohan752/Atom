package com.atom.life.io;

import com.badlogic.gdx.files.FileHandle;

import java.io.*;
import java.util.zip.*;

/**
 * Chunk storage:
 * - v2: zlib (Deflater/Inflater) + CRC32
 * - backward compatible: v1 gzip (.bin.gz)
 *
 * Files:
 * - new: c_cx_cz.bin.z
 * - legacy: c_cx_cz.bin.gz
 */
public class ChunkIO {

    private static final int MAGIC = 0x5643484B; // 'VCHK'
    private static final int VERSION_ZLIB_V2 = 2; // new
    private static final int VERSION_GZIP_V1 = 1; // legacy

    // zlib compression level: BEST_SPEED is usually good for runtime saves
    private static final int ZLIB_LEVEL = Deflater.BEST_SPEED;

    private final FileHandle root;
    private final FileHandle chunkDir;

    public ChunkIO(FileHandle root) {
        this.root = root;
        this.chunkDir = root.child("chunks");
        if (!chunkDir.exists()) chunkDir.mkdirs();
    }

    private FileHandle chunkFileZ(int cx, int cz) {
        return chunkDir.child("c_" + cx + "_" + cz + ".bin.z");
    }

    private FileHandle chunkFileGzLegacy(int cx, int cz) {
        return chunkDir.child("c_" + cx + "_" + cz + ".bin.gz");
    }

    public byte[] tryLoad(int cx, int cz, int sx, int sy, int sz) {
        // Prefer new file
        FileHandle f = chunkFileZ(cx, cz);
        boolean isNew = true;

        if (!f.exists()) {
            // fallback legacy
            f = chunkFileGzLegacy(cx, cz);
            isNew = false;
            if (!f.exists()) return null;
        }

        File file = f.file();
        try (InputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            // Auto-detect gzip header (0x1F 0x8B). If not gzip -> assume zlib.
            bis.mark(2);
            int b0 = bis.read();
            int b1 = bis.read();
            bis.reset();

            InputStream compressedIn;
            if (b0 == 0x1F && b1 == 0x8B) {
                compressedIn = new GZIPInputStream(bis);
            } else {
                compressedIn = new InflaterInputStream(bis, new Inflater(false));
            }

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(compressedIn))) {
                int magic = in.readInt();
                if (magic != MAGIC) return null;

                int ver = in.readInt();
                if (ver != VERSION_ZLIB_V2 && ver != VERSION_GZIP_V1) return null;

                int rsx = in.readInt();
                int rsy = in.readInt();
                int rsz = in.readInt();
                if (rsx != sx || rsy != sy || rsz != sz) return null;

                int len = in.readInt();
                if (len != sx * sy * sz) return null;

                int crcStored = 0;
                if (ver >= VERSION_ZLIB_V2) {
                    crcStored = in.readInt();
                }

                byte[] blocks = new byte[len];
                in.readFully(blocks);

                if (ver >= VERSION_ZLIB_V2) {
                    CRC32 crc = new CRC32();
                    crc.update(blocks, 0, blocks.length);
                    int crcNow = (int) crc.getValue();
                    if (crcNow != crcStored) return null; // corrupted
                }

                return blocks;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            // If new file fails, you may still try legacy once (optional)
            if (isNew) {
                try {
                    FileHandle legacy = chunkFileGzLegacy(cx, cz);
                    if (legacy.exists()) return tryLoadLegacyGzip(cx, cz, sx, sy, sz);
                } catch (Throwable ignored) {}
            }
            return null;
        }
    }

    // Explicit legacy reader (gzip + v1 header)
    private byte[] tryLoadLegacyGzip(int cx, int cz, int sx, int sy, int sz) {
        FileHandle f = chunkFileGzLegacy(cx, cz);
        if (!f.exists()) return null;

        try (InputStream is = new FileInputStream(f.file());
             GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(is));
             DataInputStream in = new DataInputStream(new BufferedInputStream(gis))) {

            int magic = in.readInt();
            if (magic != MAGIC) return null;
            int ver = in.readInt();
            if (ver != VERSION_GZIP_V1) return null;

            int rsx = in.readInt();
            int rsy = in.readInt();
            int rsz = in.readInt();
            if (rsx != sx || rsy != sy || rsz != sz) return null;

            int len = in.readInt();
            if (len != sx * sy * sz) return null;

            byte[] blocks = new byte[len];
            in.readFully(blocks);
            return blocks;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Save using zlib (v2) + CRC32 + atomic write (tmp -> rename).
     */
    public void save(int cx, int cz, int sx, int sy, int sz, byte[] blocks) {
        if (!chunkDir.exists()) chunkDir.mkdirs();

        FileHandle f = chunkFileZ(cx, cz);
        File outFile = f.file();
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        File tmp = new File(outFile.getAbsolutePath() + ".tmp");

        CRC32 crc = new CRC32();
        crc.update(blocks, 0, blocks.length);
        int crcVal = (int) crc.getValue();

        Deflater deflater = new Deflater(ZLIB_LEVEL, false);

        try (OutputStream fos = new FileOutputStream(tmp);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             DeflaterOutputStream dos = new DeflaterOutputStream(bos, deflater, 1 << 16);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(dos))) {

            out.writeInt(MAGIC);
            out.writeInt(VERSION_ZLIB_V2);

            out.writeInt(sx);
            out.writeInt(sy);
            out.writeInt(sz);

            out.writeInt(blocks.length);
            out.writeInt(crcVal);

            out.write(blocks);
            out.flush();

        } catch (Exception ex) {
            ex.printStackTrace();
            // best-effort cleanup
            try { tmp.delete(); } catch (Throwable ignored) {}
            return;
        } finally {
            deflater.end();
        }

        // Atomic-ish replace
        try {
            if (outFile.exists() && !outFile.delete()) {
                // if cannot delete, try rename over (some OS allow), else fall back copy
            }
            if (!tmp.renameTo(outFile)) {
                // fallback: stream copy then delete tmp
                try (InputStream in = new FileInputStream(tmp);
                     OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                }
                tmp.delete();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
