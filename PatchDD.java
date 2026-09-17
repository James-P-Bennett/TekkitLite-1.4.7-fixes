import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: Dimensional Doors 1.3.2 patches.
 *
 *   rifts   itemDimDoor / ItemRiftBlade / itemLinkSignature onItemUse: refuse (return false, so the
 *             item is not consumed) when the holding player may not build at the clicked block, so a
 *             rift or door cannot be placed in a claim. Covers the Dimensional Door, Chaos Door and
 *             Exit Door (they inherit itemDimDoor.onItemUse), the Rift Blade on a block, and the
 *             Rift Signature.
 *           ItemRiftBlade air-cast rift-open (its (ur,yc,qx) and (ur,yc,qx,int) methods): the
 *             vanilla ItemDoor placement (tx.a) is routed through TLiteDD.placeDoor(..., player), so
 *             a rift aimed into a claim is refused too. The blade still works as a melee weapon.
 *
 * In-dimension edits (pocket dims, Limbo, dungeon generation, RiftGenerator) are left alone: no
 * claims exist there. All target classes are v50, so ClassWriter(0) + SKIP_FRAMES needs no frames.
 *
 * usage: PatchDD <in.zip> <out.zip> rifts <TLiteDD.class> <TLiteProtect.class>
 */
public class PatchDD {

    static final String PKG = "StevenDimDoors/mod_pocketDim/";
    static final String DIMDOOR = PKG + "itemDimDoor";
    static final String BLADE = PKG + "ItemRiftBlade";
    static final String SIG = PKG + "itemLinkSignature";
    static final String REMOVER = PKG + "itemRiftRemover";
    static final String HELPER = "TLiteDD";
    static final String ONITEMUSE = "(Lur;Lqx;Lyc;IIIIFFF)Z";
    static final String RIGHTCLICK = "(Lur;Lyc;Lqx;)Lur;";
    static final String STOPPED = "(Lur;Lyc;Lqx;I)V";
    static final String PLACER = "(Lyc;IIIILamq;)V";

