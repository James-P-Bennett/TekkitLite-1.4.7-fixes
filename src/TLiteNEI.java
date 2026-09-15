import codechicken.core.BlockCoord;
import codechicken.nei.NEIServerConfig;
import codechicken.nei.ServerPacketHandler;

/**
 * Exploit fixes injected by TekkitLite-1.4.7-fixes into NotEnoughItems 1.4.7.0 (PatchNEI).
 *
 * Obfuscated 1.4.7 names: iq = EntityPlayerMP, yc = World, any = TileEntity,
 * ans = TileEntityMobSpawner, lv = EntityList, md = EntityLiving;
 * qx.bR = username, lq.e(double, double, double) = getDistanceSq, yc.q = getBlockTileEntity,
 * yc.i = markBlockForUpdate, ans.a(String) = setMobID, any.d = onInventoryChanged,
 * lv.b = stringToClassMapping.
 */
public class TLiteNEI {

    /** Vanilla block reach, squared, measured to the block's centre. */
    private static final double REACH_SQ = 8.0 * 8.0;

    /**
     * Replaces ServerPacketHandler.handleMobSpawnerID for NEI packet 15, which the client sends
     * after placing a mob spawner from NEI to set its mob.
     *
     * NEI authenticates every other world changing packet, but not this one, and the handler
     * trusts the packet completely: any player, any coordinates at any distance, inside any
     * claim, and any string as the mob name.
     *
     * The request now needs a spawner within 8 blocks, a mob name that is a living entity, and
     * permission from protection plugins to change that block.
     */
    public static void handleMobSpawnerID(ServerPacketHandler handler, yc world, BlockCoord coord, String mobtype, iq sender) {
        if (sender == null || world == null || coord == null || mobtype == null) {
            return;
        }
        int x = coord.x, y = coord.y, z = coord.z;
        if (sender.e(x + 0.5, y + 0.5, z + 0.5) > REACH_SQ) {
            TLiteProtect.refused(sender, "NEI spawner change at " + x + "," + y + "," + z + " (out of reach)");
            return;
        }
        Class mob = (Class) lv.b.get(mobtype);
        if (mob == null || !md.class.isAssignableFrom(mob)) {
            TLiteProtect.refused(sender, "NEI spawner change to " + mobtype + " (not a mob)");
            return;
        }
        any tile = world.q(x, y, z);
        if (!(tile instanceof ans)) {
            return;
        }
        if (!TLiteProtect.guard(sender, world, x, y, z, "NEI spawner change")) {
            return;
        }
        ((ans) tile).a(mobtype);
        tile.d();
        world.i(x, y, z);
    }

    /**
     * Called first in NEIServerUtils.toggleCreativeMode. NEI packet 13 switches the sender
     * between survival and creative, and NEI never checks the "creative" permission for it:
     * the client only hides the button. Any player could put themselves in creative mode.
     */
    public static boolean canToggleCreative(iq player) {
        if (player != null && NEIServerConfig.canPlayerUseFeature(player.bR, "creative")) {
            return true;
        }
        TLiteProtect.refused(player, "NEI creative mode (no creative permission)");
        return false;
    }
}
