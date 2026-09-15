import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;

import weaponmod.projectile.EntityProjectile;

/**
 * Protection fix injected by TekkitLite-1.4.7-fixes into Balkon's Weaponmod (PatchWM).
 *
 * Dynamite (and the cannon) explode through their own AdvancedExplosion, which removes blocks
 * with world.setBlockWithNotify instead of a vanilla explosion, so it never fires the Bukkit
 * EntityExplodeEvent that GriefPrevention filters. That let a thrown stick of dynamite blow up
 * blocks inside another player's claim. Each block the explosion would remove is now checked
 * against the player who threw it; a block they cannot break is left alone.
 *
 * Obfuscated 1.4.7 names: yc = World, qx = EntityPlayer, lq = Entity; yc.e = setBlockWithNotify.
 */
public class TLiteWM {

    /** Replaces world.setBlockWithNotify(x, y, z, 0) in AdvancedExplosion.doBlockExplosion. */
    public static boolean breakIfAllowed(yc world, int x, int y, int z, lq exploder) {
        if (world == null) {
            return false;
        }
        qx player = null;
        if (exploder instanceof qx) {
            player = (qx) exploder;
        } else if (exploder instanceof EntityProjectile) {
            lq shooter = ((EntityProjectile) exploder).shootingEntity;
            if (shooter instanceof qx) {
                player = (qx) shooter;
            }
        }
        if (player == null) {
            try {
                player = CraftFakePlayer.get(world, "[Weaponmod]", false);
            } catch (Throwable t) {
                return false;
            }
        }
        if (TLiteProtect.canEdit(player, world, x, y, z)) {
            return world.e(x, y, z, 0);
        }
        TLiteProtect.refused(player, "Weaponmod explosion at " + x + "," + y + "," + z + " (protected)");
        return false;
    }
}
