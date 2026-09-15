package tlitefixes;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * FML coremod entry point for TekkitLite-1.4.7-fixes, named by FMLCorePlugin in the jar manifest.
 *
 * Some mods ship signed jars (IndustrialCraft 2, RedPower). Changing a class inside a signed
 * jar breaks it: a changed class fails its digest, and a stripped jar makes Java refuse the rest
 * of that mod family, whose classes share a package with it under the original signature. So
 * those fixes are applied here, as the classes load, and the signed jars stay untouched.
 */
public class TLiteCorePlugin implements IFMLLoadingPlugin {

    public String[] getLibraryRequestClass() {
        return null;
    }

    public String[] getASMTransformerClass() {
        return new String[] { "tlitefixes.TLiteTransformer" };
    }

    public String getModContainerClass() {
        return null;
    }

    public String getSetupClass() {
        return null;
    }

    public void injectData(Map data) {
    }
}
