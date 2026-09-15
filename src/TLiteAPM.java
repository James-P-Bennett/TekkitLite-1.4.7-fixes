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
}
