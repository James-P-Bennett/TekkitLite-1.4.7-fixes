# Tekkit Lite 1.4.7 fixes

Bug and exploit fixes for mods in the **Tekkit Lite** modpack (Minecraft 1.4.7, MCPC+ server),
applied as bytecode patches to the shipped jars.

**These are server side patches and they work against an unmodified Tekkit Lite client.**

Each patch is selectable individually.

| Mod | Patches |
|---|---|
| [MineFactoryReloaded 2.3.2](#minefactoryreloaded-232) | `unifierdupe` · `dsudupe` · `packets` |
| [EE3 pre1f](#ee3-pre1f) | `requestcheck` · `protect` |
| [Applied Energistics rv9](#applied-energistics-rv9) | `entropy` · `catalyst` · `monitor` |
| [Factorization 0.7.21](#factorization-0721) | `wrathigniter` |
| [TreeCapitator 1.4.6 r07](#treecapitator-146-r07-coremod) (coremod) | `felling` |
| [NotEnoughItems 1.4.7.0](#notenoughitems-1470-coremod) (coremod) | `spawner` · `creative` |
| [BuildCraft 3.4.3](#buildcraft-343) | `quarry` · `filler` · `quarrychunks` |
| [ComputerCraft 1.5](#computercraft-15) | `turtle` · `packets` |
| [immibis-core 52.4.6](#immibis-core-5246-tubestuff) (Tubestuff) | `mergenbt` |
| [IndustrialCraft 2 and RedPower 2](#industrialcraft-2-and-redpower-2-tlitefixes-coremod) (TLiteFixes coremod) | `laser` · `bagdupe` · `tubeinject` |
| [ThermalExpansion 2.2.2.2](#thermalexpansion-2222) | `packets` |
| [IronChest 5.1.0.275](#ironchest-51025) | `crystalcap` |
| [LogisticsPipes 0.7.0.96](#logisticspipes-07096) | `diskdupe` |
| [AdditionalPipes 2.1.3](#additionalpipes-213) | `teleowner` |
| [IC2NuclearControl 1.4.6](#ic2nuclearcontrol-146) | `packets` |
| [OmniTools 3.0.4](#omnitools-304) | `wrench` |
| [Balkon's Weaponmod](#balkons-weaponmod) | `dynamite` |

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

Machines that work with no player at hand (Quarry, Filler, Turtles) remember who placed them and
ask as that player, through an offline MCPC+ fake player with the owner's name. The Mining Laser
asks the same way for its shooter. The fake player has no connection, so GriefPrevention's
refusal messages go nowhere instead of spamming the owner. A machine placed before these patches
has no owner and asks under a name no claim trusts, so it keeps working on open ground and is
refused inside every claim. Right clicking an ownerless Filler or Turtle with permission to break
it makes that player its owner.

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

<details>
<summary><b><code>dsudupe</code>: break a Deep Storage Unit with its GUI open and take its output twice</b></summary>

**The bug.** Two faults together:

- MFR's base inventory class closes a GUI once the tile is no longer the one in the world. The
  Deep Storage Unit overrides `isUseableByPlayer` with a distance check only, so its GUI stays
  open after the block is gone.
- `BlockFactoryMachine1.breakBlock` drops the DSU with its count in NBT, which includes the
  output slot and the two input slots, but leaves the tile's slots and count as they were.

A player can't break a block while in a GUI, so a machine does it: a RedPower Block Breaker
(763:1) on a timer, with the player standing at the DSU in its GUI. The DSU drops holding
everything, and the player shift-clicks the output stack and the input slots out of the dead
tile: up to 66 extra items every cycle. That is why the old `AdjacentBlockDupePatch` refused
placing a Block Breaker next to a DSU. MFR's own Block Breaker, or anything else that breaks
blocks, works the same way.

**The patch.**

- The DSU's `isUseableByPlayer` starts with `TLiteMFR.isInWorld`, the same check the base class
  makes, so the server closes the GUI on the next tick.
- `breakBlock` empties the DSU's slots and count before it removes the tile, so even that one
  tick has nothing left to take.
- The LiquiCrafter has the same distance only `isUseableByPlayer` and gets the same check.

**Verified** with a DSU holding 1000 cobblestone, its GUI open for a player standing next to
it, broken by a powered RedPower Block Breaker, then the three DSU slots shift-clicked in the
GUI. Stock: GUI still usable, the dropped DSU held 1000 and 66 more came out of the GUI, 1066
in total. Patched: GUI no longer usable, nothing came out of the GUI, 1000 in total.

</details>

<details>
<summary><b><code>packets</code>: change anyone's MFR machines from anywhere, and pipe items out of their DSU</b></summary>

**The bug.** Six GUI buttons send a packet with the machine's coordinates, and
`ServerPacketHandler.onPacketData` uses them as they are: no distance check, no check that the
sender has that GUI open, and the lookup loads chunks anywhere. Any player can:

- flip a Deep Storage Unit face to output. Flip the face next to your own land, put a pipe
  there, and the owner's stored items come out to you.
- lower an Auto Enchanter's level, which finishes the current enchant at once at level 1
- write any settings key into a Harvester, with no limit on how many
- toggle a Chronotyper, an Auto Spawner's exact copy mode, or an Auto Jukebox
- flood a Harvester's settings with any keys the client names. The settings map is written whole
  to the tile's NBT, so enough distinct keys grow its chunk past the region file's sector limit
  and the chunk silently fails to save, losing every block and tile in it. This one works even
  from the machine's own GUI, so the check below does not stop it.

**The patch.** Every machine lookup in the handler goes through `TLiteMFR.packetTile`. It
returns the machine only when it is within 8 blocks, checked before the lookup, and the sender
has that machine's GUI open and still usable. That is always true for a stock client, which only
sends these from the machine's own GUI. The Harvester settings write goes through
`TLiteMFR.putHarvesterSetting`, which keeps only the three keys the game reads back
(`silkTouch`, `harvestSmallMushrooms`, `harvestJungleWood`) and drops the rest.

**Verified** with the DSU side packet. Stock applied it from 30 blocks away, from next to the
DSU with no GUI open, and with another DSU's GUI open. Patched refused all three and still
applied it for a player with that DSU's GUI open. And with the Harvester settings packet: stock
grew the settings from 3 keys to 53, patched kept 3 and still applied the real one.

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

## TreeCapitator 1.4.6 r07 (coremod)

<details>
<summary><b><code>felling</code>: fell logs inside claims from outside them (protection)</b></summary>

**The bug.** Breaking a log with an axe makes TreeCapitator break every log joined to it, up to
16 blocks sideways and upwards, plus the leaves above. `TreeBlockBreaker.destroyBlocksWithChance`
does it straight through the world. Only the log the player actually broke goes through the
server's break check, so a log on open ground that touches logs inside a claim takes them out:
a tree on the border, a tree farm, or a log wall. Three leaves next to the broken log are
enough for it to count as a tree.

**The patch.** Each block the felling is about to break goes through `TLiteTreeCap.getBlockId`,
which asks [`TLiteProtect`](#how-protection-checks-work) first and returns air for a refused
block, which the loop skips. After the first refusal the rest of that felling is skipped too, so
a claim costs one refusal message instead of one per log.

**Verified** with three logs in a row, the first just outside another player's claim and the
other two inside it. Stock felled all three. Patched broke only the first. Three logs on open
ground were felled either way.

</details>

---

## NotEnoughItems 1.4.7.0 (coremod)

NEI checks `NEIServer.cfg` permissions for most of its packets in `authenticatePacket`, but
lets two through for every player. The client only hides the buttons, so a modified client can
send them.

<details>
<summary><b><code>spawner</code>: change any mob spawner in the world, claims included (protection)</b></summary>

**The bug.** Packet 15 sets a mob spawner's mob. The client sends it after placing a spawner
from NEI. `ServerPacketHandler.handleMobSpawnerID` takes the packet's coordinates and mob name
as they are: any spawner at any distance, inside any claim, and any string as the mob, which
the server stores.

**The patch.** The call goes to `TLiteNEI.handleMobSpawnerID`, which drops the request unless
the spawner is within 8 blocks, the name is a living mob, and
[`TLiteProtect`](#how-protection-checks-work) allows the change.

**Verified** against a pig spawner with Creeper requested. Stock changed it inside another
player's claim, from 30 blocks away, and to the made up name `NotAMob`. Patched refused all
three and still changed a spawner on open ground within reach.

</details>

<details>
<summary><b><code>creative</code>: any player can switch themselves to creative mode</b></summary>

**The bug.** Packet 13 runs `NEIServerUtils.toggleCreativeMode`, which cycles the sender between
survival, creative and NEI's creative inventory. It never checks the `creative` permission.
Creative mode means any item from the creative inventory.

**The patch.** `toggleCreativeMode` starts with `TLiteNEI.canToggleCreative`, which requires the
sender to be on the `creative` list in `NEIServer.cfg`.

**Verified** with a player who is not on the list. Stock put them in creative. Patched left them
in survival and logged the refusal.

</details>

---

## BuildCraft 3.4.3

<details>
<summary><b><code>quarry</code>: mine and frame inside claims (protection)</b></summary>

**The bug.** `TileQuarry.positionReached` mines the block under its head, and `buildFrame` has
its robot place frame blocks, both straight through the world. A Quarry outside a claim with its
area over the claim digs the claim out. It was banned as able to "bypass claim protections".

**The patch.**

- The Quarry remembers who placed it, saved in its NBT.
- Each block it would mine goes through `TLiteBC.quarriable`, which adds the owner's
  [protection check](#how-protection-checks-work) to the stock test.
- A refused block's column is then treated like bedrock, so the Quarry moves on instead of
  returning to it.
- Frame blocks inside a claim are skipped.

**Verified** with a Quarry placed by another player outside a claim, its head sent to a stone
block inside it. Stock mined it. Patched left it and logged the refusal, and still mined the same
block on open ground.

</details>

<details>
<summary><b><code>quarrychunks</code>: a full size Quarry stops but keeps its whole area loaded, and a stray ticket crashes world load</b></summary>

**The bug.** A Quarry keeps its chunks loaded with one Forge chunk ticket, which holds 25 chunks
here (`forgeChunkLoading.cfg`). When a ticket goes over its limit Forge drops the oldest chunk,
and the Quarry forces its own chunk first.

- `setBoundaries` lets an area through when `(xSize * zSize) >> 8` is under the limit. A 64x64
  marker area passes, but can span 5x5 chunks, and with the Quarry's own chunk that is 26.
- Forge then drops the Quarry's own chunk. The Quarry unloads and stops once nobody is near,
  and its 25 area chunks stay loaded. The ticket is saved, so this comes back after every restart.
- When the world loads, BuildCraft's ticket callback calls `forceChunkLoading` on whatever tile
  entity is at the ticket's quarry position. A quarry block without its tile entity crashes the
  world load.

**The patch.**

- `setBoundaries` counts the real chunks, the Quarry's own included, and uses the default area
  when they don't fit the ticket.
- `forceChunkLoading` forces the Quarry's own chunk again at the end, so Forge drops an area
  chunk instead. This also fixes Quarries that already have a full ticket.
- The callback releases a ticket with no quarry behind it instead of crashing.

**Verified** with a Quarry forcing a 64x64 area over 5x5 chunks on a fresh ticket. Stock: the
ticket held 25 chunks without the Quarry's own. Patched: 25 chunks including it. The callback on
a ticket pointing at stone: stock threw `NullPointerException`, patched released the ticket.

</details>

<details>
<summary><b><code>filler</code>: fill, clear and flatten inside claims (protection)</b></summary>

**The bug.** The Filler's patterns place blocks with the item and clear them with
`setBlockWithNotify` or `BlockUtil.breakBlock`, straight through the world, anywhere its markers
reach.

**The patch.** The Filler remembers who placed it. Every block a pattern places or clears goes
through `TLiteBC`, which asks the owner's
[protection check](#how-protection-checks-work) first. On a refusal the Filler stops as if its
pattern were done, and rests for 10 seconds before trying again, so Loop mode can't retry every
tick.

**Verified** with a Filler placed by another player outside a claim, its box on a stone block
inside it, running the Clear pattern. Stock cleared it. Patched left it, and still cleared the
same block on open ground.

</details>

---

## ComputerCraft 1.5

<details>
<summary><b><code>turtle</code>: dig, build, take and move inside claims (protection)</b></summary>

**The bug.** Turtles dig, attack, place, suck items from, drop items into and move into the block
next to them with no protection check. A turtle outside a claim empties it or drives in. They
were banned as able to "build in protected areas without permission".

**The patch.**

- Each turtle remembers who placed it, saved in its NBT and carried across moves.
- `move`, `useTool` (dig and attack with any tool upgrade), `place`, `suck` and `dropQuantity`
  each start with a [protection check](#how-protection-checks-work) as the owner on the cell they
  touch.
- `tryPlaceOnBlock` and `tryPlaceOnEntity` check the cell they reach, since placing can reach
  two blocks away.
- A refused action returns false to the program, like hitting bedrock.

**Verified** with a mining turtle placed by another player just outside a claim. Stock dug the
stone inside the claim and then moved into it. Patched refused both, and the same turtle dug and
moved on open ground.

</details>

<details>
<summary><b><code>packets</code>: type into and take over anyone's computer or turtle from anywhere</b></summary>

**The bug.** `ComputerCraftProxyCommon.handlePacket` reads a block position from the client
packet, looks up the tile entity there and calls its `handlePacket` with the sender. It never
checks the sender owns or has that tile's GUI open, and the lookup loads chunks anywhere. A
modified client can aim a packet at any computer or turtle and type into its terminal (running
arbitrary Lua as that computer: read or wipe its files, drive its turtle and peripherals),
reboot it, shut it down, terminate it, or read its screen, from anywhere on the server. This is
the same class of bug as the MFR and NEI packet fixes.

**The patch.** The dispatch goes through `TLiteCC.handlePacket`. For the tiles a player drives
through a GUI (computer, turtle, disk drive) it runs only when the sender has that exact tile's
container open and still usable, which is the only way a stock client sends these. Monitors,
printers and modems only reply with their own state to a refresh request, so they are left alone.

**Verified** with two computers. Driving computer A was refused for a player with no GUI open and
for a player with computer B's GUI open, and allowed only for a player with A's own GUI open.

</details>

---

## immibis-core 52.4.6 (Tubestuff)

<details>
<summary><b><code>mergenbt</code>: shift-click copies an item's contents onto every item in the stack</b></summary>

**The bug.** `BasicInventory.mergeStackIntoRange`, which Tubestuff uses for shift-click and the
Retrievulator, merges stacks by item id and damage and ignores NBT. The destination keeps its own
tag and takes the whole count. With a Deep Storage Unit holding 1000 items in an ACT Mk II
(AutoCraft Mk II, 4092:1), shift-clicking 15 empty Deep Storage Units into it gives 16 that each
hold 1000. This is the exploit the ACT Mk II was banned for. The same works with Mystcraft pages.

**The patch.** Both merge methods now call `TLiteImmibis`, the same logic with the tags required
to match.

**Verified** with that shift-click. Stock: 16 Deep Storage Units holding 1000 each, 16000 in
total. Patched: 1 holding 1000 and 15 empty ones in the next slot, 1000 in total.

</details>

---

## IndustrialCraft 2 and RedPower 2 (TLiteFixes coremod)

IC2 and RedPower ship signed jars. Changing a class in a signed jar breaks the mod: the class
fails its digest, and stripping the signature makes Java refuse the rest of RedPower, whose
other zips share a package under the original signature. So these fixes are in
`TLiteFixes-coremod.jar`, a server side coremod that patches the classes as they load and leaves
the signed jars untouched. It logs `[TLiteFixes] patched <class>` for each one, and refuses to
patch a class that doesn't match, rather than half patching it.

<details>
<summary><b><code>laser</code>: IC2 Mining Laser mines and blows up claims (protection)</b></summary>

**The bug.** `EntityMiningLaser` breaks, smelts and ignites the blocks its beam hits, and its
Explosive mode runs an `ExplosionIC2`, all with no protection check, in every mode. It was banned
as able to "bypass anti-grief".

**The patch.**

- Before the beam takes a block, `TLiteIC2.canMine` asks the shooter's
  [protection check](#how-protection-checks-work). A refusal ends the beam, as hitting an
  unminable block does.
- In a Mining Laser explosion each block is checked the same way, and a refused one reads as air,
  so it is neither destroyed nor dropped. Explosions from anything else are left as they were.

**Verified** with a beam straight down onto stone, and an Explosive shot onto a 3x3x3 stone cube,
inside another player's claim. Stock mined the block and the explosion destroyed 9 of the 27.
Patched left the block and all 27, and still mined the block and destroyed 9 on open ground.

</details>

<details>
<summary><b><code>bagdupe</code>: RedPower Canvas Bag number key dupe</b></summary>

**The bug.** The Canvas Bag GUI writes to the held bag's NBT and uses vanilla `slotClick`.
Hovering an item in the bag and pressing the number key of the bag's own hotbar slot moves the
item into the hotbar and pushes the bag back into the inventory as a copy made while it still
held the item. The item is out, and the bag still has it. It was banned as "causes problems".

**The patch.** The bag's container gets a `slotClick` that refuses number key swaps and any
click on the slot holding the open bag. Neither is needed while the bag is open.

**Verified** with 64 diamonds in a bag. Stock: 128 diamonds after one key press. Patched: 64.

</details>

<details>
<summary><b><code>tubeinject</code>: inject real items into RedPower tubes with a description packet</b></summary>

**The bug.** `CoreProxy.processPacket211`, on the server, looked up the tile at the packet's
coordinates and called its `handlePacket` with no check. Packet 211 is a description packet the
server only ever sends to clients, so nothing legitimate sends it back; a modified client could
send one that made a tube clear its contents and add client-supplied items, which the tube then
delivers into inventories as real items, or crash the handler with an out-of-range item id.

**The patch.** The server branch of `processPacket211` returns immediately, so the server ignores
these packets. The client branch (which applies a real description update for rendering) is
untouched.

**Verified** on the test server: the coremod logs `patched com.eloraam.redpower.core.CoreProxy`
and RedPower loads normally.

</details>

---

## ThermalExpansion 2.2.2.2

<details>
<summary><b><code>packets</code>: set any Energy Cell's charge, hijack a Tesseract, reconfigure any machine from anywhere</b></summary>

**The bug.** TE's packet handler looks up the tile at the packet's coordinates and runs the
packet on it with no owner, reach or open-GUI check, the same class as the MFR/NEI/CC packet
bugs. A modified client can set any Energy Cell or Engine's stored energy (energy from nothing),
retune or take over any Tesseract and pull another player's items, energy and liquid through it,
and scramble any machine's I/O faces, from anywhere.

**The patch.** The handler's tile lookup goes through `TLiteTE.gateTarget`, which returns the
tile only when the sender has that exact tile's TE container open and within reach. The Energy
Cell and Engine apply a packet's stored-energy value only on the client, so a player cannot fill
their own cell either. Public/private Tesseract channels and normal same-frequency routing are
untouched, since configuring a Tesseract happens in its own GUI.

**Verified** with an Energy Cell. Retuning it was refused from afar and from another cell's GUI,
and allowed only with that cell's own GUI open.

</details>

---

## IronChest 5.1.0.275

<details>
<summary><b><code>crystalcap</code>: cap how many items a Crystal Chest renders</b></summary>

**Why.** The Crystal Chest is the only transparent chest, so it renders its contents as items
floating in the block, up to eight per chest. A base with many of them is a lot of rotating
items for clients to draw. This caps each Crystal Chest to its three most common stacks. Only
crystal chests render items, so no other chest is affected, and nothing about storage changes.

**Verified** with a chest holding five item types: it renders three.

</details>

---

## LogisticsPipes 0.7.0.96

<details>
<summary><b><code>diskdupe</code>: Request Pipe Mk2 disk packet spawns arbitrary items</b></summary>

**The bug.** The disk-change packet stored a fully client-controlled ItemStack as a Request Pipe
Mk2's disk, and the disk-drop packet then spawned that exact stack into the world. A client sent
64 diamond blocks as the disk, dropped it, and repeated: unlimited item creation.

**The patch.** The store goes through `TLiteLP.setDisk`, which accepts only a real disk item (or
clearing it), so the drop can only ever drop a disk.

**Verified** in the patched jar: the store call is routed through the guard.

</details>

---

## AdditionalPipes 2.1.3

<details>
<summary><b><code>teleowner</code>: seize a teleport pipe's owner to steal another player's items</b></summary>

**The bug.** Packet id 16 set a teleport pipe's owner field to any client string on any teleport
pipe, with no check. An attacker set their own receiving pipe's owner to a victim's name and a
matching frequency, and the victim's sending pipe then teleported its items, energy and liquid
to the attacker, across claims and dimensions.

**The patch.** The client owner-write is dropped; the owner is only ever set server-side when the
pipe is placed. The legitimate frequency packet (id 64) already checks the player, so it is left
alone.

**Verified** in the patched jar: the owner write is removed from the packet handler.

</details>

---

## IC2NuclearControl 1.4.6

<details>
<summary><b><code>packets</code>: spam alarms and flood an Info Panel's NBT from anywhere</b></summary>

**The bug.** The packet handler read block coordinates from the client and looked up the tile
with no reach check, then wrote client data into it: an attacker-chosen sound onto any Howler
Alarm (remote alarm spam) and attacker-named fields into any Info Panel's sensor card, which is
saved to NBT. From anywhere in the world that let a player spam alarms and bloat a panel's NBT
until its chunk failed to save, the same chunk-loss as the MFR Harvester flood.

**The patch.** Each lookup goes through `TLiteNC.gate`, which returns the tile only when the
sender is within reach, so these packets can only touch tiles next to the sender.

**Not fully closed:** a player standing next to another player's Info Panel could still flood
that one panel's card NBT. Bounding that needs a per-field cap tied to each sensor card's schema,
left as a documented follow-up.

**Verified** on the test server: the four lookups route through the reach gate and the mod loads.

</details>

---

## OmniTools 3.0.4

<details>
<summary><b><code>wrench</code>: the OmniWrench removes machines inside claims (protection)</b></summary>

**The bug.** `ItemWrench.onItemUseFirst` removes an `IWrenchable` machine (sets it to air and
drops it) and rotates vanilla blocks straight through the world. Whether GriefPrevention's
interact handler stops it depends on config, so a player could pop machines out of another
player's claim.

**The patch.** The click starts with a [`TLiteProtect`](#how-protection-checks-work) check for the
player, the same as a hand break, and does nothing when refused. The player is online, so this
uses their real build permission: their own claim is fine, someone else's is refused.

**Verified** on the test server: the guard is injected and OmniTools loads.

</details>

---

## Balkon's Weaponmod

<details>
<summary><b><code>dynamite</code>: dynamite blasts blocks inside claims (protection)</b></summary>

**The bug.** Dynamite (and the cannon) explode through the mod's own `AdvancedExplosion`, which
removes blocks with `world.setBlockWithNotify` instead of a vanilla explosion, so it never fires
the Bukkit `EntityExplodeEvent` that GriefPrevention filters. A thrown stick of dynamite blew up
blocks inside another player's claim, and block damage is on by default. (The cannon is banned,
but dynamite is craftable.)

**The patch.** Each block the explosion would remove goes through `TLiteWM.breakIfAllowed`, which
checks it against the player who threw the dynamite, the same as a hand break. Dynamite with no
player behind it is treated as untrusted and breaks nothing in a claim.

**Verified** on the test server: the removal is routed through the guard and the mod loads.

</details>

---

## Not fixed yet

| What | Status |
|---|---|
| Other IC2 tools: Wrench, Foam Sprayer, Electric Hoe, Treetap, Painter, Cable Cutter, Terraformer | Change blocks with no protection check. GriefPrevention may already stop the right clicks on IC2 blocks. Not checked. |
| Mining Laser damage | Beams still hurt and set fire to players and mobs anywhere. A PvP matter, not a claim bypass. |
| Turtles placing vanilla blocks | MCPC+ asks plugins as the player "ComputerCraft" when a turtle places a vanilla block, so an owner's turtle may be refused in their own claim. Not checked. |
| Pipes, tubes and AE buses reading a chest just inside a claim from outside | A border problem for anything that moves items. No fix. |
| LogisticsPipes Security Station takeover | Packets rewrite any station's settings with no owner check. Needs LP's own owner model worked out to gate safely without locking players out. Deferred. |
| LogisticsPipes request amount | A request packet's quantity is an unvalidated int; a huge value could drive the crafting tree as a DoS. Clamp needed. Deferred. |
| ComputerCraft command block peripheral | Off by config (`enableCommandBlock=false`). If enabled, a computer wired to a command block runs op level server commands. Leave it off. |
| Tubestuff Black Hole Chest | Off by config (`enableBlackHoleChest=false`). If enabled, its unbounded inventory writes to NBT and the same chunk save loss as the Harvester flood applies. Leave it off. |
| Tampered on-disk NBT crashing one chunk/tile load (Factorization slots, ACT Mk II recipe, immibis chunk loader shape, Mystcraft legacy biome) | Only reachable if the region file is already edited or corrupt, not by a player in game. Left as defensive hardening, not applied. |
| ComputerCraft `http` API reaching localhost or the LAN | `http.request` has no host filter in 1.5, so a computer can read the server's own admin pages (dynmap, panels) or LAN devices. Config: set `enableAPI_http=false`, or a host filter could block loopback and private ranges. |
| OpenCCSensors reading nearby players | A sensor reports a player's inventory, armour and position through walls within its tier's radius. Range bounded and inherent to the mod. Server policy. |
| Balance and lag bans: Nuke, Industrial TNT, alarms, chunk loaders | Server policy rather than bugs. Left to config and plugins. |
| CodeChickenCore 0.7.3, PowerCrystalsCore 1.0.3 | Scanned. Libraries with no player driven world changes. Nothing to fix. |
| AE Conversion Matrix | Not a bug. It is a crafting material whose only use in the world is the Storage Monitor upgrade, covered by `monitor`. GriefPrevention's container trust list (900 to 902) already stops right clicks on AE blocks. |

---

## Plugins

Two Bukkit plugins used to cover these bugs. The patches replace what they did:

| Plugin | What it did | Now |
|---|---|---|
| GriefPrevention-TLite | GriefPrevention 7.6.2 plus claim checks for the Entropy Accelerator, Vibration Catalyst, Minium Stone, Wrath Igniter and ME Storage Monitor | Removed. `entropy`, `catalyst`, `protect`, `wrathigniter` and `monitor` do this inside the mods and work with stock GriefPrevention 7.6.2 |
| TekkitLiteCustomizer | TekkitCustomizer 1.6 item bans plus `AdjacentBlockDupePatch`, which refused placing a Block Breaker next to a Deep Storage Unit | Dupe ban removed, `dsudupe` fixes it inside MFR. The item bans stay |

`plugins/TekkitLiteCustomizer` is the plugin's source, recovered from IntelliJ's local history:
`src/` is what `build.sh` builds into `TekkitLiteCustomizer.jar`, `version-history/` has every
recovered version in order, and `decompiled-deployed-jar/` is the jar that ran on the server
before, dupe ban included.

---

## Build

<details>
<summary><b>Building the patched jars from the stock jars</b></summary>

```sh
./build.sh
```

Builds every patched jar, `TLiteFixes-coremod.jar` and `TekkitLiteCustomizer.jar`. Needs a Java 8 `javac` for the helper
classes and the plugin, ASM, and the server's `mcpcplus.jar`.

Override paths with `MODS`, `COREMODS`, `MCPC`, `MFR_SRC`, `EE3_SRC`, `AE_SRC`, `FZ_SRC`, `TC_SRC`,
`NEI_SRC`, `ASM`, `JAVAC8`. `MODS` defaults to the PolyMC Tekkit Lite instance, whose mod jars are identical to
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
test/run.sh stock   probe unifier ee3 entropy catalyst wrath monitor treecap spawner creative dsu mfrpacket laser act2 bag filler quarry quarrychunks turtle ccpacket harvester
test/run.sh patched probe unifier ee3 entropy catalyst wrath monitor treecap spawner creative dsu mfrpacket laser act2 bag filler quarry quarrychunks turtle ccpacket harvester
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
| `buildcraft-A-3.4.3.jar` | `buildcraft-A-3.4.3-patched.jar` |
| `ComputerCraft1.5.zip` | `ComputerCraft1.5-patched.zip` |
| `immibis-core-52.4.6.jar` | `immibis-core-52.4.6-patched.jar` |

And in the **server's** `coremods/` folder:

| Replace | With |
|---|---|
| `[1.4.6]TreeCapitator.Forge.1.4.6.r07.Uni.CoreMod.jar` | `[1.4.6]TreeCapitator.Forge.1.4.6.r07.Uni.CoreMod-patched.jar` |
| `NotEnoughItems 1.4.7.0.jar` | `NotEnoughItems 1.4.7.0-patched.jar` |
| nothing, it is new | `TLiteFixes-coremod.jar` |

**Clients need no changes.** Every patched method runs on the server: the Unifier's update, the
DSU's GUI and break checks, EE3's and NEI's packet handlers, TreeCapitator's felling, and the
tools' item use and the monitor's click, whose results the server decides. The patches do not
change any mod id or version string, so FML accepts stock clients. `TLiteFixes-coremod.jar` has
no mod entry, so clients don't need it.

Not yet checked with a real client connected. The verification above was done by server
side scenarios.

And in the **server's** `plugins/` folder, at the same time as the jars above:

| Replace | With |
|---|---|
| `GriefPrevention-TLiteEvents.jar` | stock GriefPrevention 7.6.2. Same version, so `GriefPreventionData` carries over |
| `TekkitLiteCustomizer.jar` | `TekkitLiteCustomizer.jar` from this repo |

Swap the plugins only together with the patched jars. The old plugin versions are what block
the claim bypasses and the DSU dupe on stock jars.

Once the patched jars are in, these TekkitCustomizer bans can go: BC Filler (155:0), BC Quarry
(153:0), Turtles (209, 210), Mining Laser (30208), AutoCraft Mk II (4092:1) and Canvas Bag (9268).
The Entropy Accelerator, Vibration Catalyst, Minium Stone and Wrath Igniter need no ban either.

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
| TreeCapitator | bspkrs |
| NotEnoughItems | ChickenBones |
| BuildCraft | SpaceToad and the BuildCraft team |
| ComputerCraft | dan200 |
| Tubestuff, immibis-core | immibis |
| IndustrialCraft 2 | Alblaka and the IC2 team |
| RedPower 2 | Eloraam |
| TekkitCustomizer, GriefPrevention | ryanhamshire (BigScary) |
