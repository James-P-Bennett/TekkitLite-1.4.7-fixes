package tlitefixes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches classes from signed mod jars as they load. Written against ASM 4.0, the version the
 * 1.4.7 server ships in lib/.
 *
 *   IC2 laser    EntityMiningLaser.onUpdate (obf j_)
 *                  this.canMine(blockId) -> TLiteIC2.canMine(this, blockId, x, y, z)
 *                EntityMiningLaser.explode
 *                  explosion.doExplosion() -> TLiteIC2.doExplosion(explosion, this)
 *                ExplosionIC2.doExplosion
 *                  chunkCache.getBlockId(x, y, z) -> TLiteIC2.explosionBlockId(chunkCache, x, y, z, this)
 *
 *   RP bagdupe   ContainerBag gains slotClick (obf a(IIILqx;)Lur;):
 *                  if (!TLiteRP.allowClick(this, slot, mode, this.itemBag)) return null;
 *                  return super.slotClick(slot, button, mode, player);
 *
 * When a class doesn't have exactly the expected sites it is left unpatched and a line is
 * logged, so a different mod version can't be half patched. build.sh runs main() against the
 * stock jars and fails the build if any site is missing.
 */
public class TLiteTransformer implements IClassTransformer {

    static final String TAG = "[TLiteFixes] ";

    static final String LASER = "ic2/core/item/tool/EntityMiningLaser";
    static final String EXPLOSION = "ic2/core/ExplosionIC2";
    static final String POINTEXP = "ic2/core/PointExplosion";
    static final String BAG = "com/eloraam/redpower/base/ContainerBag";
    static final String CLICK = "(IIILqx;)Lur;";
    static final String COREPROXY = "com/eloraam/redpower/core/CoreProxy";
    static final String BREAKER = "com/eloraam/redpower/machine/TileBreaker";
    static final String IGNITER = "com/eloraam/redpower/machine/TileIgniter";
    static final String TILEMACHINE = "com/eloraam/redpower/machine/TileMachine";
    static final String DEPLOYER = "com/eloraam/redpower/machine/TileDeployBase";
    static final String MOTOR = "com/eloraam/redpower/machine/TileMotor";
    static final String FRAMESOLVER = "com/eloraam/redpower/core/FrameLib$FrameSolver";
    static final String THERMO = "com/eloraam/redpower/machine/TileThermopile";
    static final String GRATE = "com/eloraam/redpower/machine/TileGrate";
    static final String GRATE_PF = "com/eloraam/redpower/machine/TileGrate$GratePathfinder";
    static final String NETMGR = "ic2/core/network/NetworkManager";
    static final String NETLISTENER = "ic2/api/network/INetworkClientTileEntityEventListener";
    static final String SORTER = "com/eloraam/redpower/machine/ContainerSorter";
    static final String TESLA = "ic2/core/block/machine/tileentity/TileEntityTesla";
    static final String WRENCH = "ic2/core/item/tool/ItemToolWrench";
    static final String HOE = "ic2/core/item/tool/ItemElectricToolHoe";
    static final String ITEM_USE = "(Lur;Lqx;Lyc;IIIIFFF)Z";
    static final String MULTIID = "ic2/core/block/BlockMultiID";
    static final String ELECMACHINE = "ic2/core/block/machine/tileentity/TileEntityElecMachine";
    static final String MINER = "ic2/core/block/machine/tileentity/TileEntityMiner";
    static final String PUMP = "ic2/core/block/machine/tileentity/TileEntityPump";
    static final String TERRA = "ic2/core/block/machine/tileentity/TileEntityTerra";
    static final String SPRAYER = "ic2/core/item/tool/ItemSprayer";
    static final String CABLE = "ic2/core/item/block/ItemCable";
    static final String BARREL = "ic2/core/item/block/ItemBarrel";
    static final String RESIN = "ic2/core/item/ItemResin";
    static final String LUMINATOR = "ic2/core/item/block/ItemLuminator";
    static final String CELL = "ic2/core/item/ItemCell";
    static final String PLACE_BLOCK_AT = "(Lur;Lqx;Lyc;IIIIFFFI)Z";
    static final String CELL_USE = "(Lur;Lyc;Lqx;)Lur;";

