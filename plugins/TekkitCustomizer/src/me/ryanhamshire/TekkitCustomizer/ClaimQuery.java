package me.ryanhamshire.TekkitCustomizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.bukkit.Location;

/**
 * Soft, reflective bridge to GriefPrevention, added by TekkitLite-1.4.7-fixes.
 *
 * Answers "is this location inside a claim that currently allows explosions?" without giving the
 * plugin a compile-time dependency on GriefPrevention, and failing safe (returns false) when
 * GriefPrevention is absent or its shape has changed. Kept reflective for the same reason
 * LoaderRegistry is: the plugin is built against the server jar alone.
 *
 * MethodHandles are used rather than Class.getField/getMethod on purpose: GriefPrevention has a
 * Vault net.milkbowl.vault.economy.Economy field, and enumerating its members (as getField does)
 * forces that class to load, which throws on a server without Vault (this one). A MethodHandles
 * lookup resolves each named member on its own, so it never touches the Economy field. The calls
 * go through invokeWithArguments (not the signature-polymorphic invoke) so the plugin still
 * compiles at Java 6 source level while running on the server's Java 7.
 *
 * The targets match GriefPrevention 7.6.2: the public static GriefPrevention.instance, its public
 * DataStore dataStore, DataStore.getClaimAt(Location, boolean, Claim), and the public
 * Claim.areExplosivesAllowed flag the owner toggles with /claimexplosions (off by default).
 */
class ClaimQuery
{
    private static boolean initialized;
    private static Object dataStore;
    private static MethodHandle getClaimAt;         // (DataStore, Location, boolean, Claim) -> Claim
    private static MethodHandle areExplosivesAllowed; // (Claim) -> boolean

    /** True only when loc is inside a claim whose owner has enabled explosives. */
    static synchronized boolean explosionsAllowedAt(Location loc)
    {
        try
        {
            if (!initialized)
            {
                init();
            }
            if (dataStore == null || getClaimAt == null || areExplosivesAllowed == null)
            {
                return false;
            }
            Object claim = getClaimAt.invokeWithArguments(dataStore, loc, Boolean.TRUE, null);
            if (claim == null)
            {
                return false;
            }
            return ((Boolean) areExplosivesAllowed.invokeWithArguments(claim)).booleanValue();
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    private static void init()
    {
        initialized = true;
        try
        {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> gp = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Class<?> dataStoreClass = Class.forName("me.ryanhamshire.GriefPrevention.DataStore");
            Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");

            Object instance = lookup.findStaticGetter(gp, "instance", gp).invokeWithArguments();
            dataStore = lookup.findGetter(gp, "dataStore", dataStoreClass).invokeWithArguments(instance);
            getClaimAt = lookup.findVirtual(dataStoreClass, "getClaimAt",
                    MethodType.methodType(claimClass, Location.class, boolean.class, claimClass));
            areExplosivesAllowed = lookup.findGetter(claimClass, "areExplosivesAllowed", boolean.class);
        }
        catch (Throwable t)
        {
            dataStore = null;
        }
    }
}
