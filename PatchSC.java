import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Steve's Carts 2.0.0.a62 patches.
 *
 *   carts   ItemCarts placement: world.spawnEntityInWorld(cart)  ->  TLiteSC.spawnCart(world, cart, player)
 *           entMCBase.readEntityFromNBT/writeEntityToNBT: load/save the owner
 *           every module block change: world.setBlockWithNotify / setBlockAndMetadataWithNotify
 *             ->  TLiteSC.breakIfAllowed / placeIfAllowed(..., this.getCart())
 *
 * Cart modules mine and build through the world with no protection check. Each cart records its
 * deployer and its module edits are checked as that owner.
 *
 * usage: PatchSC <in.zip> <out.zip> <patch>[,<patch>...] <TLiteSC.class> <TLiteProtect.class>
 */
public class PatchSC {

    static final String ITEMCARTS = "vswe/stevescarts/Items/ItemCarts";
    static final String CART = "vswe/stevescarts/Carts/entMCBase";
    static final String MODULES = "vswe/stevescarts/Modules/";
    static final String HELPER = "TLiteSC";

    static boolean doCarts;
    static int spawnHits, nbtHits, moduleHits;
    static int initGateHits, loadGateHits, loopHits, dropHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchSC <in.zip> <out.zip> <patches> <TLiteSC.class> <TLiteProtect.class>");
            System.err.println("patches: carts");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("carts")) doCarts = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doCarts && n.equals(ITEMCARTS + ".class")) d = patchSpawn(d);
            else if (doCarts && n.equals(CART + ".class")) d = patchCart(d);
            else if (doCarts && n.startsWith(MODULES) && n.endsWith(".class")) d = patchModule(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doCarts && (spawnHits != 1 || nbtHits != 2 || moduleHits != 14
                || initGateHits != 1 || loadGateHits != 1 || loopHits != 4 || dropHits != 1))
            throw new IllegalStateException("carts: expected spawn 1, nbt 2, module 14, initGate 1, "
                    + "loadGate 1, loop 4, drop 1; patched " + spawnHits + ", " + nbtHits + ", "
                    + moduleHits + ", " + initGateHits + ", " + loadGateHits + ", " + loopHits
                    + ", " + dropHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** ItemCarts.a (onItemUse): redirect world.spawnEntityInWorld(cart), pushing the player (local 2). */
    static byte[] patchSpawn(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("d") || !mi.desc.equals("(Llq;)Z")) continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 2));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "spawnCart", "(Lyc;Llq;Lqx;)Z", false));
                m.maxStack += 1;
                spawnHits++;
            }
        }
        return write(cn);
    }

    static final String TICKET = "Lnet/minecraftforge/common/ForgeChunkManager$Ticket;";

    /**
     * entMCBase: append loadOwner/saveOwner to the NBT methods, and cap the chunk-loader module.
     * The chunk loader is gated against the shared per-player quota at activation (initChunkLoading)
     * and again as the cart moves (loadChunks, the single choke point both the module and the
     * chunk-crossing listener reach), reduced from a 3x3 area to the cart's own single chunk, and
     * released from the registry when it stops (dropChunkLoading).
     */
    static byte[] patchCart(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;

            String helper = null;
            if (m.name.equals("d") && m.desc.equals("(Lbq;)V")) helper = "loadOwner";
            else if (m.name.equals("e") && m.desc.equals("(Lbq;)V")) helper = "saveOwner";
            if (helper != null) {
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i.getOpcode() != Opcodes.RETURN) continue;
                    InsnList c = new InsnList();
                    c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    c.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, helper, "(L" + CART + ";Lbq;)V", false));
                    m.instructions.insertBefore(i, c);
                }
                m.maxStack = Math.max(m.maxStack, 2);
                nbtHits++;
                continue;
            }

            if (m.name.equals("initChunkLoading") && m.desc.equals("()V")) {
                insertChunkGate(m, "chunkAllowedAnnounce");
                initGateHits++;
                continue;
            }

            if (m.name.equals("loadChunks") && m.desc.equals("(" + TICKET + "II)V")) {
                insertChunkGate(m, "chunkAllowed");
                loadGateHits++;
                loopHits += shrinkToSingleChunk(m);
                continue;
            }

            if (m.name.equals("dropChunkLoading") && m.desc.equals("()V")) {
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "chunkReleased", "(L" + CART + ";)V", false));
                m.instructions.insert(c);
                m.maxStack = Math.max(m.maxStack, 1);
                dropHits++;
            }
        }
        return write(cn);
    }

    /** Prepend {@code if (!TLiteSC.<helper>(this)) return;} at a void method's entry. */
    static void insertChunkGate(MethodNode m, String helperMethod) {
        LabelNode cont = new LabelNode();
        InsnList c = new InsnList();
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, helperMethod, "(L" + CART + ";)Z", false));
        c.add(new JumpInsnNode(Opcodes.IFNE, cont));
        c.add(new InsnNode(Opcodes.RETURN));
        c.add(cont);
        m.instructions.insert(c);
        m.maxStack = Math.max(m.maxStack, 1);
    }

    /**
     * loadChunks(Ticket,x,z) force-loads a 3x3 block of chunks via two loops running -1..1. Flip the
     * two loop starts (ICONST_M1 before ISTORE) and the two bounds (ICONST_1 before IF_ICMPGT) to 0
     * so only the cart's own chunk (offset 0,0) is forced. Returns the number of constants changed.
     */
    static int shrinkToSingleChunk(MethodNode m) {
        int changed = 0;
        for (AbstractInsnNode i : m.instructions.toArray()) {
            AbstractInsnNode nxt = nextReal(i);
            if (nxt == null) continue;
            if (i.getOpcode() == Opcodes.ICONST_M1 && nxt.getOpcode() == Opcodes.ISTORE) {
                m.instructions.set(i, new InsnNode(Opcodes.ICONST_0)); changed++;
            } else if (i.getOpcode() == Opcodes.ICONST_1 && nxt.getOpcode() == Opcodes.IF_ICMPGT) {
                m.instructions.set(i, new InsnNode(Opcodes.ICONST_0)); changed++;
            }
        }
        return changed;
    }

    static AbstractInsnNode nextReal(AbstractInsnNode i) {
        for (AbstractInsnNode n = i.getNext(); n != null; n = n.getNext()) {
            if (n.getOpcode() >= 0) return n;
        }
        return null;
    }

    /** A module class: redirect its world break/place, pushing this.getCart() for the owner. */
    static byte[] patchModule(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            boolean changed = false;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc")) continue;
                String target = null, desc = null;
                if (mi.name.equals("e") && mi.desc.equals("(IIII)Z")) { target = "breakIfAllowed"; desc = "(Lyc;IIIIL" + CART + ";)Z"; }
                else if (mi.name.equals("d") && mi.desc.equals("(IIIII)Z")) { target = "placeIfAllowed"; desc = "(Lyc;IIIIIL" + CART + ";)Z"; }
                else continue;
                InsnList push = new InsnList();
                push.add(new VarInsnNode(Opcodes.ALOAD, 0));
                push.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, cn.name, "getCart", "()L" + CART + ";", false));
                m.instructions.insertBefore(mi, push);
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, target, desc, false));
                moduleHits++;
                changed = true;
            }
            if (changed) m.maxStack += 1;
        }
        return write(cn);
    }

    static ClassNode read(byte[] b) { ClassNode cn = new ClassNode(); new ClassReader(b).accept(cn, ClassReader.SKIP_FRAMES); return cn; }
    static byte[] write(ClassNode cn) { ClassWriter cw = new ClassWriter(0); cn.accept(cw); return cw.toByteArray(); }
    static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n); is.close(); return bos.toByteArray();
    }
}
