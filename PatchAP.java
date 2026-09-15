import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: AdditionalPipes 2.1.3 patch.
 *
 *   teleowner   NetworkHandler.onPacketData
 *               drops the "set teleport-pipe owner from the client" write (packet id 16)
 *
 * Packet id 16 set a teleport pipe's owner field to any client string on any teleport pipe, with
 * no check. An attacker set their own receiving pipe's owner to a victim's name (and a matching
 * frequency) and the victim's sending pipe then teleported its items, energy and liquid to the
 * attacker, across claims and dimensions. The owner is only ever meant to be set server-side on
 * placement, so the client's write is discarded; the value on the stack is popped.
 *
 * usage: PatchAP <in.jar> <out.jar> <patch>[,<patch>...]
 */
public class PatchAP {

    static final String HANDLER = "buildcraft/additionalpipes/network/NetworkHandler";
    static final String LOGIC = "buildcraft/additionalpipes/pipes/logic/PipeLogicTeleport";

    static boolean doOwner;
    static int ownerHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PatchAP <in.jar> <out.jar> <patches>");
            System.err.println("patches: teleowner");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("teleowner")) doOwner = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doOwner && n.equals(HANDLER + ".class")) d = patchOwner(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doOwner && ownerHits != 1)
            throw new IllegalStateException("teleowner: expected 1 owner write, patched " + ownerHits);

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
     * Replaces the PUTFIELD PipeLogicTeleport.owner in onPacketData with POP2, discarding the
     * [logic, ownerString] on the stack so the client's owner write does nothing.
     */
    static byte[] patchOwner(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onPacketData")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.PUTFIELD) continue;
                FieldInsnNode fi = (FieldInsnNode) i;
                if (!fi.owner.equals(LOGIC) || !fi.name.equals("owner")) continue;
                m.instructions.set(fi, new InsnNode(Opcodes.POP2));
                ownerHits++;
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
