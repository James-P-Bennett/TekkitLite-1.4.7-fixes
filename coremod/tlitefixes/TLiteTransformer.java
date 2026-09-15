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
    static final String BAG = "com/eloraam/redpower/base/ContainerBag";
    static final String CLICK = "(IIILqx;)Lur;";
    static final String COREPROXY = "com/eloraam/redpower/core/CoreProxy";
    static final String BREAKER = "com/eloraam/redpower/machine/TileBreaker";
    static final String IGNITER = "com/eloraam/redpower/machine/TileIgniter";
    static final String TILEMACHINE = "com/eloraam/redpower/machine/TileMachine";
    static final String DEPLOYER = "com/eloraam/redpower/machine/TileDeployBase";
    static final String NETMGR = "ic2/core/network/NetworkManager";
    static final String NETLISTENER = "ic2/api/network/INetworkClientTileEntityEventListener";

    public byte[] transform(String name, byte[] bytes) {
        if (name == null || bytes == null) {
            return bytes;
        }
        String internal = name.replace('.', '/');
        if (!internal.equals(LASER) && !internal.equals(EXPLOSION) && !internal.equals(BAG)
                && !internal.equals(COREPROXY) && !internal.equals(BREAKER) && !internal.equals(IGNITER)
                && !internal.equals(TILEMACHINE) && !internal.equals(DEPLOYER) && !internal.equals(NETMGR)) {
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
        else if (internal.equals(COREPROXY)) ok = patchCoreProxy(cn);
        else if (internal.equals(BREAKER)) ok = patchBreaker(cn);
        else if (internal.equals(IGNITER)) ok = patchIgniter(cn);
        else if (internal.equals(TILEMACHINE)) ok = patchTileMachine(cn);
        else if (internal.equals(DEPLOYER)) ok = patchDeployer(cn);
        else if (internal.equals(NETMGR)) ok = patchNetworkManager(cn);
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

    /** doExplosion(): the only ys.a(III)I call has [cache, x, y, z]; this is pushed after them. */
    static boolean patchExplosion(ClassNode cn) {
        int hits = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("doExplosion") || !m.desc.equals("()V")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("ys") || !mi.name.equals("a") || !mi.desc.equals("(III)I")) continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteIC2", "explosionBlockId",
                        "(Lys;IIIL" + EXPLOSION + ";)I"));
                m.maxStack += 1;
                hits++;
            }
        }
        return hits == 1;
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
        String[][] checks = { { args[0], LASER }, { args[0], EXPLOSION }, { args[1], BAG }, { args[1], COREPROXY }, { args[2], BREAKER }, { args[2], IGNITER }, { args[2], TILEMACHINE }, { args[2], DEPLOYER }, { args[0], NETMGR } };
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
