/**
 * Dupe fix injected by TekkitLite-1.4.7-fixes into immibis-core 52.4.6 (PatchImmibis).
 *
 * Obfuscated 1.4.7 names: ur = ItemStack, la = IInventory; ur.a = stackSize, ur.c = itemID,
 * ur.j() = getItemDamage, ur.d() = getMaxStackSize, ur.a(ur, ur) = areItemStackTagsEqual,
 * la.a(int) = getStackInSlot, la.a(int, ur) = setInventorySlotContents.
 */
public class TLiteImmibis {

    /**
     * Replaces BasicInventory.mergeStackIntoRange(ItemStack, IInventory, int, int).
     *
     * Stock merges any two stacks with the same item id and damage and ignores NBT, so the
     * destination keeps its own tag and takes the whole count. Tubestuff's shift-click and the
     * Retrievulator go through it: one Deep Storage Unit holding 1000 diamonds in an ACT Mk II
     * slot, and 63 empty Deep Storage Units shift-clicked in, gives 64 that each hold 1000.
     * Same logic as stock, with the tags now required to match.
     */
    public static ur mergeStackIntoRange(ur stack, la to, int start, int end) {
        if (stack == null || stack.a == 0) {
            return null;
        }
        for (int i = start; i < end; i++) {
            ur dst = to.a(i);
            if (!canMerge(dst, stack)) {
                continue;
            }
            int max = dst.d();
            if (dst.a + stack.a <= max) {
                dst.a += stack.a;
                to.a(i, dst);
                return null;
            }
            int room = max - dst.a;
            if (room > 0) {
                dst.a += room;
                stack.a -= room;
                to.a(i, dst);
            }
        }
        if (stack.a == 0) {
            return null;
        }
        for (int i = start; i < end; i++) {
            if (to.a(i) == null) {
                to.a(i, stack);
                return null;
            }
        }
        return stack;
    }

    /**
     * Replaces BasicInventory.mergeStackIntoRange(IInventory from, IInventory to, int slot,
     * int start, int end), the shift-click from one inventory into another. Same fault and fix
     * as above.
     */
    public static boolean mergeStackIntoRange(la from, la to, int slot, int start, int end) {
        ur src = from.a(slot);
        if (src == null || src.a == 0) {
            return false;
        }
        boolean moved = false;
        for (int i = start; i < end; i++) {
            ur dst = to.a(i);
            if (!canMerge(dst, src)) {
                continue;
            }
            int max = dst.d();
            if (dst.a + src.a <= max) {
                dst.a += src.a;
                from.a(slot, null);
                to.a(i, dst);
                return true;
            }
            int room = max - dst.a;
            if (room > 0) {
                dst.a += room;
                to.a(i, dst);
                src.a -= room;
                moved = true;
            }
        }
        if (src.a == 0) {
            from.a(slot, null);
            return moved;
        }
        for (int i = start; i < end; i++) {
            if (to.a(i) == null) {
                to.a(i, src);
                from.a(slot, null);
                return true;
            }
        }
        from.a(slot, src);
        return moved;
    }

    private static boolean canMerge(ur dst, ur src) {
        return dst != null && dst.c == src.c && dst.j() == src.j() && ur.a(dst, src);
    }
}
