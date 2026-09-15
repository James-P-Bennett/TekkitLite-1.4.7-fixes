import logisticspipes.LogisticsPipes;
import logisticspipes.pipes.PipeItemsRequestLogisticsMk2;

/**
 * Dupe fix injected by TekkitLite-1.4.7-fixes into LogisticsPipes 0.7.0.96 (PatchLP).
 *
 * The disk-change packet handler stored a fully client-controlled ItemStack as a Request Pipe
 * Mk2's "disk", and the disk-drop packet then spawned that exact stack into the world. Sending
 * a stack of, say, 64 diamond blocks and then dropping it created items from nothing. This only
 * lets a real disk item be stored, so the drop can only ever drop a disk.
 *
 * ur = ItemStack; ur.b() = getItem.
 */
public class TLiteLP {

    /** Replaces PipeItemsRequestLogisticsMk2.setDisk in onDiskChangeClientSide. */
    public static void setDisk(PipeItemsRequestLogisticsMk2 pipe, ur stack) {
        if (pipe == null) {
            return;
        }
        if (stack == null || stack.b() == LogisticsPipes.LogisticsItemDisk) {
            pipe.setDisk(stack);
        }
        // anything else is a client trying to store a non-disk item to spawn later; dropped
    }
}
