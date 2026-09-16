import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: AdditionalPipes 2.1.3 patch.
 *
 *   teleowner  NetworkHandler.onPacketData
 *              drops the "set teleport-pipe owner from the client" write (packet id 16)
 *
 *   apparity   brings the AdditionalPipes chunk loader ("Teleport Tether", block 4077) to parity
 *              with the ChickenChunks loader and the Dimensional Anchor (ChunkLoaderConversion):
 *                TileChunkLoader gains a public tliteOwner field;
 *                BlockChunkLoader gains onBlockPlacedBy, which records the placer as the owner and
 *                  messages them via TLiteAP.apPlaced;
 *                TileChunkLoader.a/b (read/writeToNBT) persist tliteOwner via TLiteAP.apLoadNBT/apSaveNBT;
 *                TileChunkLoader.getLoadArea forces loadDistance to 0 (a single chunk);
 *                TileChunkLoader.s (tick) force loads only when TLiteAP.apShouldLoad allows it
 *                  (enabled, owned, owner online/grace, under the shared per-player cap), else stops.
 *
 * Packet id 16 set a teleport pipe's owner field to any client string on any teleport pipe, with
 * no check, so an attacker could steal a victim's piped items, energy and liquid. The client write
 * is dropped; the owner is only set server-side on placement. Teleport pipes themselves are not
 * touched: a pipe leaves the network when its chunk unloads, so items aimed at an unloaded
 * destination drop at the source instead of teleporting into it.
 *
 * usage: PatchAP <in.jar> <out.jar> <patch>[,<patch>...] [<TLiteAP.class>...]
 */
public class PatchAP {

    static final String HANDLER = "buildcraft/additionalpipes/network/NetworkHandler";
    static final String LOGIC = "buildcraft/additionalpipes/pipes/logic/PipeLogicTeleport";
    static final String TILE = "buildcraft/additionalpipes/chunkloader/TileChunkLoader";
    static final String BLOCK = "buildcraft/additionalpipes/chunkloader/BlockChunkLoader";
    static final String HELPER = "TLiteAP";

    static boolean doOwner;
    static int ownerHits;
    static boolean doParity;
    static int fieldHits, areaHits, loadHits, saveHits, gateHits, placedHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PatchAP <in.jar> <out.jar> <patches> [TLiteAP.class...]");
            System.err.println("patches: teleowner, apparity");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("teleowner")) doOwner = true;
            else if (p.equals("apparity")) doParity = true;
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
            if (doParity && n.equals(TILE + ".class")) d = patchTile(d);
            if (doParity && n.equals(BLOCK + ".class")) d = patchBlock(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doOwner && ownerHits != 1)
            throw new IllegalStateException("teleowner: expected 1 owner write, patched " + ownerHits);
        if (doParity && (fieldHits != 1 || areaHits != 1 || loadHits != 1 || saveHits != 1 || gateHits != 1 || placedHits != 1))
            throw new IllegalStateException("apparity: expected 1 each (field/area/load/save/gate/placed), patched "
                    + fieldHits + "/" + areaHits + "/" + loadHits + "/" + saveHits + "/" + gateHits + "/" + placedHits);

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

    /** TileChunkLoader: owner field, single-chunk load area, NBT owner persistence, and the tick gate. */
    static byte[] patchTile(byte[] in) {
        ClassNode cn = read(in);
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "tliteOwner", "Ljava/lang/String;", null, null));
        fieldHits++;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;

            if (m.name.equals("getLoadArea") && m.desc.equals("()Ljava/util/List;")) {
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new InsnNode(Opcodes.ICONST_0));
                c.add(new FieldInsnNode(Opcodes.PUTFIELD, TILE, "loadDistance", "I"));
                m.instructions.insert(c);
                m.maxStack = Math.max(m.maxStack, 2);
                areaHits++;
            }

            if (m.name.equals("a") && m.desc.equals("(Lbq;)V")) {
                appendBeforeReturn(m, nbtCall("apLoadNBT"));
                m.maxStack = Math.max(m.maxStack, 2);
                loadHits++;
            }
            if (m.name.equals("b") && m.desc.equals("(Lbq;)V")) {
                appendBeforeReturn(m, nbtCall("apSaveNBT"));
                m.maxStack = Math.max(m.maxStack, 2);
                saveHits++;
            }

            if (m.name.equals("s") && m.desc.equals("()V")) {
                InsnList pre = new InsnList();
                LabelNode cont = new LabelNode();
                pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
                pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "apShouldLoad", "(Ljava/lang/Object;)Z", false));
                pre.add(new JumpInsnNode(Opcodes.IFNE, cont));
                pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
                pre.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TILE, "stopChunkLoading", "()V", false));
                pre.add(new InsnNode(Opcodes.RETURN));
                pre.add(cont);
                m.instructions.insert(pre);
                m.maxStack = Math.max(m.maxStack, 1);
                gateHits++;
            }
        }
        return write(cn);
    }

    private static InsnList nbtCall(String method) {
        InsnList c = new InsnList();
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new VarInsnNode(Opcodes.ALOAD, 1));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, method, "(Ljava/lang/Object;Lbq;)V", false));
        return c;
    }

    private static void appendBeforeReturn(MethodNode m, InsnList payload) {
        AbstractInsnNode last = null;
        for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i.getOpcode() == Opcodes.RETURN) last = i;
        }
        if (last != null) m.instructions.insertBefore(last, payload);
    }

    /**
     * Adds onBlockPlacedBy (obf a(yc,int,int,int,md)) to BlockChunkLoader, which the AP block does
     * not have, delegating to TLiteAP.apPlaced to record the placer as the loader's owner. Minecraft
     * calls this after placement; the vanilla base method is empty, so no super call is needed.
     */
    static byte[] patchBlock(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("a") && m.desc.equals("(Lyc;IIILmd;)V")) {
                throw new IllegalStateException("apparity: BlockChunkLoader already has onBlockPlacedBy");
            }
        }
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "a", "(Lyc;IIILmd;)V", null, null);
        InsnList c = m.instructions;
        c.add(new VarInsnNode(Opcodes.ALOAD, 1));
        c.add(new VarInsnNode(Opcodes.ILOAD, 2));
        c.add(new VarInsnNode(Opcodes.ILOAD, 3));
        c.add(new VarInsnNode(Opcodes.ILOAD, 4));
        c.add(new VarInsnNode(Opcodes.ALOAD, 5));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "apPlaced", "(Lyc;IIILmd;)V", false));
        c.add(new InsnNode(Opcodes.RETURN));
        m.maxStack = 5;
        m.maxLocals = 6;
        cn.methods.add(m);
        placedHits++;
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