    public byte[] transform(String name, byte[] bytes) {
        if (name == null || bytes == null) {
            return bytes;
        }
        String internal = name.replace('.', '/');
        if (!internal.equals(LASER) && !internal.equals(EXPLOSION) && !internal.equals(POINTEXP) && !internal.equals(BAG)
                && !internal.equals(COREPROXY) && !internal.equals(BREAKER) && !internal.equals(IGNITER)
                && !internal.equals(TILEMACHINE) && !internal.equals(DEPLOYER) && !internal.equals(NETMGR)
                && !internal.equals(SORTER) && !internal.equals(TESLA) && !internal.equals(MOTOR)
                && !internal.equals(THERMO) && !internal.equals(GRATE) && !internal.equals(GRATE_PF)
                && !internal.equals(WRENCH) && !internal.equals(HOE) && !internal.equals(MULTIID)
                && !internal.equals(ELECMACHINE) && !internal.equals(MINER) && !internal.equals(PUMP)
                && !internal.equals(TERRA) && !internal.equals(SPRAYER) && !internal.equals(CABLE)
                && !internal.equals(BARREL) && !internal.equals(RESIN) && !internal.equals(LUMINATOR)
                && !internal.equals(CELL)) {
            return bytes;
        }
        try {
            byte[] out = patch(internal, bytes);
            if (out != null) {
                System.out.println(TAG + "patched " + name);
                return out;
            }
            System.out.println(TAG + "SEVERE: " + name + " does not match this fix's mod version, left unpatched");
        } catch (Throwable t) {
            System.out.println(TAG + "SEVERE: failed to patch " + name + ", left unpatched: " + t);
        }
        return bytes;
    }

