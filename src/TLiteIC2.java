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
}
