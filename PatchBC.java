import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Tekkit Lite 1.4.7 fixes: BuildCraft 3.4.3 patches.
 *
 *   quarry   BlockQuarry.onBlockPlacedBy        ends with TLiteBC.placed(world, x, y, z, placer)
 *            TileQuarry.readFromNBT/writeToNBT  end with TLiteBC.load/save(this, tag)
 *            TileQuarry.positionReached         isQuarriableBlock(x, y, z) -> TLiteBC.quarriable(this, x, y, z)
 *            TileQuarry.findTarget, createColumnVisitList
 *                                               BlockUtil.canChangeBlock(world, x, y, z) -> TLiteBC.quarryCanChange(..., this)
 *            TileQuarry.buildFrame              builder.getNextBlock(world, inv) -> TLiteBC.nextSlot(builder, world, inv)
 *
 *   filler   BlockFiller gains onBlockPlacedBy  super, then TLiteBC.placed(world, x, y, z, placer)
 *            BlockFiller.onBlockActivated       starts with TLiteBC.adopt(world, x, y, z, player)
 *            TileFiller.readFromNBT/writeToNBT  end with TLiteBC.load/save(this, tag)
 *            TileFiller.doWork                  pattern.iteratePattern(this, box, stack) -> TLiteBC.iterateFiller(pattern, ...)
 *            FillerPattern.fill, FillerFlattener.iteratePattern
 *                                               item.onItemUse(...) -> TLiteBC.fillerUse(item, ...)
 *            FillerPattern.empty                world.setBlockWithNotify -> TLiteBC.fillerSet
 *                                               BlockUtil.breakBlock -> TLiteBC.fillerBreak
 *
 *   quarrychunks  TileQuarry.setBoundaries   ticket.getMaxChunkListDepth() -> TLiteBC.quarryChunkDepth(ticket, this, area)
 *                 TileQuarry.forceChunkLoading  ends with TLiteBC.keepQuarryChunk(this, ticket)
 *                 BuildCraftFactory$QuarryChunkloadCallback.ticketsLoaded
 *                                             (TileQuarry) tile.forceChunkLoading(ticket) -> TLiteBC.reloadQuarryTicket(tile, ticket)
 *
 * Quarries and Fillers mine, clear and fill their area straight through the world, so they
 * work inside other players' claims. A full size Quarry also drops its own chunk from its chunk
 * ticket and stops, while its whole area stays loaded.
 *
 * usage: PatchBC <in.jar> <out.jar> <patch>[,<patch>...] <TLiteBC.class> <TLiteProtect.class>
 */
public class PatchBC {

    static final String HELPER = "TLiteBC";
    static final String BLOCK_QUARRY = "buildcraft/factory/BlockQuarry";
    static final String TILE_QUARRY = "buildcraft/factory/TileQuarry";
    static final String BLOCK_FILLER = "buildcraft/builders/BlockFiller";
    static final String TILE_FILLER = "buildcraft/builders/TileFiller";
    static final String PATTERN = "buildcraft/builders/FillerPattern";
    static final String FLATTENER = "buildcraft/builders/FillerFlattener";
    static final String BLOCK_UTIL = "buildcraft/core/utils/BlockUtil";
    static final String BUILDER = "buildcraft/core/blueprints/BptBuilderBase";
    static final String CALLBACK = "buildcraft/BuildCraftFactory$QuarryChunkloadCallback";
    static final String TICKET = "net/minecraftforge/common/ForgeChunkManager$Ticket";

    static final String PLACED = "(Lyc;IIILmd;)V";
    static final String ACTIVATED = "(Lyc;IIILqx;IFFF)Z";
    static final String ITEM_USE = "(Lur;Lqx;Lyc;IIIIFFF)Z";

