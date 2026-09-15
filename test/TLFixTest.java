import java.util.List;

import net.minecraftforge.oredict.OreDictionary;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_4_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_4_R1.entity.CraftFakePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import appeng.api.Blocks;
import appeng.api.Items;

import com.pahimar.ee3.core.helper.TransmutationHelper;
import com.pahimar.ee3.item.ModItems;
import com.pahimar.ee3.network.packet.PacketRequestEvent;

import factorization.common.Core;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

import powercrystals.minefactoryreloaded.processing.TileEntityUnifier;

/**
 * Test harness for TekkitLite-1.4.7-fixes. Runs a scenario against the live server's world
 * and prints what the stock or patched mod did, so a result can be compared between jars.
 *
 *   tlfix unifier    MFR Unifier with 1 input and a non-empty output of the same item
 *   tlfix probe      checks the test setup: a claim that refuses Intruder, open ground that doesn't
 *   tlfix ee3        Minium Stone transmutation: claim, open ground, empty hand, range, reach
 *   tlfix entropy    AE Entropy Accelerator on water, in a claim and on open ground
 *   tlfix catalyst   AE Vibration Catalyst on cobblestone, in a claim and on open ground
 *   tlfix wrath      Factorization Wrath Igniter, in a claim and on open ground
 *   tlfix monitor    AE Storage Monitor right-click in a claim
 *
 * The claim is owned by "Owner" and every action is done by the fake player "Intruder", who
 * has no trust in it. Default package so the obfuscated vanilla classes can be named.
 */
public class TLFixTest extends JavaPlugin {

