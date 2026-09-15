import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

import cofh.core.CoreUtils;

import thermalexpansion.core.PowerProviderAdv;
import thermalexpansion.core.network.PacketTile;

/**
 * Exploit fixes injected by TekkitLite-1.4.7-fixes into ThermalExpansion 2.2.2.2 (PatchTE).
 *
 * TE's server packet handler looks up the tile at the packet's coordinates and runs the
 * packet on it with no owner, reach or open-GUI check. That let a client set any Energy
 * Cell's stored energy (energy from nothing), retune or seize any Tesseract and pull another
 * player's items through it, and scramble any machine's I/O faces, from anywhere.
 *
 * Two fixes:
 *   - The handler's PacketTile.getTarget goes through gateTarget, which returns the tile only
 *     when the sender has that exact tile's TE container open and still usable. Every real TE
 *     settings packet is sent from the tile's own GUI, so stock clients are unaffected.
 *   - The Energy Cell and Engine apply a client packet's stored energy only on the client (for
 *     display). setEnergyStoredIfClient enforces that, so a player cannot fill their own cell
 *     even with its GUI open. Energy is server-authoritative.
 *
 * Obfuscated 1.4.7 names: yc = World, qx = EntityPlayer, any = TileEntity, rq = Container;
 * any.k = worldObj, any.l/m/n = x/y/z, qx.bL = openContainer, qx.e(DDD) = getDistanceSq,
 * rq.a(qx) = isUseableByPlayer.
 */
public class TLiteTE {

    private static final double REACH_SQ = 8.0 * 8.0;

    /** Container class -> its declared "tile" field, cached. */
    private static final Map tileFields = new WeakHashMap();

    /**
     * Replaces PacketTile.getTarget in the server packet handler. Returns the target tile only
     * when the sender has that tile's own TE container open and within reach; otherwise null,
     * which the handler's instanceof check then skips.
     */
    public static any gateTarget(PacketTile packet, yc world, qx player) {
        any tile = packet.getTarget(world);
        if (tile == null || player == null) {
            return null;
        }
        if (player.e(tile.l + 0.5, tile.m + 0.5, tile.n + 0.5) > REACH_SQ) {
            return null;
        }
        try {
            rq open = player.bL;
            if (open != null && boundTile(open) == tile && open.a(player)) {
                return tile;
            }
        } catch (Throwable t) {
            // fall through to refuse
        }
        return null;
    }

    /** The tile a TE container is bound to. Every TE GUI container declares a field named "tile". */
    private static Object boundTile(rq open) {
        Class cls = open.getClass();
        Field f = (Field) tileFields.get(cls);
        if (f == null) {
            try {
                f = cls.getDeclaredField("tile");
                f.setAccessible(true);
            } catch (Throwable t) {
                return null;
            }
            tileFields.put(cls, f);
        }
        try {
            return f.get(open);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Replaces the setEnergyStored calls in TileEnergyCell.handleTilePacket and
     * TileEngineRoot.handleTilePacket. Stock applied the client's value on both sides; this
     * applies it only on the client, so the server's energy is never set from a packet.
     */
    public static void setEnergyStoredIfClient(PowerProviderAdv provider, float value, yc world) {
        if (provider != null && CoreUtils.isClientWorld(world)) {
            provider.setEnergyStored(value);
        }
    }
}
