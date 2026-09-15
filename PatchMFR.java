import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: MineFactoryReloaded 2.3.2 patches.
 *
 *   unifierdupe   TileEntityUnifier.updateEntity (obf g)
 *                 this.moveItemStack(source)
 *                   -> TLiteMFR.moveItemStack(this, source)
 *
 * The Unifier adds the output slot's free space to the output without capping it at the
 * input count, so one ingot tops the output up to a full stack and the input goes negative.
 *
 *   dsudupe       TileEntityDeepStorageUnit.isUseableByPlayer, TileEntityLiquiCrafter.isUseableByPlayer
 *                   start with: if (!TLiteMFR.isInWorld(this)) return false;
 *                 BlockFactoryMachine1.breakBlock
 *                 world.removeBlockTileEntity(x, y, z)
 *                   -> TLiteMFR.removeBlockTileEntity(world, x, y, z)
 *
 * A DSU broken by a machine drops itself with its contents, but its GUI stays open and its tile
 * keeps its slots, so the player takes the output stack out of the GUI a second time.
 *
 * usage: PatchMFR <in.jar> <out.jar> <patch>[,<patch>...] <TLiteMFR.class>
 */
public class PatchMFR {

    static final String UNIFIER = "powercrystals/minefactoryreloaded/processing/TileEntityUnifier";
    static final String HELPER  = "TLiteMFR";

    static final String DSU = "powercrystals/minefactoryreloaded/processing/TileEntityDeepStorageUnit";
    static final String LIQUICRAFTER = "powercrystals/minefactoryreloaded/processing/TileEntityLiquiCrafter";
    static final String MACHINE1 = "powercrystals/minefactoryreloaded/core/BlockFactoryMachine1";

    static boolean doUnifier, doDsu;
    static int unifierHits, usableHits, removeHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchMFR <in.jar> <out.jar> <patches> <TLiteMFR.class>");
            System.err.println("patches: unifierdupe, dsudupe");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("unifierdupe")) doUnifier = true;
            else if (p.equals("dsudupe")) doDsu = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doUnifier && n.equals(UNIFIER + ".class")) d = patchUnifier(d);
            if (doDsu && (n.equals(DSU + ".class") || n.equals(LIQUICRAFTER + ".class"))) d = patchUsable(d);
            if (doDsu && n.equals(MACHINE1 + ".class")) d = patchBreak(d);
            out.put(n, d);
        }
        zf.close();
        out.put(HELPER + ".class", readAll(new FileInputStream(args[3])));

        if (doUnifier && unifierHits != 1)
            throw new IllegalStateException("unifierdupe: expected 1 moveItemStack call, found " + unifierHits);
        if (doDsu && usableHits != 2)
            throw new IllegalStateException("dsudupe: expected 2 isUseableByPlayer, patched " + usableHits);
        if (doDsu && removeHits != 1)
            throw new IllegalStateException("dsudupe: expected 1 removeBlockTileEntity call, found " + removeHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /**
     * updateEntity's only call to the private moveItemStack(ur) becomes a static call on the
     * helper. The operand stack is already [this, source], which is the helper's signature.
     */
    static byte[] patchUnifier(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("g") || !m.desc.equals("()V")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKESPECIAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(UNIFIER) || !mi.name.equals("moveItemStack") || !mi.desc.equals("(Lur;)V")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "moveItemStack",
                        "(L" + UNIFIER + ";Lur;)V", false));
                unifierHits++;
            }
        }
        return write(cn);
    }

    /** Inserts at the top of a_(qx) (isUseableByPlayer): if (!TLiteMFR.isInWorld(this)) return false; */
    static byte[] patchUsable(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("a_") || !m.desc.equals("(Lqx;)Z")) continue;
            InsnList g = new InsnList();
            LabelNode allowed = new LabelNode();
            g.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "isInWorld", "(Lany;)Z", false));
            g.add(new JumpInsnNode(Opcodes.IFNE, allowed));
            g.add(new InsnNode(Opcodes.ICONST_0));
            g.add(new InsnNode(Opcodes.IRETURN));
            g.add(allowed);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 1);
            usableHits++;
        }
        return write(cn);
    }

    /**
     * breakBlock a(yc, IIIII) calls yc.r(III) once, at the end of the DSU branch. The stack is
     * [world, x, y, z], which is the helper's signature.
     */
    static byte[] patchBreak(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("a") || !m.desc.equals("(Lyc;IIIII)V")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("r") || !mi.desc.equals("(III)V")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "removeBlockTileEntity",
                        "(Lyc;III)V", false));
                removeHits++;
            }
        }
        return write(cn);
    }

    static ClassNode read(byte[] b) {
        ClassNode cn = new ClassNode();
        new ClassReader(b).accept(cn, ClassReader.SKIP_FRAMES);
        return cn;
    }

    static byte[] write(ClassNode cn) {
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
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
