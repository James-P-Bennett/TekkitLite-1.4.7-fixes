import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.bukkit.Bukkit;

/**
 * Claim guards injected by TekkitLite-1.4.7-fixes into Dimensional Doors 1.3.2 (PatchDD).
 *
 * The rift and door items place blocks in whatever world the holder is in, with no protection
 * check, so a player can drop a rift or a dimensional door inside someone else's claim. The
 * Dimensional Door, Chaos Door and Exit Door items place through the vanilla ItemDoor helper
 * (tx.a); the Rift Blade opens rifts through the same helper on its air-cast right-click; the Rift
 * Signature links a rift through dimHelper.createLink. Each player-facing use is now checked as the
 * holding player: it works where the player may build and is refused (nothing placed, item not
 * consumed) in claims they cannot edit. In-dimension edits (pocket dims, Limbo, dungeon generation)
 * are left alone: there are no claims there.
 *
 * The Rift Blade therefore stays a working melee weapon everywhere but only opens rifts where its
 * holder could build. Two switches in config/DimensionalDoorsTweaks.cfg (written on first load)
 * tune the non-dimension items: riftBlade.swordOnly (default off) disables the blade's rift-opening
 * entirely so it is a plain sword, and riftRemover.enabled (default on) can turn the Rift Remover
 * (which only closes rifts, it creates nothing) into an inert item. PatchDD gates the matching
 * methods on these.
 *
 * yc = World (I = isRemote), qx = EntityPlayer, amq = Block, tx = ItemDoor (a = placeDoorBlock).
 */
public class TLiteDD {

    /** Entry guard for a door/signature onItemUse: true if the player may build at the clicked block. */
    public static boolean useAllowed(qx player, yc world, int x, int y, int z) {
        if (player == null || world == null || world.I) {
            return true;
        }
        if (TLiteProtect.canEdit(player, world, x, y, z)) {
            return true;
        }
        TLiteProtect.refused(player, "Dimensional Doors at " + x + "," + y + "," + z + " (protected)");
        return false;
    }

    /** Replaces the vanilla ItemDoor placement (tx.a) in the Rift Blade's air-cast rift-open. */
    public static void placeDoor(yc world, int x, int y, int z, int side, amq block, qx player) {
        if (player == null || world == null || world.I || TLiteProtect.canEdit(player, world, x, y, z)) {
            tx.a(world, x, y, z, side, block);
            return;
        }
        TLiteProtect.refused(player, "Rift Blade at " + x + "," + y + "," + z + " (protected)");
    }

    // ------------------------------------------------------------ config switches

    private static final String CFG = "config/DimensionalDoorsTweaks.cfg";
    private static final String SWORD_ONLY = "riftBlade.swordOnly";
    private static final String REMOVER_ENABLED = "riftRemover.enabled";
    private static final String POCKETS = "pocketDimensions.enabled";
    private static final String DUNGEONS = "dungeons.enabled";
    private static final String LIMBO = "limbo.enabled";
    private static final String WORLDGEN = "worldgenSpawns.enabled";

    private static volatile boolean loaded;
    private static boolean swordOnlyFlag;         // default false
    private static boolean removerEnabledFlag = true;
    private static boolean pocketsFlag = true;
    private static boolean dungeonsFlag = true;
    private static boolean limboFlag = true;
    private static boolean worldgenFlag = true;

    /**
     * When true, the Rift Blade's rift-opening is disabled: PatchDD makes its right-click methods
     * no-ops, leaving only the melee attack, so it behaves as a plain sword everywhere. Default off.
     */
    public static boolean swordOnly() {
        if (!loaded) { load(); }
        return swordOnlyFlag;
    }

    /**
     * When false, the Rift Remover's right-click is a no-op, so the item does nothing. Default true
     * (the Remover only closes rifts, it never creates anything).
     */
    public static boolean riftRemoverEnabled() {
        if (!loaded) { load(); }
        return removerEnabledFlag;
    }

    /** Gate for DungeonGenerator.generateDungeonlink: false skips it, so a rift-pocket has no dungeon. */
    public static boolean dungeonsEnabled() {
        if (!loaded) { load(); }
        return dungeonsFlag;
    }

    /** Gate for RiftGenerator.generate: false blocks the mod from spawning rifts/doors in worldgen. */
    public static boolean worldgenSpawnsEnabled() {
        if (!loaded) { load(); }
        return worldgenFlag;
    }

