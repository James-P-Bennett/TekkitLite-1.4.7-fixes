/**
 * Tweak injected by TekkitLite-1.4.7-fixes into IronChest 5.1.0.275 (PatchIronChest).
 *
 * The Crystal Chest is the only transparent chest, so it is the only one that renders its
 * contents as items floating in the block, and TileEntityIronChest.sortTopStacks fills up to
 * eight of them. On a base with many crystal chests that is a lot of rendered, rotating items.
 * This caps each crystal chest to the three most common stacks, cutting the client render load.
 * Only crystal chests reach sortTopStacks's fill, so nothing else is affected.
 *
 * ur = ItemStack.
 */
public class TLiteIronChest {

    /** How many item stacks a Crystal Chest shows. */
    private static final int SHOWN = 3;

    /** Called at the end of sortTopStacks; nulls the displayed stacks past the cap. */
    public static void cap(ur[] topStacks) {
        if (topStacks == null) {
            return;
        }
        for (int i = SHOWN; i < topStacks.length; i++) {
            topStacks[i] = null;
        }
    }
}
