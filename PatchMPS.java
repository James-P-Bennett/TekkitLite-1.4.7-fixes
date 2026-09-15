import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Modular Powersuits patch.
 *
 *   tweak   MusePacketTweakRequest.handleServer
 *           moduleTag.setDouble(name, value)  ->  TLiteMPS.tweak(moduleTag, name, value)
 *
 * A client could set a reserved module key ("Active") to a double, crashing the module tick every
 * tick on the server. The write now rejects the reserved keys.
 *
 * usage: PatchMPS <in.jar> <out.jar> <patch>[,<patch>...] <TLiteMPS.class>
 */
public class PatchMPS {

    static final String PACKET = "net/machinemuse/powersuits/network/packets/MusePacketTweakRequest";
    static final String HELPER = "TLiteMPS";

    static boolean doTweak;
    static int hits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchMPS <in.jar> <out.jar> <patches> <TLiteMPS.class>");
            System.err.println("patches: tweak");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("tweak")) doTweak = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doTweak && n.equals(PACKET + ".class")) d = patch(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doTweak && hits != 1)
            throw new IllegalStateException("tweak: expected 1 setDouble, patched " + hits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** handleServer: the setDouble has [moduleTag, name, value]; route to the helper. */
    static byte[] patch(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("handleServer")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("bq") || !mi.name.equals("a") || !mi.desc.equals("(Ljava/lang/String;D)V")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "tweak", "(Lbq;Ljava/lang/String;D)V", false));
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
