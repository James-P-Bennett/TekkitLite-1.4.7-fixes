import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: ComputerCraft 1.5 patches.
 *
 *   turtle   TileEntityTurtle.move, useTool, place, suck, dropQuantity
 *              start with: if (!TLiteTurtle.allowDir(this, dir, what)) return false;
 *            TileEntityTurtle.tryPlaceOnBlock
 *              starts with: if (!TLiteTurtle.allowPlace(this, x, y, z, side)) return false;
 *            TileEntityTurtle.tryPlaceOnEntity
 *              starts with: if (!TLiteTurtle.allowCell(this, x, y, z)) return false;
 *            TileEntityTurtle.transferStateFrom   starts with TLiteTurtle.transfer(this, from)
 *            TileEntityTurtle.readFromNBT/writeToNBT  end with TLiteTurtle.load/save(this, tag)
 *            BlockTurtle.onBlockPlacedBy          ends with TLiteTurtle.placed(world, x, y, z, placer)
 *            BlockTurtle.onBlockActivated         starts with TLiteTurtle.adopt(world, x, y, z, player)
 *
 * Turtles change and empty the blocks next to them with no protection check, so they dig, build
 * and move inside other players' claims.
 *
 * usage: PatchCC <in.zip> <out.zip> <patch>[,<patch>...] <TLiteTurtle.class> <TLiteProtect.class>
 */
public class PatchCC {

    static final String HELPER = "TLiteTurtle";
    static final String TILE = "dan200/turtle/shared/TileEntityTurtle";
    static final String BLOCK = "dan200/turtle/shared/BlockTurtle";

    static final String PLACED = "(Lyc;IIILmd;)V";
    static final String ACTIVATED = "(Lyc;IIILqx;IFFF)Z";

    /** method name, descriptor, local holding dir, label */
    static final String[][] DIR_GUARDS = {
        { "move", "(I)Z", "1", "Turtle move" },
        { "useTool", "(Ldan200/turtle/api/TurtleVerb;I)Z", "2", "Turtle dig or attack" },
        { "place", "(I[Ljava/lang/Object;)Z", "1", "Turtle place" },
        { "suck", "(I)Z", "1", "Turtle suck" },
        { "dropQuantity", "(II)Z", "1", "Turtle drop" },
    };

