import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: IronChest 5.1.0.275 tweak.
 *
 *   crystalcap   TileEntityIronChest.sortTopStacks
 *                ends with: TLiteIronChest.cap(this.topStacks)
 *
 * Caps how many item stacks a Crystal Chest renders (the only transparent chest) to cut client
 * render lag on bases with many of them.
 *
 * usage: PatchIronChest <in.zip> <out.zip> <patch>[,<patch>...] <TLiteIronChest.class>
 */
public class PatchIronChest {

    static final String TILE = "cpw/mods/ironchest/TileEntityIronChest";
    static final String HELPER = "TLiteIronChest";

    static boolean doCap;
    static int capHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchIronChest <in.zip> <out.zip> <patches> <TLiteIronChest.class>");
            System.err.println("patches: crystalcap");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("crystalcap")) doCap = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doCap && n.equals(TILE + ".class")) d = patchCap(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doCap && capHits < 1)
            throw new IllegalStateException("crystalcap: expected to patch sortTopStacks, patched " + capHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** Inserts TLiteIronChest.cap(this.topStacks) before every return of sortTopStacks. */
    static byte[] patchCap(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("sortTopStacks") || !m.desc.equals("()V")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.RETURN) continue;
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, "topStacks", "[Lur;"));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "cap", "([Lur;)V", false));
                m.instructions.insertBefore(i, c);
                capHits++;
            }
            m.maxStack = Math.max(m.maxStack, 1);
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
