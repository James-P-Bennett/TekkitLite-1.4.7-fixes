import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Factorization 0.7.21 patches.
 *
 *   wrathigniter  ItemWrathIgniter.tryPlaceIntoWorld
 *
 * The Wrath Igniter lights wrath fire against the clicked block, which then eats through
 * blocks of that type, and it does so straight through the world with no protection check.
 * The method now starts with a TLiteProtect.guard check on the clicked block and does nothing
 * when a protection plugin refuses.
 *
 * usage: PatchFZ <in.jar> <out.jar> <patch>[,<patch>...] <TLiteProtect.class>
 */
public class PatchFZ {

    static final String IGNITER = "factorization/common/ItemWrathIgniter";
    static final String ITEM_USE = "(Lur;Lqx;Lyc;IIIIFFF)Z";   // this, stack, player, world, x, y, z, side, hx, hy, hz

    static boolean doIgniter;
    static int igniterHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchFZ <in.jar> <out.jar> <patches> <TLiteProtect.class>");
            System.err.println("patches: wrathigniter");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("wrathigniter")) doIgniter = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doIgniter && n.equals(IGNITER + ".class")) d = patchIgniter(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doIgniter && igniterHits != 1)
            throw new IllegalStateException("wrathigniter: expected 1 tryPlaceIntoWorld, patched " + igniterHits);

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
     * Inserts at the top of tryPlaceIntoWorld:
     *   if (!TLiteProtect.guard(player, world, x, y, z, "Wrath Igniter")) return true;
     * true, as stock returns for every use, so the client does not retry the click.
     */
    static byte[] patchIgniter(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("tryPlaceIntoWorld") || !m.desc.equals(ITEM_USE)) continue;
            InsnList g = new InsnList();
            LabelNode allowed = new LabelNode();
            g.add(new VarInsnNode(Opcodes.ALOAD, 2));
            g.add(new VarInsnNode(Opcodes.ALOAD, 3));
            g.add(new VarInsnNode(Opcodes.ILOAD, 4));
            g.add(new VarInsnNode(Opcodes.ILOAD, 5));
            g.add(new VarInsnNode(Opcodes.ILOAD, 6));
            g.add(new LdcInsnNode("Wrath Igniter"));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteProtect", "guard",
                    "(Lqx;Lyc;IIILjava/lang/String;)Z", false));
            g.add(new JumpInsnNode(Opcodes.IFNE, allowed));
            g.add(new InsnNode(Opcodes.ICONST_1));
            g.add(new InsnNode(Opcodes.IRETURN));
            g.add(allowed);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 6);
            igniterHits++;
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
