import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;

import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;

import ic2.core.ExplosionIC2;
import ic2.core.item.tool.EntityMiningLaser;

/**
 * Protection fixes injected by TekkitLite-1.4.7-fixes into IndustrialCraft 2 1.115.231 (PatchIC2).
 *
 * The check runs as an offline MCPC+ fake player with the laser owner's name, like the Quarry
 * and Turtle fixes. Scatter mode fires 25 beams at once and an explosion covers hundreds of
 * blocks; asking as the real player would send the owner a GriefPrevention message for every
 * one of them. The fake player has no connection, so those messages go nowhere.
 *
 * Obfuscated 1.4.7 names: yc = World, qx = EntityPlayer, iq = EntityPlayerMP, md = EntityLiving,
 * ys = ChunkCache; qx.bR = username, lq.p = worldObj, ys.a(int, int, int) = getBlockId.
 */
public class TLiteIC2 {

    private static boolean laserExplosion;
    private static md explosionOwner;
    private static yc explosionWorld;
    private static boolean explosionRefused;

    /**
     * Replaces this.canMine(blockId) in EntityMiningLaser.onUpdate, just before the beam breaks,
     * smelts or explodes the block it hit at x, y, z. Stock never asks protection plugins, so
     * the laser mines through any claim. When this returns false the stock code kills the beam.
     */
    public static boolean canMine(EntityMiningLaser laser, int blockId, int x, int y, int z) {
        if (!laser.canMine(blockId)) {
            return false;
        }
        return allowed(laser.owner, laser.p, x, y, z, "Mining Laser");
    }

    /**
     * Replaces explosion.doExplosion() in EntityMiningLaser.explode (the Explosive mode), so the
     * blocks the explosion is about to take can be checked against the laser's owner.
     */
    public static void doExplosion(ExplosionIC2 explosion, EntityMiningLaser laser) {
        laserExplosion = true;
        explosionOwner = laser.owner;
        explosionWorld = laser.p;
        explosionRefused = false;
        try {
            explosion.doExplosion();
        } finally {
            laserExplosion = false;
            explosionOwner = null;
            explosionWorld = null;
        }
    }

    /**
     * Replaces chunkCache.getBlockId in ExplosionIC2.doExplosion, where each ray picks the next
     * block to destroy. For a Mining Laser explosion a refused block reads as air, so it is not
     * destroyed and drops nothing. After the first refusal the rest of that explosion is refused
     * too. Explosions from anything else (nukes, ITNT, reactors) are left as they were.
     */
    public static int explosionBlockId(ys cache, int x, int y, int z, ExplosionIC2 explosion) {
        int id = cache.a(x, y, z);
        if (!laserExplosion || id == 0) {
            return id;
        }
        if (explosionRefused) {
            return 0;
        }
        if (!allowed(explosionOwner, explosionWorld, x, y, z, "Mining Laser explosion")) {
            explosionRefused = true;
            return 0;
        }
        return id;
    }

