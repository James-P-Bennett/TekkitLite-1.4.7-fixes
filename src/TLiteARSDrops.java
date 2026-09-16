import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

/**
 * Forge event handler registered by {@link TLiteARS} the first time an Industrial Tesla Coil
 * fires. When the industrialTeslaCoil.denyMobDrops flag is on, it cancels the drops of anything
 * killed by that coil, matched by identity against the coil's own damage source so no other kill
 * is affected.
 *
 * lh = DamageSource. Forge event classes keep their real names, so source/setCanceled are direct.
 */
public class TLiteARSDrops {

    /** The Industrial Tesla Coil's damage source, captured by TLiteARS.shock. */
    public static lh teslaSource;

    @ForgeSubscribe
    public void onLivingDrops(LivingDropsEvent event) {
        if (teslaSource != null && event.source == teslaSource && TLiteARS.denyMobDrops()) {
            event.setCanceled(true);
        }
    }
}
