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

import bspkrs.treecapitator.fml.TreeCapitatorMod;

import codechicken.core.PacketCustom;
import codechicken.nei.NEIServerConfig;
import codechicken.nei.ServerPacketHandler;

import powercrystals.minefactoryreloaded.gui.container.ContainerDeepStorageUnit;
import powercrystals.minefactoryreloaded.processing.TileEntityDeepStorageUnit;
import powercrystals.minefactoryreloaded.processing.TileEntityUnifier;

import com.eloraam.redpower.machine.TileBreaker;

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
 *   tlfix treecap    TreeCapitator felling from open ground into a claim, and on open ground
 *   tlfix spawner    NEI spawner packet: claim, open ground, out of reach, bad mob name
 *   tlfix creative   NEI creative toggle packet from a player without the permission
 *   tlfix dsu        MFR Deep Storage Unit broken by a RedPower Block Breaker with its GUI open
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
            sender.sendMessage(TAG + "usage: tlfix <unifier|probe|ee3|entropy|catalyst|wrath|monitor|treecap|spawner|creative|dsu>");
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
            else if (s.equals("treecap")) treecap(sender);
            else if (s.equals("spawner")) spawner(sender);
            else if (s.equals("creative")) creative(sender);
            else if (s.equals("dsu")) dsu(sender);
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

    /**
     * Three logs in a row, x, x-1, x-2, with three leaves beside the first so TreeCapitator sees a
     * tree. Intruder breaks the first with an iron axe. For the claim run the first log is just
     * outside the claim and the other two are inside it.
     *
     * ItemInWorldManager.tryHarvestBlock can't run for a fake player: MCPC+ sends it a block
     * packet first and it has no connection. So this does what that method does: a
     * BlockBreakEvent for the log, then, if allowed, the call TreeCapitator's ASM hook makes
     * from removeBlock, then the block removal.
     */
    private void treecap(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int y = p[1], z = p[2];
        int[] starts = { p[0] + CLAIM_HALF + 1, p[0] + OPEN_OFFSET };
        String[] results = new String[2];
        for (int i = 0; i < 2; i++) {
            int sx = starts[i];
            for (int k = 0; k < 3; k++) w.d(sx - k, y, z, 17, 0);           // oak logs
            for (int dz = -1; dz <= 1; dz++) w.d(sx + 1, y, z + dz, 18, 0);  // leaves
            hold(player, new ur(258, 1, 0), sx, y, z);                      // iron axe
            org.bukkit.block.Block bb = w.getWorld().getBlockAt(sx, y, z);
            BlockBreakEvent event = new BlockBreakEvent(bb, (Player) player.getBukkitEntity());
            Bukkit.getPluginManager().callEvent(event);
            boolean harvested = !event.isCancelled();
            if (harvested) {
                TreeCapitatorMod.instance.onBlockHarvested(w, sx, y, z, amq.p[17], 0, player);
                w.e(sx, y, z, 0);
            }
            results[i] = "broke " + harvested + ", logs " + block(w, sx, y, z) + " " + block(w, sx - 1, y, z)
                    + " " + block(w, sx - 2, y, z);
            for (int k = 0; k < 3; k++) w.e(sx - k, y, z, 0);
            for (int dz = -1; dz <= 1; dz++) w.e(sx + 1, y, z + dz, 0);
        }
        sender.sendMessage(TAG + "treecap claim: " + results[0] + "  (stock 0:0 0:0 0:0, fixed 0:0 17:0 17:0)");
        sender.sendMessage(TAG + "treecap open: " + results[1] + "  (stock and fixed 0:0 0:0 0:0)");
    }

    /** A pig spawner, then NEI packet 15 from Intruder asking for another mob. */
    private void spawner(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int x = p[0], y = p[1], z = p[2];
        int ox = x + OPEN_OFFSET;

        String[] labels = { "claim", "open", "30 blocks away", "bad mob name" };
        String[] expect = { "stock Creeper, fixed Pig", "stock and fixed Creeper", "stock Creeper, fixed Pig",
                "stock NotAMob, fixed Pig" };
        int[] xs = { x, ox, ox, ox };
        String[] mobs = { "Creeper", "Creeper", "Creeper", "NotAMob" };
        for (int i = 0; i < 4; i++) {
            w.d(xs[i], y, z, 52, 0);                                          // mob spawner
            ((ans) w.q(xs[i], y, z)).a("Pig");                                // setMobID
            hold(player, null, xs[i], y, z);
            if (i == 2) player.b(xs[i] + 30.5, y + 1.0, z + 0.5);
            PacketCustom out = new PacketCustom("NEI", 15);
            out.writeCoord(xs[i], y, z);
            out.writeString(mobs[i]);
            String thrown = "";
            try {
                new ServerPacketHandler().handlePacket(incoming(out), null, player);
            } catch (Throwable t) {
                thrown = ", threw " + t;
            }
            sender.sendMessage(TAG + "spawner " + labels[i] + ": mob " + ((ans) w.q(xs[i], y, z)).d + thrown
                    + "  (" + expect[i] + ")");
            w.e(xs[i], y, z, 0);
        }
    }

    /** NEI packet 13 from Intruder, who is not in NEIServer.cfg's creative list. */
    private void creative(CommandSender sender) {
        yc w = world();
        iq player = intruder(w);
        NEIServerConfig.loadPlayer(player);                                   // a fake player never logs in
        player.c.a(yl.b);                                                     // survival
        String thrown = "";
        try {
            new ServerPacketHandler().handlePacket(incoming(new PacketCustom("NEI", 13)), null, player);
        } catch (Throwable t) {
            thrown = ", threw " + t;
        }
        sender.sendMessage(TAG + "creative: Intruder creative=" + player.c.d() + thrown + "  (stock true, fixed false)");
        player.c.a(yl.b);
        NEIServerConfig.unloadPlayer(player);
    }

    /**
     * A Deep Storage Unit holding 1000 cobblestone, with Owner's GUI open on it, broken by a
     * RedPower Block Breaker (763:1) that a lever on top powers. Then Owner shift-clicks the
     * DSU's three slots in the GUI, as the client would while the server keeps it open.
     */
    private void dsu(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        iq player = CraftFakePlayer.get(w, "Owner", true);
        int x = p[0] + OPEN_OFFSET, y = p[1] + 10, z = p[2];
        int bx = x - 1;

        w.d(x, y, z, 3131, 3);                                            // Deep Storage Unit
        TileEntityDeepStorageUnit dsu = (TileEntityDeepStorageUnit) w.q(x, y, z);
        dsu.setStoredItemType(4, 0, 1000);
        dsu.g();                                                          // updateEntity fills the output slot
        int before = dsu.getQuantityAdjusted();

        w.d(bx, y, z, 763, 1);                                            // RedPower Block Breaker
        TileBreaker breaker = (TileBreaker) w.q(bx, y, z);
        breaker.Rotation = 4;                                             // breaks toward +x, the DSU

        for (int i = 0; i < player.bJ.a.length; i++) player.bJ.a[i] = null;
        player.b(x + 0.5, y + 1.0, z + 1.5);                              // stand next to the DSU
        ContainerDeepStorageUnit gui = new ContainerDeepStorageUnit(dsu, player.bJ);
        player.bL = gui;                                                  // openContainer

        w.d(bx, y + 1, z, 69, 13);                                        // lever on top, switched on
        if (w.a(x, y, z) != 0) breaker.onBlockNeighborChange(69);

        String dsuBlock = block(w, x, y, z);
        boolean usable = gui.a((qx) player);                                   // canInteractWith, checked every tick
        int dropped = cobbleOnGround(w, x, y, z, false);
        for (int slot = 0; slot < 3; slot++) gui.a(slot, 0, 1, player);   // slotClick, shift-click
        int taken = 0;
        for (ur s : player.bJ.a) if (s != null && s.c == 4) taken += s.a;

        sender.sendMessage(TAG + "dsu: block after " + dsuBlock + ", GUI still usable " + usable + ", held " + before
                + ", dropped " + dropped + ", taken from GUI " + taken + ", total " + (dropped + taken)
                + "  (stock usable true, total 1066; fixed usable false, total 1000)");

        player.bL = player.bK;
        for (int i = 0; i < player.bJ.a.length; i++) player.bJ.a[i] = null;
        cobbleOnGround(w, x, y, z, true);
        w.e(bx, y + 1, z, 0);
        w.e(bx, y, z, 0);
        w.e(x, y, z, 0);
    }

    /** Cobblestone on the ground near x,y,z, counting a dropped DSU's stored cobblestone too. */
    private static int cobbleOnGround(yc w, int x, int y, int z, boolean remove) {
        int n = 0;
        for (Object o : w.a(px.class, aoe.a(x - 4, y - 4, z - 4, x + 5, y + 5, z + 5))) {   // getEntitiesWithinAABB
            px item = (px) o;
            ur s = item.d();                                                                // getEntityItem
            if (s.c == 4) n += s.a;
            if (s.c == 3131 && s.j() == 3 && s.p() != null && s.p().e("storedId") == 4) n += s.p().e("storedQuantity");
            if (remove) item.x();                                                           // setDead
        }
        return n;
    }

    /** Turns an outgoing NEI packet into the incoming form the server handler reads. */
    private static PacketCustom incoming(PacketCustom out) throws Exception {
        java.lang.reflect.Constructor c = PacketCustom.class.getDeclaredConstructor(di.class);
        c.setAccessible(true);
        return (PacketCustom) c.newInstance((di) out.toPacket());
    }

    private static String count(ur s) {
        return s == null ? "empty" : String.valueOf(s.a);
    }
}
