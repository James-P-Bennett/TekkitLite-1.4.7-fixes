# Tekkit Lite 1.4.7 fixes

Bug and exploit fixes for mods in the **Tekkit Lite** modpack (Minecraft 1.4.7, MCPC+ server),
applied as bytecode patches to the shipped jars.

**These are server side patches and they work against an unmodified Tekkit Lite client.**

Each patch is selectable individually.

| Mod | Patches |
|---|---|
| [MineFactoryReloaded 2.3.2](#minefactoryreloaded-232) | `unifierdupe` |
| [EE3 pre1f](#ee3-pre1f) | `requestcheck` · `protect` |
| [Applied Energistics rv9](#applied-energistics-rv9) | `entropy` · `catalyst` · `monitor` |
| [Factorization 0.7.21](#factorization-0721) | `wrathigniter` |

Bukkit plugins have [their own section](#plugins). The fixes that used to live in plugins are
now done inside the mods, so they hold no matter which protection plugin the server runs.

---

## How protection checks work

Mods on MCPC+ change blocks straight through the world, so protection plugins such as
GriefPrevention never hear about it. That is how the Entropy Accelerator, Minium Stone and
Wrath Igniter worked inside other players' claims.

Every patch marked **protection** below asks the plugins first, the same way a hand break
does: `TLiteProtect` fires a Bukkit `BlockBreakEvent` for the player at the block, and the mod
only goes ahead if no plugin cancels it. A claim, a WorldGuard region or anything else that
stops a player breaking that block now also stops the item.

- The check fails closed. If it throws, the change is refused and one line is logged.
- Refusals are logged at most once per player every 10 seconds:

```
[TLiteFixes] refused Entropy Accelerator at 224,200,252 (protected) from Intruder
```

- Every patched jar carries its own identical copy of `TLiteProtect`. Build them together.
- Plugins that log `BlockBreakEvent`, such as CoreProtect, may record an allowed change as a
  break by that player. Not checked.

---

## MineFactoryReloaded 2.3.2

<details>
<summary><b><code>unifierdupe</code>: one ingot fills the Unifier's output to a full stack</b></summary>

**The bug.** `TileEntityUnifier.moveItemStack`, when the output slot already holds the same
item:

```java
amt = Math.min(getInventoryStackLimit(), output.getMaxStackSize()) - output.stackSize;
output.stackSize += amt;
input.stackSize  -= amt;
if (input.stackSize == 0) input = null;
```

The amount moved is the output's free space, never capped at what the input holds. One ingot
in, with 1 already in the output, gives an output of 64 and an input of -62. Only a count of
exactly 0 clears the slot, so the negative stack stays and keeps feeding it. This is why the
Unifier was banned for "Duping".

**The patch.** The call goes to `TLiteMFR.moveItemStack`, which moves the smallest of the input
count, the output's free space and the unified item's max stack size, and clears the input at
0 or below.

**Verified** on a local MCPC+ server with one copper ingot in and one in the output.
Stock: input -62, output 64. Patched: input empty, output 2.

</details>

---

## EE3 pre1f

<details>
<summary><b><code>requestcheck</code>: transmute any area, anywhere, holding anything</b></summary>

**The bug.** The Minium Stone's world transmutation is a packet from the client with an origin,
a range and a target block. `PacketRequestEvent.execute` hands it straight to
`WorldTransmutationHandler`, which checks none of it:

- any coordinates, at any distance
- a range of up to 127 on every axis, although the Minium Stone always sends 1x1x1
- no check that the player holds a transmutation stone. With nothing in hand the handler
  throws a `NullPointerException` for every block in the range. The old server log has 106 of
  them.

**The patch.** `TLiteEE3.handleWorldTransmutation` runs first and drops the request unless:

- the player holds a transmutation stone
- the origin is within 8 blocks
- the target is `id:meta`
- the range is no larger than a real client sends: 1 along the clicked face's axis and
  `1 + 2 * charge` on the other two. The Minium Stone is not chargeable, so that is 1x1x1.

**Verified** on a local server with an uncharged Minium Stone turning grass into sand.
A 5x1x5 request: stock changed 25 blocks, patched 0. A block 30 blocks away: stock changed it,
patched did not.

</details>

<details>
<summary><b><code>protect</code>: transmute blocks inside other players' claims (protection)</b></summary>

**The bug.** Each block in the range is changed by `TransmutationHelper.transmuteInWorld`
through the world directly, so claims never see it. The Minium Stone was banned outright for
this.

**The patch.** Each block goes through `TLiteEE3.transmuteInWorld`, which asks
[`TLiteProtect`](#how-protection-checks-work) first.

**Verified** with a GriefPrevention claim owned by another player. Stock transmuted the block
inside the claim. Patched left it and logged the refusal, and still transmuted the same block
on open ground.

</details>

---

## Applied Energistics rv9

<details>
<summary><b><code>entropy</code> and <code>catalyst</code>: AE tools change blocks inside claims (protection)</b></summary>

**The bug.** The Entropy Accelerator (water to ice, lava to obsidian, stone to cobble, ...) and
the Vibration Catalyst (smelts blocks in place) change the clicked block through the world in
`onItemUse`. Claims never see it. Both were banned for this.

**The patch.** `onItemUse` starts with a [`TLiteProtect`](#how-protection-checks-work) check on
the clicked block and does nothing when it is refused.

**Verified** with charged tools on a block inside another player's claim. Stock: the Entropy
Accelerator froze water into ice and the Vibration Catalyst smelted cobblestone into stone.
Patched: both blocks unchanged inside the claim, and both tools still worked on open ground.

</details>

<details>
<summary><b><code>monitor</code>: take 64 items out of anyone's ME network (protection)</b></summary>

**The bug.** A Storage Monitor that is locked, upgraded with a Conversion Matrix and powered
hands 64 of its displayed item out of the ME network to whoever right clicks it. There is no
owner check, and anyone can also relock it with a wrench.

**The patch.** `BlockStorageMonitor.onBlockActivated` starts with a
[`TLiteProtect`](#how-protection-checks-work) check. When it is refused the click is swallowed.

**Verified** inside another player's claim. Stock ran the click. Patched refused it and logged
the refusal.

</details>

---

## Factorization 0.7.21

<details>
<summary><b><code>wrathigniter</code>: light wrath fire inside claims (protection)</b></summary>

**The bug.** The Wrath Igniter lights wrath fire against the clicked block, and the fire eats
through blocks of that type. `ItemWrathIgniter.tryPlaceIntoWorld` does it through the world
with no check. It was banned as able to "cook the world".

**The patch.** `tryPlaceIntoWorld` starts with a [`TLiteProtect`](#how-protection-checks-work)
check on the clicked block and does nothing when it is refused.

**Verified** inside another player's claim. Stock lit wrath fire above the block. Patched did
not, and still lit it on open ground.

</details>

---

## Not fixed yet

| What | Status |
|---|---|
| RedPower Block Breaker (763:1) next to a Deep Storage Unit (3131:3) | Dupe blocked by the old plugin. Not reproduced from the code yet: the DSU drops nothing through `getBlockDropped` and its contents drop once in `breakBlock`. Needs a live test. |
| Tubestuff ACT Mk II (4092:1) | Banned as "Exploits". The exploit is not identified yet. |
| MFR machine packets 2 to 10 | Trust the client's coordinates, so a client can change other players' machine settings from anywhere. Settings only, no items. |
| Claim bypassing machines and tools: Quarry, Filler, Turtles, Mining Laser, Conversion Matrix | Still handled by bans. The same protection check could fix them. |
| Balance and lag bans: Nuke, Industrial TNT, alarms, Crystal Chest, chunk loaders | Server policy rather than bugs. Left to config and plugins. |

---

## Plugins

`plugins/` holds the Bukkit plugins the server used before these patches, recovered from
IntelliJ's local history. They are kept for reference.

| Plugin | What it did | Now |
|---|---|---|
| `plugins/TekkitLiteCustomizer` | TekkitCustomizer 1.6 plus `AdjacentBlockDupePatch`, which refused placing a Block Breaker next to a Deep Storage Unit | Dupe not reproduced yet, see [Not fixed yet](#not-fixed-yet) |
| `plugins/GriefPrevention-TLite` | GriefPrevention 7.6.2 plus claim checks for the Entropy Accelerator, Vibration Catalyst, Minium Stone, Wrath Igniter and ME Storage Monitor | Replaced by `entropy`, `catalyst`, `protect`, `wrathigniter` and `monitor`, which work with stock GriefPrevention |

Each plugin folder has `src/` with the newest recovered source, `version-history/` with every
recovered version in order, and `decompiled-deployed-jar/` with the jar that ran on the server.

---

## Build

<details>
<summary><b>Building the patched jars from the stock jars</b></summary>

```sh
./build.sh
```

Needs a Java 8 `javac` for the helper classes, ASM, and the server's `mcpcplus.jar`.

Override paths with `MODS`, `COREMODS`, `MCPC`, `MFR_SRC`, `EE3_SRC`, `AE_SRC`, `FZ_SRC`, `ASM`,
`JAVAC8`. `MODS` defaults to the PolyMC Tekkit Lite instance, whose mod jars are identical to
the server's.

Minecraft 1.4.7 has no runtime deobfuscation, so the helper classes use the obfuscated
vanilla names (`ur` is ItemStack, `yc` is World) and sit in the default package, the only place
Java source can see those classes from. They compile against `mcpcplus.jar`, which holds the
whole obfuscated game plus Forge and Bukkit. The patcher throws if any selected patch fails to
apply, so it never writes a jar that silently did nothing.

</details>

## Test

<details>
<summary><b>Running the scenarios on a local server</b></summary>

`test/` has a scenario plugin, `TLFixTest`, and `test/run.sh`, which boots a local copy of the
server with stock or patched jars, runs the scenarios from the console and prints the results:

```sh
test/run.sh stock   probe unifier ee3 entropy catalyst wrath monitor
test/run.sh patched probe unifier ee3 entropy catalyst wrath monitor
```

The protection scenarios claim an area for `Owner` with stock GriefPrevention and act as the
fake player `Intruder`. `probe` checks the setup first: a break in the claim must be refused
and one on open ground must not, and it names the plugins that refused.

The copied WorldGuard `__global__` region lists members, which makes WorldGuard refuse every
other player everywhere. The test server's copy has those members removed so that only the
claim decides.

The local server is `build/testserver`: the real server without its world, on port 25599, with
the old patched plugins, AuthMe and Modifyworld removed. MCPC+ 1.4.7 refuses to start on
Java 8, so it runs on a Java 7 JDK in `build/jdk7`.

</details>

## Install

<details>
<summary><b>Server side install</b></summary>

Server side only. Drop the patched jars into the **server's** `mods/` folder, replacing the
stock ones:

| Replace | With |
|---|---|
| `MineFactoryReloaded-2.3.2-287.jar` | `MineFactoryReloaded-2.3.2-287-patched.jar` |
| `ee3-universal-pre1f.jar` | `ee3-universal-pre1f-patched.jar` |
| `appeng-rv9-i.zip` | `appeng-rv9-i-patched.zip` |
| `Factorization-0.7.21.jar` | `Factorization-0.7.21-patched.jar` |

**Clients need no changes.** Every patched method runs on the server: the Unifier's update,
EE3's packet handler, and the tools' item use and the monitor's click, whose results the
server decides. The patches do not change any mod id or version string, so FML accepts stock
clients.

Not yet checked with a real client connected. The verification above was done by server
side scenarios.

Once the patched jars are in, the Entropy Accelerator, Vibration Catalyst, Minium Stone and
Wrath Igniter no longer need to be banned, and `GriefPrevention-TLite` can go back to stock
GriefPrevention.

</details>

## Credits

All patched mods are the work of their original authors. These are third party patches, not
affiliated with or endorsed by any of them.

| Mod | Author |
|---|---|
| MineFactoryReloaded | PowerCrystals |
| Equivalent Exchange 3 | pahimar |
| Applied Energistics | AlgorithmX2 |
| Factorization | neptunepink |
| TekkitCustomizer, GriefPrevention | ryanhamshire (BigScary) |
