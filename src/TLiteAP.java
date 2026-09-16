import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * ChunkLoaderConversion: brings the AdditionalPipes chunk loader ("Teleport Tether", block 4077) to
 * parity with the ChickenChunks loader and the Dimensional Anchor (PatchAP, patch apparity).
 *
 * The AP loader has no owner concept of its own, so the patch adds a tliteOwner field to the tile
 * (set from the placer in the block's onBlockPlacedBy, persisted in NBT), forces the load distance
 * to a single chunk, and gates the tile's tick on apShouldLoad: it force loads its one chunk only
 * while the owner is online or within grace and under the shared per-player limit, exactly like the
 * other two loaders. The added field is reached reflectively here since it does not exist in the
 * stock jar this class compiles against.
 *
 * A master switch (config/ChunkLoaderConversion.cfg additionalpipes.chunkloader.enabled, now
 * defaulting on) can turn the loader off entirely. World and coordinates come from the tile as a
 * vanilla TileEntity (any: k = worldObj, l/m/n), the username from EntityPlayer (qx.bR).
 */
public class TLiteAP {

    private static final String FILE = "config/ChunkLoaderConversion.cfg";
    private static final String KEY = "additionalpipes.chunkloader.enabled";

    private static volatile Boolean enabled;

    /** Master switch; defaults on now that the loader is a capped, owner-tracked single-chunk loader. */
    public static boolean apChunkLoadEnabled() {
        Boolean e = enabled;
        if (e == null) {
            e = load();
        }
        return e.booleanValue();
    }

    // ------------------------------------------------------------ tick gate

    /** s() calls this: force load only while enabled, owned, online/grace and under the shared cap. */
    public static boolean apShouldLoad(Object tile) {
        try {
            if (!apChunkLoadEnabled()) return false;
            String owner = owner(tile);
            if (owner == null || owner.length() == 0) return false; // ownerless (legacy) stays off
            any te = (any) tile;
            return TLiteChunkQuota.apClaim(owner, te.k, te.l, te.m, te.n);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ placement / NBT

    /** BlockChunkLoader.onBlockPlacedBy: record the placer as the loader's owner and message them. */
    public static void apPlaced(yc world, int x, int y, int z, md placer) {
        try {
            if (world.I) return; // server side only
            if (!(placer instanceof qx)) return;
            any tile = world.q(x, y, z);
            if (!isApLoader(tile)) return;
            String name = ((qx) placer).bR;
            setOwner(tile, name);
            TLiteChunkQuota.apAnnounce(name, world, x, y, z);
        } catch (Throwable t) {
            // best effort
        }
    }

    /** readFromNBT tail: load the owner. */
    public static void apLoadNBT(Object tile, bq nbt) {
        try {
            String s = nbt.i("tliteOwner");
            setOwner(tile, (s == null || s.length() == 0) ? null : s);
        } catch (Throwable t) {
        }
    }

    /** writeToNBT tail: save the owner. */
    public static void apSaveNBT(Object tile, bq nbt) {
        try {
            String o = owner(tile);
            nbt.a("tliteOwner", o == null ? "" : o);
        } catch (Throwable t) {
        }
    }

    // ------------------------------------------------------------ reflective owner field (added by ASM)

    private static Field ownerField;

    private static Field ownerField(Object tile) throws Exception {
        if (ownerField == null) {
            ownerField = tile.getClass().getField("tliteOwner");
            ownerField.setAccessible(true);
        }
        return ownerField;
    }

    private static String owner(Object tile) {
        try {
            return (String) ownerField(tile).get(tile);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setOwner(Object tile, String name) {
        try {
            ownerField(tile).set(tile, name);
        } catch (Throwable t) {
        }
    }

    private static boolean isApLoader(Object tile) {
        return tile != null
                && tile.getClass().getName().equals("buildcraft.additionalpipes.chunkloader.TileChunkLoader");
    }

    // ------------------------------------------------------------ config

    private static synchronized Boolean load() {
        if (enabled != null) {
            return enabled;
        }
        boolean val = true;
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
                val = Boolean.parseBoolean(p.getProperty(KEY, "true").trim());
            } else {
                writeDefault(f);
            }
        } catch (Throwable t) {
            val = true;
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
                w.write("# Master switch for the AdditionalPipes chunk loader (Teleport Tether, block 4077).\n");
                w.write("# It is now a single-chunk, owner-tracked loader that counts against the shared\n");
                w.write("# per-player limit and shuts down when its owner is offline. Set false to disable it.\n");
                w.write(KEY + "=true\n");
                w.write("\n");
                w.write("# Combined per-player chunk-loader limit across ChickenChunks, Dimensional Anchors\n");
                w.write("# and the Teleport Tether. -1 (default) uses the ChickenChunks per-player limit\n");
                w.write("# (ChickenChunks.cfg players{}). 0 means no cap. A value of 1 or more overrides it.\n");
                w.write("chunkloader.maxchunksperplayer=-1\n");
            } finally {
                w.close();
            }
        } catch (Throwable t) {
        }
    }
}
