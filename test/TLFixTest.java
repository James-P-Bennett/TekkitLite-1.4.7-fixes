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
import me.ryanhamshire.GriefPrevention.Claim;

import bspkrs.treecapitator.fml.TreeCapitatorMod;

import codechicken.core.PacketCustom;
import codechicken.nei.NEIServerConfig;
import codechicken.nei.ServerPacketHandler;

import powercrystals.minefactoryreloaded.gui.container.ContainerDeepStorageUnit;
import powercrystals.minefactoryreloaded.processing.TileEntityDeepStorageUnit;
import powercrystals.minefactoryreloaded.processing.TileEntityUnifier;

import com.eloraam.redpower.machine.TileBreaker;

import ic2.core.item.tool.EntityMiningLaser;

import immibis.tubestuff.ContainerAutoCraftingMk2;
import immibis.tubestuff.TileAutoCraftingMk2;

import com.eloraam.redpower.base.ContainerBag;
import com.eloraam.redpower.base.ItemBag;

import buildcraft.api.filler.FillerManager;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.builders.FillerRemover;
import buildcraft.builders.TileFiller;
import buildcraft.factory.TileQuarry;

import dan200.turtle.shared.TileEntityTurtle;

import dan200.computer.shared.ContainerComputer;
import dan200.computer.shared.TileEntityComputer;

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
 *   tlfix mfrpacket  MFR DSU side packet from afar, without the GUI, with another GUI, and legit
 *   tlfix laser      IC2 Mining Laser, mining and explosive modes, in a claim and on open ground
 *   tlfix act2       Tubestuff ACT Mk II shift-click of empty DSUs onto a full one
 *   tlfix bag        RedPower Canvas Bag number key swap onto the bag's own hotbar slot
 *   tlfix filler     BuildCraft Filler, Clear pattern, outside a claim clearing a block inside it
 *   tlfix quarry     BuildCraft Quarry placed outside a claim mining a block inside it
 *   tlfix turtle     ComputerCraft mining turtle outside a claim digging and moving into it
 *   tlfix quarrychunks  BuildCraft Quarry with a 5x5 chunk area keeping its own chunk, and the ticket callback
 *   tlfix ccpacket   ComputerCraft packet guard: drive a computer from afar, from another GUI, and legitimately
 *   tlfix harvester  MFR Harvester settings packet flooding non-whitelisted keys into its NBT
 *   tlfix te         Thermal Expansion packet gate: retune an Energy Cell from afar, from another GUI, legit
 *   tlfix crystal    IronChest Crystal Chest capped to a few rendered stacks
 *   tlfix spotloader ChickenChunks Chunk Loader pinned to its own chunk regardless of radius
 *   tlfix da         immibis Dimensional Anchor pinned to its own chunk regardless of radius
 *   tlfix apgate     AdditionalPipes chunk loader off by default (ChunkLoaderConversion config)
 *   tlfix quota      one combined per-player chunk cap shared by ChickenChunks and anchors
 *   tlfix cchttp     ComputerCraft http API host filter: public allowed, loopback/LAN blocked
 *   tlfix lpclamp    LogisticsPipes request amount clamp bounds a DoS quantity
 *   tlfix lpsec      LogisticsPipes security station edit allowed only for a viewer of that station
 *   tlfix ncflood    NuclearControl sensor card field cap bounds NBT growth
 *   tlfix apmdupe    APM Battery Station output merge only for stackable items
 *   tlfix tesla      Industrial Tesla Coil flags: config reads and drop-deny handler match
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
            sender.sendMessage(TAG + "usage: tlfix <unifier|probe|ee3|entropy|catalyst|wrath|monitor|treecap|spawner|creative|dsu|mfrpacket|laser|act2|bag|filler|quarry|turtle|quarrychunks|ccpacket|harvester|te|crystal|spotloader|da|apgate|quota|cchttp|lpclamp|lpsec|ncflood|apmdupe|tesla|explode|nukewarn|iddump|dynamite|scmod|cartmine|frame|rpguard|wrench|ic2machine|place|turtleplace>");
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
            else if (s.equals("mfrpacket")) mfrpacket(sender);
            else if (s.equals("laser")) laser(sender);
            else if (s.equals("act2")) act2(sender);
            else if (s.equals("bag")) bag(sender);
            else if (s.equals("filler")) filler(sender);
            else if (s.equals("quarry")) quarry(sender);
            else if (s.equals("turtle")) turtle(sender);
            else if (s.equals("quarrychunks")) quarrychunks(sender);
            else if (s.equals("ccpacket")) ccpacket(sender);
            else if (s.equals("harvester")) harvester(sender);
            else if (s.equals("te")) te(sender);
            else if (s.equals("crystal")) crystal(sender);
            else if (s.equals("spotloader")) spotloader(sender);
            else if (s.equals("da")) da(sender);
            else if (s.equals("apgate")) apgate(sender);
            else if (s.equals("quota")) quota(sender);
            else if (s.equals("cchttp")) cchttp(sender);
            else if (s.equals("lpclamp")) lpclamp(sender);
            else if (s.equals("lpsec")) lpsec(sender);
            else if (s.equals("ncflood")) ncflood(sender);
            else if (s.equals("apmdupe")) apmdupe(sender);
            else if (s.equals("tesla")) tesla(sender);
            else if (s.equals("explode")) explode(sender);
            else if (s.equals("nukewarn")) nukewarn(sender);
            else if (s.equals("iddump")) iddump(sender);
            else if (s.equals("dynamite")) dynamite(sender);
            else if (s.equals("scmod")) scmod(sender);
            else if (s.equals("cartmine")) cartmine(sender);
            else if (s.equals("frame")) frame(sender);
            else if (s.equals("rpguard")) rpguard(sender);
            else if (s.equals("wrench")) wrench(sender);
            else if (s.equals("ic2machine")) ic2machine(sender);
            else if (s.equals("place")) place(sender);
            else if (s.equals("turtleplace")) turtleplace(sender);
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

    /**
     * MFR packet 5 toggles a Deep Storage Unit side between input and output. Side 2 starts as
     * output, so "flipped" means the packet was applied.
     */
    private void mfrpacket(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        int x = p[0] + OPEN_OFFSET, y = p[1] + 20, z = p[2];
        w.d(x, y, z, 3131, 3);
        w.d(x + 3, y, z, 3131, 3);
        TileEntityDeepStorageUnit dsu = (TileEntityDeepStorageUnit) w.q(x, y, z);
        TileEntityDeepStorageUnit other = (TileEntityDeepStorageUnit) w.q(x + 3, y, z);

        String[] labels = { "Intruder 30 blocks away, no GUI", "Intruder next to it, no GUI",
                "Intruder next to it, another DSU's GUI open", "Owner next to it, its GUI open" };
        String[] expect = { "stock flipped, fixed unchanged", "stock flipped, fixed unchanged",
                "stock flipped, fixed unchanged", "stock and fixed flipped" };
        for (int i = 0; i < 4; i++) {
            dsu.setSideIsOutput(2, true);
            iq pl = i == 3 ? CraftFakePlayer.get(w, "Owner", true) : intruder(w);
            pl.b(x + (i == 0 ? 30.5 : 0.5), y + 1.0, z + 2.5);
            pl.bL = pl.bK;
            if (i == 2) pl.bL = new ContainerDeepStorageUnit(other, pl.bJ);
            if (i == 3) pl.bL = new ContainerDeepStorageUnit(dsu, pl.bJ);
            String thrown = "";
            try {
                di pkt = powercrystals.core.net.PacketWrapper.createPacket("MFReloaded", 5, new Object[] { x, y, z, 2 });
                new powercrystals.minefactoryreloaded.net.ServerPacketHandler().onPacketData(null, pkt,
                        (cpw.mods.fml.common.network.Player) (Object) pl);
            } catch (Throwable t) {
                thrown = ", threw " + t;
            }
            pl.bL = pl.bK;
            sender.sendMessage(TAG + "mfrpacket " + labels[i] + ": " + (dsu.getIsSideOutput(2) ? "unchanged" : "flipped")
                    + thrown + "  (" + expect[i] + ")");
        }
        w.e(x, y, z, 0);
        w.e(x + 3, y, z, 0);
    }

    /**
     * Intruder stands 2 blocks above a stone target, looking straight down, and fires a Mining
     * Laser beam built the way the item builds it, ticked until it dies. Mining mode targets one
     * stone block; explosive mode targets the top of a 3x3x3 stone cube.
     */
    private void laser(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int y = p[1], z = p[2];
        int[] xs = { p[0], p[0] + OPEN_OFFSET };
        String[] where = { "claim", "open" };

        org.bukkit.World bw = getServer().getWorlds().get(0);
        for (int i = 0; i < 2; i++) {
            int x = xs[i];
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) bw.loadChunk((x + dx) >> 4, (z + dz) >> 4);
            w.d(x, y, z, 1, 0);
            fire(w, player, x, y, z, false);
            String mined = block(w, x, y, z);
            clear(w, x, y, z, 1);

            for (int dx = -1; dx <= 1; dx++) for (int dy = -2; dy <= 0; dy++) for (int dz = -1; dz <= 1; dz++)
                w.d(x + dx, y + dy, z + dz, 1, 0);
            int placed = stone(w, x, y, z);
            fire(w, player, x, y, z, true);
            int destroyed = placed - stone(w, x, y, z);
            clear(w, x, y, z, 6);

            sender.sendMessage(TAG + "laser " + where[i] + ": mining left " + mined + ", explosive destroyed " + destroyed
                    + " of " + placed + " stone"
                    + (i == 0 ? "  (stock 0:0 and some, fixed 1:0 and 0)" : "  (stock and fixed 0:0 and some)"));
        }
    }

    /** Stone in the 3x3x3 cube whose top centre is x,y,z. */
    private static int stone(yc w, int x, int y, int z) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -2; dy <= 0; dy++) for (int dz = -1; dz <= 1; dz++)
            if (w.a(x + dx, y + dy, z + dz) == 1) n++;
        return n;
    }

    private void fire(yc w, iq player, int x, int y, int z, boolean explosive) {
        player.b(x + 0.5, y + 2.0, z + 0.5);                              // setPosition
        player.z = 0F;                                                    // rotationYaw
        player.A = 90F;                                                   // rotationPitch, straight down
        EntityMiningLaser beam = new EntityMiningLaser(w, player, 64F, explosive ? 12F : 5F, Integer.MAX_VALUE, explosive);
        for (int t = 0; t < 200 && !beam.L; t++) beam.j_();                // onUpdate until isDead
    }

    /**
     * An ACT Mk II (4092:1) with one Deep Storage Unit holding 1000 cobblestone in its first
     * input slot, and Owner shift-clicking 15 empty Deep Storage Units in from hotbar slot 0.
     * Counts the cobblestone every DSU in the table and the player's inventory holds.
     */
    private void act2(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        iq player = CraftFakePlayer.get(w, "Owner", true);
        int x = p[0] + OPEN_OFFSET, y = p[1] + 30, z = p[2];

        w.d(x, y, z, 4092, 1);
        TileAutoCraftingMk2 table = (TileAutoCraftingMk2) w.q(x, y, z);
        ur full = new ur(3131, 1, 3);
        bq tag = new bq();
        tag.a("storedId", 4);
        tag.a("storedMeta", 0);
        tag.a("storedQuantity", 1000);
        full.d(tag);                                                      // setTagCompound
        table.a(10, full);

        for (int i = 0; i < player.bJ.a.length; i++) player.bJ.a[i] = null;
        player.bJ.a[0] = new ur(3131, 15, 3);
        player.b(x + 0.5, y + 1.0, z + 1.5);
        ContainerAutoCraftingMk2 gui = new ContainerAutoCraftingMk2(player, table);
        player.bL = gui;
        int before = storedIn(table, player);
        String thrown = "";
        try {
            gui.a(58, 0, 1, player);                                      // shift-click hotbar slot 0
        } catch (Throwable t) {
            thrown = ", threw " + t;
        }
        int after = storedIn(table, player);
        sender.sendMessage(TAG + "act2: slot 10 " + dsuStack(table.a(10)) + ", slot 11 " + dsuStack(table.a(11))
                + ", hotbar " + dsuStack(player.bJ.a[0]) + ", stored cobblestone " + before + " -> " + after + thrown
                + "  (stock 16 full / 16000, fixed 1 full + 15 empty / 1000)");

        player.bL = player.bK;
        for (int i = 0; i < player.bJ.a.length; i++) player.bJ.a[i] = null;
        for (int i = 0; i < table.k_(); i++) table.a(i, null);
        w.e(x, y, z, 0);
    }

    /**
     * Owner holds a Canvas Bag (9268) in hotbar slot 0 with 64 diamonds in its first slot, opens
     * it, hovers the diamonds and presses the number key for hotbar slot 0 (click mode 2).
     * Counts diamonds in the inventory plus inside every bag in the inventory.
     */
    private void bag(CommandSender sender) {
        yc w = world();
        iq player = CraftFakePlayer.get(w, "Owner", true);
        for (int i = 0; i < player.bJ.a.length; i++) player.bJ.a[i] = null;
        ur bag = new ur(9268, 1, 0);
        ItemBag.getBagInventory(bag).a(0, new ur(264, 64, 0));             // packs into the bag's NBT
        player.bJ.a[0] = bag;
        player.bJ.c = 0;                                                  // currentItem
        ContainerBag gui = new ContainerBag(player.bJ, ItemBag.getBagInventory(bag), bag);
        player.bL = gui;
        int before = diamonds(player);
        String thrown = "";
        try {
            gui.a(0, 0, 2, player);                                       // slotClick: bag slot 0, number key 1
        } catch (Throwable t) {
            thrown = ", threw " + t;
        }
        sender.sendMessage(TAG + "bag: diamonds " + before + " -> " + diamonds(player) + thrown
                + "  (stock 64 -> 128, fixed 64 -> 64)");
        player.bL = player.bK;
        for (int i = 0; i < player.bJ.a.length; i++) player.bJ.a[i] = null;
    }

    /**
     * A Filler placed by Intruder two blocks outside the claim, with a one block box on a stone
     * block inside it and the Clear pattern, runs one work step. The same on open ground.
     */
    private void filler(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int y = p[1] + 5, z = p[2];
        int[] targets = { p[0] + CLAIM_HALF - 1, p[0] + OPEN_OFFSET };
        String[] where = { "claim", "open" };
        IFillerPattern clear = null;
        for (int id = 1; id < 32 && clear == null; id++) {
            IFillerPattern pattern = FillerManager.registry.getPattern(id);
            if (pattern instanceof FillerRemover) clear = pattern;
        }
        for (int i = 0; i < 2; i++) {
            int tx = targets[i], fx = tx + 3;
            w.d(tx, y, z, 1, 0);
            w.d(fx, y, z, 155, 0);                                        // Filler
            player.b(fx + 0.5, y + 1.0, z + 2.5);
            amq.p[155].a(w, fx, y, z, player);                            // onBlockPlacedBy, as when Intruder places it
            TileFiller f = (TileFiller) w.q(fx, y, z);
            String thrown = "";
            try {
                f.box.initialize(tx, y, z, tx, y, z);
                f.currentPattern = clear;
                f.done = false;
                f.getPowerProvider().receiveEnergy(100F, net.minecraftforge.common.ForgeDirection.DOWN);
                f.doWork();
            } catch (Throwable t) {
                thrown = ", threw " + t;
            }
            sender.sendMessage(TAG + "filler " + where[i] + ": target " + block(w, tx, y, z) + thrown
                    + (i == 0 ? "  (stock 0:0, fixed 1:0)" : "  (stock and fixed 0:0)"));
            w.e(tx, y, z, 0);
            w.e(fx, y, z, 0);
        }
    }

    /**
     * A Quarry placed by Intruder four blocks outside the claim, its head sent to a stone block
     * inside the claim (the quarry mines the block under targetY). The same on open ground.
     */
    private void quarry(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int y = p[1] + 10, z = p[2];
        int[] targets = { p[0] + CLAIM_HALF - 1, p[0] + OPEN_OFFSET };
        String[] where = { "claim", "open" };
        for (int i = 0; i < 2; i++) {
            int tx = targets[i], qx = tx + 5;
            w.d(tx, y, z, 1, 0);
            w.d(qx, y + 3, z, 153, 0);                                    // Quarry
            player.b(qx + 0.5, y + 4.0, z + 2.5);
            amq.p[153].a(w, qx, y + 3, z, player);                        // onBlockPlacedBy
            TileQuarry q = (TileQuarry) w.q(qx, y + 3, z);
            String thrown = "";
            try {
                q.targetX = tx;
                q.targetY = y + 1;
                q.targetZ = z;
                q.positionReached();
            } catch (Throwable t) {
                thrown = ", threw " + t;
            }
            sender.sendMessage(TAG + "quarry " + where[i] + ": target " + block(w, tx, y, z) + thrown
                    + (i == 0 ? "  (stock 0:0, fixed 1:0)" : "  (stock and fixed 0:0)"));
            clear(w, tx, y, z, 1);
            w.e(qx, y + 3, z, 0);
        }
    }

    /**
     * A mining turtle placed by Intruder just outside the claim, facing a stone block just inside
     * it, digs (side 4, toward -x), then with the cell cleared moves into it. The same on open
     * ground. Calls the turtle's private dig and move, which the Lua API queues.
     */
    private void turtle(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        ensureClaim(p);
        iq player = intruder(w);
        int y = p[1] + 15, z = p[2];
        int[] targets = { p[0] + CLAIM_HALF, p[0] + OPEN_OFFSET };
        String[] where = { "claim", "open" };
        for (int i = 0; i < 2; i++) {
            int tx = targets[i], sx = tx + 1;
            w.d(tx, y, z, 1, 0);
            w.d(sx, y, z, 209, 0);                                        // Turtle
            player.b(sx + 0.5, y + 1.0, z + 2.5);
            amq.p[209].a(w, sx, y, z, player);                            // onBlockPlacedBy
            String dug = "?", moved = "?", thrown = "";
            try {
                TileEntityTurtle t = (TileEntityTurtle) w.q(sx, y, z);
                t.setUpgrades(dan200.CCTurtle.getTurtleUpgrade(5), null); // Mining
                t.setFuelLevel(1000);
                java.lang.reflect.Method dig = TileEntityTurtle.class.getDeclaredMethod("dig", int.class);
                dig.setAccessible(true);
                dug = dig.invoke(t, 4) + " " + block(w, tx, y, z);
                w.e(tx, y, z, 0);
                java.lang.reflect.Method move = TileEntityTurtle.class.getDeclaredMethod("move", int.class);
                move.setAccessible(true);
                moved = move.invoke(t, 4) + " " + block(w, tx, y, z);
            } catch (Throwable t) {
                thrown = ", threw " + (t.getCause() != null ? t.getCause() : t);
            }
            sender.sendMessage(TAG + "turtle " + where[i] + ": dig " + dug + ", move " + moved + thrown
                    + (i == 0 ? "  (stock dig true 0:0, move true 209; fixed dig false 1:0, move false 0:0)"
                              : "  (stock and fixed dig true 0:0, move true 209)"));
            w.e(tx, y, z, 0);
            w.e(sx, y, z, 0);
        }
    }

    /**
     * A Quarry forces its own chunk and then a 64x64 area spanning 5x5 chunks on a fresh ticket,
     * as setBoundaries and forceChunkLoading do. The ticket holds 25 chunks, so one is dropped:
     * checks it isn't the quarry's own. Then BuildCraft's ticket callback runs for a ticket whose
     * quarry position holds stone.
     */
    private void quarrychunks(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        int y = p[1] + 40;
        int x0 = (((p[0] + 300) >> 4) << 4) + 15, z0 = ((p[2] >> 4) << 4) + 15;
        int qx = x0 - 20, qz = z0 - 20;
        getServer().getWorlds().get(0).loadChunk(qx >> 4, qz >> 4);
        w.d(qx, y, qz, 153, 0);
        TileQuarry q = (TileQuarry) w.q(qx, y, qz);
        String kept = "?", reload;
        net.minecraftforge.common.ForgeChunkManager.Ticket t = net.minecraftforge.common.ForgeChunkManager.requestTicket(
                buildcraft.BuildCraftFactory.instance, w, net.minecraftforge.common.ForgeChunkManager.Type.NORMAL);
        try {
            q.box.initialize(x0, y, z0, x0 + 63, y + 4, z0 + 63);
            net.minecraftforge.common.ForgeChunkManager.forceChunk(t, new xv(qx >> 4, qz >> 4));
            q.forceChunkLoading(t);
            // MCPC+ relocates Guava, so read the ImmutableSet through reflection as a plain Collection.
            java.util.Collection chunks = (java.util.Collection) t.getClass().getMethod("getChunkList").invoke(t);
            kept = chunks.contains(new xv(qx >> 4, qz >> 4)) + " of " + chunks.size();
        } catch (Throwable th) {
            kept = "threw " + th;
            th.printStackTrace();
        }
        net.minecraftforge.common.ForgeChunkManager.releaseTicket(t);

        w.d(qx + 2, y, qz, 1, 0);
        net.minecraftforge.common.ForgeChunkManager.Ticket bad = net.minecraftforge.common.ForgeChunkManager.requestTicket(
                buildcraft.BuildCraftFactory.instance, w, net.minecraftforge.common.ForgeChunkManager.Type.NORMAL);
        bad.getModData().a("quarryX", qx + 2);
        bad.getModData().a("quarryY", y);
        bad.getModData().a("quarryZ", qz);
        try {
            List tickets = new java.util.ArrayList();
            tickets.add(bad);
            buildcraft.BuildCraftFactory.instance.new QuarryChunkloadCallback().ticketsLoaded(tickets, w);
            reload = "ran";
        } catch (Throwable th) {
            reload = "threw " + th.getClass().getSimpleName();
        }
        net.minecraftforge.common.ForgeChunkManager.releaseTicket(bad);
        sender.sendMessage(TAG + "quarrychunks: ticket holds quarry chunk " + kept + ", ticket reload with no quarry " + reload
                + "  (stock false of 25 and threw NullPointerException, fixed true of 25 and ran)");
        w.e(qx + 2, y, qz, 0);
        w.e(qx, y, qz, 0);
    }

    /**
     * Two computers next to each other. TLiteCC.allowed is what the patched proxy consults before
     * it lets a packet drive a computer. Intruder with no GUI and Intruder with the other
     * computer's GUI open must both be refused for computer A; Owner with A's GUI open is allowed.
     */
    private void ccpacket(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        int x = p[0] + OPEN_OFFSET, y = p[1] + 45, z = p[2];
        w.d(x, y, z, 207, 0);                                             // Computer A
        w.d(x + 1, y, z, 207, 0);                                         // Computer B
        TileEntityComputer a = (TileEntityComputer) w.q(x, y, z);
        TileEntityComputer b = (TileEntityComputer) w.q(x + 1, y, z);

        iq intruder = intruder(w);
        intruder.b(x + 0.5, y + 1.0, z + 1.5);
        iq owner = CraftFakePlayer.get(w, "Owner", true);
        owner.b(x + 0.5, y + 1.0, z + 1.5);

        intruder.bL = intruder.bK;                                       // no GUI open
        boolean afar = TLiteCC.allowed(a, intruder);

        intruder.bL = new ContainerComputer(b);                          // B's GUI open, aiming at A
        boolean wrongGui = TLiteCC.allowed(a, intruder);

        owner.bL = new ContainerComputer(a);                             // A's GUI open
        boolean legit = TLiteCC.allowed(a, owner);

        sender.sendMessage(TAG + "ccpacket: drive from afar allowed=" + afar + ", from another computer's GUI allowed="
                + wrongGui + ", with the computer's own GUI allowed=" + legit + "  (expect false, false, true)");

        intruder.bL = intruder.bK;
        owner.bL = owner.bK;
        w.e(x, y, z, 0);
        w.e(x + 1, y, z, 0);
    }

    /**
     * An MFR Harvester (3131:6) with Owner\'s GUI open, sent the settings packet (type 3) with a
     * real key ("silkTouch") and with junk keys, the way a modified client would flood it. Counts
     * how many keys end up in the settings map.
     */
    private void harvester(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        int x = p[0] + OPEN_OFFSET, y = p[1] + 50, z = p[2];
        int meta = ((Integer) powercrystals.minefactoryreloaded.MineFactoryReloadedCore.machine0MetadataMappings
                .get(powercrystals.minefactoryreloaded.MineFactoryReloadedCore.Machine.Harvester)).intValue();
        w.d(x, y, z, 3120, meta);
        powercrystals.minefactoryreloaded.plants.TileEntityHarvester te =
                (powercrystals.minefactoryreloaded.plants.TileEntityHarvester) w.q(x, y, z);
        iq owner = CraftFakePlayer.get(w, "Owner", true);
        owner.b(x + 0.5, y + 1.0, z + 1.5);
        owner.bL = new powercrystals.minefactoryreloaded.gui.container.ContainerHarvester(te, owner.bJ);

        int before = te.getSettings().size();
        String thrown = "";
        try {
            send3(w, owner, x, y, z, "silkTouch", true);
            for (int i = 0; i < 50; i++) send3(w, owner, x, y, z, "junk" + i, true);
        } catch (Throwable t) {
            thrown = ", threw " + t;
        }
        sender.sendMessage(TAG + "harvester: settings keys " + before + " -> " + te.getSettings().size()
                + ", silkTouch=" + te.getSettings().get("silkTouch") + thrown
                + "  (stock 3 -> 53, fixed stays 3)");
        owner.bL = owner.bK;
        w.e(x, y, z, 0);
    }

    private void send3(yc w, iq player, int x, int y, int z, String key, boolean val) throws Exception {
        di pkt = powercrystals.core.net.PacketWrapper.createPacket("MFReloaded", 3,
                new Object[] { x, y, z, key, val });
        new powercrystals.minefactoryreloaded.net.ServerPacketHandler().onPacketData(null, pkt,
                (cpw.mods.fml.common.network.Player) (Object) player);
    }

    private void te(CommandSender sender) {
        yc w = world();
        int[] p = spot();
        int x = p[0] + OPEN_OFFSET, y = p[1] + 55, z = p[2];
        boolean okA = false, okB = false;
        for (int meta = 0; meta < 4 && !okA; meta++) { w.d(x, y, z, 2005, meta); okA = w.q(x, y, z) instanceof thermalexpansion.energy.tileentity.TileEnergyCell; }
        for (int meta = 0; meta < 4 && !okB; meta++) { w.d(x + 1, y, z, 2005, meta); okB = w.q(x + 1, y, z) instanceof thermalexpansion.energy.tileentity.TileEnergyCell; }
        any a = w.q(x, y, z), b = w.q(x + 1, y, z);
        if (!(a instanceof thermalexpansion.energy.tileentity.TileEnergyCell)) {
            sender.sendMessage(TAG + "te: no Energy Cell tile (got " + a + ")");
            w.e(x, y, z, 0); w.e(x + 1, y, z, 0); return;
        }
        iq intruder = intruder(w); intruder.b(x + 0.5, y + 1.0, z + 1.5);
        iq owner = CraftFakePlayer.get(w, "Owner", true); owner.b(x + 0.5, y + 1.0, z + 1.5);
        thermalexpansion.core.network.PacketTile pkt = new thermalexpansion.core.network.PacketTile(0, x, y, z, null);
        intruder.bL = intruder.bK;
        boolean afar = TLiteTE.gateTarget(pkt, w, intruder) != null;
        intruder.bL = new thermalexpansion.energy.gui.ContainerEnergyCell(intruder.bJ, b);
        boolean wrongGui = TLiteTE.gateTarget(pkt, w, intruder) != null;
        owner.bL = new thermalexpansion.energy.gui.ContainerEnergyCell(owner.bJ, a);
        boolean legit = TLiteTE.gateTarget(pkt, w, owner) != null;
        sender.sendMessage(TAG + "te: retune from afar allowed=" + afar + ", from another cell's GUI allowed=" + wrongGui
                + ", with the cell's own GUI allowed=" + legit + "  (expect false, false, true)");
        intruder.bL = intruder.bK; owner.bL = owner.bK;
        w.e(x, y, z, 0); w.e(x + 1, y, z, 0);
    }

    private void crystal(CommandSender sender) {
        yc w = world();
        cpw.mods.ironchest.TileEntityCrystalChest chest = new cpw.mods.ironchest.TileEntityCrystalChest();
        int[] ids = { 1, 4, 20, 3, 5 };
        for (int i = 0; i < ids.length; i++) chest.a(i, new ur(ids[i], 1, 0));
        String thrown = "";
        int shown = -1;
        try {
            java.lang.reflect.Method sort = cpw.mods.ironchest.TileEntityIronChest.class.getDeclaredMethod("sortTopStacks");
            sort.setAccessible(true);
            sort.invoke(chest);
            ur[] top = chest.getTopItemStacks();
            shown = 0;
            for (ur u : top) if (u != null) shown++;
        } catch (Throwable t) {
            thrown = "threw " + t;
        }
        sender.sendMessage(TAG + "crystal: rendered stacks " + shown + thrown + "  (stock 5, fixed 3)");
    }

    /** ChickenChunks Chunk Loader: getChunks() must return one chunk however large the radius. */
    private void spotloader(CommandSender sender) {
        codechicken.chunkloader.TileChunkLoader t = new codechicken.chunkloader.TileChunkLoader();
        t.shape = codechicken.chunkloader.ChunkLoaderShape.Square;
        t.radius = 3;
        int n = t.getChunks().size();
        sender.sendMessage(TAG + "spotloader: ChickenChunks loader at radius 3 loads " + n + " chunk(s)  (stock 25, fixed 1)");
    }

    /** immibis Dimensional Anchor: limitRadius() must pin it to its own chunk however large the radius. */
    private void da(CommandSender sender) {
        immibis.chunkloader.TileChunkLoader t = new immibis.chunkloader.TileChunkLoader();
        t.owner = "Owner";
        t.shape = immibis.chunkloader.Shape.SQUARE;
        t.radius = 3;
        t.limitRadius();
        int n = t.getNumChunks();
        sender.sendMessage(TAG + "da: Dimensional Anchor at radius 3 loads " + n + " chunk(s)  (stock 49, fixed 1)");
    }

    /** AdditionalPipes chunk loader: the ChunkLoaderConversion config leaves it off by default. */
    private void apgate(CommandSender sender) {
        boolean enabled = TLiteAP.apChunkLoadEnabled();
        sender.sendMessage(TAG + "apgate: AdditionalPipes chunk loader enabled = " + enabled + "  (expect false)");
    }

    /**
     * One combined per-player cap: ChickenChunks loaders and Dimensional Anchors draw from the same
     * budget. Forces the limit to 3 through the config so the count is easy to read, claims five
     * ChickenChunks loaders for one owner, then a same-owner anchor that must already be over.
     */
    private void quota(CommandSender sender) throws Exception {
        java.io.File cfg = new java.io.File("config/ChunkLoaderConversion.cfg");
        java.io.FileWriter w = new java.io.FileWriter(cfg);
        w.write("additionalpipes.chunkloader.enabled=false\nchunkloader.maxchunksperplayer=3\n");
        w.close();

        final yc wref = world();
        int allowed = 0;
        for (int i = 0; i < 5; i++) {
            final int xi = i * 16;
            codechicken.chunkloader.IChickenChunkLoader fake = new codechicken.chunkloader.IChickenChunkLoader() {
                public String getOwner() { return "QuotaTester"; }
                public Object getMod() { return null; }
                public yc getWorld() { return wref; }
                public codechicken.core.BlockCoord getPosition() { return new codechicken.core.BlockCoord(xi, 0, 0); }
                public void deactivate() { }
                public java.util.Collection getChunks() { return null; }
            };
            if (TLiteChunkQuota.ccClaim(fake)) allowed++;
        }
        immibis.chunkloader.TileChunkLoader anchor = new immibis.chunkloader.TileChunkLoader();
        anchor.owner = "QuotaTester";
        boolean anchorAllowed = TLiteChunkQuota.daClaim(anchor);
        int active = TLiteChunkQuota.activeCount("QuotaTester");
        int disabled = TLiteChunkQuota.disabledCount("QuotaTester");
        int listed = TLiteChunkQuota.describe("QuotaTester").size();
        sender.sendMessage(TAG + "quota: ChickenChunks allowed " + allowed + " of 5, same-owner anchor allowed " + anchorAllowed
                + "; registry active " + active + ", disabled " + disabled + ", listed " + listed
                + "  (expect 3, false, active 3, disabled 3, listed 6)");
    }

    /** Industrial Tesla Coil flags: config toggles read, and the drop-deny handler cancels only
     *  drops from the coil\'s own damage source. */
    private void tesla(CommandSender sender) throws Exception {
        java.io.File cfg = new java.io.File("config/TeslaCoil.cfg");
        java.io.FileWriter w = new java.io.FileWriter(cfg);
        w.write("industrialTeslaCoil.denyMobDrops=true\nindustrialTeslaCoil.noPlayerDamage=true\n");
        w.close();
        boolean np = TLiteARS.noPlayerDamage();
        boolean dd = TLiteARS.denyMobDrops();

        String handler = "n/a";
        try {
            lh[] two = new lh[2];
            int f = 0;
            for (java.lang.reflect.Field fld : lh.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(fld.getModifiers()) && fld.getType() == lh.class) {
                    fld.setAccessible(true);
                    Object v = fld.get(null);
                    if (v != null && f < 2) two[f++] = (lh) v;
                }
            }
            TLiteARSDrops h = new TLiteARSDrops();
            TLiteARSDrops.teslaSource = two[0];
            net.minecraftforge.event.entity.living.LivingDropsEvent e1 =
                new net.minecraftforge.event.entity.living.LivingDropsEvent(null, two[0], new java.util.ArrayList(), 0, false, 0);
            h.onLivingDrops(e1);
            net.minecraftforge.event.entity.living.LivingDropsEvent e2 =
                new net.minecraftforge.event.entity.living.LivingDropsEvent(null, two[1], new java.util.ArrayList(), 0, false, 0);
            h.onLivingDrops(e2);
            handler = "coil-source cancelled=" + e1.isCanceled() + ", other-source cancelled=" + e2.isCanceled();
        } catch (Throwable t) {
            handler = "threw " + t;
        }
        sender.sendMessage(TAG + "tesla: noPlayerDamage=" + np + ", denyMobDrops=" + dd + "; " + handler
                + "  (expect true, true; coil cancelled=true, other=false)");
    }

    /**
     * Detonates an IC2 nuke ExplosionIC2 one block outside a GriefPrevention claim border and
     * checks that claimed blocks are untouched while open-ground blocks are destroyed. Runs below
     * sea level in a carved air pocket, so GP's surface-explosion strip does not mask the claim
     * check and the blast is not muffled by surrounding stone. Stock (no coremod) destroys both
     * sides; the patched coremod fires EntityExplodeEvent so GP strips only the claimed blocks.
     */
    private void explode(CommandSender sender) {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ();
        int y0 = 30;                                                     // below sea level
        for (int dx = -1; dx <= 2; dx++) bw.loadChunk((cx + dx * 16) >> 4, cz >> 4);

        Location at = new Location(bw, cx, y0, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw,
                    cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        int border = cx + 8;                                             // last claimed x

        // Carve an air pocket, then lay a stone row from inside the claim to open ground.
        for (int x = cx + 2; x <= cx + 17; x++) for (int dy = -2; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++)
            w.e(x, y0 + dy, cz + dz, 0);
        for (int x = cx + 3; x <= cx + 15; x++) w.e(x, y0, cz, 1);       // stone row

        int claimBefore = 0, openBefore = 0;
        for (int x = cx + 3; x <= border; x++) if (w.a(x, y0, cz) == 1) claimBefore++;
        for (int x = border + 1; x <= cx + 15; x++) if (w.a(x, y0, cz) == 1) openBefore++;

        // Nuke bomb one block outside the border, in the air just above the row, at power 4.0.
        ic2.core.ExplosionIC2 ex = new ic2.core.ExplosionIC2(
                w, null, (border + 1) + 0.5, y0 + 1 + 0.5, cz + 0.5, 4.0F, 0.3F, 1.5F,
                ic2.core.IC2DamageSource.nuke, "Intruder");
        ex.doExplosion();

        int claimAfter = 0, openAfter = 0;
        for (int x = cx + 3; x <= border; x++) if (w.a(x, y0, cz) == 1) claimAfter++;
        for (int x = border + 1; x <= cx + 15; x++) if (w.a(x, y0, cz) == 1) openAfter++;

        clear(w, cx + 9, y0, cz, 12);

        // Second case: detonate INSIDE the claim (origin at the centre), explosives disabled (default).
        for (int x = cx - 6; x <= cx + 6; x++) for (int dy = -2; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++)
            w.e(x, y0 + dy, cz + dz, 0);
        for (int x = cx - 6; x <= cx + 6; x++) w.e(x, y0, cz, 1);
        int inBefore = 0;
        for (int x = cx - 6; x <= cx + 6; x++) if (w.a(x, y0, cz) == 1) inBefore++;
        ic2.core.ExplosionIC2 ex2 = new ic2.core.ExplosionIC2(
                w, null, cx + 0.5, y0 + 1 + 0.5, cz + 0.5, 4.0F, 0.3F, 1.5F,
                ic2.core.IC2DamageSource.nuke, "Intruder");
        ex2.doExplosion();
        int inAfter = 0;
        for (int x = cx - 6; x <= cx + 6; x++) if (w.a(x, y0, cz) == 1) inAfter++;
        clear(w, cx, y0, cz, 12);

        sender.sendMessage(TAG + "explode: outside-border claimed " + (claimBefore - claimAfter) + "/" + claimBefore
                + " destroyed, open " + (openBefore - openAfter) + "/" + openBefore + " destroyed"
                + "; inside-claim claimed " + (inBefore - inAfter) + "/" + inBefore + " destroyed"
                + "  (fixed: 0 claimed both cases, open some; stock: claimed damaged)");
    }

    /**
     * Drives the real TekkitLiteCustomizer ClaimQuery (the nuke-placement warning's claim check)
     * through its own plugin class loader against a live claim, toggling GriefPrevention's
     * per-claim explosives flag. Confirms it reads true only when explosives are enabled and false
     * in the wilderness, on a server with GriefPrevention but no Vault.
     */
    private void nukewarn(CommandSender sender) throws Exception {
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ();
        bw.loadChunk(cx >> 4, cz >> 4);
        Location at = new Location(bw, cx, 30, cz);
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(at, true, null);
        if (claim == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
            claim = GriefPrevention.instance.dataStore.getClaimAt(at, true, null);
        }

        org.bukkit.plugin.Plugin plug = getServer().getPluginManager().getPlugin("TekkitLiteCustomizer");
        if (plug == null || claim == null) {
            sender.sendMessage(TAG + "nukewarn: setup failed (plugin=" + plug + ", claim=" + claim + ")");
            return;
        }
        java.lang.reflect.Method m = plug.getClass().getClassLoader()
                .loadClass("me.ryanhamshire.TekkitCustomizer.ClaimQuery")
                .getDeclaredMethod("explosionsAllowedAt", Location.class);
        m.setAccessible(true);

        claim.areExplosivesAllowed = false;
        boolean off = ((Boolean) m.invoke(null, at)).booleanValue();
        claim.areExplosivesAllowed = true;
        boolean on = ((Boolean) m.invoke(null, at)).booleanValue();
        boolean wild = ((Boolean) m.invoke(null, new Location(bw, cx + 500, 30, cz + 500))).booleanValue();
        claim.areExplosivesAllowed = false;

        sender.sendMessage(TAG + "nukewarn: explosionsAllowedAt off=" + off + ", on=" + on + ", wilderness=" + wild
                + "  (expect false, true, false)");
    }

    /**
     * Sets off an IC2 dynamite PointExplosion one block outside a claim border and checks that
     * claimed blocks are untouched while open-ground blocks are destroyed (below sea level, carved
     * air pocket, same as the nuke explode test). Stock removes claimed blocks; the patched coremod
     * fires EntityExplodeEvent so GriefPrevention strips only the claimed ones.
     */
    private void dynamite(CommandSender sender) {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ();
        int y0 = 30;
        for (int dx = -1; dx <= 2; dx++) bw.loadChunk((cx + dx * 16) >> 4, cz >> 4);
        Location at = new Location(bw, cx, y0, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        int border = cx + 8;
        for (int x = cx + 2; x <= cx + 17; x++) for (int dy = -2; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++)
            w.e(x, y0 + dy, cz + dz, 0);
        for (int x = cx + 3; x <= cx + 15; x++) w.e(x, y0, cz, 1);

        int cBefore = 0, oBefore = 0;
        for (int x = cx + 3; x <= border; x++) if (w.a(x, y0, cz) == 1) cBefore++;
        for (int x = border + 1; x <= cx + 15; x++) if (w.a(x, y0, cz) == 1) oBefore++;

        ic2.core.PointExplosion pe = new ic2.core.PointExplosion(w, null, border + 1, y0, cz, 2.0F, 0.3F, 1.0F);
        pe.doExplosionA(6, 3, 3, 6, 3, 3);
        pe.doExplosionB(true);

        int cAfter = 0, oAfter = 0;
        for (int x = cx + 3; x <= border; x++) if (w.a(x, y0, cz) == 1) cAfter++;
        for (int x = border + 1; x <= cx + 15; x++) if (w.a(x, y0, cz) == 1) oAfter++;
        clear(w, cx + 9, y0, cz, 12);

        sender.sendMessage(TAG + "dynamite: claimed " + (cBefore - cAfter) + "/" + cBefore + " destroyed, open "
                + (oBefore - oAfter) + "/" + oBefore + " destroyed  (fixed: claimed 0, open some; stock: both)");
    }

    /**
     * Drives the deployed Steve's Carts owner-tracking guard (TLiteSC.breakIfAllowed) against a
     * real claim: a cart owned by an intruder is refused inside another player's claim, the claim
     * owner's own cart is allowed there, and any cart is allowed on open ground.
     */
    private void cartmine(CommandSender sender) throws Exception {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ(), y = 40;
        Location at = new Location(bw, cx, y, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        String a = cartCase(w, cx, y, cz, "Intruder");
        String b = cartCase(w, cx + 1, y, cz, "Owner");
        String c = cartCase(w, cx + 40, y, cz, "Intruder");
        sender.sendMessage(TAG + "cartmine: intruder-in-claim=" + a + ", owner-in-claim=" + b + ", intruder-open=" + c
                + "  (expect refused/stone, mined/air, mined/air)");
    }

    /** Places a stone at x,y,z, tries to mine it via a cart owned by `owner`, returns the outcome. */
    private String cartCase(yc w, int x, int y, int z, String owner) {
        try {
            getServer().getWorlds().get(0).loadChunk(x >> 4, z >> 4);
            w.e(x, y, z, 1);
            vswe.stevescarts.Carts.entMCBase cart = new vswe.stevescarts.Carts.entMCBase(w);
            bq nbt = new bq();
            nbt.a("tliteOwner", owner);
            TLiteSC.loadOwner(cart, nbt);
            boolean broke = TLiteSC.breakIfAllowed(w, x, y, z, 0, cart);
            boolean air = (w.a(x, y, z) == 0);
            w.e(x, y, z, 0);
            return (broke ? "mined" : "refused") + "/" + (air ? "air" : "stone");
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }

    /**
     * Drives the RedPower frame-motor guard (TLiteRPMachine.frameAllowed) against a real claim:
     * a solved frame over a block is refused when the motor's owner is an intruder inside another
     * player's claim, allowed for the claim owner, and allowed for anyone on open ground.
     */
    private void frame(CommandSender sender) throws Exception {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ(), y = 40;
        Location at = new Location(bw, cx, y, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        String inClaim = frameCase(w, cx, y, cz, "Intruder");
        String ownerIn = frameCase(w, cx + 1, y, cz, "Owner");
        String open = frameCase(w, cx + 40, y, cz, "Intruder");
        sender.sendMessage(TAG + "frame: intruder-in-claim=" + inClaim + ", owner-in-claim=" + ownerIn
                + ", intruder-open=" + open + "  (expect refused, allowed, allowed)");
    }

    /**
     * Drives the generic RedPower tile guard (TLiteRPMachine.tileSet) used by the Thermopile and
     * Grate: an ownerless tile (Thermopile) is refused inside any claim; an owner-tracked tile
     * (Grate) is refused for an intruder and allowed for the claim owner; all are allowed on open
     * ground.
     */
    /**
     * Drives the IC2 right-click tool guard (TLiteIC2.wrenchEdit, used by the Wrench and Electric
     * Hoe) against a claim: an intruder's edit is refused inside another player's claim, the claim
     * owner's is allowed, and any is allowed on open ground.
     */
    /**
     * Drives the IC2 automated-machine guard (TLiteIC2.machineSet, used by Miner/Pump/Terraformer)
     * against a claim: an ownerless machine and an intruder-owned one are refused inside another
     * player's claim, the claim owner's own is allowed, and all are allowed on open ground.
     */
    /**
     * Drives the placement-item guard (TLiteIC2.wrenchEditMeta, used by Cable/Resin/Luminator) and
     * the Foam Sprayer per-block guard (setSprayer + sprayEdit) against a claim.
     */
    /**
     * Verifies the turtle placement fix: TLiteTurtle.nameTurtlePlayer renames the fake TurtlePlayer
     * to the turtle's owner (so its BlockPlaceEvent is attributed to the owner, allowed in the
     * owner's own claim), and leaves an ownerless turtle as "ComputerCraft".
     */
    private void turtleplace(CommandSender sender) throws Exception {
        yc w = world();
        dan200.turtle.shared.TileEntityTurtle owned = new dan200.turtle.shared.TileEntityTurtle();
        bq nbt = new bq();
        nbt.a("tliteOwner", "Owner");
        TLiteTurtle.load(owned, nbt);
        qx tp = new dan200.turtle.shared.TurtlePlayer(w);
        String before = tp.bR;
        TLiteTurtle.nameTurtlePlayer(tp, owned);
        String after = tp.bR;

        dan200.turtle.shared.TileEntityTurtle ownerless = new dan200.turtle.shared.TileEntityTurtle();
        qx tp2 = new dan200.turtle.shared.TurtlePlayer(w);
        TLiteTurtle.nameTurtlePlayer(tp2, ownerless);

        sender.sendMessage(TAG + "turtleplace: owned " + before + " -> " + after + "; ownerless=" + tp2.bR
                + "  (expect ComputerCraft -> Owner; ComputerCraft)");
    }

    private void place(CommandSender sender) throws Exception {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ(), y = 40;
        Location at = new Location(bw, cx, y, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        String mi = placeMetaCase(w, cx, y, cz, "Intruder");
        String mo = placeMetaCase(w, cx + 1, y, cz, "Owner");
        String si = sprayCase(w, cx + 2, y, cz, "Intruder");
        String so = sprayCase(w, cx + 3, y, cz, "Owner");
        String open = placeMetaCase(w, cx + 40, y, cz, "Intruder");
        sender.sendMessage(TAG + "place: meta-intruder=" + mi + " meta-owner=" + mo
                + "; spray-intruder=" + si + " spray-owner=" + so + "; open=" + open
                + "  (expect refused, placed, refused, placed, placed)");
    }

    private String placeMetaCase(yc w, int x, int y, int z, String name) {
        try {
            getServer().getWorlds().get(0).loadChunk(x >> 4, z >> 4);
            w.e(x, y, z, 0);
            iq player = CraftFakePlayer.get(w, name, true);
            boolean placed = TLiteIC2.wrenchEditMeta(w, x, y, z, 1, 0, player);
            boolean stone = (w.a(x, y, z) == 1);
            w.e(x, y, z, 0);
            return (placed ? "placed" : "refused") + "/" + (stone ? "stone" : "air");
        } catch (Throwable t) { return "ERR:" + t; }
    }

    private String sprayCase(yc w, int x, int y, int z, String name) {
        try {
            getServer().getWorlds().get(0).loadChunk(x >> 4, z >> 4);
            w.e(x, y, z, 0);
            iq player = CraftFakePlayer.get(w, name, true);
            TLiteIC2.setSprayer(player);
            boolean placed = TLiteIC2.sprayEdit(w, x, y, z, 1);
            boolean stone = (w.a(x, y, z) == 1);
            w.e(x, y, z, 0);
            return (placed ? "placed" : "refused") + "/" + (stone ? "stone" : "air");
        } catch (Throwable t) { return "ERR:" + t; }
    }

    private void ic2machine(CommandSender sender) throws Exception {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ(), y = 40;
        Location at = new Location(bw, cx, y, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        String a = machineCase(w, cx, y, cz, null);
        String b = machineCase(w, cx + 1, y, cz, "Owner");
        String c = machineCase(w, cx + 2, y, cz, "Intruder");
        String d = machineCase(w, cx + 40, y, cz, null);
        sender.sendMessage(TAG + "ic2machine: no-owner-in-claim=" + a + ", owner-in-claim=" + b
                + ", intruder-in-claim=" + c + ", no-owner-open=" + d + "  (expect refused, edited, refused, edited)");
    }

    private String machineCase(yc w, int x, int y, int z, String owner) {
        try {
            getServer().getWorlds().get(0).loadChunk(x >> 4, z >> 4);
            w.e(x, y, z, 1);
            any tile = new ic2.core.block.machine.tileentity.TileEntityMiner();
            if (owner != null) {
                bq nbt = new bq();
                nbt.a("tliteOwner", owner);
                TLiteIC2.loadMachineOwner(tile, nbt);
            }
            boolean edited = TLiteIC2.machineSet(w, x, y, z, 0, tile);
            boolean air = (w.a(x, y, z) == 0);
            w.e(x, y, z, 0);
            return (edited ? "edited" : "refused") + "/" + (air ? "air" : "block");
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }

    private void wrench(CommandSender sender) throws Exception {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ(), y = 40;
        Location at = new Location(bw, cx, y, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        String a = wrenchCase(w, cx, y, cz, "Intruder");
        String b = wrenchCase(w, cx + 1, y, cz, "Owner");
        String c = wrenchCase(w, cx + 40, y, cz, "Intruder");
        sender.sendMessage(TAG + "wrench: intruder-in-claim=" + a + ", owner-in-claim=" + b + ", intruder-open=" + c
                + "  (expect refused, dismantled, dismantled)");
    }

    private String wrenchCase(yc w, int x, int y, int z, String name) {
        try {
            getServer().getWorlds().get(0).loadChunk(x >> 4, z >> 4);
            w.e(x, y, z, 1);
            iq player = CraftFakePlayer.get(w, name, true);
            boolean broke = TLiteIC2.wrenchEdit(w, x, y, z, 0, player);
            boolean air = (w.a(x, y, z) == 0);
            w.e(x, y, z, 0);
            return (broke ? "dismantled" : "refused") + "/" + (air ? "air" : "block");
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }

    private void rpguard(CommandSender sender) throws Exception {
        yc w = world();
        org.bukkit.World bw = getServer().getWorlds().get(0);
        Location spawn = bw.getSpawnLocation();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ(), y = 40;
        Location at = new Location(bw, cx, y, cz);
        if (GriefPrevention.instance.dataStore.getClaimAt(at, true, null) == null) {
            GriefPrevention.instance.dataStore.createClaim(bw, cx - 8, cx + 8, 0, 255, cz - 8, cz + 8, "Owner", null, null);
        }
        String noOwner = rpCase(w, cx, y, cz, null);
        String ownerIn = rpCase(w, cx + 1, y, cz, "Owner");
        String intruder = rpCase(w, cx + 2, y, cz, "Intruder");
        String open = rpCase(w, cx + 40, y, cz, null);
        sender.sendMessage(TAG + "rpguard: no-owner-in-claim=" + noOwner + ", owner-in-claim=" + ownerIn
                + ", intruder-in-claim=" + intruder + ", no-owner-open=" + open
                + "  (expect refused, edited, refused, edited)");
    }

    private String rpCase(yc w, int x, int y, int z, String owner) {
        try {
            getServer().getWorlds().get(0).loadChunk(x >> 4, z >> 4);
            w.e(x, y, z, 1);
            any tile = new com.eloraam.redpower.machine.TileThermopile();
            tile.k = w;
            if (owner != null) {
                bq nbt = new bq();
                nbt.a("tliteOwner", owner);
                TLiteRPMachine.loadOwner(tile, nbt);
            }
            boolean edited = TLiteRPMachine.tileSet(w, x, y, z, 0, tile);
            boolean air = (w.a(x, y, z) == 0);
            w.e(x, y, z, 0);
            return (edited ? "edited" : "refused") + "/" + (air ? "air" : "stone");
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }

    /** Solves a one-block frame at x,y,z and asks the motor guard whether owner `owner` may move it. */
    private String frameCase(yc w, int x, int y, int z, String owner) {
        try {
            getServer().getWorlds().get(0).loadChunk(x >> 4, z >> 4);
            w.e(x, y, z, 1);                                              // a block for the frame to carry
            com.eloraam.redpower.core.WorldCoord wc = new com.eloraam.redpower.core.WorldCoord(x, y, z);
            com.eloraam.redpower.core.WorldCoord mp = new com.eloraam.redpower.core.WorldCoord(x, y - 2, z);
            com.eloraam.redpower.core.FrameLib.FrameSolver fs =
                    new com.eloraam.redpower.core.FrameLib.FrameSolver(w, wc, mp, -1);
            fs.solve();
            int n = fs.getFrameSet().size();
            com.eloraam.redpower.machine.TileMotor motor = new com.eloraam.redpower.machine.TileMotor();
            motor.k = w;
            bq nbt = new bq();
            nbt.a("tliteOwner", owner);
            TLiteRPMachine.loadOwner(motor, nbt);
            boolean allowed = TLiteRPMachine.frameAllowed(motor, fs, 1);
            w.e(x, y, z, 0);
            return (allowed ? "allowed" : "refused") + "(set=" + n + ")";
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }

    /** Resolves Steve's Carts module id 31 (banned as 31997:31) to its name and worker class. */
    private void scmod(CommandSender sender) throws Exception {
        java.util.HashMap list = vswe.stevescarts.ModuleData.ModuleData.getList();
        java.lang.reflect.Field mc = vswe.stevescarts.ModuleData.ModuleData.class.getDeclaredField("moduleClass");
        mc.setAccessible(true);
        String found = "none";
        for (Object o : list.values()) {
            vswe.stevescarts.ModuleData.ModuleData md = (vswe.stevescarts.ModuleData.ModuleData) o;
            if ((md.getID() & 0xFF) == 31) {
                Object cls = mc.get(md);
                found = "name=" + md.getName() + " worker=" + (cls == null ? "null" : ((Class) cls).getName());
            }
        }
        sender.sendMessage(TAG + "scmod: id31 " + found);
    }

    /** Prints the owning item class for each banned "dynamite" id, to see which mod each belongs to. */
    private void iddump(CommandSender sender) {
        int[][] ids = { { 30214, 0 }, { 30215, 0 }, { 31997, 31 }, { 31998, 6 } };
        StringBuilder sb = new StringBuilder();
        for (int[] im : ids) {
            String cls;
            try {
                up item = new ur(im[0], 1, im[1]).b();
                cls = (item == null) ? "null" : item.getClass().getName();
            } catch (Throwable t) {
                cls = "ERR:" + t;
            }
            sb.append(im[0]).append(":").append(im[1]).append("=").append(cls).append("  ");
        }
        sender.sendMessage(TAG + "iddump: " + sb);
    }

    /** APM Battery Station dupe guard: only stackable (same) items merge into the output slot. */
    private void apmdupe(CommandSender sender) {
        ur a1 = new ur(4, 1, 0);
        ur a2 = new ur(4, 1, 0);
        ur other = new ur(1, 1, 0);
        boolean same = TLiteAPM.canMerge(a1, a2);
        boolean diff = TLiteAPM.canMerge(a1, other);
        boolean nul = TLiteAPM.canMerge(a1, null);
        sender.sendMessage(TAG + "apmdupe: same-item merge=" + same + ", different-item merge=" + diff
                + ", null merge=" + nul + "  (expect true, false, false)");
    }

    /** NuclearControl card cap: a card already at the field limit refuses new keys but still updates existing ones. */
    private void ncflood(CommandSender sender) throws Exception {
        ur full = new ur(1, 1, 0);
        bq tag = shedar.mods.ic2.nuclearcontrol.utils.ItemStackUtils.getTagCompound(full);
        for (int i = 0; i < 40; i++) tag.a("f" + i, i);
        boolean blocksNew = !TLiteNC.allowCardField(full, "newkey");
        boolean allowsExisting = TLiteNC.allowCardField(full, "f0");
        ur fresh = new ur(1, 1, 0);
        boolean freshAllows = TLiteNC.allowCardField(fresh, "anything");
        int keys = tag.c().size();
        sender.sendMessage(TAG + "ncflood: full(" + keys + ") blocks new=" + blocksNew + ", updates existing=" + allowsExisting
                + ", fresh allows=" + freshAllows + "  (expect true, true, true)");
    }

    /** LogisticsPipes security gate: only a player with the station's GUI open may edit it. */
    private void lpsec(CommandSender sender) throws Exception {
        logisticspipes.blocks.LogisticsSecurityTileEntity tile = new logisticspipes.blocks.LogisticsSecurityTileEntity();
        iq p = intruder(world());
        iq other = CraftFakePlayer.get(world(), "Other", true);
        java.lang.reflect.Field f = logisticspipes.blocks.LogisticsSecurityTileEntity.class.getDeclaredField("listener");
        f.setAccessible(true);
        f.set(tile, new java.util.ArrayList());
        boolean noneViewing = TLiteLP.securityAllowed(tile, p);
        java.util.List only = new java.util.ArrayList(); only.add(p); f.set(tile, only);
        boolean pViewing = TLiteLP.securityAllowed(tile, p);
        java.util.List others = new java.util.ArrayList(); others.add(other); f.set(tile, others);
        boolean pWhileOther = TLiteLP.securityAllowed(tile, p);
        sender.sendMessage(TAG + "lpsec: no viewer allowed=" + noneViewing + ", sender viewing allowed=" + pViewing
                + ", only-other-viewing allowed=" + pWhileOther + "  (expect false, true, false)");
    }

    /** LogisticsPipes request clamp: legit amounts pass through, huge/negative ones are bounded. */
    private void lpclamp(CommandSender sender) {
        int[] in = { 5, 64, 100000, 100001, Integer.MAX_VALUE, -7 };
        StringBuilder sb = new StringBuilder();
        for (int v : in) sb.append(v).append("->").append(TLiteLP.clampAmount(v)).append("  ");
        sender.sendMessage(TAG + "lpclamp: " + sb + " (expect <=100000, negatives 0, small unchanged)");
    }

    /** ComputerCraft http host filter: public destinations pass, loopback/LAN are blocked. */
    private void cchttp(CommandSender sender) throws Exception {
        String[] pub = { "http://8.8.8.8/", "http://1.1.1.1/x" };
        String[] blk = { "http://127.0.0.1:8123/", "http://localhost/", "http://10.1.2.3/",
                "http://192.168.0.5/", "http://172.16.5.5/", "http://169.254.1.1/", "http://0.0.0.0/" };
        int pubAllowed = 0;
        for (String u : pub) if (!TLiteCC.isBlockedHttp(new java.net.URL(u))) pubAllowed++;
        int blkBlocked = 0;
        for (String u : blk) if (TLiteCC.isBlockedHttp(new java.net.URL(u))) blkBlocked++;
        sender.sendMessage(TAG + "cchttp: public allowed " + pubAllowed + "/" + pub.length
                + ", local/LAN blocked " + blkBlocked + "/" + blk.length
                + "  (expect " + pub.length + " and " + blk.length + ")");
    }

    private static int diamonds(iq player) {
        int n = 0;
        for (ur s : player.bJ.a) {
            if (s == null) continue;
            if (s.c == 264) n += s.a;
            la inside = ItemBag.getBagInventory(s);
            if (inside != null) {
                for (int i = 0; i < inside.k_(); i++) {
                    ur t = inside.a(i);
                    if (t != null && t.c == 264) n += t.a;
                }
            }
        }
        return n;
    }

    private static int storedIn(la table, iq player) {
        int n = 0;
        for (int i = 0; i < table.k_(); i++) n += storedIn(table.a(i));
        for (ur s : player.bJ.a) n += storedIn(s);
        return n;
    }

    private static int storedIn(ur s) {
        if (s == null || s.c != 3131 || s.j() != 3 || s.p() == null) return 0;
        return s.a * s.p().e("storedQuantity");
    }

    private static String dsuStack(ur s) {
        if (s == null) return "empty";
        return s.a + "x " + s.c + ":" + s.j() + (s.p() == null ? " no tag" : " holding " + s.p().e("storedQuantity"));
    }

    /** Sets everything within r blocks of x,y,z to air, fire included. */
    private static void clear(yc w, int x, int y, int z, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++)
            if (w.a(x + dx, y + dy, z + dz) != 0) w.e(x + dx, y + dy, z + dz, 0);
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
