import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;

import net.minecraftforge.common.MinecraftForge;

/**
 * ChunkLoaderConversion sibling: Industrial Tesla Coil flags for Advanced Repulsion Systems
 * (the immibis MFFS "Industrial Tesla Coil", block 1952, TileTeslaCoil) injected by PatchARS.
 *
 * The coil's fireShot damages its target with attackEntityFrom(damageSource, dmg). Two optional,
 * default-off flags in config/TeslaCoil.cfg change that:
 *
 *   industrialTeslaCoil.noPlayerDamage - fireShot returns early when the target is a player, so
 *       the coil clears mobs but never hurts players (a PvE server switch).
 *   industrialTeslaCoil.denyMobDrops - a LivingDropsEvent handler, keyed to this coil's own
 *       damage source, cancels the drops of anything the coil kills, so a tesla-coil mob grinder
 *       clears mobs without scattering loot. Only kills by this coil are affected.
 *
 * Both default off, so nothing changes until a server opts in. The damage redirect also registers
 * the drops handler the first time a coil fires, capturing the coil's damage source for the match.
 *
 * lq = EntityLivingBase, lh = DamageSource; lq.a(lh, int) = attackEntityFrom.
 */
public class TLiteARS {

    private static final String FILE = "config/TeslaCoil.cfg";

    private static volatile Boolean denyDrops;
    private static volatile Boolean noPlayerDmg;
    private static boolean registered;

    public static boolean noPlayerDamage() {
        if (noPlayerDmg == null) load();
        return noPlayerDmg.booleanValue();
    }

    public static boolean denyMobDrops() {
        if (denyDrops == null) load();
        return denyDrops.booleanValue();
    }

    /** Replaces target.attackEntityFrom(src, dmg) in TileTeslaCoil.fireShot. */
    public static boolean shock(lq target, lh src, int dmg) {
        registerDropHandler(src);
        return target.a(src, dmg);
    }

    private static synchronized void registerDropHandler(lh src) {
        if (registered) {
            return;
        }
        registered = true;
        try {
            TLiteARSDrops.teslaSource = src;
            MinecraftForge.EVENT_BUS.register(new TLiteARSDrops());
        } catch (Throwable t) {
            // If registration fails the coil still works; only denyMobDrops is inert.
        }
    }

    private static synchronized void load() {
        if (denyDrops != null && noPlayerDmg != null) {
            return;
        }
        boolean d = false;
        boolean np = false;
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
                d = Boolean.parseBoolean(p.getProperty("industrialTeslaCoil.denyMobDrops", "false").trim());
                np = Boolean.parseBoolean(p.getProperty("industrialTeslaCoil.noPlayerDamage", "false").trim());
            } else {
                writeDefault(f);
            }
        } catch (Throwable t) {
            // Unreadable config: keep both flags off, the safe default.
        }
        denyDrops = Boolean.valueOf(d);
        noPlayerDmg = Boolean.valueOf(np);
    }

    private static void writeDefault(File f) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            FileWriter w = new FileWriter(f);
            try {
                w.write("# IC2 basic Tesla Coil (block 223)\n");
                w.write("# Never damage players with the IC2 Tesla Coil (PvE: it still shocks mobs).\n");
                w.write("basicTeslaCoil.noPlayerDamage=false\n");
                w.write("\n");
                w.write("# Industrial Tesla Coil (Advanced Repulsion Systems, block 1952)\n");
                w.write("# Deny the drops of mobs this coil kills, so a grinder clears mobs without loot.\n");
                w.write("industrialTeslaCoil.denyMobDrops=false\n");
                w.write("# Never damage players with this coil (PvE: it still clears mobs).\n");
                w.write("industrialTeslaCoil.noPlayerDamage=false\n");
            } finally {
                w.close();
            }
        } catch (Throwable t) {
            // Best effort.
        }
    }
}
