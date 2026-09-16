#!/usr/bin/env bash
# Build every patched jar.
#
#   ./build.sh                 build all mods found at the default paths
#
# Override any path with an env var:
#   MODS  COREMODS  MCPC  MFR_SRC  EE3_SRC  AE_SRC  FZ_SRC  IC2_SRC  IMMIBIS_SRC  RPCORE_SRC  BC_SRC  CC_SRC  TE_SRC  COFH_SRC  IRONCHEST_SRC  LP_SRC  AP_SRC  NC_SRC  OT_SRC  WM_SRC  WR_SRC  SC_SRC  APM_SRC  MPS_SRC  MYST_SRC  TC_SRC  NEI_SRC  ASM  JAVAC8
#
# Helper classes are compiled against the server's mcpcplus.jar, which holds the whole
# obfuscated 1.4.7 game plus Forge and Bukkit, and against the mod jars they call into.
# Minecraft 1.4.7 has no runtime deobfuscation, so helpers name vanilla classes by their
# obfuscated names (ur = ItemStack, yc = World) and live in the default package, the only
# place Java source can see those classes from.
set -euo pipefail

MODS="${MODS:-$HOME/.local/share/PolyMC/instances/Tekkit Lite/.minecraft/mods}"
COREMODS="${COREMODS:-$HOME/.local/share/PolyMC/instances/Tekkit Lite/.minecraft/coremods}"
MCPC="${MCPC:-build/testserver/mcpcplus.jar}"
MFR_SRC="${MFR_SRC:-$MODS/MineFactoryReloaded-2.3.2-287.jar}"
EE3_SRC="${EE3_SRC:-$MODS/ee3-universal-pre1f.jar}"
AE_SRC="${AE_SRC:-$MODS/appeng-rv9-i.zip}"
FZ_SRC="${FZ_SRC:-$MODS/Factorization-0.7.21.jar}"
IC2_SRC="${IC2_SRC:-$MODS/industrialcraft-2_1.115.231-lf.jar}"
IMMIBIS_SRC="${IMMIBIS_SRC:-$MODS/immibis-core-52.4.6.jar}"
RPCORE_SRC="${RPCORE_SRC:-$MODS/RedPowerCore-2.0pr6.zip}"
RPMECH_SRC="${RPMECH_SRC:-$MODS/RedPowerMechanical-2.0pr6.zip}"
BC_SRC="${BC_SRC:-$MODS/buildcraft-A-3.4.3.jar}"
CC_SRC="${CC_SRC:-$MODS/ComputerCraft1.5.zip}"
TE_SRC="${TE_SRC:-$MODS/ThermalExpansion-2.2.2.2.zip}"
COFH_SRC="${COFH_SRC:-$MODS/CoFHCore-1.4.7.3.zip}"
IRONCHEST_SRC="${IRONCHEST_SRC:-$MODS/ironchest-universal-1.4.7-5.1.0.275.zip}"
LP_SRC="${LP_SRC:-$MODS/LogisticsPipes-MC1.4.7-0.7.0.96.jar}"
AP_SRC="${AP_SRC:-$MODS/AdditionalPipes2.1.3u42-BC3.4.2-MC1.4.7.jar}"
CHUNKS_SRC="${CHUNKS_SRC:-$MODS/ChickenChunks 1.3.1.0.jar}"
DA_SRC="${DA_SRC:-$MODS/dimensional-anchor-52.2.0.jar}"
NC_SRC="${NC_SRC:-$MODS/IC2NuclearControl-1.4.6.zip}"
OT_SRC="${OT_SRC:-$MODS/OmniTools-3.0.4.zip}"
WM_SRC="${WM_SRC:-$MODS/Weaponmod.zip}"
WR_SRC="${WR_SRC:-$MODS/WR-CBE Core 1.3.2.8.jar}"
SC_SRC="${SC_SRC:-$MODS/StevesCarts2.0.0.a62.zip}"
APM_SRC="${APM_SRC:-$MODS/AdvancedPowerManagement-1.1.55-IC2_1.112.jar}"
ARS_SRC="${ARS_SRC:-$MODS/adv-repulsion-systems-52.0.6.jar}"
MPS_SRC="${MPS_SRC:-$MODS/ModularPowersuits-0.3.2-199.jar}"
MYST_SRC="${MYST_SRC:-$MODS/mystcraft-uni-1.4.7-0.10.1.00.zip}"
PCC="${PCC:-$COREMODS/PowerCrystalsCore-1.0.3-34.jar}"
TC_SRC="${TC_SRC:-$COREMODS/[1.4.6]TreeCapitator.Forge.1.4.6.r07.Uni.CoreMod.jar}"
NEI_SRC="${NEI_SRC:-$COREMODS/NotEnoughItems 1.4.7.0.jar}"
CCC="${CCC:-$COREMODS/CodeChickenCore 0.7.3.jar}"
BSPKRS="${BSPKRS:-$MODS/[1.4.7]bspkrsCorev2.02.zip}"