    static boolean doQuarry, doFiller, doChunks;
    static final Map<String, Integer> hits = new LinkedHashMap<String, Integer>();

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: PatchBC <in.jar> <out.jar> <patches> <TLiteBC.class> <TLiteProtect.class>");
            System.err.println("patches: quarry, filler, quarrychunks");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("quarry")) doQuarry = true;
            else if (p.equals("filler")) doFiller = true;
            else if (p.equals("quarrychunks")) doChunks = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doQuarry && n.equals(BLOCK_QUARRY + ".class")) d = patchBlockQuarry(d);
            if (doQuarry && n.equals(TILE_QUARRY + ".class")) d = patchTileQuarry(d);
            if (doChunks && n.equals(TILE_QUARRY + ".class")) d = patchQuarryChunks(d);
            if (doChunks && n.equals(CALLBACK + ".class")) d = patchCallback(d);
            if (doFiller && n.equals(BLOCK_FILLER + ".class")) d = patchBlockFiller(d);
            if (doFiller && n.equals(TILE_FILLER + ".class")) d = patchTileFiller(d);
            if (doFiller && n.equals(PATTERN + ".class")) d = patchPattern(d);
            if (doFiller && n.equals(FLATTENER + ".class")) d = patchFlattener(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        Map<String, Integer> expected = new LinkedHashMap<String, Integer>();
        if (doQuarry) {
            expected.put("quarry placed", 1);
            expected.put("quarry nbt", 2);
            expected.put("quarry quarriable", 1);
            expected.put("quarry columns", 2);
            expected.put("quarry frame", 1);
        }
        if (doChunks) {
            expected.put("quarrychunks depth", 1);
            expected.put("quarrychunks keep", 1);
            expected.put("quarrychunks cast", 1);
            expected.put("quarrychunks callback", 1);
        }
        if (doFiller) {
            expected.put("filler placed", 1);
            expected.put("filler adopt", 1);
            expected.put("filler nbt", 2);
            expected.put("filler work", 1);
            expected.put("filler place", 2);
            expected.put("filler set", 1);
            expected.put("filler break", 1);
        }
        for (Map.Entry<String, Integer> en : expected.entrySet()) {
            Integer got = hits.get(en.getKey());
            if (got == null || !got.equals(en.getValue()))
                throw new IllegalStateException(en.getKey() + ": expected " + en.getValue() + " sites, patched " + got);
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

    // ------------------------------------------------------------------ Quarry

    /** onBlockPlacedBy a(yc, III, md): TLiteBC.placed(world, x, y, z, placer) before each return. */
    static byte[] patchBlockQuarry(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("a") || !m.desc.equals(PLACED)) continue;
            beforeReturns(m, placedCall());
            m.maxStack = Math.max(m.maxStack, 5);
            hit("quarry placed");
        }
        return write(cn);
    }

    static byte[] patchTileQuarry(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (nbt(m)) hit("quarry nbt");
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (!(i instanceof MethodInsnNode)) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (m.name.equals("positionReached") && mi.getOpcode() == Opcodes.INVOKESPECIAL
                        && mi.owner.equals(TILE_QUARRY) && mi.name.equals("isQuarriableBlock") && mi.desc.equals("(III)Z")) {
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "quarriable",
                            "(L" + TILE_QUARRY + ";III)Z", false));
                    hit("quarry quarriable");
                }
                if ((m.name.equals("findTarget") || m.name.equals("createColumnVisitList"))
                        && mi.getOpcode() == Opcodes.INVOKESTATIC && mi.owner.equals(BLOCK_UTIL)
                        && mi.name.equals("canChangeBlock") && mi.desc.equals("(Lyc;III)Z")) {
                    m.instructions.insertBefore(mi, new VarInsnNode(Opcodes.ALOAD, 0));
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "quarryCanChange",
                            "(Lyc;IIIL" + TILE_QUARRY + ";)Z", false));
                    m.maxStack += 1;
                    hit("quarry columns");
                }
                if (m.name.equals("buildFrame") && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && mi.owner.equals(BUILDER) && mi.name.equals("getNextBlock")) {
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "nextSlot",
                            "(L" + BUILDER + ";" + mi.desc.substring(1), false));
                    hit("quarry frame");
                }
            }
        }
        return write(cn);
    }

    /**
     * setBoundaries: the getMaxChunkListDepth call compared against the area estimate (the one
     * followed by IF_ICMPLT; the other feeds a chat message) gets [this, area from local 2] and
     * goes to the helper. forceChunkLoading: keepQuarryChunk before each return.
     */
    static byte[] patchQuarryChunks(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("setBoundaries") && m.desc.equals("(Z)V")) {
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                    MethodInsnNode mi = (MethodInsnNode) i;
                    if (!mi.owner.equals(TICKET) || !mi.name.equals("getMaxChunkListDepth")) continue;
                    AbstractInsnNode next = mi.getNext();
                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                    if (next == null || next.getOpcode() != Opcodes.IF_ICMPLT) continue;
                    InsnList extra = new InsnList();
                    extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    extra.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    m.instructions.insertBefore(mi, extra);
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "quarryChunkDepth",
                            "(L" + TICKET + ";L" + TILE_QUARRY + ";Lbuildcraft/api/core/IAreaProvider;)I", false));
                    m.maxStack += 2;
                    hit("quarrychunks depth");
                }
            }
            if (m.name.equals("forceChunkLoading") && m.desc.equals("(L" + TICKET + ";)V")) {
                InsnList c = new InsnList();
                c.add(new VarInsnNode(Opcodes.ALOAD, 0));
                c.add(new VarInsnNode(Opcodes.ALOAD, 1));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "keepQuarryChunk", "(L" + TILE_QUARRY + ";L" + TICKET + ";)V", false));
                beforeReturns(m, c);
                m.maxStack = Math.max(m.maxStack, 2);
                hit("quarrychunks keep");
            }
        }
        return write(cn);
    }

    /** ticketsLoaded(List, World): drop the TileQuarry cast and call the helper with the raw tile. */
    static byte[] patchCallback(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("ticketsLoaded") || !m.desc.equals("(Ljava/util/List;Lyc;)V")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() == Opcodes.CHECKCAST && ((TypeInsnNode) i).desc.equals(TILE_QUARRY)) {
                    m.instructions.remove(i);
                    hit("quarrychunks cast");
                }
                if (i.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) i).owner.equals(TILE_QUARRY)
                        && ((MethodInsnNode) i).name.equals("forceChunkLoading")) {
                    m.instructions.set(i, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "reloadQuarryTicket",
                            "(Ljava/lang/Object;L" + TICKET + ";)V", false));
                    hit("quarrychunks callback");
                }
            }
        }
        return write(cn);
    }

    // ------------------------------------------------------------------ Filler

    /** Adds onBlockPlacedBy (super, then placed) and puts adopt at the top of onBlockActivated. */
    static byte[] patchBlockFiller(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (m.name.equals("a") && m.desc.equals(PLACED))
                throw new IllegalStateException("filler: BlockFiller already has onBlockPlacedBy");
            if (m.name.equals("a") && m.desc.equals(ACTIVATED)) {
                InsnList g = new InsnList();
                g.add(new VarInsnNode(Opcodes.ALOAD, 1));
                g.add(new VarInsnNode(Opcodes.ILOAD, 2));
                g.add(new VarInsnNode(Opcodes.ILOAD, 3));
                g.add(new VarInsnNode(Opcodes.ILOAD, 4));
                g.add(new VarInsnNode(Opcodes.ALOAD, 5));
                g.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "adopt", "(Lyc;IIILqx;)V", false));
                m.instructions.insert(g);
                m.maxStack = Math.max(m.maxStack, 5);
                hit("filler adopt");
            }
        }
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "a", PLACED, null, null);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(placedCall());
        m.instructions.remove(m.instructions.getLast());         // placedCall ends with the helper call
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, cn.superName, "a", PLACED, false));
        m.instructions.add(placedCall());
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        m.maxStack = 6;
        m.maxLocals = 6;
        cn.methods.add(m);
        hit("filler placed");
        return write(cn);
    }

    static byte[] patchTileFiller(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (nbt(m)) hit("filler nbt");
            if (!m.name.equals("doWork")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.INVOKEINTERFACE) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (!mi.name.equals("iteratePattern")) continue;
                m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "iterateFiller",
                        "(L" + mi.owner + ";" + mi.desc.substring(1), false));
                hit("filler work");
            }
        }
        return write(cn);
    }

    static byte[] patchPattern(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (!(i instanceof MethodInsnNode)) continue;
                MethodInsnNode mi = (MethodInsnNode) i;
                if (m.name.equals("fill") && redirectItemUse(m, mi)) hit("filler place");
                if (m.name.equals("empty") && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && mi.owner.equals("yc") && mi.name.equals("e") && mi.desc.equals("(IIII)Z")) {
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "fillerSet", "(Lyc;IIII)Z", false));
                    hit("filler set");
                }
                if (m.name.equals("empty") && mi.getOpcode() == Opcodes.INVOKESTATIC
                        && mi.owner.equals(BLOCK_UTIL) && mi.name.equals("breakBlock") && mi.desc.equals("(Lyc;IIII)V")) {
                    m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "fillerBreak", "(Lyc;IIII)V", false));
                    hit("filler break");
                }
            }
        }
        return write(cn);
    }

    static byte[] patchFlattener(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("iteratePattern")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i instanceof MethodInsnNode && redirectItemUse(m, (MethodInsnNode) i)) hit("filler place");
            }
        }
        return write(cn);
    }

    /** up.a(ur, qx, yc, IIIIFFF) with [item, args...] on the stack -> TLiteBC.fillerUse(item, args...). */
    static boolean redirectItemUse(MethodNode m, MethodInsnNode mi) {
        if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL || !mi.owner.equals("up") || !mi.name.equals("a") || !mi.desc.equals(ITEM_USE))
            return false;
        m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "fillerUse", "(Lup;" + ITEM_USE.substring(1), false));
        return true;
    }

    // ------------------------------------------------------------------ shared

    /** [world, x, y, z, placer] from onBlockPlacedBy's arguments, then TLiteBC.placed. */
    static InsnList placedCall() {
        InsnList c = new InsnList();
        c.add(new VarInsnNode(Opcodes.ALOAD, 1));
        c.add(new VarInsnNode(Opcodes.ILOAD, 2));
        c.add(new VarInsnNode(Opcodes.ILOAD, 3));
        c.add(new VarInsnNode(Opcodes.ILOAD, 4));
        c.add(new VarInsnNode(Opcodes.ALOAD, 5));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "placed", PLACED, false));
        return c;
    }

    /** readFromNBT a(bq) and writeToNBT b(bq): TLiteBC.load/save(this, tag) before each return. */
    static boolean nbt(MethodNode m) {
        if (!m.desc.equals("(Lbq;)V") || !(m.name.equals("a") || m.name.equals("b"))) return false;
        InsnList c = new InsnList();
        c.add(new VarInsnNode(Opcodes.ALOAD, 0));
        c.add(new VarInsnNode(Opcodes.ALOAD, 1));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, m.name.equals("a") ? "load" : "save", "(Lany;Lbq;)V", false));
        beforeReturns(m, c);
        m.maxStack = Math.max(m.maxStack, 2);
        return true;
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
