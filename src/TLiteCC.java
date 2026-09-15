import java.lang.reflect.Field;

import dan200.computer.shared.ComputerCraftPacket;
import dan200.computer.shared.ContainerComputer;
import dan200.computer.shared.ContainerDiskDrive;
import dan200.computer.shared.INetworkedEntity;
import dan200.computer.shared.TileEntityComputer;
import dan200.computer.shared.TileEntityDiskDrive;
import dan200.turtle.shared.ContainerTurtle;
import dan200.turtle.shared.TileEntityTurtle;

/**
 * Exploit fix injected by TekkitLite-1.4.7-fixes into ComputerCraft 1.5 (PatchCC).
 *
 * ComputerCraftProxyCommon.handlePacket reads a block position from the client packet, looks up
 * the tile entity there and calls its handlePacket with the sender. It never checks the sender
 * owns or has that tile's GUI open, so a modified client can aim a packet at anyone's computer
 * or turtle from anywhere and type into its terminal (running arbitrary Lua as that computer),
 * reboot, shut down or terminate it, or drive the turtle. The lookup also loads chunks anywhere.
 *
 * The dispatch now goes through here. For the tiles a player drives through a GUI (computer,
 * turtle, disk drive) the sender must have that exact tile's container open and still usable,
 * which is the only way a stock client sends these. Monitors, printers and modems only reply
 * with their own state to a RequestUpdate, so they are left alone.
 *
 * Obfuscated 1.4.7 names: qx = EntityPlayer, rq = Container; qx.bL = openContainer,
 * rq.a(qx) = isUseableByPlayer.
 */
public class TLiteCC {

    private static Field computerTile;   // ContainerComputer.m_computer
    private static Field turtleTile;      // ContainerTurtle.turtle
    private static Field diskDriveTile;   // ContainerDiskDrive.diskDrive

    /** Replaces the INetworkedEntity.handlePacket call in ComputerCraftProxyCommon.handlePacket. */
    public static void handlePacket(INetworkedEntity entity, ComputerCraftPacket packet, qx player) {
        if (allowed(entity, player)) {
            entity.handlePacket(packet, player);
        }
    }

    public static boolean allowed(INetworkedEntity entity, qx player) {
        boolean guiTile = entity instanceof TileEntityComputer
                || entity instanceof TileEntityTurtle
                || entity instanceof TileEntityDiskDrive;
        if (!guiTile) {
            return true;
        }
        if (player == null) {
            return false;
        }
        try {
            rq open = player.bL;
            Object bound = boundTile(open);
            return bound == entity && open.a(player);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The tile a computer/turtle/disk-drive container is bound to, or null for anything else. */
    private static Object boundTile(rq open) throws Exception {
        if (open instanceof ContainerComputer) {
            if (computerTile == null) {
                computerTile = field(ContainerComputer.class, "m_computer");
            }
            return computerTile.get(open);
        }
        if (open instanceof ContainerTurtle) {
            if (turtleTile == null) {
                turtleTile = field(ContainerTurtle.class, "turtle");
            }
            return turtleTile.get(open);
        }
        if (open instanceof ContainerDiskDrive) {
            if (diskDriveTile == null) {
                diskDriveTile = field(ContainerDiskDrive.class, "diskDrive");
            }
            return diskDriveTile.get(open);
        }
        return null;
    }

    private static Field field(Class cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
