import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: immibis-core 52.4.6 patches (shared by Tubestuff).
 *
 *   mergenbt  BasicInventory.mergeStackIntoRange, both static overloads
 *             body -> return TLiteImmibis.mergeStackIntoRange(same arguments)
 *
 * The shift-click merge compares item id and damage but not NBT, so a stack of empty Deep
 * Storage Units shift-clicked onto one full one in an ACT Mk II all become full.
 *
 * usage: PatchImmibis <in.jar> <out.jar> <patch>[,<patch>...] <TLiteImmibis.class>
 */
public class PatchImmibis {

    static final String BASIC = "immibis/core/BasicInventory";
    static final String HELPER = "TLiteImmibis";
    static final Set<String> DESCS = new HashSet<String>(Arrays.asList("(Lur;Lla;II)Lur;", "(Lla;Lla;III)Z"));

    static boolean doMerge;
    static int mergeHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchImmibis <in.jar> <out.jar> <patches> <TLiteImmibis.class>");
            System.err.println("patches: mergenbt");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("mergenbt")) doMerge = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doMerge && n.equals(BASIC + ".class")) d = patchMerge(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doMerge && mergeHits != 2)
            throw new IllegalStateException("mergenbt: expected 2 static mergeStackIntoRange, patched " + mergeHits);

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
     * Replaces the whole body of each static overload with a call to the helper method of the
     * same name and descriptor. The old body is dropped rather than left unreachable.
     */
    static byte[] patchMerge(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("mergeStackIntoRange") || (m.access & Opcodes.ACC_STATIC) == 0 || !DESCS.contains(m.desc)) continue;
            InsnList body = new InsnList();
            int local = 0;
            for (Type t : Type.getArgumentTypes(m.desc)) {
                body.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), local));
                local += t.getSize();
            }
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, m.name, m.desc, false));
            body.add(new InsnNode(Type.getReturnType(m.desc).getOpcode(Opcodes.IRETURN)));
            m.instructions.clear();
            m.tryCatchBlocks.clear();
            if (m.localVariables != null) m.localVariables.clear();
            m.instructions.add(body);
            m.maxStack = local;
            m.maxLocals = local;
            mergeHits++;
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