ASM="${ASM:-$HOME/.local/share/PolyMC/libraries/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar}"
JAVAC8="${JAVAC8:-/usr/lib/jvm/java-8-openjdk/bin/javac}"

[ -f "$ASM" ]    || { echo "ASM not found at $ASM, set ASM=..." >&2; exit 1; }
[ -x "$JAVAC8" ] || { echo "Java 8 javac not found at $JAVAC8, set JAVAC8=..." >&2; exit 1; }
[ -f "$MCPC" ]   || { echo "mcpcplus.jar not found at $MCPC, copy it from the server or set MCPC=..." >&2; exit 1; }
for j in "$MFR_SRC" "$EE3_SRC" "$AE_SRC" "$FZ_SRC" "$PCC" "$IC2_SRC" "$IMMIBIS_SRC" "$RPCORE_SRC" "$BC_SRC" "$CC_SRC" "$TE_SRC" "$COFH_SRC" "$IRONCHEST_SRC" "$LP_SRC" "$AP_SRC" "$CHUNKS_SRC" "$DA_SRC" "$NC_SRC" "$OT_SRC" "$WM_SRC" "$WR_SRC" "$SC_SRC" "$APM_SRC" "$ARS_SRC" "$MPS_SRC" "$MYST_SRC" "$TC_SRC" "$NEI_SRC" "$CCC" "$BSPKRS"; do
  [ -f "$j" ] || { echo "not found: $j" >&2; exit 1; }
done

rm -rf build/cls build/tool
mkdir -p build/cls build/tool

# javac 8 warns that -source/-target 1.6 are obsolete. Filter only that noise -
# never redirect the whole stream, or a real compile error disappears and set -e
# kills the script with no explanation.
"$JAVAC8" -nowarn -source 1.6 -target 1.6 \
  -bootclasspath "$(dirname "$JAVAC8")/../jre/lib/rt.jar" \
  -cp "$MCPC:$MFR_SRC:$PCC:$EE3_SRC:$IC2_SRC:$IMMIBIS_SRC:$RPCORE_SRC:$BC_SRC:$CC_SRC:$TE_SRC:$COFH_SRC:$LP_SRC:$WM_SRC:$WR_SRC:$SC_SRC:$APM_SRC:$CCC:$TC_SRC:$BSPKRS:$NEI_SRC:$CCC:$CHUNKS_SRC:$NC_SRC" -d build/cls \
  src/TLiteProtect.java src/TLiteMFR.java src/TLiteEE3.java src/TLiteTreeCap.java src/TLiteNEI.java src/TLiteImmibis.java src/TLiteBC.java src/TLiteTurtle.java src/TLiteCC.java src/TLiteTE.java src/TLiteIronChest.java src/TLiteLP.java src/TLiteNC.java src/TLiteWM.java src/TLiteWR.java src/TLiteSC.java src/TLiteAPM.java src/TLiteMPS.java src/TLiteAP.java src/TLiteChunkQuota.java src/TLiteARS.java src/TLiteARSDrops.java 2>&1 \
  | grep -vE 'bootstrap class path|source value 1\.6|target value 1\.6|options|unchecked' || true

for f in build/cls/TLiteProtect.class build/cls/TLiteMFR.class build/cls/TLiteEE3.class \
         build/cls/TLiteTreeCap.class build/cls/TLiteNEI.class build/cls/TLiteImmibis.class build/cls/TLiteBC.class build/cls/TLiteTurtle.class build/cls/TLiteCC.class build/cls/TLiteTE.class build/cls/TLiteIronChest.class build/cls/TLiteLP.class build/cls/TLiteNC.class build/cls/TLiteWM.class build/cls/TLiteWR.class build/cls/TLiteSC.class build/cls/TLiteAPM.class build/cls/TLiteMPS.class build/cls/TLiteAP.class build/cls/TLiteChunkQuota.class build/cls/TLiteARS.class build/cls/TLiteARSDrops.class; do
  [ -f "$f" ] || { echo "helper class missing after compile: $f" >&2; exit 1; }
done

