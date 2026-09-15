import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;

import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.IBox;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.core.IBuilderInventory;
import buildcraft.core.blueprints.BptBuilderBase;
import buildcraft.core.blueprints.BptSlot;
import buildcraft.core.utils.BlockUtil;
import buildcraft.factory.TileQuarry;

/**
 * Protection fixes injected by TekkitLite-1.4.7-fixes into BuildCraft 3.4.3 (PatchBC).
 *
 * The Quarry and the Filler change blocks in their area with no player at hand, so a machine
 * next to a claim mines or clears the inside of it. Each machine now remembers who placed it,
 * and every block it would change is checked with TLiteProtect as that player, through an
 * offline MCPC+ fake player with the owner's name. The fake player has no connection, so
 * GriefPrevention's refusal messages go nowhere and nobody gets spammed.
 *
 * A machine placed before this patch has no owner and is checked as "[BuildCraft]", a name no
 * claim trusts: it keeps working on open ground and is refused inside every claim. Right
 * clicking an ownerless Filler with permission to break it there makes that player its owner.
 *
 * Obfuscated 1.4.7 names: yc = World, qx = EntityPlayer, iq = EntityPlayerMP, md = EntityLiving,
 * any = TileEntity, bq = NBTTagCompound, ur = ItemStack, up = Item; qx.bR = username,
 * yc.q = getBlockTileEntity, yc.e(IIII) = setBlockWithNotify, any.k = worldObj,
 * bq.a(String, String) = setString, bq.b(String) = hasKey, bq.i(String) = getString,
 * up.a(ur, qx, yc, IIIIFFF) = onItemUse, xv = ChunkCoordIntPair.
 */
public class TLiteBC {

    static final String NO_OWNER = "[BuildCraft]";
    static final String OWNER_TAG = "tliteOwner";

    /** A Filler refused inside a claim stops, and rests this long before asking again. */
    static final long FILLER_REST_MS = 10000L;

    private static final Map owners = new WeakHashMap();            // any -> String
    private static final Map refusedColumns = new WeakHashMap();    // TileQuarry -> Set of Long
    private static final Map fillerRestUntil = new WeakHashMap();   // any -> Long

    private static any currentFiller;
    private static boolean fillerRefused;

    // ------------------------------------------------------------------ owners

    /** Called at the end of BlockQuarry and BlockFiller onBlockPlacedBy. */
    public static void placed(yc world, int x, int y, int z, md placer) {
        if (world == null || !(placer instanceof qx)) {
            return;
        }
        any te = world.q(x, y, z);
        if (te != null) {
            owners.put(te, ((qx) placer).bR);
        }
    }

    /** Called first in BlockFiller onBlockActivated: an ownerless Filler is claimed by a player allowed to break it. */
    public static void adopt(yc world, int x, int y, int z, qx player) {
        if (world == null || player == null) {
            return;
        }
        any te = world.q(x, y, z);
        if (te != null && !owners.containsKey(te) && TLiteProtect.canEdit(player, world, x, y, z)) {
            owners.put(te, player.bR);
        }
    }

    /** Called at the end of TileQuarry and TileFiller writeToNBT. */
    public static void save(any te, bq tag) {
        String owner = (String) owners.get(te);
        if (owner != null) {
            tag.a(OWNER_TAG, owner);
        }
    }

    /** Called at the end of TileQuarry and TileFiller readFromNBT. */
    public static void load(any te, bq tag) {
        if (tag.b(OWNER_TAG)) {
            owners.put(te, tag.i(OWNER_TAG));
        }
    }

    static boolean allowed(any te, yc world, int x, int y, int z, String what) {
        if (world == null) {
            return false;
        }
        String owner = (String) owners.get(te);
        iq player;
        try {
            player = CraftFakePlayer.get(world, owner == null ? NO_OWNER : owner, false);
        } catch (Throwable t) {
            return false;
        }
        if (TLiteProtect.canEdit(player, world, x, y, z)) {
            return true;
        }
        TLiteProtect.refused(player, what + " at " + x + "," + y + "," + z + " (protected)");
        return false;
    }

