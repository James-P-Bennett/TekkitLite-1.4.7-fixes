import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: OmniTools 3.0.4 patch.
 *
 *   wrench   ItemWrench.onItemUseFirst
 *            starts with: if (!TLiteProtect.guard(player, world, i, j, k, "OmniWrench")) return true;
 *
 * The OmniWrench removes a machine (world.setBlockWithNotify to air, then drops it) and rotates
 * vanilla blocks straight through the world, so it can pop machines out of another player's
 * claim. The click is now checked against the player the same way a hand break is; a refused
 * click is swallowed. The player is online, so this uses their real build permission.
 *
 * usage: PatchOmniTools <in.zip> <out.zip> <patch>[,<patch>...] <TLiteProtect.class>
 */
public class PatchOmniTools {

    static final String WRENCH = "omnitools/item/ItemWrench";
    static final String USE = "(Lur;Lqx;Lyc;IIIIFFF)Z";

    static boolean doWrench;
    static int hits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchOmniTools <in.zip> <out.zip> <patches> <TLiteProtect.class>");
            System.err.println("patches: wrench");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("wrench")) doWrench = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doWrench && n.equals(WRENCH + ".class")) d = patch(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doWrench && hits != 1)
            throw new IllegalStateException("wrench: expected 1 onItemUseFirst, patched " + hits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** Inserts the guard at the top of onItemUseFirst(stack, player, world, i, j, k, side, ...). */
    static byte[] patch(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onItemUseFirst") || !m.desc.equals(USE)) continue;
            InsnList g = new InsnList();
            LabelNode allowed = new LabelNode();
            g.add(new VarInsnNode(Opcodes.ALOAD, 2));
            g.add(new VarInsnNode(Opcodes.ALOAD, 3));
            g.add(new VarInsnNode(Opcodes.ILOAD, 4));
            g.add(new VarInsnNode(Opcodes.ILOAD, 5));
            g.add(new VarInsnNode(Opcodes.ILOAD, 6));
            g.add(new LdcInsnNode("OmniWrench"));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteProtect", "guard",
                    "(Lqx;Lyc;IIILjava/lang/String;)Z", false));
            g.add(new JumpInsnNode(Opcodes.IFNE, allowed));
            g.add(new InsnNode(Opcodes.ICONST_1));
            g.add(new InsnNode(Opcodes.IRETURN));
            g.add(allowed);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 6);
            hits++;
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
