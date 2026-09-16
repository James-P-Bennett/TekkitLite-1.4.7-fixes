package me.ryanhamshire.TekkitCustomizer;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Bridge to the chunk-loader registry the loader-mod patches maintain (the default-package class
 * TLiteChunkQuota, bundled into the ChickenChunks and Dimensional Anchors jars). A class in a named
 * package cannot reference a default-package class directly, so it is reached by reflection. The
 * class lives on the parent class loader, so it resolves when the loader mods are installed and is
 * simply absent otherwise; every call fails soft (empty/zero/null) so the command works either way.
 */
final class LoaderRegistry {

    private static Class<?> cls;
    private static boolean looked;

    private static synchronized Class<?> quota() {
        if (!looked) {
            looked = true;
            try {
                cls = Class.forName("TLiteChunkQuota");
            } catch (Throwable t) {
                cls = null;
            }
        }
        return cls;
    }

    /** Lines like "world 12,64,-88 status: on", or null if loader tracking is unavailable. */
    @SuppressWarnings("unchecked")
    static List<String> describe(String owner) {
        Class<?> q = quota();
        if (q == null) return null;
        try {
            Method m = q.getMethod("describe", String.class);
            return (List<String>) m.invoke(null, owner);
        } catch (Throwable t) {
            return null;
        }
    }

    static int activeCount(String owner) {
        return intCall("activeCount", owner);
    }

    static int disabledCount(String owner) {
        return intCall("disabledCount", owner);
    }

    static int limit(String owner) {
        return intCall("limitFor", owner);
    }

    private static int intCall(String method, String owner) {
        Class<?> q = quota();
        if (q == null) return 0;
        try {
            Method m = q.getMethod(method, String.class);
            Object r = m.invoke(null, owner);
            return (r instanceof Integer) ? ((Integer) r).intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
