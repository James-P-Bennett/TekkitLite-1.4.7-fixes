import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;

/**
 * ChunkLoaderConversion: the AdditionalPipes chunk-loader toggle (PatchAP, patch apchunkgate).
 *
 * The AdditionalPipes chunk loader (the "Teleport Tether", block 4077) keeps its area of chunks
 * force loaded through a Forge ticket, offline included, and the mod ships no switch to turn that
 * off. The apchunkgate patch calls {@link #apChunkLoadEnabled()} at the top of the loader tile's
 * tick: when it returns false the tile drops its ticket and returns, so it requests nothing and
 * loads nothing.
 *
 * Teleport pipes are not touched. A teleport pipe removes itself from the network when its chunk
 * unloads, so an item sent toward a destination in an unloaded chunk finds no target and drops at
 * the source pipe instead of teleporting into an unloaded chunk.
 *
 * The flag lives in config/ChunkLoaderConversion.cfg beside the other mod configs and defaults to
 * off. It is read once, the first time a loader ticks, and the file is created with the default if
 * it is missing. This helper touches no Minecraft class, so it is plain Java.
 */
public class TLiteAP {

    private static final String FILE = "config/ChunkLoaderConversion.cfg";
    private static final String KEY = "additionalpipes.chunkloader.enabled";

    private static volatile Boolean enabled;

    /** True only if the config explicitly enables the AdditionalPipes chunk loader. */
    public static boolean apChunkLoadEnabled() {
        Boolean e = enabled;
        if (e == null) {
            e = load();
        }
        return e.booleanValue();
    }

    private static synchronized Boolean load() {
        if (enabled != null) {
            return enabled;
        }
        boolean val = false;
        try {
            File f = new File(FILE);
            if (f.isFile()) {
                Properties p = new Properties();
                FileInputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
                val = Boolean.parseBoolean(p.getProperty(KEY, "false").trim());
            } else {
                writeDefault(f);
            }
        } catch (Throwable t) {
            // Config unreadable: keep the feature off, which is the safe default.
        }
        enabled = Boolean.valueOf(val);
        return enabled;
    }

    private static void writeDefault(File f) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            FileWriter w = new FileWriter(f);
            try {
                w.write("# ChunkLoaderConversion\n");
                w.write("# Set to true to let the AdditionalPipes chunk loader (Teleport Tether, block\n");
                w.write("# 4077) force load chunks. Default false: the loader loads nothing. Teleport\n");
                w.write("# pipes still move items between chunks that are already loaded.\n");
                w.write(KEY + "=false\n");
            } finally {
                w.close();
            }
        } catch (Throwable t) {
            // Best effort; a missing file just means the default is used every load.
        }
    }
}