    /**
     * Injected at the top of ExplosionIC2.doExplosion's removal loop, for every IC2 explosion that
     * is not a Mining Laser shot (nuke, Industrial TNT, reactor meltdown). IC2 removes blocks
     * straight through the world and never fires the Bukkit EntityExplodeEvent, so GriefPrevention
     * and WorldGuard never see it. This fires that event with the blocks the explosion is about to
     * take, lets the plugins trim the list exactly as they do for vanilla TNT (claimed blocks and,
     * with BlockSurfaceOtherExplosions on, above-surface wilderness), then drops every trimmed
     * position from destroyedBlockPositions so the mod's own loop only removes what survived.
     *
     * The exploding entity for the event is the igniter's player when online, else an offline fake
     * player; the plugins protect all claims from any explosion regardless, so the identity only
     * sets the creeper-vs-other classification (always "other" here). Entity damage and the boom
     * effect are unchanged, the same as vanilla TNT under GriefPrevention. On any failure the
     * explosion is left as stock rather than risk an inconsistent removal.
     */
    public static void ic2ExplodeFilter(yc world, double x, double y, double z, String igniter, java.util.Map positions, lh damageSource) {
        try {
            if (laserExplosion) {
                return; // laser Explosive mode is already checked per block by explosionBlockId
            }
            if (world == null || world.I) {
                return; // server side only
            }
            // Server-wide alert for a nuke bomb detonation (not ITNT or reactor meltdowns).
            if (damageSource == ic2.core.IC2DamageSource.nuke) {
                String who = (igniter == null || igniter.length() == 0) ? "Someone" : igniter;
                org.bukkit.Bukkit.broadcastMessage("§c☢ " + who + " set off a Nuke at "
                        + (int) x + "," + (int) y + "," + (int) z + "!");
            }
            if (positions == null || positions.isEmpty()) {
                return;
            }
            org.bukkit.World bworld = world.getWorld();
            if (bworld == null) {
                return;
            }

            org.bukkit.entity.Entity who = null;
            if (igniter != null && igniter.length() > 0) {
                who = org.bukkit.Bukkit.getPlayerExact(igniter);
            }
            if (who == null) {
                iq fake = CraftFakePlayer.get(world, (igniter == null || igniter.length() == 0) ? "[IC2]" : igniter, false);
                who = (org.bukkit.entity.Entity) fake.getBukkitEntity();
            }

            java.util.List blocks = new java.util.ArrayList();
            for (java.util.Iterator it = positions.keySet().iterator(); it.hasNext(); ) {
                yv p = (yv) it.next();
                blocks.add(bworld.getBlockAt(p.a, p.b, p.c));
            }

            org.bukkit.event.entity.EntityExplodeEvent ev = new org.bukkit.event.entity.EntityExplodeEvent(
                    who, new org.bukkit.Location(bworld, x, y, z), blocks, 1.0F);
            org.bukkit.Bukkit.getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                positions.clear();
                return;
            }

            java.util.HashSet survivors = new java.util.HashSet();
            java.util.List kept = ev.blockList();
            for (int i = 0; i < kept.size(); i++) {
                org.bukkit.block.Block b = (org.bukkit.block.Block) kept.get(i);
                survivors.add(b.getX() + ":" + b.getY() + ":" + b.getZ());
            }
            for (java.util.Iterator it = positions.keySet().iterator(); it.hasNext(); ) {
                yv p = (yv) it.next();
                if (!survivors.contains(p.a + ":" + p.b + ":" + p.c)) {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            // Leave the explosion as stock on any failure.
        }
    }

    /** TLiteProtect as the laser's owner. A laser with no player owner is refused. */
    private static boolean allowed(md owner, yc world, int x, int y, int z, String what) {
        if (!(owner instanceof qx) || world == null) {
            return false;
        }
        iq player;
        try {
            player = CraftFakePlayer.get(world, ((qx) owner).bR, false);
        } catch (Throwable t) {
            return false;
        }
        if (TLiteProtect.canEdit(player, world, x, y, z)) {
            return true;
        }
        TLiteProtect.refused(player, what + " at " + x + "," + y + "," + z + " (protected)");
        return false;
    }

    /**
     * Replaces INetworkClientTileEntityEventListener.onNetworkEvent in NetworkManager's packet
     * handler (case 3). Stock looked up the tile across every dimension from client coordinates
     * and dispatched with no reach or dimension check, letting a player cycle any energy storage
     * block's redstone mode or claim an unopened Energy-O-Mat from anywhere. The event now runs
     * only when the tile is in the sender's own world and within reach.
     */
    public static void netEvent(ic2.api.network.INetworkClientTileEntityEventListener listener, qx player, int event) {
        if (listener instanceof any && player != null) {
            any tile = (any) listener;
            if (tile.k == player.p && player.e(tile.l + 0.5, tile.m + 0.5, tile.n + 0.5) <= 64.0) {
                listener.onNetworkEvent(player, event);
            }
        }
    }

    // ------------------------------------------------------------ IC2 Tesla Coil (block 223)

    /**
     * IC2's basic Tesla Coil (TileEntityTesla.shock) damages every EntityLiving in range, players
     * included (qx extends md here). This replaces its attackEntityFrom call: when the config flag
     * basicTeslaCoil.noPlayerDamage is on, players take no damage from the coil, so it clears mobs
     * on a PvE server without hurting people. Off by default, so the coil is unchanged otherwise.
     *
     * md = EntityLiving, qx = EntityPlayer, lh = DamageSource; md.a(lh, int) = attackEntityFrom.
     */
    public static boolean teslaShock(md target, lh src, int dmg) {
        if (target instanceof qx && ic2TeslaNoPlayerDamage()) {
            return false;
        }
        return target.a(src, dmg);
    }

    private static final String TESLA_CFG = "config/TeslaCoil.cfg";
    private static volatile Boolean ic2TeslaNoPlayer;

    public static boolean ic2TeslaNoPlayerDamage() {
        Boolean b = ic2TeslaNoPlayer;
        if (b == null) {
            b = loadTesla();
        }
        return b.booleanValue();
    }

    private static synchronized Boolean loadTesla() {
        if (ic2TeslaNoPlayer != null) {
            return ic2TeslaNoPlayer;
        }
        boolean v = false;
        try {
            File f = new File(TESLA_CFG);
            if (f.isFile()) {
                Properties p = new Properties();
                FileInputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
                v = Boolean.parseBoolean(p.getProperty("basicTeslaCoil.noPlayerDamage", "false").trim());
            } else {
                writeTeslaDefault(f);
            }
        } catch (Throwable t) {
            // Unreadable config: keep the coil at its normal behaviour.
        }
        ic2TeslaNoPlayer = Boolean.valueOf(v);
        return ic2TeslaNoPlayer;
    }

    /**
     * Writes the shared config/TeslaCoil.cfg with all three tesla flags if it is missing. TLiteARS
     * writes the same three keys, so whichever coil fires first creates an identical file.
     */
    private static void writeTeslaDefault(File f) {
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
                w.write("# Deny the drops of mobs it kills, so a grinder clears mobs without loot.\n");
                w.write("industrialTeslaCoil.denyMobDrops=false\n");
                w.write("# Never damage players with the Industrial Tesla Coil (PvE).\n");
                w.write("industrialTeslaCoil.noPlayerDamage=false\n");
            } finally {
                w.close();
            }
        } catch (Throwable t) {
            // Best effort.
        }
    }
}