    /**
     * Gate at dimHelper.teleportToLimbo: when Limbo is disabled, cancel the teleport (return true) and
     * tell the player. Called with the travelling player.
     */
    public static boolean limboDenied(qx player) {
        if (!loaded) { load(); }
        if (limboFlag) {
            return false;
        }
        message(player, "§c[Dimensional Doors] Limbo is disabled on this server.");
        return true;
    }

    /**
     * Gate at dimHelper.teleportToPocket: when pocket dimensions are disabled, cancel the teleport
     * (return true) for any entity and tell the player if it is one. Dungeons are pocket dimensions,
     * so this blocks entering them too.
     */
    public static boolean pocketDenied(lq entity) {
        if (!loaded) { load(); }
        if (pocketsFlag) {
            return false;
        }
        if (entity instanceof qx) {
            message((qx) entity, "§c[Dimensional Doors] Pocket dimensions are disabled on this server.");
        }
        return true;
    }

    // Throttle so a player standing in a door does not get spammed each tick.
    private static final long MSG_INTERVAL_MS = 5000L;
    private static final Map lastMsg = new HashMap();   // username -> Long

    private static void message(qx player, String text) {
        if (player == null) {
            return;
        }
        try {
            String name = String.valueOf(player.bR);
            long now = System.currentTimeMillis();
            Long last = (Long) lastMsg.get(name);
            if (last != null && now - last.longValue() < MSG_INTERVAL_MS) {
                return;
            }
            lastMsg.put(name, Long.valueOf(now));
            org.bukkit.entity.Player p = Bukkit.getPlayerExact(name);
            if (p != null) {
                p.sendMessage(text);
            }
        } catch (Throwable t) {
            // messaging is best effort
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        try {
            File f = new File(CFG);
            if (f.isFile()) {
                Properties p = new Properties();
                FileInputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
                swordOnlyFlag = Boolean.parseBoolean(p.getProperty(SWORD_ONLY, "false").trim());
                removerEnabledFlag = Boolean.parseBoolean(p.getProperty(REMOVER_ENABLED, "true").trim());
                pocketsFlag = Boolean.parseBoolean(p.getProperty(POCKETS, "true").trim());
                dungeonsFlag = Boolean.parseBoolean(p.getProperty(DUNGEONS, "true").trim());
                limboFlag = Boolean.parseBoolean(p.getProperty(LIMBO, "true").trim());
                worldgenFlag = Boolean.parseBoolean(p.getProperty(WORLDGEN, "true").trim());
            } else {
                writeDefault(f);
            }
        } catch (Throwable t) {
            // keep the safe defaults set on the fields
        }
        loaded = true;
    }

    private static void writeDefault(File f) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            FileWriter w = new FileWriter(f);
            try {
                w.write("# Dimensional Doors tweaks (TekkitLite-1.4.7-fixes).\n");
                w.write("\n");
                w.write("# true makes the Rift Blade a plain sword: right-click no longer opens rifts\n");
                w.write("# or teleports, only the melee attack remains. Default false.\n");
                w.write(SWORD_ONLY + "=false\n");
                w.write("\n");
                w.write("# false makes the Rift Remover inert (right-click does nothing). Default true.\n");
                w.write(REMOVER_ENABLED + "=true\n");
                w.write("\n");
                w.write("# false blocks entering pocket dimensions: going through a dimensional door is\n");
                w.write("# cancelled and the player is told. Dungeons are pocket dimensions, so this\n");
                w.write("# blocks entering them too. Default true.\n");
                w.write(POCKETS + "=true\n");
                w.write("\n");
                w.write("# false stops rift-pockets being filled with a dungeon (the pocket is left empty).\n");
                w.write("# Default true.\n");
                w.write(DUNGEONS + "=true\n");
                w.write("\n");
                w.write("# false blocks entering Limbo: the teleport is cancelled and the player is told.\n");
                w.write("# Default true.\n");
                w.write(LIMBO + "=true\n");
                w.write("\n");
                w.write("# false stops the mod from spawning rifts or doors during world generation.\n");
                w.write("# Default true.\n");
                w.write(WORLDGEN + "=true\n");
            } finally {
                w.close();
            }
        } catch (Throwable t) {
            // best effort
        }
    }
}
