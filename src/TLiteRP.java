/**
 * Dupe fix injected by TekkitLite-1.4.7-fixes into RedPower 2 Core pr6 (PatchRP).
 *
 * Obfuscated 1.4.7 names: rq = Container, sr = Slot, ur = ItemStack;
 * rq.c = inventorySlots, sr.c() = getStack.
 */
public class TLiteRP {

    /**
     * Called first in the slotClick that PatchRP adds to ContainerBag, the Canvas Bag GUI, which
     * edits the held bag's NBT in place.
     *
     * Stock uses vanilla slotClick. Hovering a stack in the bag and pressing the number key of
     * the bag's own hotbar slot (click mode 2) swaps that stack into the hotbar and pushes the bag
     * back into the inventory as a copy made before the stack was removed, so the item comes out
     * and the copied bag still holds it. Clicks that would pick up, move or drop the open bag
     * itself give other ways to split the bag from the stack the GUI is writing to.
     *
     * Refuses number key swaps in the bag GUI and any click on the slot holding the open bag.
     * Neither is something a player needs while the bag is open.
     */
    public static boolean allowClick(rq container, int slot, int mode, ur bag) {
        if (mode == 2) {
            return false;
        }
        if (bag != null && slot >= 0 && slot < container.c.size()) {
            Object s = container.c.get(slot);
            if (s instanceof sr && ((sr) s).c() == bag) {
                return false;
            }
        }
        return true;
    }
}
