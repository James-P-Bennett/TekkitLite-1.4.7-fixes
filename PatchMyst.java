import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Mystcraft patch.
 *
 *   linknull   LinkController.travelEntity
 *              after getWorldServer: if the world is null, return (stock logged but continued
 *              and then dereferenced the null world).
 *
 * A link book carrying a bad or removed dimension id made travelEntity crash on a null world.
 * It now returns cleanly.
 *
 * usage: PatchMyst <in.zip> <out.zip> <patch>[,<patch>...]
 */
public class PatchMyst {

    static final String LC = "com/xcompwiz/mystcraft/linking/LinkController";
    static final String SERVER = "net/minecraft/server/MinecraftServer";

    static boolean doNull;
    static int hits;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PatchMyst <in.zip> <out.zip> <patches>");
            System.err.println("patches: linknull");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("linknull")) doNull = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doNull && n.equals(LC + ".class")) d = patch(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doNull && hits != 1)
            throw new IllegalStateException("linknull: expected 1 getWorldServer call, patched " + hits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** travelEntity: after MinecraftServer.a(I)Lin; (getWorldServer), DUP; if null POP+RETURN. */
    static byte[] patch(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("travelEntity")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(SERVER) || !mi.name.equals("a") || !mi.desc.equals("(I)Lin;")) continue;
                InsnList g = new InsnList();
                LabelNode keep = new LabelNode();
                g.add(new InsnNode(Opcodes.DUP));
                g.add(new JumpInsnNode(Opcodes.IFNONNULL, keep));
                g.add(new InsnNode(Opcodes.POP));
                g.add(new InsnNode(Opcodes.RETURN));
                g.add(keep);
                m.instructions.insert(mi, g);
                m.maxStack += 1;
                hits++;
            }
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
