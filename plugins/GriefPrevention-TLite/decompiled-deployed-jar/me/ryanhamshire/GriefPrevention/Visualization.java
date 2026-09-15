package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class Visualization {
   public ArrayList<VisualizationElement> elements = new ArrayList<>();

   public static void Apply(Player player, Visualization visualization) {
      PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getName());
      if (playerData.currentVisualization != null) {
         Revert(player);
      }

      if (player.isOnline()) {
         GriefPrevention.instance
            .getServer()
            .getScheduler()
            .scheduleSyncDelayedTask(GriefPrevention.instance, new VisualizationApplicationTask(player, playerData, visualization), 10L);
      }
   }

   public static void Revert(Player player) {
      PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getName());
      Visualization visualization = playerData.currentVisualization;
      if (playerData.currentVisualization != null) {
         if (player.isOnline()) {
            for (int i = 0; i < visualization.elements.size(); i++) {
               VisualizationElement element = visualization.elements.get(i);
               Block block = element.location.getBlock();
               player.sendBlockChange(element.location, block.getType(), block.getData());
            }
         }

         playerData.currentVisualization = null;
      }
   }

   public static Visualization FromClaim(Claim claim, int height, VisualizationType visualizationType, Location locality) {
      if (claim.parent != null) {
         return FromClaim(claim.parent, height, visualizationType, locality);
      }

      Visualization visualization = new Visualization();

      for (int i = 0; i < claim.children.size(); i++) {
         visualization.addClaimElements(claim.children.get(i), height, VisualizationType.Subdivision, locality);
      }

      visualization.addClaimElements(claim, height, visualizationType, locality);
      return visualization;
   }

   private void addClaimElements(Claim claim, int height, VisualizationType visualizationType, Location locality) {
      Location smallXsmallZ = claim.getLesserBoundaryCorner();
      Location bigXbigZ = claim.getGreaterBoundaryCorner();
      World world = smallXsmallZ.getWorld();
      int smallx = smallXsmallZ.getBlockX();
      int smallz = smallXsmallZ.getBlockZ();
      int bigx = bigXbigZ.getBlockX();
      int bigz = bigXbigZ.getBlockZ();
      Material cornerMaterial;
      Material accentMaterial;
      if (visualizationType == VisualizationType.Claim) {
         cornerMaterial = Material.GLOWSTONE;
         accentMaterial = Material.GOLD_BLOCK;
      } else if (visualizationType == VisualizationType.Subdivision) {
         cornerMaterial = Material.IRON_BLOCK;
         accentMaterial = Material.WOOL;
      } else if (visualizationType == VisualizationType.RestoreNature) {
         cornerMaterial = Material.DIAMOND_BLOCK;
         accentMaterial = Material.DIAMOND_BLOCK;
      } else {
         cornerMaterial = Material.GLOWING_REDSTONE_ORE;
         accentMaterial = Material.NETHERRACK;
      }

      this.elements.add(new VisualizationElement(getVisibleLocation(world, smallx, height, smallz), cornerMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, smallx + 1, height, smallz), accentMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, smallx, height, smallz + 1), accentMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, bigx, height, smallz), cornerMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, bigx - 1, height, smallz), accentMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, bigx, height, smallz + 1), accentMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, bigx, height, bigz), cornerMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, bigx - 1, height, bigz), accentMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, bigx, height, bigz - 1), accentMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, smallx, height, bigz), cornerMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, smallx + 1, height, bigz), accentMaterial, (byte)0));
      this.elements.add(new VisualizationElement(getVisibleLocation(world, smallx, height, bigz - 1), accentMaterial, (byte)0));
      int minx = locality.getBlockX() - 100;
      int minz = locality.getBlockZ() - 100;
      int maxx = locality.getBlockX() + 100;
      int maxz = locality.getBlockZ() + 100;

      for (int x = smallx + 10; x < bigx - 10; x += 10) {
         if (x > minx && x < maxx) {
            this.elements.add(new VisualizationElement(getVisibleLocation(world, x, height, bigz), accentMaterial, (byte)0));
         }
      }

      for (int x = smallx + 10; x < bigx - 10; x += 10) {
         if (x > minx && x < maxx) {
            this.elements.add(new VisualizationElement(getVisibleLocation(world, x, height, smallz), accentMaterial, (byte)0));
         }
      }

      for (int z = smallz + 10; z < bigz - 10; z += 10) {
         if (z > minz && z < maxz) {
            this.elements.add(new VisualizationElement(getVisibleLocation(world, smallx, height, z), accentMaterial, (byte)0));
         }
      }

      for (int z = smallz + 10; z < bigz - 10; z += 10) {
         if (z > minz && z < maxz) {
            this.elements.add(new VisualizationElement(getVisibleLocation(world, bigx, height, z), accentMaterial, (byte)0));
         }
      }
   }

   private static Location getVisibleLocation(World world, int x, int y, int z) {
      Block block = world.getBlockAt(x, y, z);
      BlockFace direction = isTransparent(block) ? BlockFace.DOWN : BlockFace.UP;

      while (block.getY() >= 1 && block.getY() < world.getMaxHeight() - 1 && (!isTransparent(block.getRelative(BlockFace.UP)) || isTransparent(block))) {
         block = block.getRelative(direction);
      }

      return block.getLocation();
   }

   private static boolean isTransparent(Block block) {
      return block.getType() == Material.AIR
         || block.getType() == Material.LONG_GRASS
         || block.getType() == Material.FENCE
         || block.getType() == Material.LEAVES
         || block.getType() == Material.RED_ROSE
         || block.getType() == Material.CHEST
         || block.getType() == Material.TORCH
         || block.getType() == Material.VINE
         || block.getType() == Material.YELLOW_FLOWER;
   }
}