# <label> <src jar> <out jar> <patcher.java> <patch list> <helper class...>
patch_one() {
  local label="$1" src="$2" out="$3" tool="$4" patches="$5"
  shift 5
  javac -nowarn -cp "$ASM" -d build/tool "$tool"
  java -cp "$ASM:build/tool" "${tool%.java}" "$src" "$out" "$patches" "$@"
}

patch_one "MineFactoryReloaded" "$MFR_SRC" "MineFactoryReloaded-2.3.2-287-patched.jar" \
          PatchMFR.java "unifierdupe,dsudupe,packets" \
          build/cls/TLiteMFR.class build/cls/TLiteProtect.class

patch_one "EE3" "$EE3_SRC" "ee3-universal-pre1f-patched.jar" \
          PatchEE3.java "requestcheck,protect" \
          build/cls/TLiteEE3.class build/cls/TLiteProtect.class

patch_one "Applied Energistics" "$AE_SRC" "appeng-rv9-i-patched.zip" \
          PatchAE.java "entropy,catalyst,monitor" \
          build/cls/TLiteProtect.class

patch_one "Factorization" "$FZ_SRC" "Factorization-0.7.21-patched.jar" \
          PatchFZ.java "wrathigniter" \
          build/cls/TLiteProtect.class

patch_one "immibis-core" "$IMMIBIS_SRC" "immibis-core-52.4.6-patched.jar" \
          PatchImmibis.java "mergenbt" \
          build/cls/TLiteImmibis.class

patch_one "BuildCraft" "$BC_SRC" "buildcraft-A-3.4.3-patched.jar" \
          PatchBC.java "quarry,filler,quarrychunks" \
          build/cls/TLiteBC.class build/cls/TLiteProtect.class

patch_one "ComputerCraft" "$CC_SRC" "ComputerCraft1.5-patched.zip" \
          PatchCC.java "turtle,packets,http" \
          build/cls/TLiteTurtle.class build/cls/TLiteProtect.class build/cls/TLiteCC.class

patch_one "ThermalExpansion" "$TE_SRC" "ThermalExpansion-2.2.2.2-patched.zip" \
          PatchTE.java "packets" \
          build/cls/TLiteTE.class

patch_one "IronChest" "$IRONCHEST_SRC" "ironchest-universal-1.4.7-5.1.0.275-patched.zip" \
          PatchIronChest.java "crystalcap" \
          build/cls/TLiteIronChest.class

patch_one "LogisticsPipes" "$LP_SRC" "LogisticsPipes-MC1.4.7-0.7.0.96-patched.jar" \
          PatchLP.java "diskdupe,requestclamp,security" \
          build/cls/TLiteLP.class

patch_one "AdditionalPipes" "$AP_SRC" "AdditionalPipes2.1.3u42-BC3.4.2-MC1.4.7-patched.jar" \
          PatchAP.java "teleowner,apchunkgate" \
          build/cls/TLiteAP.class

# ChunkLoaderConversion: force chunk loaders to a single chunk (spot loaders). Each mod's own
# per-player chunk quota then acts as a per-player spot-loader count (ChickenChunks.cfg players{},
# immibis.cfg chunkloader.quotaType=perplayer + maxChunksPerPlayer).
patch_one "ChickenChunks" "$CHUNKS_SRC" "ChickenChunks 1.3.1.0-patched.jar" \
          PatchChickenChunks.java "spotloader,combinedquota" \
          build/cls/TLiteChunkQuota.class 'build/cls/TLiteChunkQuota$Loader.class'

patch_one "Dimensional Anchor" "$DA_SRC" "dimensional-anchor-52.2.0-patched.jar" \
          PatchDA.java "spotloader,combinedquota" \
          build/cls/TLiteChunkQuota.class 'build/cls/TLiteChunkQuota$Loader.class'

patch_one "IC2NuclearControl" "$NC_SRC" "IC2NuclearControl-1.4.6-patched.zip" \
          PatchNC.java "packets,cardcap" \
          build/cls/TLiteNC.class

patch_one "OmniTools" "$OT_SRC" "OmniTools-3.0.4-patched.zip" \
          PatchOmniTools.java "wrench" \
          build/cls/TLiteProtect.class

patch_one "Weaponmod" "$WM_SRC" "Weaponmod-patched.zip" \
          PatchWM.java "dynamite" \
          build/cls/TLiteWM.class build/cls/TLiteProtect.class

patch_one "WR-CBE Core" "$WR_SRC" "WR-CBE Core 1.3.2.8-patched.jar" \
          PatchWR.java "freq" \
          build/cls/TLiteWR.class

