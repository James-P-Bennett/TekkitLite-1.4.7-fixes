import java.util.Map;
import java.util.WeakHashMap;

import bspkrs.treecapitator.TreeBlockBreaker;
import bspkrs.util.Coord;

/**
 * Protection fix injected by TekkitLite-1.4.7-fixes into TreeCapitator 1.4.6 r07 (PatchTC).
 *
 * Obfuscated 1.4.7 names: qx = EntityPlayer, yc = World; yc.a(int, int, int) = getBlockId.
 */
public class TLiteTreeCap {

    /** Fellings that already hit a protected block. Weak, so a finished felling is dropped. */
    private static final Map refused = new WeakHashMap();

    /**
     * Replaces world.getBlockId at the top of the loop in TreeBlockBreaker.destroyBlocksWithChance,
     * which breaks every log and leaf the felling found. Stock breaks them all with no check, so
     * one log broken on open ground fells every log joined to it inside someone else's claim.
     *
     * Returns 0 (air) for a block the player may not break, which the loop then skips. After the
     * first refusal the rest of that felling is skipped too, so a claim costs one refusal message
     * instead of one per log. The log the player actually broke already passed the server's own
     * break check, so it is not asked again.
     */
    public static int getBlockId(yc world, int x, int y, int z, TreeBlockBreaker breaker, Coord start) {
        if (start != null && start.x == x && start.y == y && start.z == z) {
            return world.a(x, y, z);
        }
        if (refused.containsKey(breaker)) {
            return 0;
        }
        int id = world.a(x, y, z);
        if (id == 0) {
            return 0;
        }
        if (!TLiteProtect.canEdit(breaker.player, world, x, y, z)) {
            refused.put(breaker, Boolean.TRUE);
            TLiteProtect.refused(breaker.player, "TreeCapitator felling at " + x + "," + y + "," + z + " (protected)");
            return 0;
        }
        return id;
    }
}
