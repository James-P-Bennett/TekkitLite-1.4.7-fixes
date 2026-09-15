import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Balkon's Weaponmod patch.
 *
 *   dynamite   AdvancedExplosion.doBlockExplosion
 *              world.setBlockWithNotify(x, y, z, 0)  ->  TLiteWM.breakIfAllowed(world, x, y, z, this.f)
 *
 * The dynamite/cannon explosion removes blocks without firing the Bukkit event GriefPrevention
 * filters, so it broke blocks in claims. Each removal is checked against the thrower.
 *
 * usage: PatchWM <in.zip> <out.zip> <patch>[,<patch>...] <TLiteWM.class> <TLiteProtect.class>
 */
public class PatchWM {

    static final String EXPLOSION = "weaponmod/AdvancedExplosion";
    static final String HELPER = "TLiteWM";

    static boolean doDynamite;
    static int hits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchWM <in.zip> <out.zip> <patches> <TLiteWM.class> <TLiteProtect.class>");
            System.err.println("patches: dynamite");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("dynamite")) doDynamite = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doDynamite && n.equals(EXPLOSION + ".class")) d = patch(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doDynamite && hits != 1)
            throw new IllegalStateException("dynamite: expected 1 setBlockWithNotify in doBlockExplosion, patched " + hits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** doBlockExplosion: the world.e(x,y,z,0) call has [world, x, y, z]; this.f (the exploder) is pushed and the call goes to the helper. */
    static byte[] patch(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("doBlockExplosion")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("yc") || !mi.name.equals("e") || !mi.desc.equals("(IIII)Z")) continue;
                m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                m.instructions.insertBefore(mi, new FieldInsnNode(Opcodes.GETFIELD, "xx", "f", "Llq;"));
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "breakIfAllowed", "(Lyc;IIILlq;)Z", false));
                m.maxStack += 1;
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
