import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;

/**
 * Claim guards injected by TekkitLite-1.4.7-fixes into RedPower 2 machines (coremod).
 *
 * The Block Breaker breaks the block in front, and the Igniter lights fire against it, both
 * straight through the world with no protection check, so a machine outside a claim reaches into
 * it. These machines record no owner, so the check runs as an offline fake player named
 * "[RedPower]", a name no claim trusts: refused inside every claim, allowed on open ground.
 *
 * yc = World; yc.e = setBlockWithNotify.
 */
public class TLiteRPMachine {

    static final String NAME = "[RedPower]";

    /** Replaces world.setBlockWithNotify in TileBreaker (break the block in front). */
    public static boolean breakIfAllowed(yc world, int x, int y, int z, int blockId) {
        if (allowed(world, x, y, z, "Block Breaker")) {
            return world.e(x, y, z, blockId);
        }
        return false;
    }

    /** Replaces the fire-setting world.setBlockWithNotify in TileIgniter (fire removal is not routed here). */
    public static boolean igniteIfAllowed(yc world, int x, int y, int z, int blockId) {
        if (allowed(world, x, y, z, "Igniter")) {
            return world.e(x, y, z, blockId);
        }
        return false;
    }

    private static boolean allowed(yc world, int x, int y, int z, String what) {
        if (world == null) {
            return false;
        }
        try {
            qx player = CraftFakePlayer.get(world, NAME, false);
            if (TLiteProtect.canEdit(player, world, x, y, z)) {
                return true;
            }
            TLiteProtect.refused(player, what + " at " + x + "," + y + "," + z + " (protected)");
        } catch (Throwable t) {
            // fall through to refuse
        }
        return false;
    }
}
