import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * ChunkLoaderConversion: ChickenChunks 1.3.1.0 patch (part of ChunkLoaderConversion).
 *
 *   spotloader  TileChunkLoader.getChunks()
 *               forces the radius passed to getContainedChunks to 0, so the adjustable Chunk
 *               Loader (block 2048, metadata 0) loads only its own chunk, like the Spot Loader.
 *
 * A freshly placed Chunk Loader activates at radius 2 and its GUI can grow it further, up to
 * maxchunks (400). getChunks() is the one method that returns the chunks a loader keeps open, and
 * ChickenChunks both loads and quota-counts through it, so pinning the radius it uses turns every
 * Chunk Loader into a single-chunk spot loader. The per-player chunk limit (ChickenChunks.cfg
 * players{}) then acts as a per-player spot-loader count, and the stored radius is left untouched
 * so nothing else has to change. Chunk loaders saved before the patch also load one chunk, because
 * getChunks() reads the (now ignored) radius on every activation.
 *
 * getChunks() passes the radius to getContainedChunks, which loads getLoadedChunks(cx, cz, radius
 * - 1): a square of side 2*(radius-1)+1. So radius 1 is the single centre chunk, and that is the
 * value forced in here (not 0, which would subtract to -1 and load nothing).
 *
 * usage: PatchChickenChunks <in.jar> <out.jar> <patch>[,<patch>...]
 */
public class PatchChickenChunks {

    static final String TILE = "codechicken/chunkloader/TileChunkLoader";

    static boolean doSpot;
    static int spotHits;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PatchChickenChunks <in.jar> <out.jar> <patches>");
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
            throw new IllegalStateException("spotloader: expected 1 radius read in getChunks, patched " + spotHits);

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
     * In getChunks(), the fourth argument to getContainedChunks is the loader radius, pushed as
     * {@code aload_0; getfield radius:I}. Replace that read with a constant 1, which
     * getContainedChunks turns into getLoadedChunks radius 0, the single centre chunk. The stored
     * field is left alone.
     */
    static byte[] patchSpot(byte[] in) {
        ClassNode cn = read(in);
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            if (!m.name.equals("getChunks") || !m.desc.equals("()Ljava/util/HashSet;")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i.getOpcode() != Opcodes.GETFIELD) continue;
                FieldInsnNode fi = (FieldInsnNode) i;
                if (!fi.owner.equals(TILE) || !fi.name.equals("radius")) continue;
                AbstractInsnNode prev = fi.getPrevious();
                if (prev != null && prev.getOpcode() == Opcodes.ALOAD) {
                    m.instructions.remove(prev);
                }
                m.instructions.set(fi, new InsnNode(Opcodes.ICONST_1));
                spotHits++;
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
