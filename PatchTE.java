import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: ThermalExpansion 2.2.2.2 patches.
 *
 *   packets   PacketHandler.onPacketData
 *             packet.getTarget(world)  ->  TLiteTE.gateTarget(packet, world, player)
 *             TileEnergyCell.handleTilePacket / TileEngineRoot.handleTilePacket
 *             myProvider.setEnergyStored(v)  ->  TLiteTE.setEnergyStoredIfClient(myProvider, v, this.k)
 *
 * TE's packet handler trusts the client's coordinates, so anyone could set any Energy Cell's
 * stored energy, retune or seize any Tesseract and pull others' items through it, or scramble
 * any machine's faces, from anywhere. The lookup is gated to the sender's open GUI, and the
 * energy write is applied only on the client.
 *
 * usage: PatchTE <in.zip> <out.zip> <patch>[,<patch>...] <TLiteTE.class>
 */
public class PatchTE {

    static final String HANDLER = "thermalexpansion/core/network/PacketHandler";
    static final String PACKET = "thermalexpansion/core/network/PacketTile";
    static final String ENERGY_CELL = "thermalexpansion/energy/tileentity/TileEnergyCell";
    static final String ENGINE = "thermalexpansion/energy/tileentity/TileEngineRoot";
    static final String PROVIDER = "thermalexpansion/core/PowerProviderAdv";
    static final String HELPER = "TLiteTE";

    static boolean doPackets;
    static int gateHits, energyHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchTE <in.zip> <out.zip> <patches> <TLiteTE.class>");
            System.err.println("patches: packets");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("packets")) doPackets = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doPackets && n.equals(HANDLER + ".class")) d = patchHandler(d);
            if (doPackets && (n.equals(ENERGY_CELL + ".class") || n.equals(ENGINE + ".class"))) d = patchEnergy(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doPackets && gateHits != 1)
            throw new IllegalStateException("packets: expected 1 getTarget dispatch, patched " + gateHits);
        if (doPackets && energyHits != 4)
            throw new IllegalStateException("packets: expected 4 setEnergyStored writes, patched " + energyHits);

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
     * onPacketData(ce, di, Player): the sole PacketTile.getTarget(world) call has [packet, world]
     * on the stack. player is local 3; it is pushed (cast to qx) and the call goes to the helper.
     */
    static byte[] patchHandler(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onPacketData")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(PACKET) || !mi.name.equals("getTarget") || !mi.desc.equals("(Lyc;)Lany;")) continue;
                InsnList push = new InsnList();
                push.add(new VarInsnNode(Opcodes.ALOAD, 3));
                push.add(new TypeInsnNode(Opcodes.CHECKCAST, "qx"));
                m.instructions.insertBefore(mi, push);
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "gateTarget",
                        "(L" + PACKET + ";Lyc;Lqx;)Lany;", false));
                m.maxStack += 1;
                gateHits++;
            }
        }
        return write(cn);
    }

    /**
     * handleTilePacket: each myProvider.setEnergyStored(v) has [provider, float] on the stack.
     * this.k (the world) is pushed and the call goes to the guarded helper.
     */
    static byte[] patchEnergy(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("handleTilePacket")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(PROVIDER) || !mi.name.equals("setEnergyStored") || !mi.desc.equals("(F)V")) continue;
                InsnList push = new InsnList();
                push.add(new VarInsnNode(Opcodes.ALOAD, 0));
                push.add(new FieldInsnNode(Opcodes.GETFIELD, "any", "k", "Lyc;"));
                m.instructions.insertBefore(mi, push);
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "setEnergyStoredIfClient",
                        "(L" + PROVIDER + ";FLyc;)V", false));
                m.maxStack += 1;
                energyHits++;
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