    /** The patched class, or null when the class doesn't have exactly the expected sites. */
    static byte[] patch(String internal, byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES);
        boolean ok;
        if (internal.equals(LASER)) ok = patchLaser(cn);
        else if (internal.equals(EXPLOSION)) ok = patchExplosion(cn);
        else if (internal.equals(POINTEXP)) ok = patchPoint(cn);
        else if (internal.equals(COREPROXY)) ok = patchCoreProxy(cn);
        else if (internal.equals(BREAKER)) ok = patchBreaker(cn);
        else if (internal.equals(IGNITER)) ok = patchIgniter(cn);
        else if (internal.equals(TILEMACHINE)) ok = patchTileMachine(cn);
        else if (internal.equals(DEPLOYER)) ok = patchDeployer(cn);
        else if (internal.equals(NETMGR)) ok = patchNetworkManager(cn);
        else if (internal.equals(SORTER)) ok = patchSorter(cn);
        else if (internal.equals(TESLA)) ok = patchTesla(cn);
        else if (internal.equals(MOTOR)) ok = patchMotor(cn);
        else if (internal.equals(THERMO)) ok = patchThermopile(cn);
        else if (internal.equals(GRATE)) ok = patchGrate(cn);
        else if (internal.equals(GRATE_PF)) ok = patchGratePF(cn);
        else if (internal.equals(WRENCH)) ok = patchToolEdits(cn, "onItemUseFirst", ITEM_USE, 2, 1);
        else if (internal.equals(HOE)) ok = patchToolEdits(cn, "a", ITEM_USE, 2, 1);
        else if (internal.equals(MULTIID)) ok = patchMultiID(cn);
        else if (internal.equals(ELECMACHINE)) ok = patchElecMachine(cn);
        else if (internal.equals(MINER)) ok = patchMachineEdits(cn, 5);
        else if (internal.equals(PUMP)) ok = patchMachineEdits(cn, 2);
        else if (internal.equals(TERRA)) ok = patchMachineEdits(cn, 2);
        else if (internal.equals(SPRAYER)) ok = patchSprayer(cn);
        else if (internal.equals(CABLE)) ok = patchToolEdits(cn, "a", ITEM_USE, 2, 1);
        else if (internal.equals(BARREL)) ok = patchToolEdits(cn, "a", ITEM_USE, 2, 1);
        else if (internal.equals(RESIN)) ok = patchToolEdits(cn, "a", ITEM_USE, 2, 2);
        else if (internal.equals(LUMINATOR)) ok = patchToolEdits(cn, "placeBlockAt", PLACE_BLOCK_AT, 2, 1);
        else if (internal.equals(CELL)) ok = patchToolEdits(cn, "a", CELL_USE, 3, 2);
        else ok = patchBag(cn);
        if (!ok) {
            return null;
        }
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * onUpdate j_(): the hit block's x, y, z are in locals 8, 9, 10 when canMine(I)Z is called
     * with [this, blockId] on the stack. explode(): the only doExplosion call has [explosion].
     */
    static boolean patchLaser(ClassNode cn) {
        int mine = 0, explode = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            boolean update = m.name.equals("j_") && m.desc.equals("()V");
            boolean exploding = m.name.equals("explode") && m.desc.equals("()V");
            if (!update && !exploding) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (update && mi.owner.equals(LASER) && mi.name.equals("canMine") && mi.desc.equals("(I)Z")) {
                    InsnList xyz = new InsnList();
                    xyz.add(new VarInsnNode(Opcodes.ILOAD, 8));
                    xyz.add(new VarInsnNode(Opcodes.ILOAD, 9));
                    xyz.add(new VarInsnNode(Opcodes.ILOAD, 10));
                    m.instructions.insertBefore(mi, xyz);
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "canMine",
                            "(L" + LASER + ";IIII)Z"));
                    m.maxStack += 3;
                    mine++;
                }
                if (exploding && mi.owner.equals(EXPLOSION) && mi.name.equals("doExplosion") && mi.desc.equals("()V")) {
                    m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "doExplosion",
                            "(L" + EXPLOSION + ";L" + LASER + ";)V"));
                    m.maxStack += 1;
                    explode++;
                }
            }
        }
        return mine == 1 && explode == 1;
    }

    /**
     * shock(int): the coil damages every EntityLiving in range with md.attackEntityFrom
     * (md.a(lh,int)Z), players included. Route it through TLiteIC2.teslaShock so the
     * basicTeslaCoil.noPlayerDamage flag can spare players. The stack [target, source, damage]
     * matches the static call's arguments.
     */
    static boolean patchTesla(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("shock") || !m.desc.equals("(I)Z")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("md") || !mi.name.equals("a") || !mi.desc.equals("(Llh;I)Z")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "teslaShock", "(Lmd;Llh;I)Z"));
                hits++;
            }
        }
        return hits == 1;
    }

    /**
     * doExplosion(): the only ys.a(III)I call has [cache, x, y, z]; this is pushed after them, so
     * the Mining Laser explode mode can be checked per block (explosionBlockId). Before the removal
     * loop (getfield destroyedBlockPositions, entrySet) a call to TLiteIC2.ic2ExplodeFilter fires a
     * Bukkit EntityExplodeEvent for nuke, Industrial TNT and reactor explosions so GriefPrevention
     * and WorldGuard trim the blocks exactly as for vanilla TNT.
     *
     * shootRay(): the entity-kill block runs a binary search over entitiesInRange whenever
     * killEntities is set, but killEntities is gated on the raw AABB (which holds the explosive
     * itself and players, neither added to entitiesInRange), so near a lone player the search does
     * get(0) on an empty list and crashes the server (ticking entity). The block is guarded so it
     * is skipped whenever entitiesInRange is empty.
     */
    static boolean patchExplosion(ClassNode cn) {
        int blockId = 0, filter = 0, crash = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;

            if (m.name.equals("doExplosion") && m.desc.equals("()V")) {
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                        MethodInsnNode mi = (MethodInsnNode) i;
                        if (mi.owner.equals("ys") && mi.name.equals("a") && mi.desc.equals("(III)I")) {
                            m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                            m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "explosionBlockId",
                                    "(Lys;IIIL" + EXPLOSION + ";)I"));
                            m.maxStack += 1;
                            blockId++;
                        }
                    }
                    if (i.getOpcode() == Opcodes.GETFIELD) {
                        FieldInsnNode fi = (FieldInsnNode) i;
                        if (fi.owner.equals(EXPLOSION) && fi.name.equals("destroyedBlockPositions")) {
                            AbstractInsnNode next = skipMeta(fi.getNext());
                            if (next != null && next.getOpcode() == Opcodes.INVOKEINTERFACE
                                    && ((MethodInsnNode) next).name.equals("entrySet")) {
                                InsnList c = new InsnList();
                                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "worldObj", "Lyc;"));
                                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "explosionX", "D"));
                                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "explosionY", "D"));
                                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "explosionZ", "D"));
                                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "igniter", "Ljava/lang/String;"));
                                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "destroyedBlockPositions", "Ljava/util/Map;"));
                                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "damageSource", "Llh;"));
                                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "ic2ExplodeFilter",
                                        "(Lyc;DDDLjava/lang/String;Ljava/util/Map;Llh;)V"));
                                m.instructions.insertBefore(fi, c);
                                m.maxStack = Math.max(m.maxStack, 10);
                                filter++;
                            }
                        }
                    }
                }
            }

            if (m.name.equals("shootRay") && m.desc.equals("(DDDDDDZ)V")) {
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i.getOpcode() != Opcodes.ILOAD || ((VarInsnNode) i).var != 13) continue;
                    AbstractInsnNode next = skipMeta(i.getNext());
                    if (next == null || next.getOpcode() != Opcodes.IFEQ) continue;
                    LabelNode skip = ((JumpInsnNode) next).label;
                    InsnList c = new InsnList();
                    c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    c.add(new FieldInsnNode(Opcodes.GETFIELD, EXPLOSION, "entitiesInRange", "Ljava/util/List;"));
                    c.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z"));
                    c.add(new JumpInsnNode(Opcodes.IFNE, skip));
                    m.instructions.insertBefore(i, c);
                    m.maxStack = Math.max(m.maxStack, 1);
                    crash++;
                }
            }
        }
        return blockId == 1 && filter == 1 && crash == 1;
    }

    /** Next real instruction, skipping labels, line numbers and frames. */
    static AbstractInsnNode skipMeta(AbstractInsnNode n) {
        while (n != null && (n.getType() == AbstractInsnNode.LABEL || n.getType() == AbstractInsnNode.LINE
                || n.getType() == AbstractInsnNode.FRAME)) n = n.getNext();
        return n;
    }

    /**
     * doExplosionB(boolean) is where IC2 Dynamite removes its blocks (setBlock to air), with no
     * Bukkit event. A call to TLiteIC2.ic2PointFilter is prepended so a real EntityExplodeEvent
     * fires and GriefPrevention/WorldGuard trim the block set before the method copies and removes
     * it, exactly as for the ExplosionIC2 filter.
     */
    static boolean patchPoint(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("doExplosionB") || !m.desc.equals("(Z)V")) continue;
            InsnList c = new InsnList();
            c.add(new VarInsnNode(Opcodes.ALOAD, 0));
            c.add(new FieldInsnNode(Opcodes.GETFIELD, POINTEXP, "worldObj", "Lyc;"));
            c.add(new VarInsnNode(Opcodes.ALOAD, 0));
            c.add(new FieldInsnNode(Opcodes.GETFIELD, POINTEXP, "explosionX", "I"));
            c.add(new VarInsnNode(Opcodes.ALOAD, 0));
            c.add(new FieldInsnNode(Opcodes.GETFIELD, POINTEXP, "explosionY", "I"));
            c.add(new VarInsnNode(Opcodes.ALOAD, 0));
            c.add(new FieldInsnNode(Opcodes.GETFIELD, POINTEXP, "explosionZ", "I"));
            c.add(new VarInsnNode(Opcodes.ALOAD, 0));
            c.add(new FieldInsnNode(Opcodes.GETFIELD, POINTEXP, "exploder", "Llq;"));
            c.add(new VarInsnNode(Opcodes.ALOAD, 0));
            c.add(new FieldInsnNode(Opcodes.GETFIELD, POINTEXP, "destroyedBlockPositions", "Ljava/util/Set;"));
            c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "ic2PointFilter",
                    "(Lyc;IIILlq;Ljava/util/Set;)V"));
            m.instructions.insert(c);
            m.maxStack = Math.max(m.maxStack, 6);
            hits++;
        }
        return hits == 1;
    }

    /**
     * A right-click IC2 tool or placement item: in its use method, redirect world.setBlock
     * (setBlockWithNotify or setBlockAndMetadataWithNotify) to TLiteIC2.wrenchEdit/wrenchEditMeta,
     * pushing the acting player (the qx arg at playerLocal). The edit is then checked against that
     * player, refused inside a claim they cannot build in and unchanged everywhere else.
     */
    static boolean patchToolEdits(ClassNode cn, String mname, String mdesc, int playerLocal, int expected) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals(mname) || !m.desc.equals(mdesc)) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc")) continue;
                String target, desc;
                if (mi.name.equals("e") && mi.desc.equals("(IIII)Z")) { target = "wrenchEdit"; desc = "(Lyc;IIIILqx;)Z"; }
                else if (mi.name.equals("d") && mi.desc.equals("(IIIII)Z")) { target = "wrenchEditMeta"; desc = "(Lyc;IIIIILqx;)Z"; }
                else continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, playerLocal));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", target, desc));
                m.maxStack += 1;
                hits++;
            }
        }
        return hits == expected;
    }

    /**
     * Foam Sprayer: record the acting player at the top of its use method (a, local 2), then
     * redirect the world.setBlock inside sprayFoam to TLiteIC2.sprayEdit, so every foam block in the
     * sprayed area is checked against that player, not just the clicked one.
     */
    static boolean patchSprayer(ClassNode cn) {
        int setHits = 0, foamHits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("a") && m.desc.equals(ITEM_USE)) {
                InsnList g = new InsnList();
                g.add(new VarInsnNode(Opcodes.ALOAD, 2));
                g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "setSprayer", "(Lqx;)V"));
                m.instructions.insert(g);
                m.maxStack = Math.max(m.maxStack, 1);
                setHits++;
            } else if (m.name.equals("sprayFoam")) {
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                    MethodInsnNode mi = (MethodInsnNode) i;
                    if (!mi.owner.equals("yc") || !mi.name.equals("e") || !mi.desc.equals("(IIII)Z")) continue;
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "sprayEdit", "(Lyc;IIII)Z"));
                    foamHits++;
                }
            }
        }
        return setHits == 1 && foamHits == 2;
    }

    /** BlockMultiID.onBlockPlacedBy: record the placer of an IC2 machine (its tile) for the guards. */
    static boolean patchMultiID(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("a") || !m.desc.equals("(Lyc;IIILmd;)V")) continue;
            InsnList c = new InsnList();
            c.add(new VarInsnNode(Opcodes.ALOAD, 1));
            c.add(new VarInsnNode(Opcodes.ILOAD, 2));
            c.add(new VarInsnNode(Opcodes.ILOAD, 3));
            c.add(new VarInsnNode(Opcodes.ILOAD, 4));
            c.add(new VarInsnNode(Opcodes.ALOAD, 5));
            c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "recordMachineOwner", "(Lyc;IIILmd;)V"));
            beforeReturns(m, c);
            m.maxStack = Math.max(m.maxStack, 5);
            hits++;
        }
        return hits == 1;
    }

    /** TileEntityElecMachine NBT: load/save the machine owner (base of Miner, Pump, Terraformer). */
    static boolean patchElecMachine(ClassNode cn) {
        int read = 0, write = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("a") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, machineNbt("loadMachineOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                read++;
            } else if (m.name.equals("b") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, machineNbt("saveMachineOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                write++;
            }
        }
        return read == 1 && write == 1;
    }

    static InsnList machineNbt(String name) {
        InsnList c = new InsnList();
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new VarInsnNode(Opcodes.ALOAD, 1));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", name, "(Lany;Lbq;)V"));
        return c;
    }

    /** An automated machine (Miner/Pump/Terraformer): redirect every world block write to the guard,
     *  pushing this (the tile) so it is checked against that machine's owner. */
    static boolean patchMachineEdits(ClassNode cn, int expected) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc")) continue;
                String target, desc;
                if (mi.name.equals("e") && mi.desc.equals("(IIII)Z")) { target = "machineSet"; desc = "(Lyc;IIIILany;)Z"; }
                else if (mi.name.equals("d") && mi.desc.equals("(IIIII)Z")) { target = "machineSetMeta"; desc = "(Lyc;IIIIILany;)Z"; }
                else continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", target, desc));
                m.maxStack += 1;
                hits++;
            }
        }
        return hits == expected;
    }

    /** Adds the slotClick override; refuses when ContainerBag already has one. */
    static boolean patchBag(ClassNode cn) {
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("a") && m.desc.equals(CLICK)) {
                return false;
            }
        }
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "a", CLICK, null, null);
        InsnList c = m.instructions;
        LabelNode allowed = new LabelNode();
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new VarInsnNode(Opcodes.ILOAD, 1));
        c.add(new VarInsnNode(Opcodes.ILOAD, 3));
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new FieldInsnNode(Opcodes.GETFIELD, BAG, "itemBag", "Lur;"));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRP", "allowClick", "(Lrq;IILur;)Z"));
        c.add(new JumpInsnNode(Opcodes.IFNE, allowed));
        c.add(new InsnNode(Opcodes.ACONST_NULL));
        c.add(new InsnNode(Opcodes.ARETURN));
        c.add(allowed);
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new VarInsnNode(Opcodes.ILOAD, 1));
        c.add(new VarInsnNode(Opcodes.ILOAD, 2));
        c.add(new VarInsnNode(Opcodes.ILOAD, 3));
        c.add(new VarInsnNode(Opcodes.ALOAD, 4));
        c.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "rq", "a", CLICK));
        c.add(new InsnNode(Opcodes.ARETURN));
        m.maxStack = 5;
        m.maxLocals = 5;
        cn.methods.add(m);
        return true;
    }

    /**
     * RedPower CoreProxy.processPacket211(Packet211TileDesc, eg): the server branch (nh instanceof
     * iv) looks up the tile at the packet's coordinates and calls its handlePacket, letting a
     * client inject real items into tubes and crash the handler with a bad item id. That branch is
     * attacker-only (211 is a server-to-client description packet no RP client sends back), so it
     * returns immediately when the handler is the server's. The client branch is untouched.
     */
    static boolean patchCoreProxy(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("processPacket211")) continue;
            InsnList g = new InsnList();
            LabelNode client = new LabelNode();
            g.add(new VarInsnNode(Opcodes.ALOAD, 2));
            g.add(new TypeInsnNode(Opcodes.INSTANCEOF, "iv"));
            g.add(new JumpInsnNode(Opcodes.IFEQ, client));
            g.add(new InsnNode(Opcodes.RETURN));
            g.add(client);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 1);
            hits++;
        }
        return hits == 1;
    }

    /**
     * The Block Breaker breaks the block in front through the world; its single
     * setBlockWithNotify is routed through a fake-player claim guard.
     */
    static boolean patchBreaker(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onBlockNeighborChange")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("e") || !mi.desc.equals("(IIII)Z")) continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "breakIfAllowed", "(Lyc;IIIILany;)Z"));
                m.maxStack += 1;
                hits++;
            }
        }
        return hits == 1;
    }

    /**
     * The Igniter lights fire against the block in front. fireAction has two setBlockWithNotify
     * calls: setting fire (block id pushed via GETFIELD) and removing it (id pushed as ICONST_0).
     * Only the fire-set is routed through the claim guard; removing stray fire is left alone.
     */
    static boolean patchIgniter(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("fireAction")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("e") || !mi.desc.equals("(IIII)Z")) continue;
                AbstractInsnNode prev = mi.getPrevious();
                while (prev != null && (prev.getType() == AbstractInsnNode.LABEL || prev.getType() == AbstractInsnNode.LINE
                        || prev.getType() == AbstractInsnNode.FRAME)) prev = prev.getPrevious();
                if (prev != null && prev.getOpcode() == Opcodes.ICONST_0) continue;   // fire removal, leave it
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "igniteIfAllowed", "(Lyc;IIIILany;)Z"));
                m.maxStack += 1;
                hits++;
            }
        }
        return hits == 1;
    }

    /**
     * TileMachine is the base for the Breaker, Igniter and Deployer. It records the placing
     * player (onBlockPlaced) and persists the owner in NBT (read a / write b), so the guards can
     * check each machine as its owner.
     */
    static boolean patchTileMachine(ClassNode cn) {
        int placed = 0, read = 0, write = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("onBlockPlaced") && m.desc.equals("(Lur;ILmd;)V")) {
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new VarInsnNode(Opcodes.ALOAD, 3));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "recordOwner", "(Lany;Lmd;)V"));
                beforeReturns(m, c);
                m.maxStack = Math.max(m.maxStack, 2);
                placed++;
            } else if (m.name.equals("a") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, nbtCall("loadOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                read++;
            } else if (m.name.equals("b") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, nbtCall("saveOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                write++;
            }
        }
        return placed == 1 && read == 1 && write == 1;
    }

    /**
     * TileMotor: owner-track it exactly like a TileMachine (onBlockPlaced records the placer, NBT
     * a/b load and save the owner), and gate pickFrame. In pickFrame, before FrameSolver.addMoved
     * (which starts writing blocks), insert `if (!TLiteRPMachine.frameAllowed(this, fs, this.MoveDir))
     * return;` so a move that would touch a block the owner cannot edit is refused before anything
     * happens. fs is the local loaded as the receiver of the addMoved call.
     */
    static boolean patchMotor(ClassNode cn) {
        int placed = 0, read = 0, write = 0, guard = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("onBlockPlaced") && m.desc.equals("(Lur;ILmd;)V")) {
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new VarInsnNode(Opcodes.ALOAD, 3));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "recordOwner", "(Lany;Lmd;)V"));
                beforeReturns(m, c);
                m.maxStack = Math.max(m.maxStack, 2);
                placed++;
            } else if (m.name.equals("a") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, nbtCall("loadOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                read++;
            } else if (m.name.equals("b") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, nbtCall("saveOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                write++;
            } else if (m.name.equals("pickFrame")) {
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                    MethodInsnNode mi = (MethodInsnNode) i;
                    if (!mi.owner.equals(FRAMESOLVER) || !mi.name.equals("addMoved") || !mi.desc.equals("()Z")) continue;
                    AbstractInsnNode recv = mi.getPrevious();
                    while (recv != null && (recv.getType() == AbstractInsnNode.LABEL || recv.getType() == AbstractInsnNode.LINE
                            || recv.getType() == AbstractInsnNode.FRAME)) recv = recv.getPrevious();
                    if (recv == null || recv.getOpcode() != Opcodes.ALOAD) continue;
                    int fsVar = ((VarInsnNode) recv).var;
                    InsnList c = new InsnList();
                    LabelNode proceed = new LabelNode();
                    c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    c.add(new VarInsnNode(Opcodes.ALOAD, fsVar));
                    c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    c.add(new FieldInsnNode(Opcodes.GETFIELD, MOTOR, "MoveDir", "I"));
                    c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "frameAllowed",
                            "(Lany;L" + FRAMESOLVER + ";I)Z"));
                    c.add(new JumpInsnNode(Opcodes.IFNE, proceed));
                    c.add(new InsnNode(Opcodes.RETURN));
                    c.add(proceed);
                    m.instructions.insertBefore(recv, c);
                    m.maxStack = Math.max(m.maxStack, 4);
                    guard++;
                }
            }
        }
        return placed == 1 && read == 1 && write == 1 && guard == 1;
    }

    /**
     * TileThermopile.updateTemps consumes adjacent water, lava and fire straight through the world.
     * It has no placer to record (it is not a TileMachine), so the three edits are routed through
     * the generic guard as an ownerless tile: refused inside any claim (the thermopile still makes
     * power from the temperature difference; only the block-eating stops), allowed on open ground.
     */
    static boolean patchThermopile(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("updateTemps")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc")) continue;
                String target, desc;
                if (mi.name.equals("e") && mi.desc.equals("(IIII)Z")) { target = "tileSet"; desc = "(Lyc;IIIILany;)Z"; }
                else if (mi.name.equals("d") && mi.desc.equals("(IIIII)Z")) { target = "tileSetMeta"; desc = "(Lyc;IIIIILany;)Z"; }
                else continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", target, desc));
                m.maxStack += 1;
                hits++;
            }
        }
        return hits == 3;
    }

    /** TileGrate: owner-track it (records the placer, saves it in NBT) so its drain honors claims. */
    static boolean patchGrate(ClassNode cn) {
        int placed = 0, read = 0, write = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("onBlockPlaced") && m.desc.equals("(Lur;ILmd;)V")) {
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new VarInsnNode(Opcodes.ALOAD, 3));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "recordOwner", "(Lany;Lmd;)V"));
                beforeReturns(m, c);
                m.maxStack = Math.max(m.maxStack, 2);
                placed++;
            } else if (m.name.equals("a") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, nbtCall("loadOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                read++;
            } else if (m.name.equals("b") && m.desc.equals("(Lbq;)V")) {
                beforeReturns(m, nbtCall("saveOwner"));
                m.maxStack = Math.max(m.maxStack, 2);
                write++;
            }
        }
        return placed == 1 && read == 1 && write == 1;
    }

    /**
     * TileGrate$GratePathfinder drains fluids by setting blocks to air. Route that through the guard
     * as the outer grate's owner (reached via the inner class's this$0 field).
     */
    static boolean patchGratePF(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("e") || !mi.desc.equals("(IIII)Z")) continue;
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new FieldInsnNode(Opcodes.GETFIELD, GRATE_PF, "this$0", "L" + GRATE + ";"));
                m.instructions.insertBefore(mi, c);
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "tileSet", "(Lyc;IIIILany;)Z"));
                m.maxStack += 1;
                hits++;
            }
        }
        return hits == 1;
    }

    static InsnList nbtCall(String name) {
        InsnList c = new InsnList();
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new VarInsnNode(Opcodes.ALOAD, 1));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", name, "(Lany;Lbq;)V"));
        return c;
    }

    static void beforeReturns(MethodNode m, InsnList call) {
        for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i.getOpcode() != Opcodes.RETURN) continue;
            InsnList copy = new InsnList();
            for (AbstractInsnNode c = call.getFirst(); c != null; c = c.getNext()) copy.add(c.clone(null));
            m.instructions.insertBefore(i, copy);
        }
    }

    /**
     * The Deployer uses the held item on the block in front (tryUseItemStack, coords in locals
     * 2/3/4). It is guarded at the top against the machine's owner; a refused deploy returns false.
     */
    static boolean patchDeployer(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("tryUseItemStack")) continue;
            InsnList g = new InsnList();
            LabelNode allowed = new LabelNode();
            g.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g.add(new FieldInsnNode(Opcodes.GETFIELD, "any", "k", "Lyc;"));
            g.add(new VarInsnNode(Opcodes.ILOAD, 2));
            g.add(new VarInsnNode(Opcodes.ILOAD, 3));
            g.add(new VarInsnNode(Opcodes.ILOAD, 4));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteRPMachine", "deployAllowed", "(Lany;Lyc;III)Z"));
            g.add(new JumpInsnNode(Opcodes.IFNE, allowed));
            g.add(new InsnNode(Opcodes.ICONST_0));
            g.add(new InsnNode(Opcodes.IRETURN));
            g.add(allowed);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 5);
            hits++;
        }
        return hits == 1;
    }

    /**
     * ContainerSorter.handleGuiEvent bounds a colour index with i <= 8, but colors is length 8
     * (valid 0-7), so index 8 threw. The bound is tightened to i <= 7.
     */
    static boolean patchSorter(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("handleGuiEvent")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.BIPUSH || ((IntInsnNode) i).operand != 8) continue;
                AbstractInsnNode next = i.getNext();
                while (next != null && (next.getType() == AbstractInsnNode.LABEL || next.getType() == AbstractInsnNode.LINE
                        || next.getType() == AbstractInsnNode.FRAME)) next = next.getNext();
                if (next == null || next.getOpcode() != Opcodes.IF_ICMPLE) continue;
                ((IntInsnNode) i).operand = 7;
                hits++;
            }
        }
        return hits == 1;
    }

    /**
     * NetworkManager.onPacketData case 3 dispatched a tile network event looked up across every
     * dimension from client coordinates. The dispatch is routed through a gate that runs it only
     * for a tile in the sender's own world within reach.
     */
    static boolean patchNetworkManager(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onPacketData")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEINTERFACE) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(NETLISTENER) || !mi.name.equals("onNetworkEvent") || !mi.desc.equals("(Lqx;I)V")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "netEvent",
                        "(L" + NETLISTENER + ";Lqx;I)V"));
                hits++;
            }
        }
        return hits == 1;
    }

    /**
     * Build check: TLiteTransformer <ic2.jar> <RedPowerCore.zip> <RedPowerMechanical.zip>. Patches
     * the classes from the stock jars and exits non zero unless every one applies.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: TLiteTransformer <ic2.jar> <RedPowerCore.zip> <RedPowerMechanical.zip>");
            System.exit(2);
        }
        String[][] checks = { { args[0], LASER }, { args[0], EXPLOSION }, { args[0], POINTEXP }, { args[1], BAG }, { args[1], COREPROXY }, { args[2], BREAKER }, { args[2], IGNITER }, { args[2], TILEMACHINE }, { args[2], DEPLOYER }, { args[0], NETMGR }, { args[2], SORTER }, { args[0], TESLA }, { args[2], MOTOR }, { args[2], THERMO }, { args[2], GRATE }, { args[2], GRATE_PF }, { args[0], WRENCH }, { args[0], HOE }, { args[0], MULTIID }, { args[0], ELECMACHINE }, { args[0], MINER }, { args[0], PUMP }, { args[0], TERRA }, { args[0], SPRAYER }, { args[0], CABLE }, { args[0], BARREL }, { args[0], RESIN }, { args[0], LUMINATOR }, { args[0], CELL } };
        boolean failed = false;
        for (String[] check : checks) {
            ZipFile zf = new ZipFile(check[0]);
            ZipEntry e = zf.getEntry(check[1] + ".class");
            byte[] out = e == null ? null : patch(check[1], readAll(zf.getInputStream(e)));
            zf.close();
            System.out.println((out != null ? "OK  " : "FAIL") + "  coremod patch " + check[1]);
            failed |= out == null;
        }
        if (failed) {
            System.exit(1);
        }
    }

    static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }
}