    static boolean doRifts;
    static int guardHits, redirectHits, swordHits, removerHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PatchDD <in.zip> <out.zip> rifts <TLiteDD.class> <TLiteProtect.class>");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("rifts")) doRifts = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doRifts && (n.equals(DIMDOOR + ".class") || n.equals(BLADE + ".class")
                    || n.equals(SIG + ".class") || n.equals(REMOVER + ".class"))) {
                d = patch(d);
            }
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doRifts && (guardHits != 3 || redirectHits != 2 || swordHits != 3 || removerHits != 1))
            throw new IllegalStateException("rifts: expected guard 3, redirect 2, sword 3, remover 1; patched "
                    + guardHits + ", " + redirectHits + ", " + swordHits + ", " + removerHits);

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
        boolean isBlade = cn.name.equals(BLADE);

        // Rift Remover: its right-click closes a rift; gate it on the enable switch (no-op when off).
        if (cn.name.equals(REMOVER)) {
            for (Object mo : cn.methods) {
                MethodNode m = (MethodNode) mo;
                if (m.name.equals("a") && m.desc.equals(RIGHTCLICK)) {
                    LabelNode cont = new LabelNode();
                    InsnList c = new InsnList();
                    c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "riftRemoverEnabled", "()Z", false));
                    c.add(new JumpInsnNode(Opcodes.IFNE, cont));
                    c.add(new VarInsnNode(Opcodes.ALOAD, 1));   // return the itemstack unchanged
                    c.add(new InsnNode(Opcodes.ARETURN));
                    c.add(cont);
                    m.instructions.insert(c);
                    m.maxStack = Math.max(m.maxStack, 1);
                    removerHits++;
                }
            }
            return write(cn);
        }

        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;

            // onItemUse: refuse (return false) if the player may not build at the clicked block.
            if (m.name.equals("a") && m.desc.equals(ONITEMUSE)) {
                LabelNode cont = new LabelNode();
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 2));   // player
                c.add(new VarInsnNode(Opcodes.ALOAD, 3));   // world
                c.add(new VarInsnNode(Opcodes.ILOAD, 4));   // x
                c.add(new VarInsnNode(Opcodes.ILOAD, 5));   // y
                c.add(new VarInsnNode(Opcodes.ILOAD, 6));   // z
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "useAllowed", "(Lqx;Lyc;III)Z", false));
                c.add(new JumpInsnNode(Opcodes.IFNE, cont));
                c.add(new InsnNode(Opcodes.ICONST_0));
                c.add(new InsnNode(Opcodes.IRETURN));
                c.add(cont);
                m.instructions.insert(c);
                m.maxStack = Math.max(m.maxStack, 5);
                guardHits++;
                // The Rift Blade's block right-click also obeys the sword-only switch (inserted
                // above the claim guard, so it runs first).
                if (isBlade) { prependSwordGate(m); swordHits++; }
                continue;
            }

            if (isBlade) {
                // The blade's air-cast rift-open and charged-use methods obey the sword-only switch.
                if (m.name.equals("a") && (m.desc.equals(RIGHTCLICK) || m.desc.equals(STOPPED))) {
                    prependSwordGate(m);
                    swordHits++;
                }
                // Air-cast rift-open (player in local slot 3): route the ItemDoor placer through the
                // guarded helper so a rift aimed into a claim is refused.
                if (playerSlot(m.desc) == 3) {
                    boolean changed = false;
                    for (AbstractInsnNode i : m.instructions.toArray()) {
                        if (i.getOpcode() != Opcodes.INVOKESTATIC) continue;
                        MethodInsnNode mi = (MethodInsnNode) i;
                        if (!mi.name.equals("a") || !mi.desc.equals(PLACER)) continue;
                        m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 3));   // player
                        m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "placeDoor", "(Lyc;IIIILamq;Lqx;)V", false));
                        redirectHits++;
                        changed = true;
                    }
                    if (changed) m.maxStack += 1;
                }
            }
        }
        return write(cn);
    }

    /** Prepend {@code if (TLiteDD.swordOnly()) return <no-op>;} so the blade acts as a plain sword. */
    static void prependSwordGate(MethodNode m) {
        LabelNode cont = new LabelNode();
        InsnList c = new InsnList();
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "swordOnly", "()Z", false));
        c.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        Type ret = Type.getReturnType(m.desc);
        switch (ret.getSort()) {
            case Type.VOID:
                c.add(new InsnNode(Opcodes.RETURN));
                break;
            case Type.BOOLEAN:
                c.add(new InsnNode(Opcodes.ICONST_0));
                c.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.OBJECT:   // onItemRightClick returns the itemstack; hand back the one passed in
                c.add(new VarInsnNode(Opcodes.ALOAD, 1));
                c.add(new InsnNode(Opcodes.ARETURN));
                break;
            default:
                throw new IllegalStateException("sword gate: unexpected return " + ret);
        }
        c.add(cont);
        m.instructions.insert(c);
        m.maxStack = Math.max(m.maxStack, 1);
    }

    /** Local slot of the qx (EntityPlayer) parameter of an instance method, or -1 if none. */
    static int playerSlot(String desc) {
        Type[] args = Type.getArgumentTypes(desc);
        int slot = 1;   // this
        for (Type t : args) {
            if (t.getDescriptor().equals("Lqx;")) return slot;
            slot += t.getSize();
        }
        return -1;
    }

    static ClassNode read(byte[] b) { ClassNode cn = new ClassNode(); new ClassReader(b).accept(cn, ClassReader.SKIP_FRAMES); return cn; }
    static byte[] write(ClassNode cn) { ClassWriter cw = new ClassWriter(0); cn.accept(cw); return cw.toByteArray(); }
    static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n); is.close(); return bos.toByteArray();
    }
}
