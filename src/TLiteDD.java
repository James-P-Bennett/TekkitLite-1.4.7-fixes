import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;

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

    // ------------------------------------------------------------ non-dimension item switches

    private static final String CFG = "config/DimensionalDoorsTweaks.cfg";
    private static final String SWORD_ONLY = "riftBlade.swordOnly";
    private static final String REMOVER_ENABLED = "riftRemover.enabled";

    private static volatile Boolean swordOnly;
    private static volatile Boolean removerEnabled;

    /**
     * When true, the Rift Blade's rift-opening is disabled: PatchDD makes its right-click methods
     * no-ops, leaving only the melee attack, so it behaves as a plain sword everywhere. Default off.
     */
    public static boolean swordOnly() {
        if (swordOnly == null) {
            load();
        }
        return swordOnly.booleanValue();
    }

    /**
     * When false, the Rift Remover's right-click is a no-op, so the item does nothing. Default true
     * (the Remover only closes rifts, it never creates anything).
     */
    public static boolean riftRemoverEnabled() {
        if (removerEnabled == null) {
            load();
        }
        return removerEnabled.booleanValue();
    }

    private static synchronized void load() {
        if (swordOnly != null && removerEnabled != null) {
            return;
        }
        boolean sword = false;
        boolean remover = true;
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
                sword = Boolean.parseBoolean(p.getProperty(SWORD_ONLY, "false").trim());
                remover = Boolean.parseBoolean(p.getProperty(REMOVER_ENABLED, "true").trim());
            } else {
                writeDefault(f);
            }
        } catch (Throwable t) {
            sword = false;
            remover = true;
        }
        swordOnly = Boolean.valueOf(sword);
        removerEnabled = Boolean.valueOf(remover);
    }

    private static void writeDefault(File f) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            FileWriter w = new FileWriter(f);
            try {
                w.write("# Dimensional Doors non-dimension item tweaks (TekkitLite-1.4.7-fixes).\n");
                w.write("\n");
                w.write("# true makes the Rift Blade a plain sword: right-click no longer opens rifts\n");
                w.write("# or teleports, only the melee attack remains. Default false.\n");
                w.write(SWORD_ONLY + "=false\n");
                w.write("\n");
                w.write("# false makes the Rift Remover inert (right-click does nothing). The Remover only\n");
                w.write("# closes rifts, it never creates a dimension. Default true.\n");
                w.write(REMOVER_ENABLED + "=true\n");
            } finally {
                w.close();
            }
        } catch (Throwable t) {
            // best effort
        }
    }
}