    // ------------------------------------------------------------------ Quarry

    /**
     * Replaces isQuarriableBlock(x, y, z) in TileQuarry.positionReached, just before the quarry
     * mines the block under its head. Same test as stock, plus the owner's permission. A refused
     * block's column is remembered so the quarry treats it like bedrock from then on.
     */
    public static boolean quarriable(TileQuarry quarry, int x, int y, int z) {
        yc world = quarry.k;
        if (!BlockUtil.canChangeBlock(world, x, y, z) || BlockUtil.isSoftBlock(world, x, y, z)) {
            return false;
        }
        if (allowed(quarry, world, x, y, z, "Quarry")) {
            return true;
        }
        columns(quarry).add(Long.valueOf(column(x, z)));
        return false;
    }

    /**
     * Replaces BlockUtil.canChangeBlock in TileQuarry.findTarget and createColumnVisitList, which
     * pick the next column to dig. Refused columns read as unchangeable, so the quarry moves on
     * instead of returning to the same refused block forever.
     */
    public static boolean quarryCanChange(yc world, int x, int y, int z, TileQuarry quarry) {
        Set set = (Set) refusedColumns.get(quarry);
        if (set != null && set.contains(Long.valueOf(column(x, z)))) {
            return false;
        }
        return BlockUtil.canChangeBlock(world, x, y, z);
    }

    /**
     * Replaces bluePrintBuilder.getNextBlock in TileQuarry.buildFrame, which hands the next frame
     * block to the builder robot. A frame slot inside a claim is dropped: getNextBlock has already
     * taken it off the list, and the robot ignores null.
     */
    public static BptSlot nextSlot(BptBuilderBase builder, yc world, IBuilderInventory inv) {
        BptSlot slot = builder.getNextBlock(world, inv);
        if (slot == null || !(inv instanceof any)) {
            return slot;
        }
        return allowed((any) inv, world, slot.x, slot.y, slot.z, "Quarry frame") ? slot : null;
    }