    static boolean doTurtle;
    static final Map<String, Integer> hits = new LinkedHashMap<String, Integer>();

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: PatchCC <in.zip> <out.zip> <patches> <TLiteTurtle.class> <TLiteProtect.class>");
            System.err.println("patches: turtle");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("turtle")) doTurtle = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doTurtle && n.equals(TILE + ".class")) d = patchTile(d);
            if (doTurtle && n.equals(BLOCK + ".class")) d = patchBlock(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doTurtle) {
            Map<String, Integer> expected = new LinkedHashMap<String, Integer>();
            for (String[] g : DIR_GUARDS) expected.put(g[0], 1);
            expected.put("tryPlaceOnBlock", 1);
            expected.put("tryPlaceOnEntity", 1);
            expected.put("transferStateFrom", 1);
            expected.put("nbt", 2);
            expected.put("placed", 1);
            expected.put("adopt", 1);
            for (Map.Entry<String, Integer> en : expected.entrySet()) {
                Integer got = hits.get(en.getKey());
                if (got == null || !got.equals(en.getValue()))
                    throw new IllegalStateException("turtle " + en.getKey() + ": expected " + en.getValue() + " sites, patched " + got);
            }
        }

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])));
        for (Map.Entry<String, byte[]> en : out.entrySet()) {
            zos.putNextEntry(new ZipEntry(en.getKey()));
            if (en.getValue() != null) zos.write(en.getValue());
            zos.closeEntry();
        }
        zos.close();
        System.out.println("OK  wrote " + args[1] + "  [" + args[2] + "]");
    }

    static void hit(String key) {
        Integer n = hits.get(key);
        hits.put(key, n == null ? 1 : n + 1);
    }

    static byte[] patchTile(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            for (String[] g : DIR_GUARDS) {
                if (!m.name.equals(g[0]) || !m.desc.equals(g[1])) continue;
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new VarInsnNode(Opcodes.ILOAD, Integer.parseInt(g[2])));
                call.add(new LdcInsnNode(g[3]));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "allowDir", "(L" + TILE + ";ILjava/lang/String;)Z", false));
                guardFalse(m, call, 3);
                hit(g[0]);
            }
            if (m.name.equals("tryPlaceOnBlock") && m.desc.equals("(Lur;Lyc;IIII[Ljava/lang/Object;)Z")) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                for (int local = 3; local <= 6; local++) call.add(new VarInsnNode(Opcodes.ILOAD, local));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "allowPlace", "(L" + TILE + ";IIII)Z", false));
                guardFalse(m, call, 5);
                hit("tryPlaceOnBlock");
            }
            if (m.name.equals("tryPlaceOnEntity") && m.desc.equals("(Lur;Lyc;IIIIZ[Ljava/lang/Object;F)Z")) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                for (int local = 3; local <= 5; local++) call.add(new VarInsnNode(Opcodes.ILOAD, local));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "allowCell", "(L" + TILE + ";III)Z", false));
                guardFalse(m, call, 4);
                hit("tryPlaceOnEntity");
            }
            if (m.name.equals("transferStateFrom") && m.desc.equals("(L" + TILE + ";)V")) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "transfer", "(L" + TILE + ";L" + TILE + ";)V", false));
                m.instructions.insert(call);
                m.maxStack = Math.max(m.maxStack, 2);
                hit("transferStateFrom");
            }
            if (m.desc.equals("(Lbq;)V") && (m.name.equals("a") || m.name.equals("b"))) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, m.name.equals("a") ? "load" : "save", "(Lany;Lbq;)V", false));
                beforeReturns(m, call);
                m.maxStack = Math.max(m.maxStack, 2);
                hit("nbt");
            }
        }
        return write(cn);
    }

    static byte[] patchBlock(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("a") && m.desc.equals(PLACED)) {
                beforeReturns(m, worldCall("placed", "(Lyc;IIILmd;)V"));
                m.maxStack = Math.max(m.maxStack, 5);
                hit("placed");
            }
            if (m.name.equals("a") && m.desc.equals(ACTIVATED)) {
                m.instructions.insert(worldCall("adopt", "(Lyc;IIILqx;)V"));
                m.maxStack = Math.max(m.maxStack, 5);
                hit("adopt");
            }
        }
        return write(cn);
    }

    /** [world, x, y, z, entity] from a block method's first five arguments, then the helper. */
    static InsnList worldCall(String name, String desc) {
        InsnList c = new InsnList();
        c.add(new VarInsnNode(Opcodes.ALOAD, 1));
        c.add(new VarInsnNode(Opcodes.ILOAD, 2));
        c.add(new VarInsnNode(Opcodes.ILOAD, 3));
        c.add(new VarInsnNode(Opcodes.ILOAD, 4));
        c.add(new VarInsnNode(Opcodes.ALOAD, 5));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, name, desc, false));
        return c;
    }

    /** Inserts at the top: call (leaves a boolean); if false, return false. */
    static void guardFalse(MethodNode m, InsnList call, int stack) {
        LabelNode allowed = new LabelNode();
        call.add(new JumpInsnNode(Opcodes.IFNE, allowed));
        call.add(new InsnNode(Opcodes.ICONST_0));
        call.add(new InsnNode(Opcodes.IRETURN));
        call.add(allowed);
        m.instructions.insert(call);
        m.maxStack = Math.max(m.maxStack, stack);
    }

    static void beforeReturns(MethodNode m, InsnList call) {
        for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i.getOpcode() != Opcodes.RETURN) continue;
            InsnList copy = new InsnList();
            Map<LabelNode, LabelNode> labels = new HashMap<LabelNode, LabelNode>();
            for (AbstractInsnNode c = call.getFirst(); c != null; c = c.getNext()) copy.add(c.clone(labels));
            m.instructions.insertBefore(i, copy);
        }
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
