package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Claim {
   Location lesserBoundaryCorner;
   Location greaterBoundaryCorner;
   public Date modifiedDate;
   Long id = null;
   public String ownerName;
   public ArrayList<String> managers = new ArrayList<>();
   private HashMap<String, ClaimPermission> playerNameToClaimPermissionMap = new HashMap<>();
   public boolean inDataStore = false;
   public boolean areExplosivesAllowed = false;
   public Claim parent = null;
   public ArrayList<Claim> children = new ArrayList<>();
   public SiegeData siegeData = null;
   public boolean doorsOpen = false;

   public boolean isAdminClaim() {
      return this.ownerName == null || this.ownerName.isEmpty();
   }

   public Long getID() {
      return this.id;
   }

   Claim() {
      this.modifiedDate = Calendar.getInstance().getTime();
   }

   public boolean canSiege(Player defender) {
      return this.isAdminClaim() ? false : this.allowAccess(defender) == null;
   }

   public void removeSurfaceFluids(Claim exclusionClaim) {
      if (!this.isAdminClaim()) {
         if (this.getArea() <= 10000) {
            if (GriefPrevention.instance.config_blockWildernessWaterBuckets) {
               Location lesser = this.getLesserBoundaryCorner();
               Location greater = this.getGreaterBoundaryCorner();
               if (lesser.getWorld().getEnvironment() != Environment.NETHER) {
                  int seaLevel = 0;
                  if (lesser.getWorld().getEnvironment() == Environment.NORMAL) {
                     seaLevel = GriefPrevention.instance.getSeaLevel(lesser.getWorld());
                  }

                  for (int x = lesser.getBlockX(); x <= greater.getBlockX(); x++) {
                     for (int z = lesser.getBlockZ(); z <= greater.getBlockZ(); z++) {
                        for (int y = seaLevel - 1; y <= lesser.getWorld().getMaxHeight(); y++) {
                           Block block = lesser.getWorld().getBlockAt(x, y, z);
                           if ((exclusionClaim == null || !exclusionClaim.contains(block.getLocation(), true, false))
                              && (
                                 block.getType() == Material.STATIONARY_WATER
                                    || block.getType() == Material.STATIONARY_LAVA
                                    || block.getType() == Material.LAVA
                                    || block.getType() == Material.WATER
                              )) {
                              block.setType(Material.AIR);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   boolean hasSurfaceFluids() {
      Location lesser = this.getLesserBoundaryCorner();
      Location greater = this.getGreaterBoundaryCorner();
      if (this.getArea() > 10000) {
         return false;
      }

      int seaLevel = 0;
      if (lesser.getWorld().getEnvironment() == Environment.NORMAL) {
         seaLevel = GriefPrevention.instance.getSeaLevel(lesser.getWorld());
      }

      int waterCount = 0;

      for (int x = lesser.getBlockX(); x <= greater.getBlockX(); x++) {
         for (int z = lesser.getBlockZ(); z <= greater.getBlockZ(); z++) {
            for (int y = seaLevel - 1; y <= lesser.getWorld().getMaxHeight(); y++) {
               Block block = lesser.getWorld().getBlockAt(x, y, z);
               if (block.getType() != Material.STATIONARY_WATER && block.getType() != Material.WATER) {
                  if (block.getType() == Material.STATIONARY_LAVA || block.getType() == Material.LAVA) {
                     return true;
                  }
               } else if (++waterCount > 10) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   Claim(
      Location lesserBoundaryCorner,
      Location greaterBoundaryCorner,
      String ownerName,
      String[] builderNames,
      String[] containerNames,
      String[] accessorNames,
      String[] managerNames,
      Long id
   ) {
      this.modifiedDate = Calendar.getInstance().getTime();
      this.id = id;
      this.lesserBoundaryCorner = lesserBoundaryCorner;
      this.greaterBoundaryCorner = greaterBoundaryCorner;
      this.ownerName = ownerName;

      for (int i = 0; i < builderNames.length; i++) {
         String name = builderNames[i];
         if (name != null && !name.isEmpty()) {
            this.playerNameToClaimPermissionMap.put(name, ClaimPermission.Build);
         }
      }

      for (int i = 0; i < containerNames.length; i++) {
         String name = containerNames[i];
         if (name != null && !name.isEmpty()) {
            this.playerNameToClaimPermissionMap.put(name, ClaimPermission.Inventory);
         }
      }

      for (int i = 0; i < accessorNames.length; i++) {
         String name = accessorNames[i];
         if (name != null && !name.isEmpty()) {
            this.playerNameToClaimPermissionMap.put(name, ClaimPermission.Access);
         }
      }

      for (int i = 0; i < managerNames.length; i++) {
         String name = managerNames[i];
         if (name != null && !name.isEmpty()) {
            this.managers.add(name);
         }
      }
   }

   public int getArea() {
      int claimWidth = this.greaterBoundaryCorner.getBlockX() - this.lesserBoundaryCorner.getBlockX() + 1;
      int claimHeight = this.greaterBoundaryCorner.getBlockZ() - this.lesserBoundaryCorner.getBlockZ() + 1;
      return claimWidth * claimHeight;
   }

   public int getWidth() {
      return this.greaterBoundaryCorner.getBlockX() - this.lesserBoundaryCorner.getBlockX() + 1;
   }

   public int getHeight() {
      return this.greaterBoundaryCorner.getBlockZ() - this.lesserBoundaryCorner.getBlockZ() + 1;
   }

   public boolean isNear(Location location, int howNear) {
      Claim claim = new Claim(
         new Location(
            this.lesserBoundaryCorner.getWorld(),
            this.lesserBoundaryCorner.getBlockX() - howNear,
            this.lesserBoundaryCorner.getBlockY(),
            this.lesserBoundaryCorner.getBlockZ() - howNear
         ),
         new Location(
            this.greaterBoundaryCorner.getWorld(),
            this.greaterBoundaryCorner.getBlockX() + howNear,
            this.greaterBoundaryCorner.getBlockY(),
            this.greaterBoundaryCorner.getBlockZ() + howNear
         ),
         "",
         new String[0],
         new String[0],
         new String[0],
         new String[0],
         null
      );
      return claim.contains(location, false, true);
   }

   public String allowEdit(Player player) {
      if (player == null) {
         return "";
      }

      if (this.isAdminClaim()) {
         if (player.hasPermission("griefprevention.adminclaims")) {
            return null;
         }
      } else if (player.hasPermission("griefprevention.deleteclaims")) {
         return null;
      }

      if (this.ownerName.equals(player.getName())) {
         return this.siegeData != null ? GriefPrevention.instance.dataStore.getMessage(Messages.NoModifyDuringSiege) : null;
      } else {
         return this.parent != null
            ? this.parent.allowBuild(player)
            : GriefPrevention.instance.dataStore.getMessage(Messages.OnlyOwnersModifyClaims, this.getOwnerName());
      }
   }

   public String allowBuild(Player player) {
      if (player == null) {
         return "";
      }

      GriefPrevention.instance.dataStore.tryExtendSiege(player, this);
      if (this.isAdminClaim() && player.hasPermission("griefprevention.adminclaims")) {
         return null;
      }

      if (this.siegeData != null) {
         return GriefPrevention.instance.dataStore.getMessage(Messages.NoBuildUnderSiege, this.siegeData.attacker.getName());
      }

      PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getName());
      if (playerData.inPvpCombat()) {
         return GriefPrevention.instance.dataStore.getMessage(Messages.NoBuildPvP);
      }

      if (this.ownerName.equals(player.getName()) || GriefPrevention.instance.dataStore.getPlayerData(player.getName()).ignoreClaims) {
         return null;
      }

      if (this.hasExplicitPermission(player, ClaimPermission.Build)) {
         return null;
      }

      ClaimPermission permissionLevel = this.playerNameToClaimPermissionMap.get("public");
      if (ClaimPermission.Build == permissionLevel) {
         return null;
      }

      if (this.parent != null) {
         return this.parent.allowBuild(player);
      }

      String reason = GriefPrevention.instance.dataStore.getMessage(Messages.NoBuildPermission, this.getOwnerName());
      if (player.hasPermission("griefprevention.ignoreclaims")) {
         reason = reason + "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
      }

      return reason;
   }

   private boolean hasExplicitPermission(Player player, ClaimPermission level) {
      String playerName = player.getName();

      for (String identifier : this.playerNameToClaimPermissionMap.keySet()) {
         if (playerName.equalsIgnoreCase(identifier) && this.playerNameToClaimPermissionMap.get(identifier) == level) {
            return true;
         }

         if (identifier.startsWith("[") && identifier.endsWith("]")) {
            String permissionIdentifier = identifier.substring(1, identifier.length() - 1);
            if (permissionIdentifier != null
               && !permissionIdentifier.isEmpty()
               && player.hasPermission(permissionIdentifier)
               && this.playerNameToClaimPermissionMap.get(identifier) == level) {
               return true;
            }
         }
      }

      return false;
   }

   public String allowBreak(Player player, Material material) {
      if (this.siegeData == null) {
         return this.allowBuild(player);
      }

      boolean breakable = false;

      for (int i = 0; i < GriefPrevention.instance.config_siege_blocks.size(); i++) {
         Material breakableMaterial = GriefPrevention.instance.config_siege_blocks.get(i);
         if (breakableMaterial.getId() == material.getId()) {
            breakable = true;
            break;
         }
      }

      if (!breakable) {
         return GriefPrevention.instance.dataStore.getMessage(Messages.NonSiegeMaterial);
      } else {
         return this.ownerName.equals(player.getName()) ? GriefPrevention.instance.dataStore.getMessage(Messages.NoOwnerBuildUnderSiege) : null;
      }
   }

   public String allowAccess(Player player) {
      if (this.doorsOpen) {
         return null;
      }

      if (this.isAdminClaim() && player.hasPermission("griefprevention.adminclaims")) {
         return null;
      }

      if (this.ownerName.equals(player.getName()) || GriefPrevention.instance.dataStore.getPlayerData(player.getName()).ignoreClaims) {
         return null;
      }

      if (this.hasExplicitPermission(player, ClaimPermission.Access)) {
         return null;
      }

      if (this.hasExplicitPermission(player, ClaimPermission.Inventory)) {
         return null;
      }

      if (this.hasExplicitPermission(player, ClaimPermission.Build)) {
         return null;
      }

      ClaimPermission permissionLevel = this.playerNameToClaimPermissionMap.get("public");
      if (ClaimPermission.Build == permissionLevel || ClaimPermission.Inventory == permissionLevel || ClaimPermission.Access == permissionLevel) {
         return null;
      }

      if (this.parent != null) {
         return this.parent.allowAccess(player);
      }

      String reason = GriefPrevention.instance.dataStore.getMessage(Messages.NoAccessPermission, this.getOwnerName());
      if (player.hasPermission("griefprevention.ignoreclaims")) {
         reason = reason + "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
      }

      return reason;
   }

   public String allowContainers(Player player) {
      if (player == null) {
         return "";
      }

      GriefPrevention.instance.dataStore.tryExtendSiege(player, this);
      if (this.siegeData != null) {
         return GriefPrevention.instance.dataStore.getMessage(Messages.NoContainersSiege, this.siegeData.attacker.getName());
      }

      if (this.ownerName.equals(player.getName()) || GriefPrevention.instance.dataStore.getPlayerData(player.getName()).ignoreClaims) {
         return null;
      }

      if (this.isAdminClaim() && player.hasPermission("griefprevention.adminclaims")) {
         return null;
      }

      if (this.hasExplicitPermission(player, ClaimPermission.Inventory)) {
         return null;
      }

      if (this.hasExplicitPermission(player, ClaimPermission.Build)) {
         return null;
      }

      ClaimPermission permissionLevel = this.playerNameToClaimPermissionMap.get("public");
      if (ClaimPermission.Build == permissionLevel || ClaimPermission.Inventory == permissionLevel) {
         return null;
      }

      if (this.parent != null) {
         return this.parent.allowContainers(player);
      }

      String reason = GriefPrevention.instance.dataStore.getMessage(Messages.NoContainersPermission, this.getOwnerName());
      if (player.hasPermission("griefprevention.ignoreclaims")) {
         reason = reason + "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
      }

      return reason;
   }

   public String allowGrantPermission(Player player) {
      if (player == null) {
         return "";
      }

      if (this.allowEdit(player) == null) {
         return null;
      }

      for (int i = 0; i < this.managers.size(); i++) {
         String managerID = this.managers.get(i);
         if (player.getName().equalsIgnoreCase(managerID)) {
            return null;
         }

         if (managerID.startsWith("[") && managerID.endsWith("]")) {
            managerID = managerID.substring(1, managerID.length() - 1);
            if (managerID != null && !managerID.isEmpty() && player.hasPermission(managerID)) {
               return null;
            }
         }
      }

      if (this.parent != null) {
         return this.parent.allowGrantPermission(player);
      }

      String reason = GriefPrevention.instance.dataStore.getMessage(Messages.NoPermissionTrust, this.getOwnerName());
      if (player.hasPermission("griefprevention.ignoreclaims")) {
         reason = reason + "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
      }

      return reason;
   }

   public void setPermission(String playerName, ClaimPermission permissionLevel) {
      this.playerNameToClaimPermissionMap.put(playerName.toLowerCase(), permissionLevel);
   }

   public void dropPermission(String playerName) {
      this.playerNameToClaimPermissionMap.remove(playerName.toLowerCase());
   }

   public void clearPermissions() {
      this.playerNameToClaimPermissionMap.clear();
   }

   public void getPermissions(ArrayList<String> builders, ArrayList<String> containers, ArrayList<String> accessors, ArrayList<String> managers) {
      for (Entry<String, ClaimPermission> entry : this.playerNameToClaimPermissionMap.entrySet()) {
         if (entry.getValue() == ClaimPermission.Build) {
            builders.add(entry.getKey());
         } else if (entry.getValue() == ClaimPermission.Inventory) {
            containers.add(entry.getKey());
         } else {
            accessors.add(entry.getKey());
         }
      }

      for (int i = 0; i < this.managers.size(); i++) {
         managers.add(this.managers.get(i));
      }
   }

   public Location getLesserBoundaryCorner() {
      return this.lesserBoundaryCorner.clone();
   }

   public Location getGreaterBoundaryCorner() {
      return this.greaterBoundaryCorner.clone();
   }

   public String getOwnerName() {
      if (this.parent != null) {
         return this.parent.getOwnerName();
      } else {
         return this.ownerName.length() == 0 ? GriefPrevention.instance.dataStore.getMessage(Messages.OwnerNameForAdminClaims) : this.ownerName;
      }
   }

   public boolean contains(Location location, boolean ignoreHeight, boolean excludeSubdivisions) {
      if (!location.getWorld().equals(this.lesserBoundaryCorner.getWorld())) {
         return false;
      }

      int x = location.getBlockX();
      int y = location.getBlockY();
      int z = location.getBlockZ();
      boolean inClaim = (ignoreHeight || y >= this.lesserBoundaryCorner.getBlockY())
         && x >= this.lesserBoundaryCorner.getBlockX()
         && x <= this.greaterBoundaryCorner.getBlockX()
         && z >= this.lesserBoundaryCorner.getBlockZ()
         && z <= this.greaterBoundaryCorner.getBlockZ();
      if (!inClaim) {
         return false;
      }

      if (this.parent != null) {
         return this.parent.contains(location, ignoreHeight, false);
      }

      if (excludeSubdivisions) {
         for (int i = 0; i < this.children.size(); i++) {
            if (this.children.get(i).contains(location, ignoreHeight, true)) {
               return false;
            }
         }
      }

      return true;
   }

   boolean overlaps(Claim otherClaim) {
      if (!this.lesserBoundaryCorner.getWorld().equals(otherClaim.getLesserBoundaryCorner().getWorld())) {
         return false;
      } else if (otherClaim.contains(this.lesserBoundaryCorner, true, false)) {
         return true;
      } else if (otherClaim.contains(this.greaterBoundaryCorner, true, false)) {
         return true;
      } else if (otherClaim.contains(
         new Location(this.lesserBoundaryCorner.getWorld(), this.lesserBoundaryCorner.getBlockX(), 0.0, this.greaterBoundaryCorner.getBlockZ()), true, false
      )) {
         return true;
      } else if (otherClaim.contains(
         new Location(this.lesserBoundaryCorner.getWorld(), this.greaterBoundaryCorner.getBlockX(), 0.0, this.lesserBoundaryCorner.getBlockZ()), true, false
      )) {
         return true;
      } else if (this.contains(otherClaim.getLesserBoundaryCorner(), true, false)) {
         return true;
      } else if (this.getLesserBoundaryCorner().getBlockZ() <= otherClaim.getGreaterBoundaryCorner().getBlockZ()
         && this.getLesserBoundaryCorner().getBlockZ() >= otherClaim.getLesserBoundaryCorner().getBlockZ()
         && this.getLesserBoundaryCorner().getBlockX() < otherClaim.getLesserBoundaryCorner().getBlockX()
         && this.getGreaterBoundaryCorner().getBlockX() > otherClaim.getGreaterBoundaryCorner().getBlockX()) {
         return true;
      } else if (this.getGreaterBoundaryCorner().getBlockZ() <= otherClaim.getGreaterBoundaryCorner().getBlockZ()
         && this.getGreaterBoundaryCorner().getBlockZ() >= otherClaim.getLesserBoundaryCorner().getBlockZ()
         && this.getLesserBoundaryCorner().getBlockX() < otherClaim.getLesserBoundaryCorner().getBlockX()
         && this.getGreaterBoundaryCorner().getBlockX() > otherClaim.getGreaterBoundaryCorner().getBlockX()) {
         return true;
      } else {
         return this.getLesserBoundaryCorner().getBlockX() <= otherClaim.getGreaterBoundaryCorner().getBlockX()
               && this.getLesserBoundaryCorner().getBlockX() >= otherClaim.getLesserBoundaryCorner().getBlockX()
               && this.getLesserBoundaryCorner().getBlockZ() < otherClaim.getLesserBoundaryCorner().getBlockZ()
               && this.getGreaterBoundaryCorner().getBlockZ() > otherClaim.getGreaterBoundaryCorner().getBlockZ()
            ? true
            : this.getGreaterBoundaryCorner().getBlockX() <= otherClaim.getGreaterBoundaryCorner().getBlockX()
               && this.getGreaterBoundaryCorner().getBlockX() >= otherClaim.getLesserBoundaryCorner().getBlockX()
               && this.getLesserBoundaryCorner().getBlockZ() < otherClaim.getLesserBoundaryCorner().getBlockZ()
               && this.getGreaterBoundaryCorner().getBlockZ() > otherClaim.getGreaterBoundaryCorner().getBlockZ();
      }
   }

   public String allowMoreEntities() {
      if (this.parent != null) {
         return this.parent.allowMoreEntities();
      }

      if (!GriefPrevention.instance.creativeRulesApply(this.getLesserBoundaryCorner())) {
         return null;
      }

      if (this.isAdminClaim()) {
         return null;
      }

      if (this.getArea() > 10000) {
         return null;
      }

      int maxEntities = this.getArea() / 50;
      if (maxEntities == 0) {
         return GriefPrevention.instance.dataStore.getMessage(Messages.ClaimTooSmallForEntities);
      }

      Chunk lesserChunk = this.getLesserBoundaryCorner().getChunk();
      Chunk greaterChunk = this.getGreaterBoundaryCorner().getChunk();
      int totalEntities = 0;

      for (int x = lesserChunk.getX(); x <= greaterChunk.getX(); x++) {
         for (int z = lesserChunk.getZ(); z <= greaterChunk.getZ(); z++) {
            Chunk chunk = lesserChunk.getWorld().getChunkAt(x, z);
            Entity[] entities = chunk.getEntities();

            for (int i = 0; i < entities.length; i++) {
               Entity entity = entities[i];
               if (!(entity instanceof Player) && this.contains(entity.getLocation(), false, false)) {
                  if (++totalEntities > maxEntities) {
                     entity.remove();
                  }
               }
            }
         }
      }

      return totalEntities > maxEntities ? GriefPrevention.instance.dataStore.getMessage(Messages.TooManyEntitiesInClaim) : null;
   }

   boolean greaterThan(Claim otherClaim) {
      Location thisCorner = this.getLesserBoundaryCorner();
      Location otherCorner = otherClaim.getLesserBoundaryCorner();
      if (thisCorner.getBlockX() > otherCorner.getBlockX()) {
         return true;
      } else if (thisCorner.getBlockX() < otherCorner.getBlockX()) {
         return false;
      } else if (thisCorner.getBlockZ() > otherCorner.getBlockZ()) {
         return true;
      } else {
         return thisCorner.getBlockZ() < otherCorner.getBlockZ() ? false : thisCorner.getWorld().getName().compareTo(otherCorner.getWorld().getName()) < 0;
      }
   }

   long getPlayerInvestmentScore() {
      Location lesserBoundaryCorner = this.getLesserBoundaryCorner();
      ArrayList<Integer> playerBlocks = RestoreNatureProcessingTask.getPlayerBlocks(
         lesserBoundaryCorner.getWorld().getEnvironment(), lesserBoundaryCorner.getBlock().getBiome()
      );
      double score = 0.0;
      boolean creativeMode = GriefPrevention.instance.creativeRulesApply(lesserBoundaryCorner);

      for (int x = this.lesserBoundaryCorner.getBlockX(); x <= this.greaterBoundaryCorner.getBlockX(); x++) {
         for (int z = this.lesserBoundaryCorner.getBlockZ(); z <= this.greaterBoundaryCorner.getBlockZ(); z++) {
            int y;
            for (y = this.lesserBoundaryCorner.getBlockY(); y < GriefPrevention.instance.getSeaLevel(this.lesserBoundaryCorner.getWorld()) - 5; y++) {
               Block block = this.lesserBoundaryCorner.getWorld().getBlockAt(x, y, z);
               if (playerBlocks.contains(block.getTypeId())) {
                  if (block.getType() == Material.CHEST && !creativeMode) {
                     score += 10.0;
                  } else {
                     score += 0.5;
                  }
               }
            }

            for (; y < this.lesserBoundaryCorner.getWorld().getMaxHeight(); y++) {
               Block block = this.lesserBoundaryCorner.getWorld().getBlockAt(x, y, z);
               if (playerBlocks.contains(block.getTypeId())) {
                  if (block.getType() == Material.CHEST && !creativeMode) {
                     score += 10.0;
                  } else if (!creativeMode || block.getType() != Material.LAVA && block.getType() != Material.STATIONARY_LAVA) {
                     score++;
                  } else {
                     score -= 10.0;
                  }
               }
            }
         }
      }

      return (long)score;
   }
}
