#!/usr/bin/env bash
# Run TLFixTest scenarios on the local test server against stock or patched jars.
#
#   test/run.sh stock   unifier [scenario...]
#   test/run.sh patched unifier [scenario...]
#
# Needs build/testserver (a copy of the server without its world), build/jdk7 (MCPC+ 1.4.7
# refuses Java 8) and build/TLFixTest.jar. The patched jars are taken from the repo root.
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:?usage: test/run.sh <stock|patched> <scenario...>}"; shift
[ $# -gt 0 ] || { echo "no scenarios given" >&2; exit 2; }
STOCK="${STOCK:-/run/media/james/Intel 660p Series M.2 2280/WindowsOS/James/Documents/CCTLiteServer/mods}"
STOCK_CORE="${STOCK_CORE:-$STOCK/../coremods}"
SRV=build/testserver
JAVA7="${JAVA7:-build/jdk7/bin/java}"

# <stock jar name> <patched jar in repo root>
JARS=(
  "MineFactoryReloaded-2.3.2-287.jar|MineFactoryReloaded-2.3.2-287-patched.jar"
  "ee3-universal-pre1f.jar|ee3-universal-pre1f-patched.jar"
  "appeng-rv9-i.zip|appeng-rv9-i-patched.zip"
  "Factorization-0.7.21.jar|Factorization-0.7.21-patched.jar"
  "immibis-core-52.4.6.jar|immibis-core-52.4.6-patched.jar"
  "buildcraft-A-3.4.3.jar|buildcraft-A-3.4.3-patched.jar"
  "ThermalExpansion-2.2.2.2.zip|ThermalExpansion-2.2.2.2-patched.zip"
  "ironchest-universal-1.4.7-5.1.0.275.zip|ironchest-universal-1.4.7-5.1.0.275-patched.zip"
  "LogisticsPipes-MC1.4.7-0.7.0.96.jar|LogisticsPipes-MC1.4.7-0.7.0.96-patched.jar"
  "AdditionalPipes2.1.3u42-BC3.4.2-MC1.4.7.jar|AdditionalPipes2.1.3u42-BC3.4.2-MC1.4.7-patched.jar"
  "IC2NuclearControl-1.4.6.zip|IC2NuclearControl-1.4.6-patched.zip"
  "OmniTools-3.0.4.zip|OmniTools-3.0.4-patched.zip"
  "Weaponmod.zip|Weaponmod-patched.zip"
  "WR-CBE Core 1.3.2.8.jar|WR-CBE Core 1.3.2.8-patched.jar"
  "StevesCarts2.0.0.a62.zip|StevesCarts2.0.0.a62-patched.zip"
  "AdvancedPowerManagement-1.1.55-IC2_1.112.jar|AdvancedPowerManagement-1.1.55-IC2_1.112-patched.jar"
  "ModularPowersuits-0.3.2-199.jar|ModularPowersuits-0.3.2-199-patched.jar"
  "mystcraft-uni-1.4.7-0.10.1.00.zip|mystcraft-uni-1.4.7-0.10.1.00-patched.zip"
  "ComputerCraft1.5.zip|ComputerCraft1.5-patched.zip"
)

COREJARS=(
  "[1.4.6]TreeCapitator.Forge.1.4.6.r07.Uni.CoreMod.jar|[1.4.6]TreeCapitator.Forge.1.4.6.r07.Uni.CoreMod-patched.jar"
  "NotEnoughItems 1.4.7.0.jar|NotEnoughItems 1.4.7.0-patched.jar"
)

# <folder> <stock dir> <pair...>
swap() {
  local dir="$1" from="$2"
  shift 2
  for pair in "$@"; do
    stock="${pair%%|*}"; patched="${pair##*|}"
    rm -f "$SRV/$dir/$stock" "$SRV/$dir/$patched"
    if [ "$MODE" = patched ] && [ -f "$patched" ]; then
      cp "$patched" "$SRV/$dir/"
    else
      cp "$from/$stock" "$SRV/$dir/"
    fi
  done
}
swap mods "$STOCK" "${JARS[@]}"
swap coremods "$STOCK_CORE" "${COREJARS[@]}"
# The coremod for the signed jars is new, not a replacement: only there when patched.
rm -f "$SRV/coremods/TLiteFixes-coremod.jar"
[ "$MODE" = patched ] && cp TLiteFixes-coremod.jar "$SRV/coremods/"
cp build/TLFixTest.jar "$SRV/plugins/"

cd "$SRV"
rm -f run.log srvin
mkfifo srvin
sleep 100000 > srvin & HOLD=$!
"$OLDPWD/$JAVA7" -Xms1G -Xmx2G -XX:MaxPermSize=256m -Djava.awt.headless=true -jar mcpcplus.jar nogui < srvin > run.log 2>&1 & JPID=$!
trap 'kill $JPID $HOLD 2>/dev/null; rm -f srvin' EXIT

for i in $(seq 1 400); do
  grep -qE 'Done \(' run.log 2>/dev/null && break
  kill -0 $JPID 2>/dev/null || { echo "server exited before Done" >&2; tail -20 run.log >&2; exit 1; }
  sleep 1
done

for s in "$@"; do
  echo "tlfix $s" > srvin
  sleep 3
done
echo stop > srvin
for i in $(seq 1 120); do kill -0 $JPID 2>/dev/null || break; sleep 1; done

echo "== $MODE"
grep -F '[TLFixTest]' run.log | sed -E 's/^.*\[TLFixTest\] //' || echo "(no scenario output)"
grep -E '\[TLiteFixes\]' run.log || true
