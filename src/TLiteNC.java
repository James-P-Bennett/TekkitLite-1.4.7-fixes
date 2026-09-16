import shedar.mods.ic2.nuclearcontrol.utils.ItemStackUtils;

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
 * The reach gate still leaves a player standing next to another player's panel able to flood that
 * one card, since the card packet writes a client-named field into the card's NBT with setInt,
 * setBoolean, setLong or setString and there is no bound on how many distinct keys are added. The
 * cardcap patch routes each of those through allowCardField, which refuses a new key once the card
 * already holds MAX_CARD_FIELDS of them, so the card's NBT cannot be grown without limit. A legit
 * card uses only a handful of fields, well under the cap, so normal use is unaffected.
 *
 * yc = World, iq = EntityPlayerMP, any = TileEntity, ur = ItemStack, bq = NBTTagCompound;
 * iq.e(DDD) = getDistanceSq, yc.q = getBlockTileEntity, bq.b(String) = hasKey, bq.c() = the tags.
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

    /** A card of any type uses only a few fields; this is far above that and far below a size that
     *  could threaten the chunk's save. */
    public static final int MAX_CARD_FIELDS = 32;

    /** True if the card may take field {@code name}: the key already exists (an update), the card
     *  has no NBT yet, or it holds fewer than MAX_CARD_FIELDS keys. */
    public static boolean allowCardField(ur card, String name) {
        if (card == null || name == null) {
            return true;
        }
        bq tag = ItemStackUtils.getTagCompound(card);
        if (tag == null) {
            return true;
        }
        if (tag.b(name)) {
            return true;
        }
        return tag.c().size() < MAX_CARD_FIELDS;
    }
}