    private static final String TAG = "[TLFixTest] ";
    private static final int CLAIM_HALF = 8;
    private static final int OPEN_OFFSET = 40;

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(TAG + "usage: tlfix <unifier|probe|ee3|entropy|catalyst|wrath|monitor>");
            return true;
        }
        String s = args[0].toLowerCase();
        try {
            if (s.equals("unifier")) unifier(sender);
            else if (s.equals("probe")) probe(sender);
            else if (s.equals("ee3")) ee3(sender);
            else if (s.equals("entropy")) entropy(sender);
            else if (s.equals("catalyst")) catalyst(sender);
            else if (s.equals("wrath")) wrath(sender);
            else if (s.equals("monitor")) monitor(sender);
            else sender.sendMessage(TAG + "unknown scenario " + s);
        } catch (Throwable t) {
            sender.sendMessage(TAG + s + " threw " + t);
            t.printStackTrace();
        }
        return true;
    }

    // ------------------------------------------------------------------ setup

    private yc world() {
        return ((CraftWorld) getServer().getWorlds().get(0)).getHandle();
    }

    /** A spot 200 high above spawn, in a loaded chunk. */
    private int[] spot() {
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        bw.loadChunk(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4);
        bw.loadChunk((spawn.getBlockX() + OPEN_OFFSET) >> 4, spawn.getBlockZ() >> 4);
        return new int[] { spawn.getBlockX(), 200, spawn.getBlockZ() };
    }

    /** Claims CLAIM_HALF blocks around the spot for Owner, once per server run. */
    private void ensureClaim(int[] p) {
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location at = new Location(bw, p[0], p[1], p[2]);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) != null) {
            return;
        }
        GriefPrevention.instance.dataStore.createClaim(bw,
                p[0] - CLAIM_HALF, p[0] + CLAIM_HALF, 0, 255, p[2] - CLAIM_HALF, p[2] + CLAIM_HALF,
                "Owner", null, null);
    }

    private iq intruder(yc w) {
        return CraftFakePlayer.get(w, "Intruder", true);
    }

    /** Puts the stack in Intruder's first hotbar slot, selects it, and stands Intruder next to x,y,z. */
    private void hold(iq player, ur stack, int x, int y, int z) {
        player.bJ.a(0, stack);                                            // inventory.setInventorySlotContents
        player.bJ.c = 0;                                                  // inventory.currentItem
        player.b(x + 0.5, y + 1.0, z + 1.5);                              // setPosition
    }

    private static String block(yc w, int x, int y, int z) {
        return w.a(x, y, z) + ":" + w.h(x, y, z);
    }

    // ------------------------------------------------------------- scenarios

    /** Places a Unifier (3131:8) above spawn and ticks it once. */
    private void unifier(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        int x = p[0], y = p[1], z = p[2];
        boolean placed = w.d(x, y, z, 3131, 8);                           // setBlockAndMetadataWithNotify
        any te = w.q(x, y, z);                                            // getBlockTileEntity
        if (!(te instanceof TileEntityUnifier)) {
            sender.sendMessage(TAG + "unifier: no Unifier tile at " + x + "," + y + "," + z + " (placed " + placed
                    + ", block " + block(w, x, y, z) + ", tile " + te + ")");
            return;
        }
        TileEntityUnifier u = (TileEntityUnifier) te;

        List ores = OreDictionary.getOres("ingotCopper");
        if (ores == null || ores.isEmpty()) {
            sender.sendMessage(TAG + "unifier: no ingotCopper registered");
            return;
        }
        ur ingot = ((ur) ores.get(0)).l();                                // copy
        ur in = ingot.l();
        in.a = 1;
        ur out = ingot.l();
        out.a = 1;
        u.a(0, in);
        u.a(1, out);

        u.g();                                                            // updateEntity

        sender.sendMessage(TAG + "unifier: input " + count(u.a(0)) + ", output " + count(u.a(1))
                + "  (expect stock 64 / -62, fixed 2 / empty)");
        w.e(x, y, z, 0);                                                  // setBlockWithNotify, clean up
    }

    /** The claim must refuse Intruder and open ground must not, or the other scenarios prove nothing. */
    private void probe(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        Player bp = (Player) intruder(w).getBukkitEntity();
        org.bukkit.World bw = getServer().getWorlds().get(0);

        BlockBreakEvent inside = new BlockBreakEvent(bw.getBlockAt(p[0], p[1], p[2]), bp);
        Bukkit.getPluginManager().callEvent(inside);
        BlockBreakEvent open = new BlockBreakEvent(bw.getBlockAt(p[0] + OPEN_OFFSET, p[1], p[2]), bp);
        Bukkit.getPluginManager().callEvent(open);

        sender.sendMessage(TAG + "probe: break in claim cancelled=" + inside.isCancelled()
                + ", on open ground cancelled=" + open.isCancelled() + "  (expect true / false)");
        sender.sendMessage(TAG + "probe: cancelled in claim by " + cancellers(bw.getBlockAt(p[0], p[1], p[2]), bp)
                + ", on open ground by " + cancellers(bw.getBlockAt(p[0] + OPEN_OFFSET, p[1], p[2]), bp));
    }

    /** Runs each BlockBreakEvent listener on its own fresh event and names the plugins that cancel it. */
    private String cancellers(org.bukkit.block.Block block, Player bp) {
        StringBuilder sb = new StringBuilder();
        for (org.bukkit.plugin.RegisteredListener rl : BlockBreakEvent.getHandlerList().getRegisteredListeners()) {
            BlockBreakEvent e = new BlockBreakEvent(block, bp);
            try {
                rl.callEvent(e);
            } catch (Throwable t) {
                sb.append(rl.getPlugin().getName()).append("(threw) ");
                continue;
            }
            if (e.isCancelled()) sb.append(rl.getPlugin().getName()).append(' ');
        }
        return sb.length() == 0 ? "nobody" : sb.toString().trim();
    }

    private void ee3(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int x = p[0], y = p[1], z = p[2];
        int ox = x + OPEN_OFFSET;

        // EE3 only knows the equivalencies its recipes register, so find a block it can transmute.
        int srcId = -1;
        ur next = null;
        for (int id = 1; id < 4096 && next == null; id++) {
            if (amq.p[id] == null) continue;                                  // Block.blocksList
            ur n = TransmutationHelper.getNextBlock(id, 0);
            if (n != null && n.c != id && n.c > 0 && n.c < 4096 && amq.p[n.c] != null) {
                srcId = id;
                next = n;
            }
        }
        if (next == null) {
            sender.sendMessage(TAG + "ee3: no transmutable block found");
            return;
        }
        String data = next.c + ":" + next.j();
        String src = srcId + ":0";
        sender.sendMessage(TAG + "ee3: using " + src + " -> " + data);

        // 1. in the claim, range 1
        w.d(x, y, z, srcId, 0);
        hold(player, new ur(ModItems.miniumStone), x, y, z);
        request(player, x, y, z, 1, 1, 1, data);
        sender.sendMessage(TAG + "ee3 claim: block " + block(w, x, y, z) + "  (stock " + data + ", fixed " + src + ")");
        w.e(x, y, z, 0);

        // 2. open ground, range 1: allowed either way
        w.d(ox, y, z, srcId, 0);
        hold(player, new ur(ModItems.miniumStone), ox, y, z);
        request(player, ox, y, z, 1, 1, 1, data);
        sender.sendMessage(TAG + "ee3 open: block " + block(w, ox, y, z) + "  (stock and fixed " + data + ")");
        w.e(ox, y, z, 0);

        // 3. open ground, empty hand
        w.d(ox, y, z, srcId, 0);
        hold(player, null, ox, y, z);
        String thrown = "none";
        try {
            request(player, ox, y, z, 1, 1, 1, data);
        } catch (Throwable t) {
            thrown = t.getClass().getSimpleName();
        }
        sender.sendMessage(TAG + "ee3 empty hand: block " + block(w, ox, y, z) + ", exception " + thrown
                + "  (stock NullPointerException, fixed none and " + src + ")");
        w.e(ox, y, z, 0);

        // 4. open ground, 5x1x5 with an uncharged Minium Stone
        int changed = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) w.d(ox + dx, y, z + dz, srcId, 0);
        hold(player, new ur(ModItems.miniumStone), ox, y, z);
        request(player, ox, y, z, 5, 1, 5, data);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (w.a(ox + dx, y, z + dz) != srcId) changed++;
            w.e(ox + dx, y, z + dz, 0);
        }
        sender.sendMessage(TAG + "ee3 range 5x5: " + changed + " blocks changed  (stock 25, fixed 0)");

        // 5. open ground, origin 30 blocks from the player
        w.d(ox, y, z, srcId, 0);
        hold(player, new ur(ModItems.miniumStone), ox, y, z);
        player.b(ox + 30.5, y + 1.0, z + 0.5);
        request(player, ox, y, z, 1, 1, 1, data);
        sender.sendMessage(TAG + "ee3 reach: block " + block(w, ox, y, z) + "  (stock " + data + ", fixed " + src + ")");
        w.e(ox, y, z, 0);
    }

    private void request(iq player, int x, int y, int z, int rx, int ry, int rz, String data) {
        PacketRequestEvent packet = new PacketRequestEvent((byte) 0, x, y, z, (byte) 1,
                (byte) rx, (byte) ry, (byte) rz, data);
        packet.execute(null, (cpw.mods.fml.common.network.Player) player);
    }

    private void entropy(CommandSender sender) {
        toolScenario(sender, "entropy", Items.toolEntropyAccelerator, 9, 0);
    }

    private void catalyst(CommandSender sender) {
        toolScenario(sender, "catalyst", Items.toolVibrationCatalyst, 4, 0);
    }

    /** Uses a charged AE tool on blockId:meta in the claim and on open ground. */
    private void toolScenario(CommandSender sender, String name, ur tool, int blockId, int meta) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int x = p[0], y = p[1], z = p[2];
        int ox = x + OPEN_OFFSET;
        String before = blockId + ":" + meta;

        String[] results = new String[2];
        int[] xs = { x, ox };
        for (int i = 0; i < 2; i++) {
            w.d(xs[i], y, z, blockId, meta);
            ur stack = tool.l();
            ic2.api.ElectricItem.charge(stack, 1000000, 4, true, false);
            hold(player, stack, xs[i], y, z);
            stack.b().a(stack, player, w, xs[i], y, z, 1, 0.5F, 1.0F, 0.5F);   // Item.onItemUse
            results[i] = block(w, xs[i], y, z);
            w.e(xs[i], y, z, 0);
        }
        sender.sendMessage(TAG + name + ": claim " + results[0] + ", open " + results[1]
                + "  (was " + before + "; stock changes both, fixed changes only open)");
    }

    private void wrath(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int x = p[0], y = p[1], z = p[2];
        int ox = x + OPEN_OFFSET;

        String[] above = new String[2];
        int[] xs = { x, ox };
        for (int i = 0; i < 2; i++) {
            w.d(xs[i], y, z, 1, 0);
            w.e(xs[i], y + 1, z, 0);
            ur stack = new ur(Core.registry.wrath_igniter);
            hold(player, stack, xs[i], y, z);
            Core.registry.wrath_igniter.a(stack, player, w, xs[i], y, z, 1, 0.5F, 1.0F, 0.5F);
            above[i] = block(w, xs[i], y + 1, z);
            w.e(xs[i], y + 1, z, 0);
            w.e(xs[i], y, z, 0);
        }
        sender.sendMessage(TAG + "wrath: above claim block " + above[0] + ", above open block " + above[1]
                + "  (stock fire in both, fixed 0:0 in the claim)");
    }

    private void monitor(CommandSender sender) {
        // AE registers one AppEngMultiBlock for many machines; the vanilla onBlockActivated
        // dispatches to the sub-block for the metadata, which is BlockStorageMonitor here.
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int x = p[0], y = p[1], z = p[2];

        w.d(x, y, z, Blocks.blkStorageMonitor.c, Blocks.blkStorageMonitor.j());
        amq b = amq.p[w.a(x, y, z)];                                      // Block.blocksList
        hold(player, null, x, y, z);
        String outcome;
        try {
            boolean r = b.a(w, x, y, z, player, 1, 0.5F, 0.5F, 0.5F);         // Block.onBlockActivated
            outcome = "returned " + r;
        } catch (Throwable t) {
            outcome = "stock body ran and threw " + t.getClass().getSimpleName();
        }
        sender.sendMessage(TAG + "monitor in claim: " + outcome
                + "  (stock runs the click and throws with no tile, fixed returns true and logs a refusal)");
        w.e(x, y, z, 0);
    }

    private static String count(ur s) {
        return s == null ? "empty" : String.valueOf(s.a);
    }
}
