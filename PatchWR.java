import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: WR-CBE Core 1.3.2.8 patches.
 *
 *   freq   WRCoreServerPacketHandler
 *          packet 9: RedstoneEther.setFreqOwner(freq, name)  ->  TLiteWR.dropFreqOwner(...)
 *          setTileFreq: RedstoneEther.getTile(world, pos)     ->  TLiteWR.gateTile(world, pos, sender)
 *
 * Packet 9 let a client seize any private frequency's owner; it is dropped. Packet 1 retuned any
 * wireless tile from anywhere; the lookup is gated to tiles within reach of the sender.
 *
 * usage: PatchWR <in.jar> <out.jar> <patch>[,<patch>...] <TLiteWR.class>
 */
public class PatchWR {

    static final String HANDLER = "codechicken/wirelessredstone/core/WRCoreServerPacketHandler";
    static final String ETHER = "codechicken/wirelessredstone/core/RedstoneEther";
    static final String HELPER = "TLiteWR";

    static boolean doFreq;
    static int ownerHits, tileHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchWR <in.jar> <out.jar> <patches> <TLiteWR.class>");
            System.err.println("patches: freq");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("freq")) doFreq = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doFreq && n.equals(HANDLER + ".class")) d = patch(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doFreq && (ownerHits != 1 || tileHits != 1))
            throw new IllegalStateException("freq: expected 1 setFreqOwner and 1 getTile, patched " + ownerHits + " and " + tileHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    static byte[] patch(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL && i.getOpcode() != Opcodes.INVOKESTATIC) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (mi.getOpcode() == Opcodes.INVOKEVIRTUAL && mi.owner.equals(ETHER)
                        && mi.name.equals("setFreqOwner") && mi.desc.equals("(ILjava/lang/String;)V")) {
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "dropFreqOwner",
                            "(L" + ETHER + ";ILjava/lang/String;)V", false));
                    ownerHits++;
                }
                if (m.name.equals("setTileFreq") && mi.getOpcode() == Opcodes.INVOKESTATIC && mi.owner.equals(ETHER)
                        && mi.name.equals("getTile") && mi.desc.equals("(Lyc;Lcodechicken/core/BlockCoord;)Lany;")) {
                    m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 1));
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "gateTile",
                            "(Lyc;Lcodechicken/core/BlockCoord;Lqx;)Lany;", false));
                    m.maxStack += 1;
                    tileHits++;
                }
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
