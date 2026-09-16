import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * ChunkLoaderConversion: immibis Dimensional Anchors 52.2.0 patch (part of ChunkLoaderConversion).
 *
 *   spotloader    TileChunkLoader.limitRadius()
 *                 clamps the anchor radius to 0 at the top of the method, so a Dimensional Anchor
 *                 (block 4090) loads only its own chunk.
 *
 *   combinedquota WorldInfo.addLoader / removeLoader / delayRemoveLoader
 *                 routes every register and unregister through TLiteChunkQuota so a player's
 *                 anchors and ChickenChunks loaders share one per-player chunk cap.
 *
 * limitRadius() runs on every activation: on placement, on world load (validate -> setActive), and
 * after any GUI change (loaderChanged -> setActive). Pinning radius to 0 there makes every anchor a
 * single-chunk spot loader, and anchors saved with a larger radius shrink to one chunk the next
 * time they activate. The clamp is placed before the method's own quota logic so it applies whether
 * the quota type is unlimited or perplayer; with perplayer set, each single-chunk anchor then
 * counts as one against the player's maxChunksPerPlayer, making that a per-player anchor count. The
 * existing radius = -1 (no owner, inactive) case is left alone, since the clamp only lowers a
 * positive radius.
 *
 * usage: PatchDA <in.jar> <out.jar> <patch>[,<patch>...] [<TLiteChunkQuota.class>]
 */
public class PatchDA {

    static final String TILE = "immibis/chunkloader/TileChunkLoader";
    static final String WORLDINFO = "immibis/chunkloader/WorldInfo";
    static final String BLOCK = "immibis/chunkloader/BlockChunkLoader";
    static final String TILEDESC = "(L" + TILE + ";)V";
    static final String QUOTA = "TLiteChunkQuota";

    static boolean doSpot;
    static int spotHits;
    static boolean doQuota;
    static int claimHits, releaseHits, announceHits, tickHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PatchDA <in.jar> <out.jar> <patches> [TLiteChunkQuota.class]");
            System.err.println("patches: spotloader, combinedquota");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("spotloader")) doSpot = true;
            else if (p.equals("combinedquota")) doQuota = true;
            else throw new IllegalArgumentException("unknown patch: " + p);
        }

        LinkedHashMap<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipFile zf = new ZipFile(args[0]);
        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            if (ze.isDirectory()) { out.put(ze.getName(), null); continue; }
            byte[] d = readAll(zf.getInputStream(ze));
            String n = ze.getName();
            if (doSpot && n.equals(TILE + ".class")) d = patchSpot(d);
            if (doQuota && n.equals(TILE + ".class")) d = patchTick(d);
            if (doQuota && n.equals(WORLDINFO + ".class")) d = patchQuota(d);
            if (doQuota && n.equals(BLOCK + ".class")) d = patchAnnounce(d);
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doSpot && spotHits != 1)
            throw new IllegalStateException("spotloader: expected to patch 1 limitRadius, patched " + spotHits);
        if (doQuota && (claimHits != 1 || releaseHits != 2 || announceHits != 1 || tickHits != 1))
            throw new IllegalStateException("combinedquota: expected 1 addLoader, 2 releases, 1 announce, 1 tick, patched "
                    + claimHits + ", " + releaseHits + ", " + announceHits + ", " + tickHits);

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
     * Prepends {@code if (this.radius > 0) this.radius = 0;} to limitRadius(), so no anchor ever
     * loads more than its own chunk.
     */
    static byte[] patchSpot(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("limitRadius") || !m.desc.equals("()V")) continue;
            InsnList pre = new InsnList();
            LabelNode done = new LabelNode();
            pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
            pre.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, "radius", "I"));
            pre.add(new JumpInsnNode(Opcodes.IFLE, done)); // radius <= 0: leave it
            pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
            pre.add(new InsnNode(Opcodes.ICONST_0));
            pre.add(new FieldInsnNode(Opcodes.PUTFIELD, TILE, "radius", "I"));
            pre.add(done);
            m.instructions.insert(pre);
            spotHits++;
        }
        return write(cn);
    }

    /**
     * In WorldInfo: addLoader(tile) returns before the real add when TLiteChunkQuota.daClaim(tile)
     * is false (owner at the combined limit); removeLoader(tile) and delayRemoveLoader(tile) each
     * call TLiteChunkQuota.daRelease(tile) first. The private removeLoader(LoaderInfo) overload has
     * a different descriptor and is left alone.
     */
    static byte[] patchQuota(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.desc.equals(TILEDESC)) continue;
            if (m.name.equals("addLoader")) {
                InsnList pre = new InsnList();
                LabelNode cont = new LabelNode();
                pre.add(new VarInsnNode(Opcodes.ALOAD, 1));
                pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, QUOTA, "daClaim", "(Ljava/lang/Object;)Z"));
                pre.add(new JumpInsnNode(Opcodes.IFNE, cont));
                pre.add(new InsnNode(Opcodes.RETURN));
                pre.add(cont);
                m.instructions.insert(pre);
                claimHits++;
            } else if (m.name.equals("removeLoader") || m.name.equals("delayRemoveLoader")) {
                InsnList pre = new InsnList();
                pre.add(new VarInsnNode(Opcodes.ALOAD, 1));
                pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, QUOTA, "daRelease", "(Ljava/lang/Object;)V"));
                m.instructions.insert(pre);
                releaseHits++;
            }
        }
        return write(cn);
    }

    /**
     * BlockChunkLoader.a(yc,int,int,int,md) (onBlockPlacedBy) sets the anchor's owner. Before it
     * returns, re-fetch the tile with world.getBlockTileEntity(x,y,z) and call
     * TLiteChunkQuota.daAnnounce so the placer is told their count or that it was disabled at the
     * limit. The method is server-only in the mod, so this only messages real placements.
     */
    static byte[] patchAnnounce(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("a") || !m.desc.equals("(Lyc;IIILmd;)V")) continue;
            AbstractInsnNode last = null;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() == Opcodes.RETURN) last = i;
            }
            if (last == null) continue;
            InsnList c = new InsnList();
            c.add(new VarInsnNode(Opcodes.ALOAD, 1));
            c.add(new VarInsnNode(Opcodes.ILOAD, 2));
            c.add(new VarInsnNode(Opcodes.ILOAD, 3));
            c.add(new VarInsnNode(Opcodes.ILOAD, 4));
            c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "yc", "q", "(III)Lany;", false));
            c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, QUOTA, "daAnnounce", "(Ljava/lang/Object;)V", false));
            m.instructions.insertBefore(last, c);
            m.maxStack = Math.max(m.maxStack, 4);
            announceHits++;
        }
        return write(cn);
    }

    /**
     * At the top of the anchor's updateEntity (obf g()), call TLiteChunkQuota.daTick(this), which
     * drives setActive from the owner's online/grace state each tick (server-side, reflective). The
     * anchor otherwise never re-checks activation once loaded when fuel is off, so it would never
     * shut down on logout or come back on login without this.
     */
    static byte[] patchTick(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("g") || !m.desc.equals("()V")) continue;
            InsnList c = new InsnList();
            c.add(new VarInsnNode(Opcodes.ALOAD, 0));
            c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, QUOTA, "daTick", "(Ljava/lang/Object;)V", false));
            m.instructions.insert(c);
            m.maxStack = Math.max(m.maxStack, 1);
            tickHits++;
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