patch_one "Steve's Carts" "$SC_SRC" "StevesCarts2.0.0.a62-patched.zip" \
          PatchSC.java "carts" \
          build/cls/TLiteSC.class build/cls/TLiteProtect.class

patch_one "AdvancedPowerManagement" "$APM_SRC" "AdvancedPowerManagement-1.1.55-IC2_1.112-patched.jar" \
          PatchAPM.java "guibutton,outputdupe" \
          build/cls/TLiteAPM.class

patch_one "AdvancedRepulsionSystems" "$ARS_SRC" "adv-repulsion-systems-52.0.6-patched.jar" \
          PatchARS.java "tesla" \
          build/cls/TLiteARS.class build/cls/TLiteARSDrops.class

patch_one "ModularPowersuits" "$MPS_SRC" "ModularPowersuits-0.3.2-199-patched.jar" \
          PatchMPS.java "tweak" \
          build/cls/TLiteMPS.class

patch_one "Mystcraft" "$MYST_SRC" "mystcraft-uni-1.4.7-0.10.1.00-patched.zip" \
          PatchMyst.java "linknull"

# Coremods. Server side like the rest: they go in the server's coremods/ folder.
patch_one "TreeCapitator" "$TC_SRC" "[1.4.6]TreeCapitator.Forge.1.4.6.r07.Uni.CoreMod-patched.jar" \
          PatchTC.java "felling" \
          build/cls/TLiteTreeCap.class build/cls/TLiteProtect.class

patch_one "NotEnoughItems" "$NEI_SRC" "NotEnoughItems 1.4.7.0-patched.jar" \
          PatchNEI.java "spawner,creative" \
          build/cls/TLiteNEI.class build/cls/TLiteProtect.class

# Coremod for the signed jars (IndustrialCraft 2, RedPower Core). Changing a class in a signed jar
# breaks the mod, so these fixes are applied by a class transformer as the classes load. Built
# against the server's own ASM 4.0, and checked against the stock jars before it is packaged.
ASM4="${ASM4:-$(dirname "$MCPC")/lib/asm-all-4.0.jar}"
[ -f "$ASM4" ] || { echo "ASM 4.0 not found at $ASM4, set ASM4=..." >&2; exit 1; }
rm -rf build/coremod
mkdir -p build/coremod
"$JAVAC8" -nowarn -source 1.6 -target 1.6 \
  -bootclasspath "$(dirname "$JAVAC8")/../jre/lib/rt.jar" \
  -cp "$MCPC:$ASM4:$IC2_SRC:$RPCORE_SRC" -d build/coremod \
  coremod/tlitefixes/*.java src/TLiteProtect.java src/TLiteIC2.java src/TLiteRP.java src/TLiteRPMachine.java 2>&1 \
  | grep -vE 'bootstrap class path|source value 1\.6|target value 1\.6|options|unchecked' || true
java -cp "$ASM4:$MCPC:build/coremod" tlitefixes.TLiteTransformer "$IC2_SRC" "$RPCORE_SRC" "$RPMECH_SRC"
printf 'Manifest-Version: 1.0\nFMLCorePlugin: tlitefixes.TLiteCorePlugin\n' > build/coremod.mf
rm -f TLiteFixes-coremod.jar
"$(dirname "$JAVAC8")/jar" cfm TLiteFixes-coremod.jar build/coremod.mf -C build/coremod .
echo "OK  wrote TLiteFixes-coremod.jar"

# Bukkit plugin: TekkitLiteCustomizer, the item ban plugin, without its old Block Breaker next to
# Deep Storage Unit placement ban (dsudupe fixes that inside MFR).
rm -rf build/customizer
mkdir -p build/customizer
"$JAVAC8" -nowarn -source 1.6 -target 1.6 \
  -bootclasspath "$(dirname "$JAVAC8")/../jre/lib/rt.jar" \
  -cp "$MCPC" -d build/customizer \
  plugins/TekkitLiteCustomizer/src/me/ryanhamshire/TekkitCustomizer/*.java 2>&1 \
  | grep -vE 'bootstrap class path|source value 1\.6|target value 1\.6|options|unchecked|deprecat|^Note:' || true
[ -f build/customizer/me/ryanhamshire/TekkitCustomizer/TekkitCustomizer.class ] \
  || { echo "TekkitLiteCustomizer failed to compile" >&2; exit 1; }
cp plugins/TekkitLiteCustomizer/plugin.yml build/customizer/
rm -f TekkitLiteCustomizer.jar
(cd build/customizer && "$(dirname "$JAVAC8")/jar" cf ../../TekkitLiteCustomizer.jar .)
echo "OK  wrote TekkitLiteCustomizer.jar"
