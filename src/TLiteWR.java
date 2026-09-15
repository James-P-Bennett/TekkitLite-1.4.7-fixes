import codechicken.core.BlockCoord;
import codechicken.wirelessredstone.core.RedstoneEther;

/**
 * Exploit fixes injected by TekkitLite-1.4.7-fixes into WR-CBE (Wireless Redstone) Core (PatchWR).
 *
 * The server packet handler accepted two packets it should never take from a client:
 *   - packet 9 reassigned any frequency's owner to any name, letting a player seize another
 *     player's private frequency. It is only ever a server-to-client broadcast, so it is dropped.
 *   - packet 1 retuned any wireless tile at attacker-chosen coordinates, gated only by a
 *     per-frequency check. The tile lookup is now gated to tiles within reach of the sender.
 *
 * Obfuscated 1.4.7 names: yc = World, qx = EntityPlayer, any = TileEntity; any.l/m/n = x/y/z,
 * qx.e(DDD) = getDistanceSq.
 */
public class TLiteWR {

    private static final double REACH_SQ = 8.0 * 8.0;

    /** Replaces RedstoneEther.setFreqOwner in the packet-9 branch; discards the client's write. */
    public static void dropFreqOwner(RedstoneEther ether, int freq, String name) {
        // a client never legitimately sets a frequency's owner; ignored
    }

    /** Replaces RedstoneEther.getTile in setTileFreq; returns the tile only when within reach. */
    public static any gateTile(yc world, BlockCoord pos, qx sender) {
        any tile = RedstoneEther.getTile(world, pos);
        if (tile == null || sender == null) {
            return null;
        }
        if (sender.e(tile.l + 0.5, tile.m + 0.5, tile.n + 0.5) > REACH_SQ) {
            return null;
        }
        return tile;
    }
}
