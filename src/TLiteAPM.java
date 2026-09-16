import com.kaijin.AdvPowerMan.TECommon;

/**
 * Exploit fix injected by TekkitLite-1.4.7-fixes into AdvancedPowerManagement 1.1.55 (PatchAPM).
 *
 * The server packet handler read a Battery Station / Charging Bench's coordinates from the client
 * and called receiveGuiButton with no reach or ownership check, so a player could toggle any such
 * machine's operating mode or emitter packet size from anywhere. The button now only applies when
 * the sender is within reach of the machine in the same world.
 *
 * yc = World, qx = EntityPlayer, any = TileEntity; TECommon extends any (k/l/m/n = world/x/y/z),
 * qx.e(DDD) = getDistanceSq.
 */
public class TLiteAPM {

    /** Replaces TECommon.receiveGuiButton in ServerPacketHandler.onPacketData. */
    public static void guiButton(TECommon tile, int button, qx player) {
        if (tile == null || player == null) {
            return;
        }
        if (tile.k == player.p && player.e(tile.l + 0.5, tile.m + 0.5, tile.n + 0.5) <= 64.0) {
            tile.receiveGuiButton(button);
        }
    }

    /**
     * The Battery Station's moveOutputItems increments the output slot's stack by one whenever a
     * discharged electric item is pulled from an input slot, without checking that the output slot
     * already holds the same item. Discharging a different empty electric item into an occupied
     * output slot therefore mints the output item. The outputdupe patch guards that increment with
     * this: the merge only happens when the two stacks are actually stackable (same item, same
     * damage, matching tags), which is exactly the normal case, so real use is unaffected.
     *
     * ur = ItemStack; ur.c = itemID, ur.j() = getItemDamage, ur.a(ur, ur) = areItemStackTagsEqual.
     */
    public static boolean canMerge(ur out, ur src) {
        return out != null && src != null && out.c == src.c && out.j() == src.j() && ur.a(out, src);
    }
}
