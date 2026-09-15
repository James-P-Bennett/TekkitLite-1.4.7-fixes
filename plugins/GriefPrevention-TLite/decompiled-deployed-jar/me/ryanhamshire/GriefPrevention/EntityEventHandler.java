package me.ryanhamshire.GriefPrevention;

import java.util.Calendar;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;

class EntityEventHandler implements Listener {
   private DataStore dataStore;

   public EntityEventHandler(DataStore dataStore) {
      this.dataStore = dataStore;
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onEntityChangeBLock(EntityChangeBlockEvent event) {
      if (!GriefPrevention.instance.config_endermenMoveBlocks && event.getEntityType() == EntityType.ENDERMAN) {
         event.setCancelled(true);
      } else if (!GriefPrevention.instance.config_silverfishBreakBlocks && event.getEntityType() == EntityType.SILVERFISH) {
         event.setCancelled(true);
      } else if (event.getEntityType() == EntityType.WITHER && GriefPrevention.instance.config_claims_enabledWorlds.contains(event.getBlock().getWorld())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onZombieBreakDoor(EntityBreakDoorEvent event) {
      if (!GriefPrevention.instance.config_zombiesBreakDoors) {
         event.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onEntityInteract(EntityInteractEvent event) {
      if (!GriefPrevention.instance.config_creaturesTrampleCrops && event.getBlock().getType() == Material.SOIL) {
         event.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onEntityExplode(EntityExplodeEvent explodeEvent) {
      List<Block> blocks = explodeEvent.blockList();
      Location location = explodeEvent.getLocation();
      boolean isCreeper = explodeEvent.getEntity() != null && explodeEvent.getEntity() instanceof Creeper;
      if (location.getWorld().getEnvironment() == Environment.NORMAL
         && GriefPrevention.instance.config_claims_enabledWorlds.contains(location.getWorld())
         && (
            isCreeper && GriefPrevention.instance.config_blockSurfaceCreeperExplosions
               || !isCreeper && GriefPrevention.instance.config_blockSurfaceOtherExplosions
         )) {
         for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (!GriefPrevention.instance.config_mods_explodableIds.Contains(new MaterialInfo(block.getTypeId(), block.getData(), null))
               && block.getLocation().getBlockY() > GriefPrevention.instance.getSeaLevel(location.getWorld()) - 7) {
               blocks.remove(i--);
            }
         }
      }

      if (GriefPrevention.instance.creativeRulesApply(explodeEvent.getLocation())) {
         for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (!GriefPrevention.instance.config_mods_explodableIds.Contains(new MaterialInfo(block.getTypeId(), block.getData(), null))) {
               blocks.remove(i--);
            }
         }
      }

      Claim claim = null;

      for (int i = 0; i < blocks.size(); i++) {
         Block block = blocks.get(i);
         if (block.getType() != Material.AIR
            && !GriefPrevention.instance.config_mods_explodableIds.Contains(new MaterialInfo(block.getTypeId(), block.getData(), null))) {
            claim = this.dataStore.getClaimAt(block.getLocation(), false, claim);
            if (claim != null && !claim.areExplosivesAllowed) {
               blocks.remove(i--);
            } else if (block.getType() == Material.LOG) {
               GriefPrevention.instance.handleLogBroken(block);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onItemSpawn(ItemSpawnEvent event) {
      if (GriefPrevention.instance.creativeRulesApply(event.getLocation())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onExpBottle(ExpBottleEvent event) {
      if (GriefPrevention.instance.creativeRulesApply(event.getEntity().getLocation())) {
         event.setExperience(0);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onEntitySpawn(CreatureSpawnEvent event) {
      LivingEntity entity = event.getEntity();
      if (GriefPrevention.instance.creativeRulesApply(entity.getLocation())) {
         SpawnReason reason = event.getSpawnReason();
         if (reason != SpawnReason.SPAWNER_EGG && reason != SpawnReason.BUILD_IRONGOLEM && reason != SpawnReason.BUILD_SNOWMAN) {
            event.setCancelled(true);
         } else {
            Claim claim = this.dataStore.getClaimAt(event.getLocation(), false, null);
            if (claim == null || claim.allowMoreEntities() != null) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void onEntityDeath(EntityDeathEvent event) {
      LivingEntity entity = event.getEntity();
      if (GriefPrevention.instance.creativeRulesApply(entity.getLocation())) {
         event.setDroppedExp(0);
         event.getDrops().clear();
      }

      if (entity instanceof Player) {
         Player player = (Player)entity;
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         if (playerData.siegeData != null) {
            event.getDrops().clear();
            this.dataStore.endSiege(playerData.siegeData, null, player.getName(), true);
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onEntityPickup(EntityChangeBlockEvent event) {
      if (event.getEntity() instanceof Enderman && this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null) != null) {
         event.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onHangingBreak(HangingBreakEvent event) {
      if (!(event instanceof HangingBreakByEntityEvent)) {
         event.setCancelled(true);
      } else {
         HangingBreakByEntityEvent entityEvent = (HangingBreakByEntityEvent)event;
         Entity remover = entityEvent.getRemover();
         if (!(remover instanceof Player)) {
            event.setCancelled(true);
         } else {
            Player playerRemover = (Player)entityEvent.getRemover();
            String noBuildReason = GriefPrevention.instance.allowBuild(playerRemover, event.getEntity().getLocation());
            if (noBuildReason != null) {
               event.setCancelled(true);
               GriefPrevention.sendMessage(playerRemover, TextMode.Err, noBuildReason);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onPaintingPlace(HangingPlaceEvent event) {
      String noBuildReason = GriefPrevention.instance.allowBuild(event.getPlayer(), event.getEntity().getLocation());
      if (noBuildReason != null) {
         event.setCancelled(true);
         GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, noBuildReason);
      } else {
         if (GriefPrevention.instance.creativeRulesApply(event.getEntity().getLocation())) {
            PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getName());
            Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, playerData.lastClaim);
            if (claim == null) {
               return;
            }

            String noEntitiesReason = claim.allowMoreEntities();
            if (noEntitiesReason != null) {
               GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, noEntitiesReason);
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
   public void onEntityDamage(EntityDamageEvent event) {
      if (event instanceof EntityDamageByEntityEvent) {
         if (!(event.getEntity() instanceof Monster)) {
            EntityDamageByEntityEvent subEvent = (EntityDamageByEntityEvent)event;
            Player attacker = null;
            Arrow arrow = null;
            Entity damageSource = subEvent.getDamager();
            if (damageSource instanceof Player) {
               attacker = (Player)damageSource;
            } else if (damageSource instanceof Arrow) {
               arrow = (Arrow)damageSource;
               if (arrow.getShooter() instanceof Player) {
                  attacker = (Player)arrow.getShooter();
               }
            } else if (damageSource instanceof ThrownPotion) {
               ThrownPotion potion = (ThrownPotion)damageSource;
               if (potion.getShooter() instanceof Player) {
                  attacker = (Player)potion.getShooter();
               }
            }

            if (attacker != null && event.getEntity() instanceof Player && GriefPrevention.instance.config_pvp_enabledWorlds.contains(attacker.getWorld())) {
               if (attacker.hasPermission("griefprevention.nopvpimmunity")) {
                  return;
               }

               var defender = (Player & Player)event.getEntity();
               PlayerData defenderData = this.dataStore.getPlayerData(((Player)event.getEntity()).getName());
               PlayerData attackerData = this.dataStore.getPlayerData(attacker.getName());
               if (GriefPrevention.instance.config_pvp_protectFreshSpawns) {
                  if (defenderData.pvpImmune) {
                     event.setCancelled(true);
                     GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.ThatPlayerPvPImmune);
                     return;
                  }

                  if (attackerData.pvpImmune) {
                     event.setCancelled(true);
                     GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.CantFightWhileImmune);
                     return;
                  }
               }

               if (GriefPrevention.instance.config_pvp_noCombatInPlayerLandClaims || GriefPrevention.instance.config_pvp_noCombatInAdminLandClaims) {
                  Claim attackerClaim = this.dataStore.getClaimAt(attacker.getLocation(), false, attackerData.lastClaim);
                  if (attackerClaim != null
                     && (
                        attackerClaim.isAdminClaim() && GriefPrevention.instance.config_pvp_noCombatInAdminLandClaims
                           || !attackerClaim.isAdminClaim() && GriefPrevention.instance.config_pvp_noCombatInPlayerLandClaims
                     )) {
                     attackerData.lastClaim = attackerClaim;
                     event.setCancelled(true);
                     GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.CantFightWhileImmune);
                     return;
                  }

                  Claim defenderClaim = this.dataStore.getClaimAt(defender.getLocation(), false, defenderData.lastClaim);
                  if (defenderClaim != null
                     && (
                        defenderClaim.isAdminClaim() && GriefPrevention.instance.config_pvp_noCombatInAdminLandClaims
                           || !defenderClaim.isAdminClaim() && GriefPrevention.instance.config_pvp_noCombatInPlayerLandClaims
                     )) {
                     defenderData.lastClaim = defenderClaim;
                     event.setCancelled(true);
                     GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.PlayerInPvPSafeZone);
                     return;
                  }
               }

               long now = Calendar.getInstance().getTimeInMillis();
               defenderData.lastPvpTimestamp = now;
               defenderData.lastPvpPlayer = attacker.getName();
               attackerData.lastPvpTimestamp = now;
               attackerData.lastPvpPlayer = defender.getName();
            }

            if (event instanceof EntityDamageByEntityEvent
               && subEvent.getEntity() instanceof Creature
               && GriefPrevention.instance.config_claims_protectCreatures) {
               Claim cachedClaim = null;
               PlayerData playerData = null;
               if (attacker != null) {
                  playerData = this.dataStore.getPlayerData(attacker.getName());
                  cachedClaim = playerData.lastClaim;
               }

               Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, cachedClaim);
               if (claim != null) {
                  if (attacker == null) {
                     if (event.getEntity() instanceof Villager && damageSource instanceof Monster && claim.isAdminClaim()) {
                        return;
                     }

                     event.setCancelled(true);
                  } else {
                     String noContainersReason = claim.allowContainers(attacker);
                     if (noContainersReason != null) {
                        event.setCancelled(true);
                        if (arrow != null) {
                           arrow.remove();
                        }

                        GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.NoDamageClaimedEntity, claim.getOwnerName());
                     }

                     if (playerData != null) {
                        playerData.lastClaim = claim;
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onVehicleDamage(VehicleDamageEvent event) {
      if (GriefPrevention.instance.config_claims_preventTheft) {
         Player attacker = null;
         Entity damageSource = event.getAttacker();
         if (damageSource instanceof Player) {
            attacker = (Player)damageSource;
         } else if (damageSource instanceof Arrow) {
            Arrow arrow = (Arrow)damageSource;
            if (arrow.getShooter() instanceof Player) {
               attacker = (Player)arrow.getShooter();
            }
         } else if (damageSource instanceof ThrownPotion) {
            ThrownPotion potion = (ThrownPotion)damageSource;
            if (potion.getShooter() instanceof Player) {
               attacker = (Player)potion.getShooter();
            }
         }

         Claim cachedClaim = null;
         PlayerData playerData = null;
         if (attacker != null) {
            playerData = this.dataStore.getPlayerData(attacker.getName());
            cachedClaim = playerData.lastClaim;
         }

         Claim claim = this.dataStore.getClaimAt(event.getVehicle().getLocation(), false, cachedClaim);
         if (claim != null) {
            if (attacker == null) {
               event.setCancelled(true);
            } else {
               String noContainersReason = claim.allowContainers(attacker);
               if (noContainersReason != null) {
                  event.setCancelled(true);
                  GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.NoDamageClaimedEntity, claim.getOwnerName());
               }

               if (playerData != null) {
                  playerData.lastClaim = claim;
               }
            }
         }
      }
   }
}
