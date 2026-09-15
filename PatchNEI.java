import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: NotEnoughItems 1.4.7.0 (coremod) patches.
 *
 *   spawner   ServerPacketHandler.handlePacket
 *             this.handleMobSpawnerID(world, coord, mobtype)
 *               -> TLiteNEI.handleMobSpawnerID(this, world, coord, mobtype, sender)
 *
 *   creative  NEIServerUtils.toggleCreativeMode
 *             starts with: if (!TLiteNEI.canToggleCreative(player)) return;
 *
 * NEI's authenticatePacket lets packets 13 (toggle creative) and 15 (set a spawner's mob)
 * through for every player. 15 changes any spawner in the world, claims included, and 13 puts
 * the sender in creative mode.
 *
 * usage: PatchNEI <in.jar> <out.jar> <patch>[,<patch>...] <TLiteNEI.class> <TLiteProtect.class>
 */
public class PatchNEI {

    static final String HANDLER = "codechicken/nei/ServerPacketHandler";
    static final String UTILS = "codechicken/nei/NEIServerUtils";
    static final String HELPER = "TLiteNEI";

    static boolean doSpawner, doCreative;
    static int spawnerHits, creativeHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: PatchNEI <in.jar> <out.jar> <patches> <TLiteNEI.class> <TLiteProtect.class>");
            System.err.println("patches: spawner, creative");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("spawner")) doSpawner = true;
            else if (p.equals("creative")) doCreative = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doSpawner && n.equals(HANDLER + ".class")) d = patchSpawner(d);
            if (doCreative && n.equals(UTILS + ".class")) d = patchCreative(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doSpawner && spawnerHits != 1)
            throw new IllegalStateException("spawner: expected 1 handleMobSpawnerID call, patched " + spawnerHits);
        if (doCreative && creativeHits != 1)
            throw new IllegalStateException("creative: expected 1 toggleCreativeMode, patched " + creativeHits);

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
     * handlePacket(PacketCustom packet, iv nethandler, iq sender): sender is local 3. It is pushed
     * after the call's own arguments and the private call becomes a static call to the helper,
     * which takes this as its first argument.
     */
    static byte[] patchSpawner(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("handlePacket")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKESPECIAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(HANDLER) || !mi.name.equals("handleMobSpawnerID")) continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 3));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "handleMobSpawnerID",
                        "(L" + HANDLER + ";Lyc;Lcodechicken/core/BlockCoord;Ljava/lang/String;Liq;)V", false));
                m.maxStack += 1;
                spawnerHits++;
            }
        }
        return write(cn);
    }

    /** Inserts at the top of toggleCreativeMode(iq player): if (!TLiteNEI.canToggleCreative(player)) return; */
    static byte[] patchCreative(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("toggleCreativeMode") || !m.desc.equals("(Liq;)V")) continue;
            InsnList g = new InsnList();
            LabelNode allowed = new LabelNode();
            g.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "canToggleCreative", "(Liq;)Z", false));
            g.add(new JumpInsnNode(Opcodes.IFNE, allowed));
            g.add(new InsnNode(Opcodes.RETURN));
            g.add(allowed);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 1);
            creativeHits++;
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