    private static Set columns(TileQuarry quarry) {
        Set set = (Set) refusedColumns.get(quarry);
        if (set == null) {
            set = new HashSet();
            refusedColumns.put(quarry, set);
        }
        return set;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // ------------------------------------------------------------------ Quarry chunk loading

    /**
     * Replaces ticket.getMaxChunkListDepth() in TileQuarry.setBoundaries, where stock refuses a
     * marker area when (xSize * zSize) >> 8 reaches the ticket's chunk limit. That estimate is
     * too low: a 64x64 area can span 5x5 chunks, and the quarry forces its own chunk as well.
     * Returns 0, which makes stock refuse the area and use the default one, when the chunks the
     * quarry would force are more than the ticket holds.
     */
    public static int quarryChunkDepth(Ticket ticket, TileQuarry quarry, IAreaProvider area) {
        int depth = ticket.getMaxChunkListDepth();
        if (area == null) {
            return depth;
        }
        if (area.xMax() - area.xMin() > 4096 || area.zMax() - area.zMin() > 4096) {
            return 0;
        }
        Set chunks = new HashSet();
        chunks.add(Long.valueOf(column(quarry.l >> 4, quarry.n >> 4)));
        for (int cx = area.xMin() >> 4; cx <= area.xMax() >> 4; cx++) {
            for (int cz = area.zMin() >> 4; cz <= area.zMax() >> 4; cz++) {
                chunks.add(Long.valueOf(column(cx, cz)));
            }
        }
        return chunks.size() > depth ? 0 : depth;
    }

    /**
     * Called at the end of TileQuarry.forceChunkLoading. Forge drops a ticket's oldest chunk once
     * it holds more than its limit, and the quarry forces its own chunk first. So a quarry whose
     * area fills the ticket loses its own chunk: it unloads and stops, while its whole area stays
     * loaded, and the ticket brings that back after every restart. Forcing the quarry's chunk
     * again makes it the newest, so an area chunk is dropped instead.
     */
    public static void keepQuarryChunk(TileQuarry quarry, Ticket ticket) {
        if (ticket == null) {
            return;
        }
        xv own = new xv(quarry.l >> 4, quarry.n >> 4);
        ForgeChunkManager.unforceChunk(ticket, own);
        ForgeChunkManager.forceChunk(ticket, own);
    }

    /**
     * Replaces the cast and forceChunkLoading call in BuildCraftFactory's chunk ticket callback,
     * which runs as the world loads. Stock calls forceChunkLoading on whatever tile entity is at
     * the ticket's quarry position, so a quarry block without its tile entity crashes the world
     * load. A ticket with no quarry behind it is released instead.
     */
    public static void reloadQuarryTicket(Object tile, Ticket ticket) {
        if (tile instanceof TileQuarry) {
            ((TileQuarry) tile).forceChunkLoading(ticket);
            return;
        }
        ForgeChunkManager.releaseTicket(ticket);
        System.out.println("[TLiteFixes] released a BuildCraft quarry chunk ticket with no quarry behind it");
    }

    // ------------------------------------------------------------------ Filler

    /**
     * Replaces currentPattern.iteratePattern(this, box, stack) in TileFiller.doWork, one work
     * step of the Filler. Remembers which Filler is working so the block changes below can be
     * checked against its owner. When one is refused the step reports the pattern done, so the
     * Filler stops, and it rests before trying again so Loop mode can't retry every tick.
     */
    public static boolean iterateFiller(IFillerPattern pattern, any filler, IBox box, ur stack) {
        long now = System.currentTimeMillis();
        Long until = (Long) fillerRestUntil.get(filler);
        if (until != null && now < until.longValue()) {
            return true;
        }
        any previous = currentFiller;
        boolean previousRefused = fillerRefused;
        currentFiller = filler;
        fillerRefused = false;
        try {
            boolean done = pattern.iteratePattern(filler, box, stack);
            if (fillerRefused) {
                fillerRestUntil.put(filler, Long.valueOf(now + FILLER_REST_MS));
                return true;
            }
            return done;
        } finally {
            currentFiller = previous;
            fillerRefused = previousRefused;
        }
    }

    /** Replaces item.onItemUse in FillerPattern.fill and FillerFlattener, where a block is placed next to x, y, z on side. */
    public static boolean fillerUse(up item, ur stack, qx player, yc world, int x, int y, int z, int side,
                                    float hitX, float hitY, float hitZ) {
        int px = x, py = y, pz = z;
        switch (side) {
            case 0: py--; break;
            case 1: py++; break;
            case 2: pz--; break;
            case 3: pz++; break;
            case 4: px--; break;
            case 5: px++; break;
            default: break;
        }
        if (!fillerAllows(world, px, py, pz)) {
            return false;
        }
        return item.a(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
    }

    /** Replaces world.setBlockWithNotify in FillerPattern.empty (filler.destroy=true). */
    public static boolean fillerSet(yc world, int x, int y, int z, int id) {
        if (!fillerAllows(world, x, y, z)) {
            return false;
        }
        return world.e(x, y, z, id);
    }

    /** Replaces BlockUtil.breakBlock in FillerPattern.empty (filler.destroy=false). */
    public static void fillerBreak(yc world, int x, int y, int z, int lifespan) {
        if (fillerAllows(world, x, y, z)) {
            BlockUtil.breakBlock(world, x, y, z, lifespan);
        }
    }

    private static boolean fillerAllows(yc world, int x, int y, int z) {
        if (currentFiller == null) {
            return true;
        }
        if (fillerRefused) {
            return false;
        }
        if (allowed(currentFiller, world, x, y, z, "Filler")) {
            return true;
        }
        fillerRefused = true;
        return false;
    }
}
