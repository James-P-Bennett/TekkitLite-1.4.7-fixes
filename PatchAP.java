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
 *   apchunkgate chunkloader.TileChunkLoader.s (tile tick)
 *               gates the loader on TLiteAP.apChunkLoadEnabled(): when off, the loader drops its
 *               ticket and returns, so it force loads nothing (part of ChunkLoaderConversion)
 *
 * Packet id 16 set a teleport pipe's owner field to any client string on any teleport pipe, with
 * no check. An attacker set their own receiving pipe's owner to a victim's name (and a matching
 * frequency) and the victim's sending pipe then teleported its items, energy and liquid to the
 * attacker, across claims and dimensions. The owner is only ever meant to be set server-side on
 * placement, so the client's write is discarded; the value on the stack is popped.
 *
 * The AdditionalPipes chunk loader (the "Teleport Tether", block 4077) force loads its chunks with
 * a Forge ticket, offline included, and the mod has no config switch for it. apchunkgate adds one:
 * the loader tick calls TLiteAP.apChunkLoadEnabled() and, when it returns false (the default), calls
 * stopChunkLoading() and returns before requesting a ticket. Teleport pipes are left alone: a pipe
 * removes itself from the network when its chunk unloads, so an item sent toward an unloaded
 * destination has no target and drops at the source pipe rather than teleporting into it.
 *
 * usage: PatchAP <in.jar> <out.jar> <patch>[,<patch>...] [<TLiteAP.class>]
 */
public class PatchAP {

    static final String HANDLER = "buildcraft/additionalpipes/network/NetworkHandler";
    static final String LOGIC = "buildcraft/additionalpipes/pipes/logic/PipeLogicTeleport";
    static final String CHUNKTILE = "buildcraft/additionalpipes/chunkloader/TileChunkLoader";
    static final String HELPER = "TLiteAP";

    static boolean doOwner;
    static int ownerHits;
    static boolean doGate;
    static int gateHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PatchAP <in.jar> <out.jar> <patches> [TLiteAP.class]");
            System.err.println("patches: teleowner, apchunkgate");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("teleowner")) doOwner = true;
            else if (p.equals("apchunkgate")) doGate = true;
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
            if (doGate && n.equals(CHUNKTILE + ".class")) d = patchGate(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doOwner && ownerHits != 1)
            throw new IllegalStateException("teleowner: expected 1 owner write, patched " + ownerHits);
        if (doGate && gateHits != 1)
            throw new IllegalStateException("apchunkgate: expected to patch 1 tick, patched " + gateHits);

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

    /**
     * Prepends to the loader tile's tick s(): if TLiteAP.apChunkLoadEnabled() is false, call
     * this.stopChunkLoading() and return, before the tick can request a chunk ticket.
     */
    static byte[] patchGate(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("s") || !m.desc.equals("()V")) continue;
            InsnList pre = new InsnList();
            LabelNode cont = new LabelNode();
            pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "apChunkLoadEnabled", "()Z"));
            pre.add(new JumpInsnNode(Opcodes.IFNE, cont)); // enabled: run the normal tick
            pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
            pre.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKTILE, "stopChunkLoading", "()V"));
            pre.add(new InsnNode(Opcodes.RETURN));
            pre.add(cont);
            m.instructions.insert(pre);
            gateHits++;
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
