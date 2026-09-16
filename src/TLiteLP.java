import java.lang.reflect.Field;
import java.util.List;

import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.LogisticsSecurityTileEntity;
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

    /**
     * The request packet's amount is an unvalidated client int, and it sizes the crafting tree the
     * server plans, so a value near Integer.MAX_VALUE can drive that planning as a denial of
     * service. Clamp it to a bound far above any real request (a hundred thousand items) and floor
     * negatives at zero. Injected at the RequestHandler.request/simulate item paths, right before
     * ItemIdentifier.makeStack, so ordinary requests are unaffected.
     */
    public static final int MAX_REQUEST = 100000;

    public static int clampAmount(int amount) {
        if (amount < 0) {
            return 0;
        }
        return amount > MAX_REQUEST ? MAX_REQUEST : amount;
    }

    private static Field listenerField; // LogisticsSecurityTileEntity.listener

    /**
     * The security-station packets (card button, open per-player settings, save settings, toggle
     * CC access) looked up the station by the packet's coordinates and rewrote it with no check
     * that the sender was interacting with it, so anyone could rewrite or lock any station from
     * anywhere. The station tracks the players who have its GUI open in a private "listener" list
     * (IGuiOpenControler); a legitimate edit only ever comes from one of them. The security patch
     * gates each handler on this: the packet is honoured only if the sender has that station's GUI
     * open. This closes the remote takeover without locking out anyone who could already edit it.
     */
    public static boolean securityAllowed(LogisticsSecurityTileEntity tile, qx player) {
        if (tile == null || player == null) {
            return false;
        }
        try {
            if (listenerField == null) {
                listenerField = LogisticsSecurityTileEntity.class.getDeclaredField("listener");
                listenerField.setAccessible(true);
            }
            Object l = listenerField.get(tile);
            return (l instanceof List) && ((List) l).contains(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
