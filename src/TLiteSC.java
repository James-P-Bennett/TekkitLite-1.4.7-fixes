import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;

import vswe.stevescarts.Carts.entMCBase;

/**
 * Claim guards injected by TekkitLite-1.4.7-fixes into Steve's Carts (PatchSC).
 *
 * Mining, farming, melter and similar cart modules break and place blocks straight through the
 * world with no protection check, so a cart running past a claim mines or builds into it. Each
 * cart now records the player who deployed it (saved to its NBT), and every module block change
 * is checked as that owner: the owner's cart works in the owner's claim, refused in others'. A
 * cart deployed before this patch, or by something other than a player, has no owner and is
 * checked as "[StevesCarts]", a name no claim trusts, so it works only on open ground.
 *
 * yc = World, qx = EntityPlayer, lq = Entity, bq = NBTTagCompound; yc.e = setBlockWithNotify,
 * yc.d(IIIII) = setBlockAndMetadataWithNotify, yc.d(lq) = spawnEntityInWorld, qx.bR = username.
 */
public class TLiteSC {

    static final String NO_OWNER = "[StevesCarts]";
    static final String OWNER_TAG = "tliteOwner";

    private static final Map owners = new WeakHashMap();   // entMCBase -> String

    /** Replaces world.spawnEntityInWorld in ItemCarts placement; records the deploying player. */
    public static boolean spawnCart(yc world, lq entity, qx player) {
        if (entity instanceof entMCBase && player != null) {
            owners.put(entity, player.bR);
        }
        return world.d(entity);
    }

    /** Appended to entMCBase.writeEntityToNBT. */
    public static void saveOwner(entMCBase cart, bq nbt) {
        String owner = (String) owners.get(cart);
        if (owner != null) {
            nbt.a(OWNER_TAG, owner);
        }
    }

    /** Appended to entMCBase.readEntityFromNBT. */
    public static void loadOwner(entMCBase cart, bq nbt) {
        if (nbt.b(OWNER_TAG)) {
            owners.put(cart, nbt.i(OWNER_TAG));
        }
    }

    /** Replaces world.setBlockWithNotify in a cart module (break). */
    public static boolean breakIfAllowed(yc world, int x, int y, int z, int id, entMCBase cart) {
        if (allowed(cart, world, x, y, z)) {
            return world.e(x, y, z, id);
        }
        return false;
    }

    /** Replaces world.setBlockAndMetadataWithNotify in a cart module (place). */
    public static boolean placeIfAllowed(yc world, int x, int y, int z, int id, int meta, entMCBase cart) {
        if (allowed(cart, world, x, y, z)) {
            return world.d(x, y, z, id, meta);
        }
        return false;
    }

    private static boolean allowed(entMCBase cart, yc world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        String owner = (String) owners.get(cart);
        try {
            qx player = CraftFakePlayer.get(world, owner == null ? NO_OWNER : owner, false);
            if (TLiteProtect.canEdit(player, world, x, y, z)) {
                return true;
            }
            TLiteProtect.refused(player, "Steve's Cart at " + x + "," + y + "," + z + " (protected)");
        } catch (Throwable t) {
            // fall through to refuse
        }
        return false;
    }

    // ------------------------------------------------------------ chunk-loader cap

    /**
     * The Steve's Carts chunk-loader module force-loads chunks through ForgeChunkManager, outside the
     * shared chunk-loader quota that ChickenChunks, the Dimensional Anchor and the Teleport Tether
     * use. Each cart loader is now cut to a single chunk (PatchSC) and counted against its owner's
     * shared per-player limit here: refused at activation and skipped as the cart moves once the
     * owner is at their limit or offline past the grace window. A cart with no recorded owner
     * (deployed before this patch, or not by a player) is not capped, matching the module edit guard.
     */
    public static boolean chunkAllowed(entMCBase cart) {
        return chunkClaim(cart, false);
    }

    /** As chunkAllowed, but messages the owner when the loader is refused (used at activation). */
    public static boolean chunkAllowedAnnounce(entMCBase cart) {
        return chunkClaim(cart, true);
    }

    private static boolean chunkClaim(entMCBase cart, boolean announce) {
        String owner = (String) owners.get(cart);
        if (owner == null) {
            return true;
        }
        return TLiteChunkQuota.scClaim(owner, cartKey(cart), announce);
    }

    /** Appended to entMCBase.dropChunkLoading: drop this cart from the shared loader registry. */
    public static void chunkReleased(entMCBase cart) {
        String owner = (String) owners.get(cart);
        if (owner != null) {
            TLiteChunkQuota.scRelease(owner, cartKey(cart));
        }
    }

    private static String cartKey(entMCBase cart) {
        return "cart:" + System.identityHashCode(cart);
    }
}
