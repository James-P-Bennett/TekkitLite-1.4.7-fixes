import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import codechicken.chunkloader.ChunkLoaderManager;
import codechicken.chunkloader.IChickenChunkLoader;
import codechicken.core.BlockCoord;
import codechicken.core.CommonUtils;

/**
 * ChunkLoaderConversion: one authoritative per-player cap and registry across every chunk loader.
 *
 * ChickenChunks, immibis Dimensional Anchors and the AdditionalPipes Tether all register here, so a
 * player's loaders share a single per-player limit (ChickenChunks.cfg players{}, default 6, or the
 * chunkloader.maxchunksperplayer override in config/ChunkLoaderConversion.cfg; 0 means no cap).
 * Every loader is a single chunk (the spotloader patches), so counting loaders is counting chunks.
 * A loader over the limit registers but is not allowed to load (active=false).
 *
 * The registry also backs the /loaders command and the placement messages: it remembers each
 * owner's loaders by world and position with an on/off flag. It is in memory and rebuilds as
 * loaders re-register on world load, so a restart heals any drift; a listing therefore covers a
 * player's currently loaded loaders reliably and anything active while they are online.
 *
 * Bukkit is used for the online check and chat (Bukkit.getPlayerExact / sendMessage), and
 * CodeChickenCore's CommonUtils for the world name; both are on the shared class loader at runtime.
 * The immibis tile is reached as a vanilla TileEntity (any: k = worldObj, l/m/n = coords).
 */
public class TLiteChunkQuota {

    private static final class Loader {
        final String world;
        final int x, y, z;
        boolean active;
        Loader(String world, int x, int y, int z) { this.world = world; this.x = x; this.y = y; this.z = z; }
    }

    /** owner -> (key -> loader), insertion ordered so a listing is stable. */
    private static final Map<String, Map<String, Loader>> reg = new LinkedHashMap<String, Map<String, Loader>>();

    // ------------------------------------------------------------ ChickenChunks

    /** ChunkLoaderManager.addChunkLoader: true lets the real add run, false skips it. ChickenChunks
     *  handles its own offline shutdown (allowoffline), so this is not online-gated. */
    public static synchronized boolean ccClaim(IChickenChunkLoader l) {
        String owner = l.getOwner();
        if (owner == null) return true;
        BlockCoord p = l.getPosition();
        return claim(owner, worldName(l.getWorld()), p.x, p.y, p.z, false);
    }

    /** ChunkLoaderManager.remChunkLoader: drop this loader from the owner's registry. */
    public static synchronized void ccRelease(IChickenChunkLoader l) {
        String owner = l.getOwner();
        if (owner == null) return;
        BlockCoord p = l.getPosition();
        release(owner, worldName(l.getWorld()), p.x, p.y, p.z);
    }

    /** BlockChunkLoader.onBlockPlacedBy hook: register the placement and message the placer. */
    public static synchronized void ccAnnounce(IChickenChunkLoader l) {
        announce(l.getOwner(), ccClaim(l));
    }

    // ------------------------------------------------------------ Dimensional Anchors

    public static synchronized boolean daClaim(Object tile) {
        any te = (any) tile;
        String owner = daOwner(te);
        if (owner == null) return true;
        return claim(owner, worldName(te.k), te.l, te.m, te.n, true);
    }

    public static synchronized void daRelease(Object tile) {
        any te = (any) tile;
        String owner = daOwner(te);
        if (owner == null) return;
        release(owner, worldName(te.k), te.l, te.m, te.n);
    }

    public static synchronized void daAnnounce(Object tile) {
        announce(daOwner((any) tile), daClaim(tile));
    }

