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
| [ComputerCraft 1.5](#computercraft-15) | `turtle` · `packets` · `http` |
| [immibis-core 52.4.6](#immibis-core-5246-tubestuff) (Tubestuff) | `mergenbt` |
| [IndustrialCraft 2 and RedPower 2](#industrialcraft-2-and-redpower-2-tlitefixes-coremod) (TLiteFixes coremod) | `laser` · `explosion` · `wrench` · `hoe` · `sprayer` · `cable` · `barrel` · `resin` · `luminator` · `cell` · `miner` · `pump` · `terraformer` · `bagdupe` · `tubeinject` · `breaker` · `igniter` · `deployer` · `frame` · `thermopile` · `grate` · `netevent` · `sorter` · `tesla` |
| [ThermalExpansion 2.2.2.2](#thermalexpansion-2222) | `packets` |
| [IronChest 5.1.0.275](#ironchest-51025) | `crystalcap` |
| [LogisticsPipes 0.7.0.96](#logisticspipes-07096) | `diskdupe` · `requestclamp` · `security` |
| [AdditionalPipes 2.1.3](#additionalpipes-213) | `teleowner` · `apparity` |
| [ChickenChunks 1.3.1.0](#chickenchunks-1310) | `spotloader` · `combinedquota` |
| [Dimensional Anchors 52.2.0](#dimensional-anchors-5220) | `spotloader` · `combinedquota` |
| [IC2NuclearControl 1.4.6](#ic2nuclearcontrol-146) | `packets` · `cardcap` |
| [OmniTools 3.0.4](#omnitools-304) | `wrench` |
| [Balkon's Weaponmod](#balkons-weaponmod) | `dynamite` |
| [WR-CBE Wireless Redstone 1.3.2.8](#wr-cbe-wireless-redstone-1328) | `freq` |
| [Steve's Carts 2.0.0.a62](#steves-carts-2000a62) | `carts` |
| [AdvancedPowerManagement 1.1.55](#advancedpowermanagement-1155) | `guibutton` · `outputdupe` |
| [Advanced Repulsion Systems 52.0.6](#advanced-repulsion-systems-5206) | `tesla` |
| [Modular Powersuits 0.7](#modular-powersuits-07) | `tweak` |
| [Mystcraft 0.10.1](#mystcraft-0101) | `linknull` |

Bukkit plugins have [their own section](#plugins). The fixes that used to live in plugins are
now done inside the mods, so they hold no matter which protection plugin the server runs.

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
`TLiteProtect` first.

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

**The patch.** `onItemUse` starts with a `TLiteProtect` check on
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
`TLiteProtect` check. When it is refused the click is swallowed.

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

**The patch.** `tryPlaceIntoWorld` starts with a `TLiteProtect`
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
which asks `TLiteProtect` first and returns air for a refused
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
`TLiteProtect` allows the change.

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
  protection check to the stock test.
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
protection check first. On a refusal the Filler stops as if its
pattern were done, and rests for 10 seconds before trying again, so Loop mode can't retry every
tick.

**Verified** with a Filler placed by another player outside a claim, its box on a stone block
inside it, running the Clear pattern. Stock cleared it. Patched left it, and still cleared the
same block on open ground.

</details>

---

## ComputerCraft 1.5

<details>
<summary><b><code>http</code>: a computer reaching localhost or the LAN through the http API</b></summary>

**The bug.** `http.request` resolves and connects with no host filter, so a computer could read the server's own services (dynmap, admin panels) or other machines on the LAN.

**The patch.** `HTTPRequest`'s constructor, after its protocol check, runs `TLiteCC.isBlockedHttp` on the target and throws the mod's own `HTTPRequestException` when the host resolves to a loopback, wildcard, link-local, site-local (private LAN) or IPv6 unique-local address. Public destinations are unaffected; a blocked request fails cleanly in Lua.

**Verified** on the test server: public IPs allowed, loopback/private/localhost blocked.

</details>


<details>
<summary><b><code>turtle</code>: dig, build, take and move inside claims (protection)</b></summary>

**The bug.** Turtles dig, attack, place, suck items from, drop items into and move into the block
next to them with no protection check. A turtle outside a claim empties it or drives in. They
were banned as able to "build in protected areas without permission".

**The patch.**

- Each turtle remembers who placed it, saved in its NBT and carried across moves.
- `move`, `useTool` (dig and attack with any tool upgrade), `place`, `suck` and `dropQuantity`
  each start with a protection check as the owner on the cell they
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

<details>
<summary><b><code>tesla</code>: a PvE switch for the IC2 Tesla Coil</b></summary>

**Why.** The IC2 Tesla Coil (block 223) shocks every living entity in range, players included, with no way to spare them.

**The patch.** `TileEntityTesla.shock`'s attack is routed through `TLiteIC2.teslaShock`. With `config/TeslaCoil.cfg` `basicTeslaCoil.noPlayerDamage` on, players take no damage from the coil; it still clears mobs. Off by default.

**Verified**: the coil's class is patched at load and the redirect is in place; live player-sparing wants an in-game check.

</details>


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
  protection check. A refusal ends the beam, as hitting an
  unminable block does.
- In a Mining Laser explosion each block is checked the same way, and a refused one reads as air,
  so it is neither destroyed nor dropped. Explosions from anything else are left as they were.

**Verified** with a beam straight down onto stone, and an Explosive shot onto a 3x3x3 stone cube,
inside another player's claim. Stock mined the block and the explosion destroyed 9 of the 27.
Patched left the block and all 27, and still mined the block and destroyed 9 on open ground.

</details>

<details>
<summary><b><code>explosion</code>: Nuke, Industrial TNT and dynamite ignore claims, and the nuke crashes the server (protection, crash)</b></summary>

**The bug.** Every IC2 explosion (Nuke, Industrial TNT, reactor meltdown) runs through
`ExplosionIC2.doExplosion`, which removes blocks straight through the world with `world.setBlock`
and never fires the Bukkit `EntityExplodeEvent`. GriefPrevention, WorldGuard and the
TekkitCustomizer `ProtectSurfaceFromExplosives` option all filter that event, so none of them saw
an IC2 explosion: a nuke or ITNT blew up claims and surface builds the same rules protect from
vanilla TNT. Both were banned for it.

IC2 Dynamite and Sticky Dynamite have the same hole through a second class,
`PointExplosion.doExplosionB`, which also removes blocks with `world.setBlock` and fires no event,
so a stick of dynamite blew up claimed blocks too.

There is also a stock crash: `ExplosionIC2.shootRay` runs a binary search over the entities it
collected, but it only collects `EntityLiving` and `EntityItem`, while it decides to run the
search from the raw area query, which also holds the explosive itself and any players (a player is
not an `EntityLiving` in 1.4.7). Detonated near a lone player with no mobs or items in range, the
search does `get(0)` on an empty list and throws, crashing the server with a ticking-entity error.

**The patch (`TLiteFixes` coremod, IC2 is signed).**

- Before the removal loop, `TLiteIC2.ic2ExplodeFilter` fires a real `EntityExplodeEvent` with the
  blocks the explosion is about to take, lets the plugins trim the list exactly as they do for
  vanilla TNT (claimed blocks, and above-surface wilderness where that option is on), then drops
  every trimmed block so the explosion removes only what survived. The exploding entity is the
  igniter when online, else an offline fake player; explosions protect all claims regardless of
  who set them off. Entity damage and the boom effect are unchanged, the same as TNT under GP. The
  Mining Laser's Explosive mode is excluded, since `laser` already checks it per block.
- `shootRay`'s entity-kill block is skipped whenever the collected entity list is empty, so the
  binary search never runs on nothing.
- `PointExplosion.doExplosionB` gets the same treatment through `TLiteIC2.ic2PointFilter` (its
  block set is a `Set`, not a `Map`), so IC2 dynamite now honors claims too.
- On a nuke bomb detonation (only, not ITNT or reactor meltdowns) a server-wide alert is
  broadcast: `☢ <player> set off a Nuke at x,y,z!`.

With this in place **Industrial TNT is unbanned with crafting re-enabled** (`IC2.cfg`
`enableCraftingITnt`). The **Nuke is left banned by choice**: the fixes make it claim-safe and
crash-safe, but like any explosion it still hurts players (server `pvp=false` does not gate TNT or
nuke explosion damage, since the blast is not attributed to the igniting player), and it stays a
policy/balance ban. The fixes still apply to it whenever one is set off (admin or creative), and to
reactor meltdowns, which run the same `ExplosionIC2`. This server's `explosionPowerNuke` is `4.0`
and the separate reactor cap `explosionPowerReactorMax` is `2.0`.

**IC2 Dynamite and Sticky Dynamite are unbanned** now that `PointExplosion` honors claims. The two
Steve's Carts items that were banned as dynamite are unbanned as well: the Dynamite Carrier module
(`31997:31`) detonates through vanilla `World.createExplosion`, which already fires the Bukkit
event GriefPrevention filters, and `31998:6` is only a crafting component. (The four were mislabeled
as one mod's dynamite in the ban list; they are actually IC2 items `30214`/`30215` and Steve's
Carts items, not Balkon's, whose own `dynamite` fix is separate.)

GriefPrevention's per-claim explosives toggle is honored: it strips claimed blocks unless the
owner has enabled explosives for that claim (`/claimexplosions`, `Claim.areExplosivesAllowed`),
which is off by default. So a nuke set off inside or outside a claim does no block damage there
until the owner opts in. To catch that opt-in case, the TekkitCustomizer plugin warns a player
who places a Nuke block (237) inside a claim that currently has explosives enabled, so they know
it will actually damage the claim. The plugin reads GriefPrevention through a `MethodHandles`
bridge (`ClaimQuery`) rather than `getField`, since GriefPrevention has a Vault `Economy` field
and this server has no Vault; enumerating its members would otherwise fail.

**Verified** on the test server (`tlfix explode`): a power-4.0 nuke destroyed **0 of 6** claimed
blocks detonated one block outside a claim border, and **0 of 13** detonated at the claim's centre,
on the patched coremod, versus **1 of 6** and **3 of 13** on stock; both destroyed open-ground
blocks, and the coremod logged the broadcast `☢ Intruder set off a Nuke at 233,31,252!`. The build
check also confirms `ExplosionIC2` is patched at all three sites (per-block laser check, the
explode-event filter, and the crash guard). The placement warning's claim check was verified too
(`tlfix nukewarn`): the plugin's `ClaimQuery` read `false` with explosives off, `true` with them
on, and `false` in the wilderness, on a server with GriefPrevention and no Vault. IC2 dynamite was
checked the same way (`tlfix dynamite`): a `PointExplosion` one block outside a claim border
destroyed **0 of 6** claimed blocks patched versus **6 of 6** on stock, both clearing open ground.

</details>

<details>
<summary><b><code>wrench</code>, <code>hoe</code>, <code>sprayer</code>, placement items, <code>miner</code>, <code>pump</code>, <code>terraformer</code>: IC2 tools and machines edit claims (protection)</b></summary>

**The bug.** A sweep for tools and machines that change blocks with no protection check turned up
six in IC2 (Applied Energistics, Balkon's ranged weapons and Modular Powersuits were clean). Three
are right-click tools: the **Wrench** dismantles a machine to item (so a player could pocket
machines in another's claim), the **Electric Hoe** tills soil, and the **Foam Sprayer** lays foam.
Three are automated machines that edit the world in their tick with no owner: the **Miner** mines a
shaft, the **Pump** removes liquids, and the **Terraformer** reshapes terrain over a radius.

**The patch.**

- The Wrench and Hoe route their `world.setBlock` through `TLiteIC2.wrenchEdit`, checked against
  the acting player (available in the use method), the same as a hand break. The Foam Sprayer, whose
  edits are deeper where no player is in scope, is guarded at its use method on the clicked block.
- The Miner, Pump and Terraformer are owner-tracked: `BlockMultiID.onBlockPlacedBy` records the
  placer of every IC2 machine and `TileEntityElecMachine` saves it in NBT, and each machine's block
  edits go through `TLiteIC2.machineSet` as that owner. A machine placed before this patch has no
  owner and works only on open ground.
- The right-click placement items that write blocks directly (Cable, Barrel, Resin, Luminator, and
  the Cell, which drains a liquid source) are guarded the same way as the Wrench, against the acting
  player in their use method. GriefPrevention's interact handler does not stop these on its own: it
  only cancels right-clicks on specific vanilla blocks (containers, doors, buttons) and items, not a
  generic IC2 item placing a block, and these never fire `BlockPlaceEvent`. The Foam Sprayer records
  the player at its use method and checks every foam block it lays in `sprayFoam`, so foam sprayed
  from open ground cannot spill across a claim border, not just the clicked block. The Painter and
  Treetap were checked and change block state through mod methods, not `setBlock`, so they are not
  this kind of bypass.

**Verified** on the test server (`tlfix wrench`, `tlfix ic2machine`): both guards refused an
intruder's edit inside another player's claim, allowed the claim owner's own, and allowed any on
open ground; the build check patches all six classes plus the placement hook and NBT.

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

<details>
<summary><b><code>breaker</code>, <code>igniter</code>, <code>deployer</code>: RedPower machines act inside claims (protection)</b></summary>

**The bug.** The Block Breaker breaks the block in front, the Igniter lights fire against it, and
the Deployer uses the held item on it, all straight through the world with no protection check.
A machine placed just outside a claim, facing in, mines, burns or builds into the claim.

**The patch.** Each machine records the player who placed it (saved to its NBT, hooked into
`TileMachine`), and the break, fire-set and deploy go through a
`TLiteProtect` check as that owner: the owner's machine works in
the owner's claim and is refused in others'. A machine placed before this patch has no owner and
is checked as `[RedPower]`, a name no claim trusts, so it works only on open ground. Removing
stray fire is left alone. All are patched at load by the coremod (the RedPower jars are signed).

**Verified** on the test server: the coremod logs `patched ...TileMachine`, `...TileDeployBase`,
`...TileBreaker` and `...TileIgniter` and RedPower loads normally.

</details>

<details>
<summary><b><code>frame</code>: RedPower frame motors move blocks through claims (protection)</b></summary>

**The bug.** A Frame Motor relocates a whole frame structure by writing the moved blocks and their
tile entities straight to the world (`TileMotor.pickFrame` / `dropFrame`), with no Bukkit event and
no permission check, and the motor records no owner. A motor sitting outside a claim can shove a
moving frame across the border, overwriting or displacing the claimed blocks in its path, and
GriefPrevention never hears about it. This is the automation-grief the RedPower computer ban was
partly about, and the only frame edit the `breaker`/`deployer` patches did not already cover.

**The patch.** The motor is owner-tracked like the other RedPower machines (placer saved to NBT).
In `pickFrame`, before `FrameSolver.addMoved` (which is the first thing to write to the world),
`TLiteRPMachine.frameAllowed` checks every block the solved frame would move and every destination
it would write, as the motor's owner. If any is protected the whole move is refused there and then,
using the motor's own abort path, so nothing is touched. A motor with no owner is checked as
`[RedPower]`, so it moves frames only on open ground. Server side only; the client defers to the
server.

**Verified** on the test server (`tlfix frame`): a solved frame carrying a block was **refused** for
an intruder's motor inside another player's claim, **allowed** for the claim owner's own motor
there, and **allowed** for any motor on open ground; the coremod's build check patches `TileMotor`
at all four sites (placement, NBT read, NBT write, and the pickFrame guard).

</details>

<details>
<summary><b><code>thermopile</code>, <code>grate</code>: RedPower environmental blocks edit claims (protection)</b></summary>

**The bug.** Two more RedPower tiles edit the world directly with no event or permission check. The
**Thermopile** (`TileThermopile.updateTemps`) consumes adjacent water (to obsidian/stone), lava (to
cobble/air) and fire, so one placed next to a claim slowly eats the liquids and fire inside it. The
**Grate** (`TileGrate$GratePathfinder`) drains fluids by flood-filling and setting blocks to air,
which can drain a claim's fluids from outside.

**The patch.** Both route their edits through the shared `TLiteRPMachine` guard. The Grate has an
`onBlockPlaced`, so it is owner-tracked like the machines and works in its owner's claim. The
Thermopile has no placer hook (it is not a `TileMachine`), so it is treated as ownerless: its
block-eating is refused inside any claim and allowed only on open ground. It still generates power
from the temperature difference either way, since that does not depend on consuming the blocks.

**Verified** on the test server (`tlfix rpguard`): the shared guard refused an ownerless tile's edit
inside a claim, refused an intruder-owned tile's, allowed the claim owner's own, and allowed all on
open ground; the build check patches `TileThermopile`, `TileGrate` and `TileGrate$GratePathfinder`.

Two related things were checked and found already safe: RedPower **microblock/cover placement** goes
through `world.setBlock(...,entity)`, which fires the Bukkit `BlockPlaceEvent` GriefPrevention
filters, so covers cannot be placed in others' claims; and the other **GUI event handlers** set
scalar config (colour, mode, priority) that is used as a value or a bitmask, not an array index, so
they have no out-of-bounds like the one the `sorter` patch fixed.

</details>

<details>
<summary><b><code>netevent</code>: cycle any energy storage block's redstone mode or claim an Energy-O-Mat from anywhere</b></summary>

**The bug.** `NetworkManager.onPacketData` (packet 3) looked up the target tile across every
dimension from client coordinates and called its `onNetworkEvent` with no reach or dimension
check. Any player could cycle any BatBox/CESU/MFE/MFSU's redstone-output mode, or claim an
unopened Energy-O-Mat, from anywhere on the server and in any dimension.

**The patch.** The dispatch goes through `TLiteIC2.netEvent`, which runs the event only when the
tile is in the sender's own world and within reach. Patched at load by the coremod (IC2 is signed).

**Verified** on the test server: the coremod logs `patched ic2.core.network.NetworkManager` and IC2 loads.

</details>

<details>
<summary><b><code>sorter</code>: RedPower Sorter GUI colour index out of bounds</b></summary>

**The bug.** `ContainerSorter.handleGuiEvent` bounded a colour index with `i <= 8`, but the
colour array is length 8 (valid 0-7), so a crafted index of 8 threw an uncaught exception in the
packet handler.

**The patch.** The bound is tightened to `i <= 7`. Patched at load by the coremod.

**Verified** the build-check applies it to the stock class; it loads lazily with the Sorter GUI,
so it does not appear in the boot log.

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
<summary><b><code>requestclamp</code>: a huge request quantity as a denial of service</b></summary>

**The bug.** The request packet's amount is an unvalidated client int that sizes the crafting tree, so a near-max value drove that planning as a DoS.

**The patch.** `RequestHandler.request` and `simulate` route `packet.amount` through `TLiteLP.clampAmount` before `ItemIdentifier.makeStack`, bounding it to 100000 (far above any real request) and flooring negatives at zero. The liquid path is left alone.

**Verified**: 5/64/100000 pass unchanged; 100001 and MAX_INT clamp to 100000; -7 to 0.

</details>

<details>
<summary><b><code>security</code>: rewrite or lock any Security Station from anywhere</b></summary>

**The bug.** The four security-station packet handlers looked up the station by the packet's coordinates and rewrote it with no check that the sender was interacting with it, so anyone could rewrite or lock any station remotely.

**The patch.** Each handler routes through `TLiteLP.securityAllowed` after the tile cast: the packet is honoured only if the sender is in the station's viewer list (has its GUI open). Closes the remote takeover without locking out anyone who could already edit it.

**Verified**: no viewer refused, viewer allowed, a different-viewer sender refused.

</details>


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

<details>
<summary><b><code>apparity</code>: the Teleport Tether becomes a capped single-chunk loader (ChunkLoaderConversion)</b></summary>

**Why.** The AdditionalPipes chunk loader (the "Teleport Tether", block 4077) force loaded an area
of chunks through a Forge ticket, offline included, with no owner and no per-player limit, so it was
banned. Rather than leave it disabled, apparity rebuilds it as a peer of the other two loaders.

**The patch.** The mod tracks no owner and has no placement hook, so this adds the whole system:
- a public `tliteOwner` field on `TileChunkLoader`, set from the placer by a new `onBlockPlacedBy`
  on `BlockChunkLoader` and persisted in the tile's NBT;
- `getLoadArea` is forced to a single chunk (`loadDistance` 0);
- the tick `s()` force loads only when `TLiteAP.apShouldLoad` allows it: the master switch
  `additionalpipes.chunkloader.enabled` (now on by default) is set, the loader has an owner, the
  owner is online or within grace, and the owner is under the shared per-player cap (via
  `TLiteChunkQuota`); otherwise it stops loading.

So the Tether is now a single-chunk, owner-tracked loader in the same per-player budget as the
others, and it is **unbanned with its recipe re-enabled** (`APUnofficial.cfg` `-4077` -> `4077`).
Like the anchor, one offline past the grace deactivates and revives when its chunk next loads.

**Teleport pipes are not touched.** A teleport pipe removes itself from the network when its chunk
unloads (`invalidate` and `onChunkUnload` both call `TeleportManager.remove`), so an item sent
toward a destination in an unloaded chunk finds no target and drops at the source pipe rather than
teleporting into an unloaded chunk.

**Verified**: all six injections are present in the patched jar and it loads clean; live placement,
ownership and offline behaviour want an in-game check.

</details>

---

## ChickenChunks 1.3.1.0

<details>
<summary><b><code>spotloader</code>: pin every Chunk Loader to its own chunk (ChunkLoaderConversion)</b></summary>

**Why.** ChickenChunks has a single-chunk Spot Loader and an adjustable Chunk Loader (block 2048).
A placed Chunk Loader activates covering a 3x3 area and its GUI grows it up to `maxchunks` (400 in
`ChickenChunks.cfg`), so one player can keep a large region loaded. The goal is that every loader
is a single-chunk spot loader, with the per-player limit deciding how many.

**The patch.** `TileChunkLoader.getChunks()` is the one method that returns the chunks a loader
keeps open; ChickenChunks both loads and quota-counts through it. The radius it passes to
`getContainedChunks` is forced to 1, which that method turns into the single centre chunk (it loads
`getLoadedChunks(cx, cz, radius - 1)`, so 1 means radius 0). The stored radius is left alone, and
loaders saved before the patch also drop to one chunk on their next activation.

**Per-player count.** With every loader one chunk, a per-player chunk quota is a per-player
spot-loader count, and `combinedquota` (below) makes that quota authoritative and shared with the
Dimensional Anchors. `allowoffline{ DEFAULT=false }` already stops a player's loaders while they are
logged out.

**Verified** on the test server: a loader set to radius 3 loads 25 chunks on the stock jar and 1
on the patched jar.

</details>

<details>
<summary><b><code>combinedquota</code>: one per-player chunk cap shared with the anchors</b></summary>

**Why.** ChickenChunks and Dimensional Anchors each enforce their own per-player quota, so a player
could load a full ChickenChunks allowance and a separate anchor allowance on top. The goal is one
authoritative total: no more than N chunks loaded per player across every loader.

**The patch.** `ChunkLoaderManager.addChunkLoader` and `remChunkLoader` route through
`TLiteChunkQuota`, a shared per-owner set of loaded chunks that the anchor patch feeds too. A loader
that would take the owner past the limit does not register, so it loads nothing. The limit is the
owner's ChickenChunks per-player limit (`ChickenChunks.cfg` `players{}`), which stays authoritative,
unless `config/ChunkLoaderConversion.cfg` sets `chunkloader.maxchunksperplayer` to 0 or more (0
means no cap). Server-owned and ownerless loaders are not capped. The count is in memory and rebuilt
as loaders re-register on world load, so a restart heals any drift and every failure mode is a
conservative under-count.

**Verified** on the test server with the limit forced to 3: one owner's first three ChickenChunks
claims are allowed, the next two are refused, and a same-owner Dimensional Anchor is refused because
the three ChickenChunks chunks already fill the shared budget (registry: 3 active, 3 disabled, 6
listed).

**Player-facing.** `TLiteChunkQuota` also backs a small registry used across the loaders: placing a
loader messages the owner "Chunk loaders: N/6" (or, at the cap, "disabled: you are at your limit");
`/loaders` lists a player's loaders and on/off status (`/loaders <player>` needs op or
`tekkitcustomizer.loaders.others`); and an owner over the limit is reminded on login. Offline
shutdown is uniform too: the Dimensional Anchor and the Teleport Tether now stop loading when their
owner is offline past a ~10-minute grace (ChickenChunks already did via `allowoffline`).

</details>

---

## Dimensional Anchors 52.2.0

<details>
<summary><b><code>spotloader</code>: pin every Dimensional Anchor to its own chunk (ChunkLoaderConversion)</b></summary>

**Why.** The immibis Dimensional Anchor (block 4090) is placed as a single chunk but its GUI grows
the loaded area (square or line, any radius) up to the player's quota. The goal is the same as for
ChickenChunks: every anchor loads one chunk, and the per-player quota decides how many.

**The patch.** `TileChunkLoader.limitRadius()` runs on every activation (placement, world load, and
after any GUI change). A clamp is prepended so a radius above 0 is reset to 0, before the method's
own quota logic, so it applies whether the quota type is unlimited or perplayer. The existing
radius -1 (no owner, inactive) case is left alone. Anchors saved with a larger radius shrink to one
chunk the next time they activate.

**Per-player count.** The anchor's own `immibis.cfg` quota is no longer relied on: `combinedquota`
(below) counts anchors against the same shared per-player budget as the ChickenChunks loaders, so a
player's total loaded chunks across both mods cannot exceed the one limit.

**Verified** on the test server: an anchor set to radius 3 loads 49 chunks on the stock jar and 1
on the patched jar.

</details>

<details>
<summary><b><code>combinedquota</code>: count anchors against the shared per-player cap</b></summary>

**Why.** So a Dimensional Anchor and a ChickenChunks loader owned by the same player draw from one
per-player chunk budget rather than two separate ones.

**The patch.** `WorldInfo.addLoader`, `removeLoader` and `delayRemoveLoader` route through
`TLiteChunkQuota`, the same shared per-owner counter the ChickenChunks patch uses. An anchor that
would take its owner past the limit does not register and loads nothing. The limit and its config
override are described under [ChickenChunks `combinedquota`](#chickenchunks-1310).

**Verified** on the test server: with the shared limit filled by ChickenChunks claims, a same-owner
anchor is refused.

</details>

---

## IC2NuclearControl 1.4.6

<details>
<summary><b><code>cardcap</code>: flooding one Info Panel's sensor-card NBT</b></summary>

**The bug.** The reach gate still left a player next to a panel able to flood one sensor card: the card packet writes a client-named field with `setInt`/`setBoolean`/`setLong`/`setString` and nothing bounded how many distinct keys were added, growing the card's NBT until its chunk failed to save.

**The patch.** Each setter checks `TLiteNC.allowCardField` first, refusing a new key once the card already holds 32 of them. A legit card uses a handful of fields, well under the cap.

**Verified**: a 40-key card refuses new keys but still updates existing ones; a fresh card is unaffected.

</details>


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

**The patch.** The click starts with a `TLiteProtect` check for the
player, the same as a hand break, and does nothing when refused. The player is online, so this
uses their real build permission: their own claim is fine, someone else's is refused.

**Verified** on the test server: the guard is injected and OmniTools loads.

</details>

---

## Balkon's Weaponmod

<details>
<summary><b><code>dynamite</code>: dynamite blasts blocks inside claims (protection)</b></summary>

**The bug.** Dynamite, the Cannon and the Warhammer all explode through the mod's own
`AdvancedExplosion`, which removes blocks with `world.setBlockWithNotify` instead of a vanilla
explosion, so it never fires the Bukkit `EntityExplodeEvent` that GriefPrevention filters. A thrown
stick of dynamite blew up blocks inside another player's claim, and block damage is on by default.

**The patch.** Each block the explosion would remove goes through `TLiteWM.breakIfAllowed`, which
checks it against the player who threw the dynamite, the same as a hand break. Dynamite with no
player behind it is treated as untrusted and breaks nothing in a claim. The Cannon (`EntityCannonBall`)
and the Warhammer share this one `doBlockExplosion`, so they are covered by the same patch; the
Warhammer's own explosion only damages entities and edits no blocks. None of the three is banned.

**Verified** on the test server: the removal is routed through the guard and the mod loads.

</details>

---

## WR-CBE Wireless Redstone 1.3.2.8

<details>
<summary><b><code>freq</code>: seize private frequencies and retune anyone's wireless tiles</b></summary>

**The bug.** The server packet handler accepted two packets a client should never send:

- packet 9 reassigned any frequency's owner to any name, so a player could take over another
  player's private frequency (and the redstone it drives).
- packet 1 retuned any wireless tile at attacker-chosen coordinates, checked only against the
  frequency, not the tile, so a receiver inside a claim could be retuned from across the map.

**The patch.** Packet 9 is dropped (it is only ever a server-to-client broadcast). The packet-1
tile lookup goes through `TLiteWR.gateTile`, which returns the tile only when the sender is
within reach, so a player can only retune tiles next to them.

**Verified** on the test server: both sites are routed through the guards and the mod loads.

</details>

---

## Steve's Carts 2.0.0.a62

<details>
<summary><b><code>carts</code>: mining and building cart modules act inside claims (protection)</b></summary>

**The bug.** The mining, remover, wood, farm, torch, railer and melter cart modules break and
place blocks straight through the world with no protection check, so a cart running past a claim
mines or builds into it.

**The patch.** Each cart records the player who deployed it (saved to its NBT), and every module
block change (14 sites across the module classes) goes through a
`TLiteProtect` check as that owner: the owner's cart works in the
owner's claim and is refused in others'. A cart deployed before this patch, or by something other
than a player, has no owner and is checked as `[StevesCarts]`, a name no claim trusts, so it works
only on open ground.

**Verified** on the test server: the spawn, NBT and 14 module edits are routed through the guards
(build asserts the exact counts) and the mod loads.

</details>

<details>
<summary><b><code>carts</code>: the Chunk Loader cart module is capped like every other loader (chunk loading)</b></summary>

**The bug.** The Chunk Loader module (`31997:49`) force-loads chunks through `ForgeChunkManager`
on an `ENTITY` ticket, outside the shared per-player quota that ChickenChunks, the Dimensional
Anchor and the Teleport Tether register with (see [ChickenChunks](#chickenchunks-1310)). It also
force-loaded a 3x3 block of chunks (`setChunkListDepth(9)` and a `-1..1` double loop in
`entMCBase.loadChunks`), so one cart kept nine chunks live and none of it counted against the cap.

**The patch (same `carts` flag).** `entMCBase.loadChunks(Ticket,x,z)` (the single point both the
module and the chunk-crossing listener reach) is cut to force only the cart's own chunk (the two
loop starts and bounds are flipped to `0`). The loader is then registered in the shared
`TLiteChunkQuota` as the cart's owner, keyed by a stable per-cart id because the cart moves:
`initChunkLoading` refuses (requests no ticket, messages the owner) when the owner is at their limit
or offline past the grace window, `loadChunks` re-checks the same as the cart moves, and
`dropChunkLoading` releases the registry entry. A cart with no recorded owner (deployed before this
patch, or not by a player) is not capped, matching the module edit guard.

**Verified** on the test server by the `cartchunk` scenario (with the shared limit forced to 3):
an offline owner's cart loader is refused by the online gate; a recently-seen owner gets exactly 3
of 5 cart loaders active with the other 2 disabled but listed; cart loaders share the budget with
the owner's ChickenChunks loaders (2 anchors + 1 cart fill the limit, the next cart is refused); and
releasing an active loader frees the budget for a disabled one. The `cartmine` guard still passes.

</details>

---

## AdvancedPowerManagement 1.1.55

<details>
<summary><b><code>outputdupe</code>: Battery Station output item dupe</b></summary>

**The bug.** `TEBatteryStation.moveOutputItems` raised the output slot's stack by one for any discharged item without checking the slot already held the same item, so discharging a different empty electric item into an occupied output slot minted the output item.

**The patch.** The increment is guarded by `TLiteAPM.canMerge(contents[1], contents[i])`, so only genuinely stackable items merge; normal same-item stacking is unchanged.

**Verified**: same-item merges; a different item and null are refused.

</details>


<details>
<summary><b><code>guibutton</code>: toggle any Battery Station's mode from anywhere</b></summary>

**The bug.** The GUI-button packet ran `receiveGuiButton` on the machine at the client's
coordinates with no reach check, so a player could flip any Battery Station's operating mode or
an Emitter's packet size from anywhere.

**The patch.** The button goes through `TLiteAPM.guiButton`, which applies it only when the sender
is within reach of the machine in the same world.

**Verified** on the test server: the call is routed through the reach gate and the mod loads.

</details>

---

## Advanced Repulsion Systems 52.0.6

<details>
<summary><b><code>tesla</code>: PvE and drop-deny switches for the Industrial Tesla Coil</b></summary>

**Why.** The Industrial Tesla Coil (block 1952) shocks its target in `fireShot` with no way to spare players or withhold mob loot.

**The patch.** Two default-off flags in `config/TeslaCoil.cfg`: `industrialTeslaCoil.noPlayerDamage` makes `fireShot` skip player targets (PvE), and `industrialTeslaCoil.denyMobDrops` routes the shock through `TLiteARS.shock`, which registers a `LivingDropsEvent` handler keyed to this coil's own damage source and cancels the drops of anything it kills. Both off by default.

**Verified** on the test server: both flags read from config, and the drops handler cancels the coil's own source while leaving a different source alone; live behaviour wants an in-game check.

</details>

---

## Modular Powersuits 0.7

<details>
<summary><b><code>tweak</code>: crash the server every tick with a crafted tinker packet</b></summary>

**The bug.** The tinker "tweak" packet wrote a client-named key into a module's NBT as a double.
Sending a reserved key such as `Active`, which the module tick reads as a boolean, made every
server tick throw a `ClassCastException`.

**The patch.** The write goes through `TLiteMPS.tweak`, which rejects the reserved keys.

**Verified** on the test server: the write is routed through the guard and the mod loads. (The
separate bad-item-slot NPE in the same packet family is a one-shot handler error, not fixed.)

</details>

---

## Mystcraft 0.10.1

<details>
<summary><b><code>linknull</code>: a link book to a bad dimension crashes the link</b></summary>

**The bug.** `LinkController.travelEntity` fetched the destination world, logged if it was null,
but did not stop, then dereferenced the null world. A link book carrying a bad or removed
dimension id crashed the link server-side.

**The patch.** `travelEntity` returns as soon as the destination world is null.

**Verified** on the test server: the null-world return is injected and Mystcraft loads.

</details>

---

## Dimensional Doors 1.3.2

Not installed on the server. It ships in the client pack (which is why the Rift Blade shows in
NEI), but no server jar was present, so on the server the rift items do nothing. This is an
optional, patched build for anyone who wants Dimensional Doors working on the server without the
grief, kept out of the deployed zip and taken from the repo instead.

<details>
<summary><b><code>rifts</code>: rift and door items respect claims (protection)</b></summary>

**The bug.** The Dimensional Door, Chaos Door and Exit Door items, the Rift Blade, and the Rift
Signature place blocks in whatever world the holder is in with no protection check, so a player can
drop a rift or a dimensional door inside someone else's claim.

**The patch.** Each player-facing use is checked as the holding player through `TLiteProtect`. The
door and signature `onItemUse` (`itemDimDoor`, which the Chaos and Exit doors inherit, plus
`ItemRiftBlade` and `itemLinkSignature`) return early (`false`, so the item is not consumed) when
the player may not build at the clicked block. The Rift Blade's air-cast rift-open routes the
vanilla `ItemDoor` placement (`tx.a`) through `TLiteDD.placeDoor(..., player)`, which places only
where the player may build; refused, it does nothing. So rifts and doors cannot land in a claim,
and the Rift Blade stays a working melee weapon that only opens rifts where its holder could build.
In-dimension edits (pocket dims, Limbo, dungeon generation) are untouched: there are no claims
there.

Two switches in `config/DimensionalDoorsTweaks.cfg` (written on first load) tune the non-dimension
items. `riftBlade.swordOnly` (default `false`) disables the Rift Blade's rift-opening outright: its
right-click methods (air-cast, charged use and block use) become no-ops, so it is a plain melee
sword everywhere regardless of claims. `riftRemover.enabled` (default `true`) can make the Rift
Remover inert; the Remover only closes rifts (via `dimHelper.removeRift`, ray-traced onto a rift
block) and never creates anything, so it is left on by default. The mod's own per-item
`enable ... crafting` flags still govern what can be crafted at all.

The Rift Goggles reveal nearby rifts purely client-side, so there is nothing to guard or toggle
server-side; the Stable Fabric item is only a door crafting ingredient (its right-click is a
leftover debug print that places nothing), so it needs no guard either.

**Verified**: build asserts the exact counts (3 `onItemUse` guards, 2 blade placement redirects,
3 sword-only gates, 1 rift-remover gate);
the target classes are v50, so no stack-map frames are needed. Not exercised on the test server,
which does not run Dimensional Doors.

</details>

---

## Plugins

Two Bukkit plugins used to cover these bugs. The patches replace what they did:

| Plugin | What it did | Now |
|---|---|---|
| GriefPrevention-TLite | GriefPrevention 7.6.2 plus claim checks for the Entropy Accelerator, Vibration Catalyst, Minium Stone, Wrath Igniter and ME Storage Monitor | Removed. `entropy`, `catalyst`, `protect`, `wrathigniter` and `monitor` do this inside the mods and work with stock GriefPrevention 7.6.2 |
| TekkitCustomizer | TekkitCustomizer 1.6 item bans plus `AdjacentBlockDupePatch`, which refused placing a Block Breaker next to a Deep Storage Unit | Dupe ban removed, `dsudupe` fixes it inside MFR. The item bans stay |

`plugins/TekkitCustomizer` is the plugin's source, recovered from IntelliJ's local history:
`src/` is what `build.sh` builds into `TekkitCustomizer.jar`, `version-history/` has every
recovered version in order, and `decompiled-deployed-jar/` is the jar that ran on the server
before, dupe ban included.

### What changed in TekkitCustomizer

- The `/loaders [player]` command and the `tekkitcustomizer.loaders.others` permission, backed by
  `LoaderRegistry`, which reads the coremod's shared chunk-loader registry (`TLiteChunkQuota`) by
  reflection.
- GriefPrevention awareness through `ClaimQuery`, which reaches GriefPrevention's data store
  (`getClaimAt`, `areExplosivesAllowed`) by reflection, so it works with the stock jar and takes no
  hard dependency (`GriefPrevention` is only a `softdepend`).
- The over-limit chunk-loader login reminder.

and drops `AdjacentBlockDupePatch` (the ban on placing a Block Breaker next to a Deep Storage Unit);
`dsudupe` fixes that dupe inside MineFactoryReloaded instead. The item ban list is unchanged.

---

## Build

<details>
<summary><b>Building the patched jars from the stock jars</b></summary>

```sh
./build.sh
```

Builds every patched jar, `TLiteFixes-coremod.jar` and `TekkitCustomizer.jar`. Needs a Java 8 `javac` for the helper
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
test/run.sh patched probe unifier ee3 entropy catalyst wrath monitor treecap spawner creative dsu mfrpacket laser act2 bag filler quarry quarrychunks turtle ccpacket harvester cartmine cartchunk frame rpguard
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
| `TekkitCustomizer.jar` | `TekkitCustomizer.jar` from this repo |

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
