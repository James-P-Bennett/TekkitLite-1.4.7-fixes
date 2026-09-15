import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * ChunkLoaderConversion: immibis Dimensional Anchors 52.2.0 patch (part of ChunkLoaderConversion).
 *
 *   spotloader  TileChunkLoader.limitRadius()
 *               clamps the anchor radius to 0 at the top of the method, so a Dimensional Anchor
 *               (block 4090) loads only its own chunk.
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
 * usage: PatchDA <in.jar> <out.jar> <patch>[,<patch>...]
 */
public class PatchDA {

    static final String TILE = "immibis/chunkloader/TileChunkLoader";

    static boolean doSpot;
    static int spotHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PatchDA <in.jar> <out.jar> <patches>");
            System.err.println("patches: spotloader");
            System.exit(2);
        }
        for (String p : args[2].split(",")) {
            p = p.trim();
            if (p.equals("spotloader")) doSpot = true;
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
            out.put(n, d);
        }
        zf.close();
        for (int i = 3; i < args.length; i++) {
            File f = new File(args[i]);
            out.put(f.getName(), readAll(new FileInputStream(f)));
        }

        if (doSpot && spotHits != 1)
            throw new IllegalStateException("spotloader: expected to patch 1 limitRadius, patched " + spotHits);

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