    private static String daOwner(Object tile) {
        try {
            return (String) tile.getClass().getField("owner").get(tile);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ AdditionalPipes (via TLiteAP)

    public static synchronized boolean apClaim(String owner, Object world, int x, int y, int z) {
        if (owner == null) return true;
        return claim(owner, worldName(world), x, y, z, true);
    }

    public static synchronized void apRelease(String owner, Object world, int x, int y, int z) {
        if (owner == null) return;
        release(owner, worldName(world), x, y, z);
    }

    public static synchronized void apAnnounce(String owner, Object world, int x, int y, int z) {
        if (owner == null) return;
        announce(owner, claim(owner, worldName(world), x, y, z, true));
    }

    // ------------------------------------------------------------ registry core

    private static boolean claim(String owner, String world, int x, int y, int z, boolean gateOnline) {
        Map<String, Loader> m = reg.get(owner);
        if (m == null) { m = new LinkedHashMap<String, Loader>(); reg.put(owner, m); }
        String k = world + ":" + x + ":" + y + ":" + z;
        Loader ld = m.get(k);
        if (ld == null) { ld = new Loader(world, x, y, z); m.put(k, ld); }
        boolean withinLimit;
        int lim = limit(owner);
        if (lim <= 0) {
            withinLimit = true;
        } else {
            int othersActive = 0;
            for (Loader o : m.values()) {
                if (o != ld && o.active) othersActive++;
            }
            withinLimit = othersActive < lim;
        }
        ld.active = withinLimit && (!gateOnline || ownerOnlineOrGrace(owner));
        return ld.active;
    }

    // ------------------------------------------------------------ online / grace

    private static final long GRACE_MS = 10L * 60L * 1000L;
    private static final Map<String, Long> lastSeen = new java.util.HashMap<String, Long>();

    /**
     * True while the owner is online or was online within the grace window. Called every tick by a
     * loaded loader (see daTick), so the last-seen stamp is refreshed for as long as any of the
     * owner's loaders are ticking while they are online, and the grace counts down from their last
     * seen tick after they log off. ChickenChunks keeps its own allowoffline/awayTimeout.
     */
    public static synchronized boolean ownerOnlineOrGrace(String owner) {
        if (owner == null) return true;
        try {
            if (Bukkit.getPlayerExact(owner) != null) {
                lastSeen.put(owner, Long.valueOf(System.currentTimeMillis()));
                return true;
            }
        } catch (Throwable t) {
            return true; // if the online check fails, do not shut loaders off
        }
        Long t = lastSeen.get(owner);
        return t != null && (System.currentTimeMillis() - t.longValue()) < GRACE_MS;
    }

    // ------------------------------------------------------------ anchor per-tick re-evaluation

    private static java.lang.reflect.Method daSetActive, daCanBe;

    /**
     * Injected at the top of the anchor's updateEntity. The anchor never re-checks activation once
     * loaded (with fuel off), so this drives setActive from the owner's online/grace state each
     * tick, server-side. Reflection keeps this class free of an anchor-mod compile dependency; the
     * setActive call is idempotent and cheap, and ownerless/server-owned anchors are left to the mod.
     */
    public static void daTick(Object tile) {
        try {
            any te = (any) tile;
            if (te.k == null || te.k.I) return; // server side only (yc.I = isRemote)
            String owner = daOwner(tile);
            if (owner == null) return;
            Class<?> c = tile.getClass();
            if (daSetActive == null) {
                daCanBe = c.getMethod("canLoaderBeActive");
                daSetActive = c.getMethod("setActive", boolean.class);
            }
            boolean canBe = ((Boolean) daCanBe.invoke(tile)).booleanValue();
            daSetActive.invoke(tile, Boolean.valueOf(canBe && ownerOnlineOrGrace(owner)));
        } catch (Throwable t) {
            // best effort; never disrupt the tick
        }
    }

    private static void release(String owner, String world, int x, int y, int z) {
        Map<String, Loader> m = reg.get(owner);
        if (m == null) return;
        m.remove(world + ":" + x + ":" + y + ":" + z);
        if (m.isEmpty()) reg.remove(owner);
    }

    private static void announce(String owner, boolean active) {
        if (owner == null) return;
        try {
            Player p = Bukkit.getPlayerExact(owner);
            if (p == null) return;
            int lim = limit(owner);
            if (active) {
                p.sendMessage("§eChunk loaders: " + activeCount(owner) + "/" + lim + ".");
            } else {
                p.sendMessage("§cThis chunk loader is disabled: you are at your limit (" + lim + "/" + lim + ").");
            }
        } catch (Throwable t) {
            // messaging is best effort
        }
    }

    // ------------------------------------------------------------ queries (for /loaders and login notice)

    public static synchronized int activeCount(String owner) {
        Map<String, Loader> m = reg.get(owner);
        if (m == null) return 0;
        int n = 0;
        for (Loader o : m.values()) if (o.active) n++;
        return n;
    }

    /** How many of the owner's loaders are off because the owner is over the limit. */
    public static synchronized int disabledCount(String owner) {
        Map<String, Loader> m = reg.get(owner);
        if (m == null) return 0;
        int n = 0;
        for (Loader o : m.values()) if (!o.active) n++;
        return n;
    }

    public static synchronized int limitFor(String owner) {
        return limit(owner);
    }

    /** Lines for the /loaders listing: "world 12,64,-88 status: on". */
    public static synchronized java.util.List<String> describe(String owner) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        Map<String, Loader> m = reg.get(owner);
        if (m == null) return out;
        for (Loader o : m.values()) {
            out.add(o.world + " " + o.x + "," + o.y + "," + o.z + " status: " + (o.active ? "on" : "off"));
        }
        return out;
    }

    private static String worldName(Object world) {
        try {
            return CommonUtils.getWorldName((yc) world);
        } catch (Throwable t) {
            return "world";
        }
    }

    // ------------------------------------------------------------ limit

    private static final String FILE = "config/ChunkLoaderConversion.cfg";
    private static final String KEY = "chunkloader.maxchunksperplayer";
    private static int override = Integer.MIN_VALUE;

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
            java.io.File f = new java.io.File(FILE);
            if (f.isFile()) {
                java.util.Properties p = new java.util.Properties();
                java.io.FileInputStream in = new java.io.FileInputStream(f);
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
