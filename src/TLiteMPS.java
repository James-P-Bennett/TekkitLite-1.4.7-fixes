/**
 * Crash fix injected by TekkitLite-1.4.7-fixes into Modular Powersuits 0.7 (PatchMPS).
 *
 * The tinker "tweak" packet wrote a client-named key into the module's NBT as a double. Sending
 * a reserved key such as "Active" (which the module tick reads as a boolean) made every tick on
 * the server throw a ClassCastException. The write now rejects the reserved keys.
 *
 * bq = NBTTagCompound; bq.a(String, double) = setDouble.
 */
public class TLiteMPS {

    /** Replaces the module NBT setDouble in MusePacketTweakRequest.handleServer. */
    public static void tweak(bq tag, String name, double value) {
        if (tag == null || name == null
                || name.equals("Active") || name.equals("Online") || name.equals("Mode")) {
            return;
        }
        tag.a(name, value);
    }
}
