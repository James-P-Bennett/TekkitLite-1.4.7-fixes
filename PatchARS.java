import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Advanced Repulsion Systems 52.0.6 (immibis MFFS) patch.
 *
 *   tesla   TileTeslaCoil.fireShot(lq target, int)
 *             at entry:  if (target instanceof qx && TLiteARS.noPlayerDamage()) return;
 *             the target.attackEntityFrom(src, dmg) call -> TLiteARS.shock(target, src, dmg)
 *
 * The Industrial Tesla Coil (block 1952) shocks its target in fireShot with no option to spare
 * players or to withhold mob loot. This adds two default-off config flags: noPlayerDamage makes
 * fireShot skip players (PvE), and denyMobDrops (enforced by the TLiteARS drops handler that
 * shock registers) cancels the drops of mobs this coil kills. Both are off by default, so the
 * coil is unchanged until a server opts in.
 *
 * usage: PatchARS <in.jar> <out.jar> <patch>[,<patch>...] <TLiteARS.class> <TLiteARSDrops.class>
 */
public class PatchARS {

    static final String TILE = "immibis/ars/beams/TileTeslaCoil";
    static final String FIRESHOT = "(Llq;I)V";
    static final String HELPER = "TLiteARS";

    static boolean doTesla;
    static int pveHits, dropHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchARS <in.jar> <out.jar> <patches> <helper.class...>");
            System.err.println("patches: tesla");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("tesla")) doTesla = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doTesla && n.equals(TILE + ".class")) d = patchTesla(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doTesla && (pveHits != 1 || dropHits != 1))
            throw new IllegalStateException("tesla: expected 1 pve guard and 1 damage redirect, patched "
                    + pveHits + " and " + dropHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    static byte[] patchTesla(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("fireShot") || !m.desc.equals(FIRESHOT)) continue;

            // PvE guard at method entry: if (target instanceof qx && TLiteARS.noPlayerDamage()) return;
            LabelNode cont = new LabelNode();
            InsnList g = new InsnList();
            g.add(new VarInsnNode(Opcodes.ALOAD, 1));
            g.add(new TypeInsnNode(Opcodes.INSTANCEOF, "qx"));
            g.add(new JumpInsnNode(Opcodes.IFEQ, cont));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "noPlayerDamage", "()Z", false));
            g.add(new JumpInsnNode(Opcodes.IFEQ, cont));
            g.add(new InsnNode(Opcodes.RETURN));
            g.add(cont);
            m.instructions.insert(g);
            m.maxStack = Math.max(m.maxStack, 1);
            pveHits++;

            // Redirect the shock: target.attackEntityFrom(src, dmg) -> TLiteARS.shock(target, src, dmg)
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals("lq") || !mi.name.equals("a") || !mi.desc.equals("(Llh;I)Z")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "shock", "(Llq;Llh;I)Z", false));
                dropHits++;
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
