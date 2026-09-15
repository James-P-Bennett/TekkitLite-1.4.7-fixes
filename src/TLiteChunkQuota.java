import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import codechicken.chunkloader.ChunkLoaderManager;
import codechicken.chunkloader.IChickenChunkLoader;
import codechicken.core.BlockCoord;

/**
 * ChunkLoaderConversion: one authoritative per-player cap across every chunk loader.
 *
 * ChickenChunks and immibis Dimensional Anchors each track their own per-player quota, so without
 * this a player could load a full ChickenChunks allowance and a separate anchor allowance on top.
 * Both mods' loaders are pinned to a single chunk each (the spotloader patches), so counting
 * loaders is counting chunks. This class keeps one shared set of loaded chunks per owner, and each
 * mod's register and unregister runs through it: a loader that would take the owner past the limit
 * does not register, so it loads nothing.
 *
 * The limit is the owner's ChickenChunks per-player limit (ChickenChunks.cfg players{}), which
 * stays authoritative, unless config/ChunkLoaderConversion.cfg sets chunkloader.maxchunksperplayer
 * to a value of 0 or more (0 meaning no cap, matching ChickenChunks). Server-owned or ownerless
 * loaders are never capped.
 *
 * The counter is in memory and rebuilds as loaders re-register on world load, so a restart heals
 * any drift and every failure mode is a conservative under-count, never an over-count. The immibis
 * tile is reached as a vanilla TileEntity (any: k = worldObj, l/m/n = coords) for its world and
 * position, which the class needs without a compile dependency on the anchor mod.
 */
public class TLiteChunkQuota {

    private static final Map<String, Set<String>> byOwner = new HashMap<String, Set<String>>();

    // ------------------------------------------------------------ ChickenChunks

    /** ChunkLoaderManager.addChunkLoader: true lets the real add run, false skips it. */
    public static synchronized boolean ccClaim(IChickenChunkLoader l) {
        String owner = l.getOwner();
        if (owner == null) return true;
        BlockCoord p = l.getPosition();
        return claim(owner, key(System.identityHashCode(l.getWorld()), p.x, p.y, p.z));
    }

    /** ChunkLoaderManager.remChunkLoader: drop this loader's chunk from the owner's count. */
    public static synchronized void ccRelease(IChickenChunkLoader l) {
        String owner = l.getOwner();
        if (owner == null) return;
        BlockCoord p = l.getPosition();
        release(owner, key(System.identityHashCode(l.getWorld()), p.x, p.y, p.z));
    }

    // ------------------------------------------------------------ Dimensional Anchors

    /** WorldInfo.addLoader: true lets the real add run, false skips it. Arg is a TileChunkLoader. */
    public static synchronized boolean daClaim(Object tile) {
        any te = (any) tile;
        String owner = daOwner(te);
        if (owner == null) return true;
        return claim(owner, key(System.identityHashCode(te.k), te.l, te.m, te.n));
    }

    /** WorldInfo.removeLoader and delayRemoveLoader. Arg is a TileChunkLoader. */
    public static synchronized void daRelease(Object tile) {
        any te = (any) tile;
        String owner = daOwner(te);
        if (owner == null) return;
        release(owner, key(System.identityHashCode(te.k), te.l, te.m, te.n));
    }

    /** The anchor's public owner field, read reflectively so this class needs no anchor import. */
    private static String daOwner(Object tile) {
        try {
            Object o = tile.getClass().getField("owner").get(tile);
            return (String) o;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ shared counter

    private static boolean claim(String owner, String key) {
        Set<String> s = byOwner.get(owner);
        if (s == null) { s = new HashSet<String>(); byOwner.put(owner, s); }
        if (s.contains(key)) return true;          // already counted (reactivation)
        int lim = limit(owner);
        if (lim <= 0) { s.add(key); return true; } // 0 or less: no cap
        if (s.size() >= lim) return false;
        s.add(key);
        return true;
    }

    private static void release(String owner, String key) {
        Set<String> s = byOwner.get(owner);
        if (s != null) {
            s.remove(key);
            if (s.isEmpty()) byOwner.remove(owner);
        }
    }

    private static String key(int worldHash, int x, int y, int z) {
        return worldHash + ":" + x + ":" + y + ":" + z;
    }

    // ------------------------------------------------------------ limit

    private static final String FILE = "config/ChunkLoaderConversion.cfg";
    private static final String KEY = "chunkloader.maxchunksperplayer";
    private static int override = Integer.MIN_VALUE; // unread

    private static int limit(String owner) {
        int ov = configOverride();
        if (ov >= 0) return ov;
        try {
            return ChunkLoaderManager.getPlayerChunkLimit(owner);
        } catch (Throwable t) {
            return 6;
        }
    }

    private static int configOverride() {
        if (override != Integer.MIN_VALUE) return override;
        int val = -1;
        try {
            File f = new File(FILE);
            if (f.isFile()) {
                Properties p = new Properties();
                FileInputStream in = new FileInputStream(f);
                try { p.load(in); } finally { in.close(); }
                String s = p.getProperty(KEY);
                if (s != null) val = Integer.parseInt(s.trim());
            }
        } catch (Throwable t) {
            val = -1;
        }
        override = val;
        return override;
    }
}
