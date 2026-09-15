#!/usr/bin/env bash
# Build every patched jar.
#
#   ./build.sh                 build all mods found at the default paths
#
# Override any path with an env var:
#   MODS  COREMODS  MCPC  MFR_SRC  EE3_SRC  AE_SRC  FZ_SRC  TC_SRC  NEI_SRC  ASM  JAVAC8
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
for j in "$MFR_SRC" "$EE3_SRC" "$AE_SRC" "$FZ_SRC" "$PCC" "$TC_SRC" "$NEI_SRC" "$CCC" "$BSPKRS"; do
  [ -f "$j" ] || { echo "not found: $j" >&2; exit 1; }
done

rm -rf build/cls build/tool
mkdir -p build/cls build/tool

# javac 8 warns that -source/-target 1.6 are obsolete. Filter only that noise -
# never redirect the whole stream, or a real compile error disappears and set -e
# kills the script with no explanation.
"$JAVAC8" -nowarn -source 1.6 -target 1.6 \
  -bootclasspath "$(dirname "$JAVAC8")/../jre/lib/rt.jar" \
  -cp "$MCPC:$MFR_SRC:$PCC:$EE3_SRC:$TC_SRC:$BSPKRS:$NEI_SRC:$CCC" -d build/cls \
  src/TLiteProtect.java src/TLiteMFR.java src/TLiteEE3.java src/TLiteTreeCap.java src/TLiteNEI.java 2>&1 \
  | grep -vE 'bootstrap class path|source value 1\.6|target value 1\.6|options|unchecked' || true

for f in build/cls/TLiteProtect.class build/cls/TLiteMFR.class build/cls/TLiteEE3.class \
         build/cls/TLiteTreeCap.class build/cls/TLiteNEI.class; do
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
          PatchMFR.java "unifierdupe,dsudupe" \
          build/cls/TLiteMFR.class

patch_one "EE3" "$EE3_SRC" "ee3-universal-pre1f-patched.jar" \
          PatchEE3.java "requestcheck,protect" \
          build/cls/TLiteEE3.class build/cls/TLiteProtect.class

patch_one "Applied Energistics" "$AE_SRC" "appeng-rv9-i-patched.zip" \
          PatchAE.java "entropy,catalyst,monitor" \
          build/cls/TLiteProtect.class

patch_one "Factorization" "$FZ_SRC" "Factorization-0.7.21-patched.jar" \
          PatchFZ.java "wrathigniter" \
          build/cls/TLiteProtect.class

# Coremods. Server side like the rest: they go in the server's coremods/ folder.
patch_one "TreeCapitator" "$TC_SRC" "[1.4.6]TreeCapitator.Forge.1.4.6.r07.Uni.CoreMod-patched.jar" \
          PatchTC.java "felling" \
          build/cls/TLiteTreeCap.class build/cls/TLiteProtect.class

patch_one "NotEnoughItems" "$NEI_SRC" "NotEnoughItems 1.4.7.0-patched.jar" \
          PatchNEI.java "spawner,creative" \
          build/cls/TLiteNEI.class build/cls/TLiteProtect.class
