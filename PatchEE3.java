import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: EE3 pre1f patches.
 *
 *   requestcheck  PacketRequestEvent.execute
 *                 WorldTransmutationHandler.handleWorldTransmutation(...)
 *                   -> TLiteEE3.handleWorldTransmutation(...)
 *
 *   protect       WorldTransmutationHandler.onWorldTransmutationEvent
 *                 TransmutationHelper.transmuteInWorld(...)
 *                   -> TLiteEE3.transmuteInWorld(...)
 *
 * The transmutation packet is trusted completely: any coordinates, any range, no stone in hand.
 * And every block in the range is changed without asking protection plugins.
 *
 * usage: PatchEE3 <in.jar> <out.jar> <patch>[,<patch>...] <TLiteEE3.class> <TLiteProtect.class>
 */
public class PatchEE3 {

    static final String PACKET  = "com/pahimar/ee3/network/packet/PacketRequestEvent";
    static final String HANDLER = "com/pahimar/ee3/core/handlers/WorldTransmutationHandler";
    static final String HELPER_TM = "com/pahimar/ee3/core/helper/TransmutationHelper";
    static final String HELPER  = "TLiteEE3";

    static boolean doRequest, doProtect;
    static int requestHits, protectHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: PatchEE3 <in.jar> <out.jar> <patches> <TLiteEE3.class> <TLiteProtect.class>");
            System.err.println("patches: requestcheck, protect");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("requestcheck")) doRequest = true;
            else if (p.equals("protect")) doProtect = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doRequest && n.equals(PACKET + ".class")) d = redirect(d, "execute", HANDLER, "handleWorldTransmutation", true);
            if (doProtect && n.equals(HANDLER + ".class")) d = redirect(d, "onWorldTransmutationEvent", HELPER_TM, "transmuteInWorld", false);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doRequest && requestHits != 1)
            throw new IllegalStateException("requestcheck: expected 1 handleWorldTransmutation call, found " + requestHits);
        if (doProtect && protectHits != 1)
            throw new IllegalStateException("protect: expected 1 transmuteInWorld call, found " + protectHits);

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
     * In method inMethod, every static call to owner.name becomes the same call on the helper,
     * with the same descriptor, so the operand stack needs no change.
     */
    static byte[] redirect(byte[] in, String inMethod, String owner, String name, boolean request) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals(inMethod)) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKESTATIC) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(owner) || !mi.name.equals(name)) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, name, mi.desc, false));
                if (request) requestHits++; else protectHits++;
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
