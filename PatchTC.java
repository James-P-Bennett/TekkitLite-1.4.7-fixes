import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: TreeCapitator 1.4.6 r07 (coremod) patches.
 *
 *   felling   TreeBlockBreaker.destroyBlocksWithChance
 *             world.getBlockId(x, y, z)
 *               -> TLiteTreeCap.getBlockId(world, x, y, z, this, this.startPos)
 *
 * TreeCapitator breaks every log joined to the one the player broke, and the leaves above,
 * straight through the world. Only the first log goes through the server's break check, so
 * felling a log on open ground takes out connected logs inside other players' claims. The
 * helper returns air for blocks protection plugins refuse, and the loop skips air.
 *
 * usage: PatchTC <in.jar> <out.jar> <patch>[,<patch>...] <TLiteTreeCap.class> <TLiteProtect.class>
 */
public class PatchTC {

    static final String BREAKER = "bspkrs/treecapitator/TreeBlockBreaker";
    static final String DESTROY_DESC = "(Lyc;Ljava/util/List;FZ)V";
    static final String HELPER = "TLiteTreeCap";

    static boolean doFelling;
    static int fellingHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: PatchTC <in.jar> <out.jar> <patches> <TLiteTreeCap.class> <TLiteProtect.class>");
            System.err.println("patches: felling");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("felling")) doFelling = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doFelling && n.equals(BREAKER + ".class")) d = patchFelling(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doFelling && fellingHits != 1)
            throw new IllegalStateException("felling: expected 1 getBlockId call, patched " + fellingHits);

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
     * The only yc.a(III)I call in destroyBlocksWithChance reads the id of the block about to be
     * broken. The world and coordinates are already on the stack; this and this.startPos are
     * pushed after them and the call goes to the helper instead.
     */
    static byte[] patchFelling(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("destroyBlocksWithChance") || !m.desc.equals(DESTROY_DESC)) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("a") || !mi.desc.equals("(III)I")) continue;
                InsnList extra = new InsnList();
                extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
                extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
                extra.add(new FieldInsnNode(Opcodes.GETFIELD, BREAKER, "startPos", "Lbspkrs/util/Coord;"));
                m.instructions.insertBefore(mi, extra);
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "getBlockId",
                        "(Lyc;IIILbspkrs/treecapitator/TreeBlockBreaker;Lbspkrs/util/Coord;)I", false));
                m.maxStack += 2;
                fellingHits++;
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
