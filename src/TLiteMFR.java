import powercrystals.minefactoryreloaded.processing.TileEntityDeepStorageUnit;
import powercrystals.minefactoryreloaded.processing.TileEntityUnifier;

/**
 * Exploit fixes injected by TekkitLite-1.4.7-fixes into MineFactoryReloaded 2.3.2 (PatchMFR).
 *
 * Compiled against the server's mcpcplus.jar, so vanilla classes are used by their obfuscated
 * 1.4.7 names: ur = ItemStack, yc = World, any = TileEntity, and on an IInventory
 * a(int) = getStackInSlot, a(int, ur) = setInventorySlotContents, c() = getInventoryStackLimit;
 * yc.q = getBlockTileEntity, yc.r = removeBlockTileEntity, any.k/l/m/n = worldObj/x/y/z.
 */
public class TLiteMFR {

    /**
     * Replaces the private TileEntityUnifier.moveItemStack(ur) call in updateEntity.
     *
     * source is the unified copy of the input stack. Stock, when the output slot already holds
     * the same item, adds the output's free space to it without capping it at the input count:
     * one ingot in and an output of 1 gives an output of 64, and the input goes to -62 and is
     * never cleared because only a count of exactly 0 empties the slot. When the output slot is
     * empty, stock moves the whole input even if the unified item stacks smaller.
     *
     * Now the move is the smallest of the input count, the output's free space and the unified
     * item's max stack size, and the input is cleared at 0 or below.
     */
    public static void moveItemStack(TileEntityUnifier unifier, ur source) {
        if (source == null) {
            return;
        }
        ur input = unifier.a(0);
        if (input == null || input.a <= 0) {
            unifier.a(0, null);
            return;
        }
        ur output = unifier.a(1);

        int amount;
        if (output == null) {
            amount = Math.min(unifier.c(), source.d());
        } else {
            if (source.c != output.c || source.j() != output.j()) {
                return;
            }
            if (source.p() != null || output.p() != null) {
                return;
            }
            amount = Math.min(unifier.c(), output.d()) - output.a;
        }
        amount = Math.min(amount, input.a);
        if (amount <= 0) {
            return;
        }

        if (output == null) {
            ur moved = source.l();
            moved.a = amount;
            unifier.a(1, moved);
        } else {
            output.a += amount;
            unifier.a(1, output);
        }

        input.a -= amount;
        unifier.a(0, input.a <= 0 ? null : input);
    }

    /**
     * Called first in TileEntityDeepStorageUnit.isUseableByPlayer and
     * TileEntityLiquiCrafter.isUseableByPlayer. MFR's base inventory class refuses a player when
     * the tile is no longer the one at its position, but these two override it with a distance
     * check only. So once a machine breaks the block, a GUI that was open on it stays open, and
     * the dead tile's slots can still be emptied into the player's inventory.
     */
    public static boolean isInWorld(any te) {
        return te != null && te.k != null && te.k.q(te.l, te.m, te.n) == te;
    }

    /**
     * Replaces world.removeBlockTileEntity at the end of BlockFactoryMachine1.breakBlock, on the
     * Deep Storage Unit branch. That branch drops the DSU with its count in NBT (or copies of its
     * slots when the count is 0) and leaves the tile's slots and count as they were, so a GUI
     * still open on it can take the output stack again. The tile is emptied before removal.
     */
    public static void removeBlockTileEntity(yc world, int x, int y, int z) {
        any te = world.q(x, y, z);
        if (te instanceof TileEntityDeepStorageUnit) {
            TileEntityDeepStorageUnit dsu = (TileEntityDeepStorageUnit) te;
            dsu.clearSlots();
            dsu.setQuantity(0);
        }
        world.r(x, y, z);
    }
}
