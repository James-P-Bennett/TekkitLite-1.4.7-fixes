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
 * usage: PatchMFR <in.jar> <out.jar> <patch>[,<patch>...] <TLiteMFR.class>
 */
public class PatchMFR {

    static final String UNIFIER = "powercrystals/minefactoryreloaded/processing/TileEntityUnifier";
    static final String HELPER  = "TLiteMFR";

    static boolean doUnifier;
    static int unifierHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchMFR <in.jar> <out.jar> <patches> <TLiteMFR.class>");
            System.err.println("patches: unifierdupe");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("unifierdupe")) doUnifier = true;
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
            out.put(n, d);
        }
        zf.close();
        out.put(HELPER + ".class", readAll(new FileInputStream(args[3])));

        if (doUnifier && unifierHits != 1)
            throw new IllegalStateException("unifierdupe: expected 1 moveItemStack call, found " + unifierHits);

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
