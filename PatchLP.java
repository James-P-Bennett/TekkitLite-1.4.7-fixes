import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: LogisticsPipes 0.7.0.96 patches.
 *
 *   diskdupe      ServerPacketHandler.onDiskChangeClientSide
 *                 pipe.setDisk(packet.itemstack)  ->  TLiteLP.setDisk(pipe, packet.itemstack)
 *
 *   requestclamp  RequestHandler.request and simulate
 *                 packet.amount (before ItemIdentifier.makeStack) -> TLiteLP.clampAmount(amount)
 *
 *   security      ServerPacketHandler.onSecurityCardButton/onOpenSecurityPlayer/
 *                 onSaveSecurityPlayer/onSetSecurityCC
 *                 after each casts the tile: if (!TLiteLP.securityAllowed(tile, player)) return
 *
 * The disk-change packet stored a client-controlled ItemStack as a Request Pipe Mk2's disk, and
 * the disk-drop packet spawned it into the world: unlimited item creation. The store now only
 * accepts a real disk item.
 *
 * The request packet's amount is an unvalidated client int that sizes the crafting tree, so a
 * near-max value is a denial of service; requestclamp bounds it well above any real request.
 *
 * usage: PatchLP <in.jar> <out.jar> <patch>[,<patch>...] <TLiteLP.class>
 */
public class PatchLP {

    static final String HANDLER = "logisticspipes/network/ServerPacketHandler";
    static final String PIPE = "logisticspipes/pipes/PipeItemsRequestLogisticsMk2";
    static final String HELPER = "TLiteLP";
    static final String REQHANDLER = "logisticspipes/request/RequestHandler";
    static final String PACKET = "logisticspipes/network/packets/PacketRequestSubmit";
    static final String IDENT = "logisticspipes/utils/ItemIdentifier";
    static final String SPH = "logisticspipes/network/ServerPacketHandler";
    static final String SECTILE = "logisticspipes/blocks/LogisticsSecurityTileEntity";
    static final java.util.Set<String> SEC_HANDLERS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "onSecurityCardButton", "onOpenSecurityPlayer", "onSaveSecurityPlayer", "onSetSecurityCC"));

    static boolean doDisk;
    static int diskHits;
    static boolean doClamp;
    static int clampHits;
    static boolean doSecurity;
    static int securityHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchLP <in.jar> <out.jar> <patches> <TLiteLP.class>");
            System.err.println("patches: diskdupe, requestclamp");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("diskdupe")) doDisk = true;
            else if (p.equals("requestclamp")) doClamp = true;
            else if (p.equals("security")) doSecurity = true;
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
            if (doClamp && n.equals(REQHANDLER + ".class")) d = patchClamp(d);
            if (doSecurity && n.equals(SPH + ".class")) d = patchSecurity(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doDisk && diskHits != 1)
            throw new IllegalStateException("diskdupe: expected 1 setDisk call, patched " + diskHits);
        if (doClamp && clampHits != 2)
            throw new IllegalStateException("requestclamp: expected 2 amount reads (request, simulate), patched " + clampHits);
        if (doSecurity && securityHits != 4)
            throw new IllegalStateException("security: expected 4 handler guards, patched " + securityHits);

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

    /**
     * Wherever a request reads packet.amount and passes it straight to ItemIdentifier.makeStack
     * (the item request/simulate paths), route the value through TLiteLP.clampAmount first. The
     * liquid path reads amount in millibuckets through a different route and is left alone.
     */
    static byte[] patchClamp(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.GETFIELD) continue;
                FieldInsnNode fi = (FieldInsnNode) i;
                if (!fi.owner.equals(PACKET) || !fi.name.equals("amount")) continue;
                AbstractInsnNode next = fi.getNext();
                if (next == null || next.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mk = (MethodInsnNode) next;
                if (!mk.owner.equals(IDENT) || !mk.name.equals("makeStack")) continue;
                m.instructions.insert(fi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "clampAmount", "(I)I", false));
                clampHits++;
            }
        }
        return write(cn);
    }

    /**
     * In each security-station handler, right after it casts the looked-up tile to
     * LogisticsSecurityTileEntity, insert: dup the tile, pass it and the sender (local 0) to
     * TLiteLP.securityAllowed; if that is false, drop the tile and return, so the station is not
     * touched. The player is the handler's first argument.
     */
    static byte[] patchSecurity(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!SEC_HANDLERS.contains(m.name)) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.CHECKCAST) continue;
                TypeInsnNode ti = (TypeInsnNode) i;
                if (!ti.desc.equals(SECTILE)) continue;
                LabelNode ok = new LabelNode();
                InsnList g = new InsnList();
                g.add(new InsnNode(Opcodes.DUP));
                g.add(new VarInsnNode(Opcodes.ALOAD, 0));
                g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "securityAllowed", "(L" + SECTILE + ";Lqx;)Z", false));
                g.add(new JumpInsnNode(Opcodes.IFNE, ok));
                g.add(new InsnNode(Opcodes.POP));
                g.add(new InsnNode(Opcodes.RETURN));
                g.add(ok);
                m.instructions.insert(ti, g);
                m.maxStack = Math.max(m.maxStack, 6);
                securityHits++;
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
