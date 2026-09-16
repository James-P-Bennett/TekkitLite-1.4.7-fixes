import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: IC2NuclearControl 1.4.6 patch.
 *
 *   packets   CommonProxy.onPacketData
 *             world.getBlockTileEntity(x, y, z)  ->  TLiteNC.gate(world, x, y, z, player)
 *
 *   cardcap   CardWrapperImpl.setInt/setLong/setBoolean/setString
 *             start with: if (!TLiteNC.allowCardField(this.card, name)) return;
 *
 * The handler trusted the packet's coordinates, letting a client spam any Howler Alarm's sound
 * and flood any Info Panel's sensor-card NBT from anywhere in its world. Each lookup is gated to
 * tiles within reach of the sender. cardcap then bounds the reach-limited case: a sensor card can
 * hold only so many distinct fields, so a player next to a panel can no longer grow one card's
 * NBT without limit.
 *
 * usage: PatchNC <in.zip> <out.zip> <patch>[,<patch>...] <TLiteNC.class>
 */
public class PatchNC {

    static final String PROXY = "shedar/mods/ic2/nuclearcontrol/CommonProxy";
    static final String WRAPPER = "shedar/mods/ic2/nuclearcontrol/panel/CardWrapperImpl";
    static final String HELPER = "TLiteNC";
    static final java.util.Set<String> CARD_SETTERS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "setInt", "setLong", "setBoolean", "setString"));

    static boolean doPackets;
    static int hits;
    static boolean doCardCap;
    static int cardHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchNC <in.zip> <out.zip> <patches> <TLiteNC.class>");
            System.err.println("patches: packets, cardcap");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("packets")) doPackets = true;
            else if (p.equals("cardcap")) doCardCap = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doPackets && n.equals(PROXY + ".class")) d = patch(d);
            if (doCardCap && n.equals(WRAPPER + ".class")) d = patchCardCap(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doPackets && hits != 4)
            throw new IllegalStateException("packets: expected 4 getBlockTileEntity lookups, patched " + hits);
        if (doCardCap && cardHits != 4)
            throw new IllegalStateException("cardcap: expected 4 card setters, patched " + cardHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** onPacketData: each world.q(x,y,z) has [world, x, y, z]; player (local 3, cast iq) is pushed and the lookup goes to the gate. */
    static byte[] patch(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onPacketData")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("q") || !mi.desc.equals("(III)Lany;")) continue;
                InsnList push = new InsnList();
                push.add(new VarInsnNode(Opcodes.ALOAD, 3));
                push.add(new TypeInsnNode(Opcodes.CHECKCAST, "iq"));
                m.instructions.insertBefore(mi, push);
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "gate", "(Lyc;IIILiq;)Lany;", false));
                hits++;
            }
            m.maxStack += 1;
        }
        return write(cn);
    }

    /** Prepend to each card setter: if (!TLiteNC.allowCardField(this.card, name)) return; name is arg 1. */
    static byte[] patchCardCap(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!CARD_SETTERS.contains(m.name)) continue;
            LabelNode ok = new LabelNode();
            InsnList g = new InsnList();
            g.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g.add(new FieldInsnNode(Opcodes.GETFIELD, WRAPPER, "card", "Lur;"));
            g.add(new VarInsnNode(Opcodes.ALOAD, 1));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "allowCardField", "(Lur;Ljava/lang/String;)Z", false));
            g.add(new JumpInsnNode(Opcodes.IFNE, ok));
            g.add(new InsnNode(Opcodes.RETURN));
            g.add(ok);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 2);
            cardHits++;
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
