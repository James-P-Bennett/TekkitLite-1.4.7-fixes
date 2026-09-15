import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Applied Energistics rv9-i patches.
 *
 *   entropy   toolEntropyAccelerator.onItemUse (obf a)
 *   catalyst  toolVibrationCatalyst.onItemUse (obf a)
 *   monitor   BlockStorageMonitor.onBlockActivated
 *
 * Each method gets a TLiteProtect.guard check at the top for the clicked block. When a
 * protection plugin refuses, the tools do nothing (return false) and the monitor click is
 * swallowed (return true).
 *
 * The tools turn blocks into other blocks (water to ice, stone to cobble, ores to ingots, ...)
 * straight through the world, so claims never see it. A locked, upgraded Storage Monitor hands
 * 64 of its item out of the ME network to anyone who right-clicks it.
 *
 * usage: PatchAE <in.zip> <out.zip> <patch>[,<patch>...] <TLiteProtect.class>
 */
public class PatchAE {

    static final String ITEM_USE = "(Lur;Lqx;Lyc;IIIIFFF)Z";   // this, stack, player, world, x, y, z, side, hx, hy, hz
    static final String ACTIVATE = "(Lyc;IIILqx;I)Z";           // this, world, x, y, z, player, side

    static final String[][] TARGETS = {
        // patch, class, method, desc, player local, world local, x local, denied return, label
        { "entropy",  "appeng/tools/toolEntropyAccelerator", "a", ITEM_USE, "2", "3", "4", "0", "Entropy Accelerator" },
        { "catalyst", "appeng/tools/toolVibrationCatalyst",  "a", ITEM_USE, "2", "3", "4", "0", "Vibration Catalyst" },
        { "monitor",  "appeng/me/block/BlockStorageMonitor", "onBlockActivated", ACTIVATE, "5", "1", "2", "1", "Storage Monitor use" },
    };

    static final Set<String> selected = new HashSet<String>();
    static final Map<String, Integer> hits = new HashMap<String, Integer>();

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchAE <in.zip> <out.zip> <patches> <TLiteProtect.class>");
            System.err.println("patches: entropy, catalyst, monitor");
            System.exit(2);
        }
        Set<String> known = new HashSet<String>();
        for (String[] t : TARGETS) known.add(t[0]);
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (!known.contains(p)) throw new IllegalArgumentException("unknown patch: " + p);
            selected.add(p);
            hits.put(p, 0);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            for (String[] t : TARGETS) {
                if (selected.contains(t[0]) && n.equals(t[1] + ".class")) d = guard(d, t);
            }
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        for (String p : selected) {
            if (hits.get(p) != 1) throw new IllegalStateException(p + ": expected 1 method, patched " + hits.get(p));
        }

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
     * Inserts at the top of the method:
     *   if (!TLiteProtect.guard(player, world, x, y, z, label)) return denied;
     */
    static byte[] guard(byte[] in, String[] t) {
        ClassNode cn = read(in);
        int player = Integer.parseInt(t[4]), world = Integer.parseInt(t[5]), x = Integer.parseInt(t[6]);
        int denied = Integer.parseInt(t[7]);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals(t[2]) || !m.desc.equals(t[3])) continue;
            InsnList g = new InsnList();
            LabelNode allowed = new LabelNode();
            g.add(new VarInsnNode(Opcodes.ALOAD, player));
            g.add(new VarInsnNode(Opcodes.ALOAD, world));
            g.add(new VarInsnNode(Opcodes.ILOAD, x));
            g.add(new VarInsnNode(Opcodes.ILOAD, x + 1));
            g.add(new VarInsnNode(Opcodes.ILOAD, x + 2));
            g.add(new LdcInsnNode(t[8]));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TLiteProtect", "guard",
                    "(Lqx;Lyc;IIILjava/lang/String;)Z", false));
            g.add(new JumpInsnNode(Opcodes.IFNE, allowed));
            g.add(new InsnNode(denied == 0 ? Opcodes.ICONST_0 : Opcodes.ICONST_1));
            g.add(new InsnNode(Opcodes.IRETURN));
            g.add(allowed);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 6);
            hits.put(t[0], hits.get(t[0]) + 1);
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
