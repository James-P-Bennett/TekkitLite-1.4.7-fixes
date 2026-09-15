import com.pahimar.ee3.core.handlers.WorldTransmutationHandler;
import com.pahimar.ee3.core.helper.TransmutationHelper;
import com.pahimar.ee3.item.IChargeable;
import com.pahimar.ee3.item.ITransmutationStone;

/**
 * Exploit fixes injected by TekkitLite-1.4.7-fixes into EE3 pre1f (PatchEE3).
 *
 * Obfuscated 1.4.7 names: qx = EntityPlayer, yc = World, ur = ItemStack;
 * qx.bS() = getCurrentEquippedItem, ur.b() = getItem, qx.e(double, double, double) = getDistanceSq.
 */
public class TLiteEE3 {

    /** Vanilla block reach, squared, measured to the block's centre. */
    private static final double REACH_SQ = 8.0 * 8.0;

    /**
     * Replaces WorldTransmutationHandler.handleWorldTransmutation in PacketRequestEvent.execute.
     *
     * Stock runs whatever the packet says: any coordinates at any distance, a range of up to 127
     * on every axis, and no check that the player holds a transmutation stone. With nothing in
     * hand the event handler throws a NullPointerException for every block in the range.
     *
     * The request now needs a transmutation stone in hand, an origin within 8 blocks, a target
     * of the form id:meta, and a range no larger than the client itself sends: 1 on the axis the
     * clicked face points along, and 1 + 2 * charge on the other two (charge is 0 for the Minium
     * Stone, which is not chargeable).
     */
    public static void handleWorldTransmutation(qx player, int x, int y, int z,
                                                byte rangeX, byte rangeY, byte rangeZ, byte sideHit, String data) {
        if (player == null) {
            return;
        }
        ur held = player.bS();
        if (held == null || !(held.b() instanceof ITransmutationStone)) {
            TLiteProtect.refused(player, "transmutation without a transmutation stone in hand");
            return;
        }
        if (player.e(x + 0.5, y + 0.5, z + 0.5) > REACH_SQ) {
            TLiteProtect.refused(player, "transmutation at " + x + "," + y + "," + z + " (out of reach)");
            return;
        }
        if (data == null || !data.matches("\\d{1,5}:\\d{1,5}")) {
            TLiteProtect.refused(player, "transmutation with a malformed target");
            return;
        }

        int charge = held.b() instanceof IChargeable ? ((IChargeable) held.b()).getCharge(held) : 0;
        int wide = 1 + 2 * Math.max(0, charge);
        int maxX = 1, maxY = 1, maxZ = 1;
        switch (sideHit) {
            case 0: case 1: maxX = wide; maxZ = wide; break;       // DOWN, UP
            case 2: case 3: maxX = wide; maxY = wide; break;       // NORTH, SOUTH
            case 4: case 5: maxY = wide; maxZ = wide; break;       // WEST, EAST
            default: break;
        }
        if (rangeX < 1 || rangeY < 1 || rangeZ < 1 || rangeX > maxX || rangeY > maxY || rangeZ > maxZ) {
            TLiteProtect.refused(player, "transmutation range " + rangeX + "x" + rangeY + "x" + rangeZ
                    + " (allowed " + maxX + "x" + maxY + "x" + maxZ + ")");
            return;
        }

        WorldTransmutationHandler.handleWorldTransmutation(player, x, y, z, rangeX, rangeY, rangeZ, sideHit, data);
    }

    /**
     * Replaces TransmutationHelper.transmuteInWorld in WorldTransmutationHandler.onWorldTransmutationEvent,
     * which is where each block in the range is actually changed. Stock changes it whoever owns it.
     * Now protection plugins are asked first, per block.
     */
    public static boolean transmuteInWorld(yc world, qx player, ur stack, int x, int y, int z, int targetID, int targetMeta) {
        if (!TLiteProtect.canEdit(player, world, x, y, z)) {
            TLiteProtect.refused(player, "transmutation at " + x + "," + y + "," + z + " (protected)");
            return false;
        }
        return TransmutationHelper.transmuteInWorld(world, player, stack, x, y, z, targetID, targetMeta);
    }
}
