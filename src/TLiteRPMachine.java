import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;

import com.eloraam.redpower.core.FrameLib;
import com.eloraam.redpower.core.WorldCoord;

/**
 * Claim guards injected by TekkitLite-1.4.7-fixes into RedPower 2 machines (coremod).
 *
 * The Block Breaker breaks the block in front, the Igniter lights fire against it, and the
 * Deployer uses items on it, all straight through the world with no protection check, so a
 * machine outside a claim reaches into it. Each machine now records the player who placed it
 * (saved to its NBT) and is checked as that owner: the owner's machine works in the owner's
 * claim and is refused in others'. A machine placed before this patch has no owner and is
 * checked as "[RedPower]", a name no claim trusts, so it works only on open ground.
 *
 * The Frame Motor is the same story on a larger scale: it relocates a whole frame structure by
 * writing blocks and tile entities straight to the world, so a motor outside a claim can shove a
 * moving frame across the border. The motor is owner-tracked the same way, and before a move
 * starts, every block it would move and every destination it would write is checked as the owner;
 * if any is protected the whole move is refused (RedPower's own abort path, so nothing is touched).
 *
 * yc = World, qx = EntityPlayer, md = EntityLiving, any = TileEntity, bq = NBTTagCompound;
 * yc.e = setBlockWithNotify, qx.bR = username, bq.a = setString, bq.b = hasKey, bq.i = getString.
 */
public class TLiteRPMachine {

    static final String NO_OWNER = "[RedPower]";
    static final String OWNER_TAG = "tliteOwner";

    private static final Map owners = new WeakHashMap();   // any -> String

    /** Appended to TileMachine.onBlockPlaced: records the placing player. */
    public static void recordOwner(any tile, md placer) {
        if (tile != null && placer instanceof qx) {
            owners.put(tile, ((qx) placer).bR);
        }
    }

    /** Appended to TileMachine.writeToNBT. */
    public static void saveOwner(any tile, bq nbt) {
        String owner = (String) owners.get(tile);
        if (owner != null) {
            nbt.a(OWNER_TAG, owner);
        }
    }

    /** Appended to TileMachine.readFromNBT. */
    public static void loadOwner(any tile, bq nbt) {
        if (nbt.b(OWNER_TAG)) {
            owners.put(tile, nbt.i(OWNER_TAG));
        }
    }

    /** Replaces world.setBlockWithNotify in TileBreaker (break the block in front). */
    public static boolean breakIfAllowed(yc world, int x, int y, int z, int blockId, any tile) {
        if (allowed(tile, world, x, y, z, "Block Breaker")) {
            return world.e(x, y, z, blockId);
        }
        return false;
    }

    /** Replaces the fire-setting world.setBlockWithNotify in TileIgniter. */
    public static boolean igniteIfAllowed(yc world, int x, int y, int z, int blockId, any tile) {
        if (allowed(tile, world, x, y, z, "Igniter")) {
            return world.e(x, y, z, blockId);
        }
        return false;
    }

    /** Guards the top of TileDeployBase.tryUseItemStack (the block in front). */
    public static boolean deployAllowed(any tile, yc world, int x, int y, int z) {
        return allowed(tile, world, x, y, z, "Deployer");
    }

    /**
     * Called in TileMotor.pickFrame just before FrameSolver.addMoved (which itself starts writing
     * to the world). Returns true only if the motor's owner may edit every block the solved frame
     * would move and every destination it would write. A false return makes pickFrame abort, so no
     * block is touched. Server side only; on the client it defers to the server (true).
     */
    public static boolean frameAllowed(any motor, FrameLib.FrameSolver fs, int moveDir) {
        yc world = motor == null ? null : motor.k;
        if (world == null || world.I) {
            return true;
        }
        try {
            String owner = (String) owners.get(motor);
            qx player = CraftFakePlayer.get(world, owner == null ? NO_OWNER : owner, false);
            for (Object o : fs.getFrameSet()) {
                WorldCoord wc = (WorldCoord) o;
                if (!TLiteProtect.canEdit(player, world, wc.x, wc.y, wc.z)) {
                    TLiteProtect.refused(player, "Frame motor at " + wc.x + "," + wc.y + "," + wc.z + " (protected)");
                    return false;
                }
                WorldCoord d = wc.coordStep(moveDir);
                if (!TLiteProtect.canEdit(player, world, d.x, d.y, d.z)) {
                    TLiteProtect.refused(player, "Frame motor into " + d.x + "," + d.y + "," + d.z + " (protected)");
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Generic guarded setBlockWithNotify for an automated tile (Thermopile lava/fire consume, Grate
     * drain). Checks the tile's owner (or [RedPower] when none, so it works only on open ground and,
     * for an owner-tracked tile, in that owner's claim). Refused edits are simply skipped.
     */
    public static boolean tileSet(yc world, int x, int y, int z, int id, any tile) {
        if (allowed(tile, world, x, y, z, "RedPower")) {
            return world.e(x, y, z, id);
        }
        return false;
    }

    /** Generic guarded setBlockAndMetadataWithNotify (Thermopile water -> obsidian/stone). */
    public static boolean tileSetMeta(yc world, int x, int y, int z, int id, int meta, any tile) {
        if (allowed(tile, world, x, y, z, "RedPower")) {
            return world.d(x, y, z, id, meta);
        }
        return false;
    }

    private static boolean allowed(any tile, yc world, int x, int y, int z, String what) {
        if (world == null) {
            return false;
        }
        String owner = (String) owners.get(tile);
        try {
            qx player = CraftFakePlayer.get(world, owner == null ? NO_OWNER : owner, false);
            if (TLiteProtect.canEdit(player, world, x, y, z)) {
                return true;
            }
            TLiteProtect.refused(player, what + " at " + x + "," + y + "," + z + " (protected)");
        } catch (Throwable t) {
            // fall through to refuse
        }
        return false;
    }
}
