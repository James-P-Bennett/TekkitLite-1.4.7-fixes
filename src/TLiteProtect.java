import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Protection check shared by the TekkitLite-1.4.7-fixes mod patches.
 *
 * Mods on MCPC+ change blocks straight through the world, so protection plugins never hear
 * about it. Before a patched mod changes a block for a player it asks here, and this asks the
 * plugins the same way a hand break would: a Bukkit BlockBreakEvent for that player at that
 * block. If any plugin cancels it (a GriefPrevention claim, a WorldGuard region), the change
 * is refused.
 *
 * Every patched jar carries an identical copy of this class, so build them all together.
 */
public class TLiteProtect {

    static final String TAG = "[TLiteFixes] ";
    private static final long LOG_INTERVAL_MS = 10000L;

    private static final Map lastLog = Collections.synchronizedMap(new HashMap());
    private static boolean warned = false;

    /**
     * True if protection plugins let this player change the block at x, y, z.
     * Refuses when the check itself fails, so a broken check can never open claims up.
     */
    public static boolean canEdit(qx player, yc world, int x, int y, int z) {
        if (player == null || world == null) {
            return false;
        }
        try {
            Block block = world.getWorld().getBlockAt(x, y, z);
            Player bukkitPlayer = (Player) player.getBukkitEntity();
            BlockBreakEvent event = new BlockBreakEvent(block, bukkitPlayer);
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                System.out.println(TAG + "protection check failed, change refused: " + t);
            }
            return false;
        }
    }

    /**
     * canEdit, plus a log line when it refuses. Patched methods call this first thing and return
     * early when it is false, so the stock body only runs for permitted changes.
     */
    public static boolean guard(qx player, yc world, int x, int y, int z, String what) {
        if (canEdit(player, world, x, y, z)) {
            return true;
        }
        refused(player, what + " at " + x + "," + y + "," + z + " (protected)");
        return false;
    }

    /** Logs a refusal, at most once per player every 10 seconds. */
    public static void refused(qx player, String what) {
        String name = player == null ? "?" : String.valueOf(player.bR);        // EntityPlayer.username
        long now = System.currentTimeMillis();
        Long last = (Long) lastLog.get(name);
        if (last != null && now - last.longValue() < LOG_INTERVAL_MS) {
            return;
        }
        lastLog.put(name, Long.valueOf(now));
        System.out.println(TAG + "refused " + what + " from " + name);
    }
}
