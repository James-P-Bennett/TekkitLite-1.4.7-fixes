import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: AdvancedPowerManagement 1.1.55 patch.
 *
 *   guibutton   ServerPacketHandler.onPacketData
 *               tile.receiveGuiButton(button)  ->  TLiteAPM.guiButton(tile, button, player)
 *
 *   outputdupe  TEBatteryStation.moveOutputItems
 *               guards the output-slot increment with TLiteAPM.canMerge(contents[1], contents[i]),
 *               so a mismatched empty electric item cannot mint the output item
 *
 * The GUI-button packet ran on the tile at client coordinates with no reach check, so a player
 * could toggle any Battery Station's mode from anywhere. The button is now gated to a machine
 * within reach of the sender. moveOutputItems raised the output stack by one for any discharged
 * item without checking the output already held the same item, so discharging a different empty
 * electric item into an occupied output slot duplicated the output item.
 *
 * usage: PatchAPM <in.jar> <out.jar> <patch>[,<patch>...] <TLiteAPM.class>
 */
public class PatchAPM {

    static final String HANDLER = "com/kaijin/AdvPowerMan/ServerPacketHandler";
    static final String TE = "com/kaijin/AdvPowerMan/TECommon";
    static final String BATTERY = "com/kaijin/AdvPowerMan/TEBatteryStation";
    static final String HELPER = "TLiteAPM";

    static boolean doButton;
    static int hits;
    static boolean doDupe;
    static int dupeHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchAPM <in.jar> <out.jar> <patches> <TLiteAPM.class>");
            System.err.println("patches: guibutton, outputdupe");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("guibutton")) doButton = true;
            else if (p.equals("outputdupe")) doDupe = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doButton && n.equals(HANDLER + ".class")) d = patch(d);
            if (doDupe && n.equals(BATTERY + ".class")) d = patchDupe(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doButton && hits != 1)
            throw new IllegalStateException("guibutton: expected 1 receiveGuiButton, patched " + hits);
        if (doDupe && dupeHits != 1)
            throw new IllegalStateException("outputdupe: expected 1 output increment guard, patched " + dupeHits);

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    /** onPacketData(ce, di, Player): receiveGuiButton has [tile, button]; player (local 3) is pushed. */
    static byte[] patch(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("onPacketData")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.owner.equals(TE) || !mi.name.equals("receiveGuiButton") || !mi.desc.equals("(I)V")) continue;
                InsnList push = new InsnList();
                push.add(new VarInsnNode(Opcodes.ALOAD, 3));
                push.add(new TypeInsnNode(Opcodes.CHECKCAST, "qx"));
                m.instructions.insertBefore(mi, push);
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "guiButton", "(L" + TE + ";ILqx;)V", false));
                m.maxStack += 1;
                hits++;
            }
        }
        return write(cn);
    }

    /**
     * moveOutputItems() enters its output-slot increment branch through the first IFNONNULL (output
     * slot occupied), and its per-slot loop continues at the "iinc <loopvar>, 1". At the start of
     * that increment branch, insert: if (!TLiteAPM.canMerge(contents[1], contents[i])) continue.
     * The loop index is local 2; the contents field reference is cloned from the method itself.
     */
    static byte[] patchDupe(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("moveOutputItems") || !m.desc.equals("()V")) continue;

            FieldInsnNode contents = null;
            LabelNode incrLabel = null;
            LabelNode continueLabel = null;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (contents == null && i.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) i).desc.equals("[Lur;")) {
                    contents = (FieldInsnNode) i;
                }
                if (incrLabel == null && i.getOpcode() == Opcodes.IFNONNULL) {
                    incrLabel = ((JumpInsnNode) i).label;
                }
                if (continueLabel == null && i instanceof IincInsnNode && ((IincInsnNode) i).var == 2) {
                    for (AbstractInsnNode b = i.getPrevious(); b != null; b = b.getPrevious()) {
                        if (b instanceof LabelNode) { continueLabel = (LabelNode) b; break; }
                    }
                }
            }
            if (contents == null || incrLabel == null || continueLabel == null)
                throw new IllegalStateException("outputdupe: anchors not found (contents=" + contents + ", incr=" + incrLabel + ", cont=" + continueLabel + ")");

            InsnList g = new InsnList();
            g.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g.add(new FieldInsnNode(Opcodes.GETFIELD, contents.owner, contents.name, contents.desc));
            g.add(new InsnNode(Opcodes.ICONST_1));
            g.add(new InsnNode(Opcodes.AALOAD));
            g.add(new VarInsnNode(Opcodes.ALOAD, 0));
            g.add(new FieldInsnNode(Opcodes.GETFIELD, contents.owner, contents.name, contents.desc));
            g.add(new VarInsnNode(Opcodes.ILOAD, 2));
            g.add(new InsnNode(Opcodes.AALOAD));
            g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "canMerge", "(Lur;Lur;)Z", false));
            g.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
            m.instructions.insert(incrLabel, g);
            m.maxStack = Math.max(m.maxStack, 6);
            dupeHits++;
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
