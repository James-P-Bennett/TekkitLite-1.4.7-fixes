/**
 * Exploit fix injected by TekkitLite-1.4.7-fixes into IC2NuclearControl 1.4.6 (PatchNC).
 *
 * CommonProxy.onPacketData read block coordinates from the client packet and looked up the tile
 * with no reach check, then wrote client data into it: an attacker-named sound onto any Howler
 * Alarm (remote alarm spam) and attacker-named fields into any Info Panel's sensor card, which
 * is persisted to NBT. From across the map that let a player spam alarms and bloat a panel's NBT
 * until its chunk fails to save. Each lookup now requires the sender to be within reach of the
 * tile, so these can only touch tiles next to the sender.
 *
 * yc = World, iq = EntityPlayerMP, any = TileEntity; iq.e(DDD) = getDistanceSq, yc.q = getBlockTileEntity.
 */
public class TLiteNC {

    private static final double REACH_SQ = 8.0 * 8.0;

    /** Replaces world.getBlockTileEntity(x, y, z) in onPacketData; returns null when out of reach. */
    public static any gate(yc world, int x, int y, int z, iq player) {
        if (world == null || player == null) {
            return null;
        }
        if (player.e(x + 0.5, y + 0.5, z + 0.5) > REACH_SQ) {
            return null;
        }
        return world.q(x, y, z);
    }
}
