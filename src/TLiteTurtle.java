import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;

import dan200.turtle.shared.TileEntityTurtle;

/**
 * Protection fixes injected by TekkitLite-1.4.7-fixes into ComputerCraft 1.5 turtles (PatchCC).
 *
 * Turtles dig, attack, place, move, take from and drop into the block next to them with no
 * player at hand, so a turtle outside a claim digs, fills or empties the inside of it, and can
 * drive straight in. Each turtle now remembers who placed it, and every action is checked with
 * TLiteProtect against the cell it touches, as that player, through an offline MCPC+ fake player
 * with the owner's name. The fake player has no connection, so GriefPrevention's refusal
 * messages go nowhere; the turtle API just returns false.
 *
 * A turtle placed before this patch, or placed by another turtle, has no owner and is checked
 * as "[CCTurtle]", a name no claim trusts: it keeps working on open ground and is refused inside
 * every claim. Right clicking an ownerless turtle with permission to break it there makes that
 * player its owner.
 *
 * Obfuscated 1.4.7 names: yc = World, qx = EntityPlayer, iq = EntityPlayerMP, md = EntityLiving,
 * any = TileEntity, bq = NBTTagCompound, r = Facing; qx.bR = username, any.k/l/m/n =
 * worldObj/x/y/z, yc.q = getBlockTileEntity, r.b/c/d = offsetsXForSide/Y/Z,
 * bq.a(String, String) = setString, bq.b(String) = hasKey, bq.i(String) = getString.
 */
public class TLiteTurtle {

    static final String NO_OWNER = "[CCTurtle]";
    static final String OWNER_TAG = "tliteOwner";
    static final String TURTLE_PLAYER = "ComputerCraft";

    private static final Map owners = new WeakHashMap();   // TileEntityTurtle -> String

    // ------------------------------------------------------------------ owners

    /** Called at the end of BlockTurtle.onBlockPlacedBy. */
    public static void placed(yc world, int x, int y, int z, md placer) {
        if (world == null || !(placer instanceof qx)) {
            return;
        }
        String name = ((qx) placer).bR;
        if (name == null || name.equals(TURTLE_PLAYER)) {
            return;
        }
        any te = world.q(x, y, z);
        if (te instanceof TileEntityTurtle) {
            owners.put(te, name);
        }
    }

    /** Called first in BlockTurtle.onBlockActivated: an ownerless turtle is claimed by a player allowed to break it. */
    public static void adopt(yc world, int x, int y, int z, qx player) {
        if (world == null || player == null) {
            return;
        }
        any te = world.q(x, y, z);
        if (te instanceof TileEntityTurtle && !owners.containsKey(te) && TLiteProtect.canEdit(player, world, x, y, z)) {
            owners.put(te, player.bR);
        }
    }

    /** Called first in TileEntityTurtle.transferStateFrom: a moving turtle is a new tile entity. */
    public static void transfer(TileEntityTurtle to, TileEntityTurtle from) {
        Object owner = from == null ? null : owners.get(from);
        if (owner != null) {
            owners.put(to, owner);
        }
    }

    /** Called at the end of TileEntityTurtle.writeToNBT. */
    public static void save(any te, bq tag) {
        String owner = (String) owners.get(te);
        if (owner != null) {
            tag.a(OWNER_TAG, owner);
        }
    }

    /** Called at the end of TileEntityTurtle.readFromNBT. */
    public static void load(any te, bq tag) {
        if (tag.b(OWNER_TAG)) {
            owners.put(te, tag.i(OWNER_TAG));
        }
    }

    // ------------------------------------------------------------------ checks

    /**
     * Called first in move, useTool (dig and attack with every tool upgrade), place, suck and
     * dropQuantity, which all act on the cell next to the turtle on side dir.
     */
    public static boolean allowDir(TileEntityTurtle turtle, int dir, String what) {
        if (dir < 0 || dir > 5) {
            return true;
        }
        return allowed(turtle, turtle.l + r.b[dir], turtle.m + r.c[dir], turtle.n + r.d[dir], what);
    }

    /**
     * Called first in tryPlaceOnBlock, which uses the selected item on the block at x, y, z (up to
     * two cells away) against side: both that block and the cell a placed block would fill.
     */
    public static boolean allowPlace(TileEntityTurtle turtle, int x, int y, int z, int side) {
        if (!allowed(turtle, x, y, z, "Turtle place")) {
            return false;
        }
        if (side < 0 || side > 5) {
            return true;
        }
        return allowed(turtle, x + r.b[side], y + r.c[side], z + r.d[side], "Turtle place");
    }

    /** Called first in tryPlaceOnEntity, which uses the selected item on an entity in the cell at x, y, z. */
    public static boolean allowCell(TileEntityTurtle turtle, int x, int y, int z) {
        return allowed(turtle, x, y, z, "Turtle item use");
    }

    static boolean allowed(TileEntityTurtle turtle, int x, int y, int z, String what) {
        yc world = turtle.k;
        if (world == null) {
            return false;
        }
        String owner = (String) owners.get(turtle);
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
}
