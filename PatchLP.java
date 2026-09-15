import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: LogisticsPipes 0.7.0.96 patches.
 *
 *   diskdupe   ServerPacketHandler.onDiskChangeClientSide
 *              pipe.setDisk(packet.itemstack)  ->  TLiteLP.setDisk(pipe, packet.itemstack)
 *
 * The disk-change packet stored a client-controlled ItemStack as a Request Pipe Mk2's disk, and
 * the disk-drop packet spawned it into the world: unlimited item creation. The store now only
 * accepts a real disk item.
 *
 * usage: PatchLP <in.jar> <out.jar> <patch>[,<patch>...] <TLiteLP.class>
 */
public class PatchLP {

    static final String HANDLER = "logisticspipes/network/ServerPacketHandler";
    static final String PIPE = "logisticspipes/pipes/PipeItemsRequestLogisticsMk2";
    static final String HELPER = "TLiteLP";

    static boolean doDisk;
    static int diskHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchLP <in.jar> <out.jar> <patches> <TLiteLP.class>");
            System.err.println("patches: diskdupe");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("diskdupe")) doDisk = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doDisk && n.equals(HANDLER + ".class")) d = patchDisk(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doDisk && diskHits != 1)
            throw new IllegalStateException("diskdupe: expected 1 setDisk call, patched " + diskHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** onDiskChangeClientSide: [pipe, itemstack] on the stack at setDisk; route to the guarded helper. */
    static byte[] patchDisk(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onDiskChangeClientSide")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(PIPE) || !mi.name.equals("setDisk") || !mi.desc.equals("(Lur;)V")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "setDisk",
                        "(L" + PIPE + ";Lur;)V", false));
                diskHits++;
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
