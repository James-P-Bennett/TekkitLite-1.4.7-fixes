package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

public class BlockEventHandler implements Listener {
   private DataStore dataStore;
   private ArrayList<Material> trashBlocks;
   private Claim lastSpreadClaim = null;

   public BlockEventHandler(DataStore dataStore) {
      this.dataStore = dataStore;
      this.trashBlocks = new ArrayList<>();
      this.trashBlocks.add(Material.COBBLESTONE);
      this.trashBlocks.add(Material.TORCH);
      this.trashBlocks.add(Material.DIRT);
      this.trashBlocks.add(Material.SAPLING);
      this.trashBlocks.add(Material.GRAVEL);
      this.trashBlocks.add(Material.SAND);
      this.trashBlocks.add(Material.TNT);
      this.trashBlocks.add(Material.WORKBENCH);
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockDamaged(BlockDamageEvent event) {
      if (GriefPrevention.instance.config_addItemsToClaimedChests) {
         Block block = event.getBlock();
         Player player = event.getPlayer();
         if (player != null) {
            if (block.getType() == Material.CHEST) {
               if (player.getGameMode() == GameMode.CREATIVE) {
                  return;
               }

               PlayerInventory playerInventory = player.getInventory();
               ItemStack stackInHand = playerInventory.getItemInHand();
               if (stackInHand == null || stackInHand.getType() == Material.AIR) {
                  return;
               }

               Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, null);
               if (claim == null || claim.allowContainers(player) == null) {
                  return;
               }

               PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getName());
               if (playerData.siegeData != null) {
                  GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeNoDrop);
                  event.setCancelled(true);
                  return;
               }

               if (playerData.inPvpCombat()) {
                  return;
               }

               if (playerData.lastChestDamageLocation != null && block.getLocation().equals(playerData.lastChestDamageLocation)) {
                  Chest chest = (Chest)block.getState();
                  Inventory chestInventory = chest.getInventory();
                  int availableSlot = chestInventory.firstEmpty();
                  if (availableSlot < 0) {
                     GriefPrevention.sendMessage(player, TextMode.Err, Messages.ChestFull);
                     return;
                  }

                  chestInventory.addItem(new ItemStack[]{stackInHand});
                  playerInventory.setItemInHand(new ItemStack(Material.AIR));
                  GriefPrevention.sendMessage(player, TextMode.Success, Messages.DonationSuccess);
               } else {
                  playerData.lastChestDamageLocation = block.getLocation();
                  GriefPrevention.sendMessage(player, TextMode.Instr, Messages.DonateItemsInstruction);
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onBlockBreak(BlockBreakEvent breakEvent) {
      Player player = breakEvent.getPlayer();
      Block block = breakEvent.getBlock();
      String noBuildReason = GriefPrevention.instance.allowBreak(player, block.getLocation());
      if (noBuildReason != null) {
         GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
         breakEvent.setCancelled(true);
      } else {
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         Claim claim = this.dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);
         if (claim != null && block.getY() < claim.lesserBoundaryCorner.getBlockY() && claim.allowBuild(player) == null) {
            this.dataStore
               .extendClaim(claim, claim.getLesserBoundaryCorner().getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance);
         }

         if (block.getType() == Material.LOG && GriefPrevention.instance.config_trees_removeFloatingTreetops) {
            GriefPrevention.instance.handleLogBroken(block);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onSignChanged(SignChangeEvent event) {
      Player player = event.getPlayer();
      if (player != null) {
         StringBuilder lines = new StringBuilder();
         boolean notEmpty = false;

         for (int i = 0; i < event.getLines().length; i++) {
            if (event.getLine(i).length() != 0) {
               notEmpty = true;
            }

            lines.append(event.getLine(i) + ";");
         }

         String signMessage = lines.toString();
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         if (notEmpty && playerData.lastMessage != null && !playerData.lastMessage.equals(signMessage)) {
            GriefPrevention.AddLogEntry(
               "[Sign Placement] <"
                  + player.getName()
                  + "> "
                  + lines.toString()
                  + " @ "
                  + GriefPrevention.getfriendlyLocationString(event.getBlock().getLocation())
            );
            playerData.lastMessage = signMessage;
            if (!player.hasPermission("griefprevention.eavesdrop")) {
               Player[] players = GriefPrevention.instance.getServer().getOnlinePlayers();

               for (int i = 0; i < players.length; i++) {
                  Player otherPlayer = players[i];
                  if (otherPlayer.hasPermission("griefprevention.eavesdrop")) {
                     otherPlayer.sendMessage(ChatColor.GRAY + player.getName() + "(sign): " + signMessage);
                  }
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
   public void onBlockPlace(BlockPlaceEvent placeEvent) {
      Player player = placeEvent.getPlayer();
      Block block = placeEvent.getBlock();
      if (block.getType() == Material.FIRE
         && !GriefPrevention.instance.config_pvp_enabledWorlds.contains(block.getWorld())
         && !player.hasPermission("griefprevention.lava")) {
         List<Player> players = block.getWorld().getPlayers();

         for (int i = 0; i < players.size(); i++) {
            Player otherPlayer = players.get(i);
            Location location = otherPlayer.getLocation();
            if (!otherPlayer.equals(player) && location.distanceSquared(block.getLocation()) < 9.0) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerTooCloseForFire, otherPlayer.getName());
               placeEvent.setCancelled(true);
               return;
            }
         }
      }

      String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation());
      if (noBuildReason != null) {
         GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
         placeEvent.setCancelled(true);
      } else {
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         Claim claim = this.dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);
         if (claim != null) {
            if (block.getType() == Material.TNT && !claim.areExplosivesAllowed) {
               GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoTNTDamageClaims);
               GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimExplosivesAdvertisement);
            }

            if (block.getY() < claim.lesserBoundaryCorner.getBlockY() && claim.allowBuild(player) == null) {
               this.dataStore
                  .extendClaim(claim, claim.getLesserBoundaryCorner().getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance);
            }

            playerData.unclaimedBlockPlacementsUntilWarning = 1;
         } else if (block.getType() == Material.CHEST
            && GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius > -1
            && GriefPrevention.instance.claimsEnabledForWorld(block.getWorld())) {
            if (GriefPrevention.instance.config_claims_preventTheft && block.getY() < GriefPrevention.instance.config_claims_maxDepth) {
               GriefPrevention.sendMessage(player, TextMode.Warn, Messages.TooDeepToClaim);
               return;
            }

            int radius = GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius;
            if (playerData.claims.size() == 0) {
               if (GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius == 0) {
                  this.dataStore
                     .createClaim(
                        block.getWorld(), block.getX(), block.getX(), block.getY(), block.getY(), block.getZ(), block.getZ(), player.getName(), null, null
                     );
                  GriefPrevention.sendMessage(player, TextMode.Success, Messages.ChestClaimConfirmation);
               } else {
                  while (
                     radius >= 0
                        && !this.dataStore
                           .createClaim(
                              block.getWorld(),
                              block.getX() - radius,
                              block.getX() + radius,
                              block.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance,
                              block.getY(),
                              block.getZ() - radius,
                              block.getZ() + radius,
                              player.getName(),
                              null,
                              null
                           )
                           .succeeded
                  ) {
                     radius--;
                  }

                  GriefPrevention.sendMessage(player, TextMode.Success, Messages.AutomaticClaimNotification);
                  Claim newClaim = this.dataStore.getClaimAt(block.getLocation(), false, null);
                  Visualization visualization = Visualization.FromClaim(newClaim, block.getY(), VisualizationType.Claim, player.getLocation());
                  Visualization.Apply(player, visualization);
               }

               GriefPrevention.sendMessage(player, TextMode.Instr, Messages.TrustCommandAdvertisement);
               if (!GriefPrevention.instance.config_claims_creationRequiresPermission) {
                  GriefPrevention.sendMessage(player, TextMode.Instr, Messages.GoldenShovelAdvertisement);
               }
            }

            if (GriefPrevention.instance.config_claims_preventTheft && this.dataStore.getClaimAt(block.getLocation(), false, playerData.lastClaim) == null) {
               GriefPrevention.sendMessage(player, TextMode.Warn, Messages.UnprotectedChestWarning);
            }
         } else if (block.getType() == Material.SAPLING
            && GriefPrevention.instance.config_blockSkyTrees
            && GriefPrevention.instance.claimsEnabledForWorld(player.getWorld())) {
            Block earthBlock = placeEvent.getBlockAgainst();
            if (earthBlock.getType() != Material.GRASS
               && (
                  earthBlock.getRelative(BlockFace.DOWN).getType() == Material.AIR
                     || earthBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getType() == Material.AIR
               )) {
               placeEvent.setCancelled(true);
            }
         } else if (GriefPrevention.instance.config_claims_warnOnBuildOutside
            && !this.trashBlocks.contains(block.getType())
            && GriefPrevention.instance.claimsEnabledForWorld(block.getWorld())
            && playerData.claims.size() > 0
            && --playerData.unclaimedBlockPlacementsUntilWarning <= 0) {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.BuildingOutsideClaims);
            playerData.unclaimedBlockPlacementsUntilWarning = 15;
            if (playerData.lastClaim != null && playerData.lastClaim.allowBuild(player) == null) {
               Visualization visualization = Visualization.FromClaim(playerData.lastClaim, block.getY(), VisualizationType.Claim, player.getLocation());
               Visualization.Apply(player, visualization);
            }
         }

         if (GriefPrevention.instance.config_blockSurfaceOtherExplosions
            && block.getType() == Material.TNT
            && block.getWorld().getEnvironment() != Environment.NETHER
            && block.getY() > GriefPrevention.instance.getSeaLevel(block.getWorld()) - 5) {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoTNTDamageAboveSeaLevel);
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onBlockPistonExtend(BlockPistonExtendEvent event) {
      List<Block> blocks = event.getBlocks();
      if (blocks.size() == 0) {
         Block pistonBlock = event.getBlock();
         Block invadedBlock = pistonBlock.getRelative(event.getDirection());
         if (this.dataStore.getClaimAt(pistonBlock.getLocation(), false, null) == null
            && this.dataStore.getClaimAt(invadedBlock.getLocation(), false, null) != null) {
            event.setCancelled(true);
         }
      } else {
         String pistonClaimOwnerName = "_";
         Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
         if (claim != null) {
            pistonClaimOwnerName = claim.getOwnerName();
         }

         for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            claim = this.dataStore.getClaimAt(block.getLocation(), false, null);
            if (claim != null && !claim.getOwnerName().equals(pistonClaimOwnerName)) {
               event.setCancelled(true);
               event.getBlock().getWorld().createExplosion(event.getBlock().getLocation(), 0.0F);
               event.getBlock().getWorld().dropItem(event.getBlock().getLocation(), new ItemStack(event.getBlock().getType()));
               event.getBlock().setType(Material.AIR);
               return;
            }
         }

         int xchange = 0;
         int zchange = 0;
         Block piston = event.getBlock();
         Block firstBlock = blocks.get(0);
         if (firstBlock.getX() > piston.getX()) {
            xchange = 1;
         } else if (firstBlock.getX() < piston.getX()) {
            xchange = -1;
         } else if (firstBlock.getZ() > piston.getZ()) {
            zchange = 1;
         } else if (firstBlock.getZ() < piston.getZ()) {
            zchange = -1;
         }

         if (xchange != 0 || zchange != 0) {
            for (int i = 0; i < blocks.size(); i++) {
               Block block = blocks.get(i);
               Claim originalClaim = this.dataStore.getClaimAt(block.getLocation(), false, null);
               String originalOwnerName = "";
               if (originalClaim != null) {
                  originalOwnerName = originalClaim.getOwnerName();
               }

               Claim newClaim = this.dataStore.getClaimAt(block.getLocation().add(xchange, 0.0, zchange), false, null);
               String newOwnerName = "";
               if (newClaim != null) {
                  newOwnerName = newClaim.getOwnerName();
               }

               if (!newOwnerName.equals(originalOwnerName)) {
                  event.setCancelled(true);
                  event.getBlock().getWorld().createExplosion(event.getBlock().getLocation(), 0.0F);
                  event.getBlock().getWorld().dropItem(event.getBlock().getLocation(), new ItemStack(event.getBlock().getType()));
                  event.getBlock().setType(Material.AIR);
                  return;
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onBlockPistonRetract(BlockPistonRetractEvent event) {
      if (event.isSticky()) {
         String movingBlockOwnerName = "_";
         Claim movingBlockClaim = this.dataStore.getClaimAt(event.getRetractLocation(), false, null);
         if (movingBlockClaim != null) {
            movingBlockOwnerName = movingBlockClaim.getOwnerName();
         }

         String pistonOwnerName = "_";
         Location pistonLocation = event.getBlock().getLocation();
         Claim pistonClaim = this.dataStore.getClaimAt(pistonLocation, false, null);
         if (pistonClaim != null) {
            pistonOwnerName = pistonClaim.getOwnerName();
         }

         if (!pistonOwnerName.equals(movingBlockOwnerName)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onBlockIgnite(BlockIgniteEvent igniteEvent) {
      if (!GriefPrevention.instance.config_fireSpreads
         && igniteEvent.getCause() != IgniteCause.FLINT_AND_STEEL
         && igniteEvent.getCause() != IgniteCause.LIGHTNING) {
         igniteEvent.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onBlockSpread(BlockSpreadEvent spreadEvent) {
      if (spreadEvent.getSource().getType() == Material.FIRE) {
         if (!GriefPrevention.instance.config_fireSpreads) {
            spreadEvent.setCancelled(true);
            Block underBlock = spreadEvent.getSource().getRelative(BlockFace.DOWN);
            if (underBlock.getType() != Material.NETHERRACK) {
               spreadEvent.getSource().setType(Material.AIR);
            }
         } else {
            if (this.dataStore.getClaimAt(spreadEvent.getBlock().getLocation(), false, null) != null) {
               spreadEvent.setCancelled(true);
               Block source = spreadEvent.getSource();
               if (source.getType() == Material.FIRE && source.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK) {
                  source.setType(Material.AIR);
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onBlockBurn(BlockBurnEvent burnEvent) {
      if (!GriefPrevention.instance.config_fireDestroys) {
         burnEvent.setCancelled(true);
         Block block = burnEvent.getBlock();
         Block[] adjacentBlocks = new Block[]{
            block.getRelative(BlockFace.UP),
            block.getRelative(BlockFace.DOWN),
            block.getRelative(BlockFace.NORTH),
            block.getRelative(BlockFace.SOUTH),
            block.getRelative(BlockFace.EAST),
            block.getRelative(BlockFace.WEST)
         };

         for (int i = 0; i < adjacentBlocks.length; i++) {
            Block adjacentBlock = adjacentBlocks[i];
            if (adjacentBlock.getType() == Material.FIRE && adjacentBlock.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK) {
               adjacentBlock.setType(Material.AIR);
            }
         }

         Block aboveBlock = block.getRelative(BlockFace.UP);
         if (aboveBlock.getType() == Material.FIRE) {
            aboveBlock.setType(Material.AIR);
         }
      } else {
         if (this.dataStore.getClaimAt(burnEvent.getBlock().getLocation(), false, null) != null) {
            burnEvent.setCancelled(true);
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onBlockFromTo(BlockFromToEvent spreadEvent) {
      if (GriefPrevention.instance.config_claims_enabledWorlds.contains(spreadEvent.getBlock().getWorld())) {
         if (spreadEvent.getFace() != BlockFace.DOWN) {
            Block fromBlock = spreadEvent.getBlock();
            Claim fromClaim = this.dataStore.getClaimAt(fromBlock.getLocation(), false, this.lastSpreadClaim);
            if (fromClaim != null) {
               this.lastSpreadClaim = fromClaim;
            }

            Block toBlock = spreadEvent.getToBlock();
            Claim toClaim = this.dataStore.getClaimAt(toBlock.getLocation(), false, fromClaim);
            if (fromClaim != toClaim) {
               if (fromClaim != null && toClaim == null) {
                  spreadEvent.setCancelled(true);
               } else {
                  if (toClaim != null) {
                     OfflinePlayer fromOwner = null;
                     if (fromClaim != null) {
                        fromOwner = GriefPrevention.instance.getServer().getOfflinePlayer(fromClaim.ownerName);
                     }

                     if (fromOwner == null || fromOwner.getPlayer() == null || toClaim.allowBuild(fromOwner.getPlayer()) != null) {
                        spreadEvent.setCancelled(true);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onDispense(BlockDispenseEvent dispenseEvent) {
      Block fromBlock = dispenseEvent.getBlock();
      Vector velocity = dispenseEvent.getVelocity();
      int xChange = 0;
      int zChange = 0;
      if (Math.abs(velocity.getX()) > Math.abs(velocity.getZ())) {
         if (velocity.getX() > 0.0) {
            xChange = 1;
         } else {
            xChange = -1;
         }
      } else if (velocity.getZ() > 0.0) {
         zChange = 1;
      } else {
         zChange = -1;
      }

      Block toBlock = fromBlock.getRelative(xChange, 0, zChange);
      Claim fromClaim = this.dataStore.getClaimAt(fromBlock.getLocation(), false, null);
      Claim toClaim = this.dataStore.getClaimAt(toBlock.getLocation(), false, fromClaim);
      Material materialDispensed = dispenseEvent.getItem().getType();
      if ((materialDispensed == Material.WATER_BUCKET || materialDispensed == Material.LAVA_BUCKET)
         && GriefPrevention.instance.config_blockWildernessWaterBuckets
         && GriefPrevention.instance.claimsEnabledForWorld(fromBlock.getWorld())
         && toClaim == null) {
         dispenseEvent.setCancelled(true);
      } else if (fromClaim != null || toClaim != null) {
         if (fromClaim != toClaim) {
            dispenseEvent.setCancelled(true);
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onStorageMonitorInteract(PlayerInteractEvent interactEvent) {
      Action action = interactEvent.getAction();
      Player player = interactEvent.getPlayer();
      Block clickedBlock = interactEvent.getClickedBlock();
      if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
         int itemID = player.getInventory().getItemInHand().getType().getId();
         if (clickedBlock != null && clickedBlock.getType() == Material.matchMaterial("blkStorageMonitor")) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim == null || !claim.allowAccess(player).isEmpty()) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoMeAccess);
               interactEvent.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onTreeGrow(StructureGrowEvent growEvent) {
      Location rootLocation = growEvent.getLocation();
      Claim rootClaim = this.dataStore.getClaimAt(rootLocation, false, null);
      String rootOwnerName = null;
      if (rootClaim != null) {
         if (rootClaim.parent != null) {
            rootClaim = rootClaim.parent;
         }

         if (rootClaim.isAdminClaim()) {
            return;
         }

         rootOwnerName = rootClaim.getOwnerName();
      }

      for (int i = 0; i < growEvent.getBlocks().size(); i++) {
         BlockState block = (BlockState)growEvent.getBlocks().get(i);
         Claim blockClaim = this.dataStore.getClaimAt(block.getLocation(), false, rootClaim);
         if (blockClaim != null && (rootOwnerName == null || !rootOwnerName.equals(blockClaim.getOwnerName()))) {
            growEvent.getBlocks().remove(i--);
         }
      }
   }
}
