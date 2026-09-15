package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

class RestoreNatureProcessingTask implements Runnable {
   private BlockSnapshot[][][] snapshots;
   private int miny;
   private Environment environment;
   private Location lesserBoundaryCorner;
   private Location greaterBoundaryCorner;
   private Player player;
   private Biome biome;
   private boolean creativeMode;
   private int seaLevel;
   private boolean aggressiveMode;
   private ArrayList<Integer> notAllowedToHang;
   private ArrayList<Integer> playerBlocks;

   public RestoreNatureProcessingTask(
      BlockSnapshot[][][] snapshots,
      int miny,
      Environment environment,
      Biome biome,
      Location lesserBoundaryCorner,
      Location greaterBoundaryCorner,
      int seaLevel,
      boolean aggressiveMode,
      boolean creativeMode,
      Player player
   ) {
      this.snapshots = snapshots;
      this.miny = miny;
      if (this.miny < 0) {
         this.miny = 0;
      }

      this.environment = environment;
      this.lesserBoundaryCorner = lesserBoundaryCorner;
      this.greaterBoundaryCorner = greaterBoundaryCorner;
      this.biome = biome;
      this.seaLevel = seaLevel;
      this.aggressiveMode = aggressiveMode;
      this.player = player;
      this.creativeMode = creativeMode;
      this.notAllowedToHang = new ArrayList<>();
      this.notAllowedToHang.add(Material.DIRT.getId());
      this.notAllowedToHang.add(Material.LONG_GRASS.getId());
      this.notAllowedToHang.add(Material.SNOW.getId());
      this.notAllowedToHang.add(Material.LOG.getId());
      if (this.aggressiveMode) {
         this.notAllowedToHang.add(Material.GRASS.getId());
         this.notAllowedToHang.add(Material.STONE.getId());
      }

      this.playerBlocks = new ArrayList<>();
      this.playerBlocks.addAll(getPlayerBlocks(this.environment, this.biome));
      if (this.aggressiveMode || this.creativeMode) {
         this.playerBlocks.add(Material.IRON_ORE.getId());
         this.playerBlocks.add(Material.GOLD_ORE.getId());
         this.playerBlocks.add(Material.DIAMOND_ORE.getId());
         this.playerBlocks.add(Material.MELON_BLOCK.getId());
         this.playerBlocks.add(Material.MELON_STEM.getId());
         this.playerBlocks.add(Material.BEDROCK.getId());
         this.playerBlocks.add(Material.COAL_ORE.getId());
         this.playerBlocks.add(Material.PUMPKIN.getId());
         this.playerBlocks.add(Material.PUMPKIN_STEM.getId());
         this.playerBlocks.add(Material.MELON.getId());
      }

      if (this.aggressiveMode) {
         this.playerBlocks.add(Material.LEAVES.getId());
         this.playerBlocks.add(Material.LOG.getId());
         this.playerBlocks.add(Material.VINE.getId());
      }
   }

   @Override
   public void run() {
      this.removeSandstone();
      this.removePlayerBlocks();
      this.reduceStone();
      this.reduceLogs();
      this.removeHanging();
      this.removeWallsAndTowers();
      this.fillHolesAndTrenches();
      this.fixWater();
      this.removeDumpedFluids();
      if (this.creativeMode && this.environment == Environment.NORMAL) {
         this.fillBigHoles();
      }

      this.coverSurfaceStone();
      this.removePlayerLeaves();
      RestoreNatureExecutionTask task = new RestoreNatureExecutionTask(
         this.snapshots, this.miny, this.lesserBoundaryCorner, this.greaterBoundaryCorner, this.player
      );
      GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task);
   }

   private void removePlayerLeaves() {
      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int y = this.seaLevel - 1; y < this.snapshots[0].length; y++) {
               BlockSnapshot block = this.snapshots[x][y][z];
               if (block.typeId == Material.LEAVES.getId() && (block.data & 4) != 0) {
                  block.typeId = Material.AIR.getId();
               }
            }
         }
      }
   }

   private void fillBigHoles() {
      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            if (this.snapshots[x][this.seaLevel - 2][z].typeId == Material.AIR.getId()
               || this.snapshots[x][this.seaLevel - 2][z].typeId == Material.LAVA.getId()
               || this.snapshots[x][this.seaLevel - 2][z].typeId == Material.WATER.getId()
               || this.snapshots[x][this.seaLevel - 2][z].data != 0) {
               this.snapshots[x][this.seaLevel - 2][z].typeId = Material.STONE.getId();
            }

            if (this.snapshots[x][this.seaLevel - 3][z].typeId == Material.AIR.getId()
               || this.snapshots[x][this.seaLevel - 3][z].typeId == Material.LAVA.getId()
               || this.snapshots[x][this.seaLevel - 3][z].typeId == Material.WATER.getId()
               || this.snapshots[x][this.seaLevel - 3][z].data != 0) {
               this.snapshots[x][this.seaLevel - 3][z].typeId = Material.STONE.getId();
            }
         }
      }
   }

   private void removeSandstone() {
      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int y = this.snapshots[0].length - 2; y > this.miny; y--) {
               if (this.snapshots[x][y][z].typeId == Material.SANDSTONE.getId()) {
                  BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                  BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];
                  BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                  BlockSnapshot downBlock = this.snapshots[x][y][z - 1];
                  BlockSnapshot underBlock = this.snapshots[x][y - 1][z];
                  BlockSnapshot aboveBlock = this.snapshots[x][y + 1][z];
                  if (aboveBlock.typeId != Material.SAND.getId() || underBlock.typeId != Material.AIR.getId()) {
                     if (leftBlock.typeId != Material.SAND.getId()
                        && rightBlock.typeId != Material.SAND.getId()
                        && upBlock.typeId != Material.SAND.getId()
                        && downBlock.typeId != Material.SAND.getId()
                        && aboveBlock.typeId != Material.SAND.getId()
                        && underBlock.typeId != Material.SAND.getId()) {
                        this.snapshots[x][y][z].typeId = Material.AIR.getId();
                     } else {
                        this.snapshots[x][y][z].typeId = Material.SAND.getId();
                     }
                  }
               }
            }
         }
      }
   }

   private void reduceStone() {
      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int thisy = this.highestY(x, z, true);
               thisy > this.seaLevel - 1
                  && (this.snapshots[x][thisy][z].typeId == Material.STONE.getId() || this.snapshots[x][thisy][z].typeId == Material.SANDSTONE.getId());
               thisy--
            ) {
               BlockSnapshot leftBlock = this.snapshots[x + 1][thisy][z];
               BlockSnapshot rightBlock = this.snapshots[x - 1][thisy][z];
               BlockSnapshot upBlock = this.snapshots[x][thisy][z + 1];
               BlockSnapshot downBlock = this.snapshots[x][thisy][z - 1];
               byte adjacentBlockCount = 0;
               if (leftBlock.typeId != Material.AIR.getId() && leftBlock.typeId != Material.LEAVES.getId() && leftBlock.typeId != Material.VINE.getId()) {
                  adjacentBlockCount++;
               }

               if (rightBlock.typeId != Material.AIR.getId() && rightBlock.typeId != Material.LEAVES.getId() && rightBlock.typeId != Material.VINE.getId()) {
                  adjacentBlockCount++;
               }

               if (downBlock.typeId != Material.AIR.getId() && downBlock.typeId != Material.LEAVES.getId() && downBlock.typeId != Material.VINE.getId()) {
                  adjacentBlockCount++;
               }

               if (upBlock.typeId != Material.AIR.getId() && upBlock.typeId != Material.LEAVES.getId() && upBlock.typeId != Material.VINE.getId()) {
                  adjacentBlockCount++;
               }

               if (adjacentBlockCount < 3) {
                  this.snapshots[x][thisy][z].typeId = Material.AIR.getId();
               }
            }
         }
      }
   }

   private void reduceLogs() {
      boolean jungleBiome = this.biome == Biome.JUNGLE || this.biome == Biome.JUNGLE_HILLS;

      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int y = this.seaLevel - 1; y < this.snapshots[0].length; y++) {
               BlockSnapshot block = this.snapshots[x][y][z];
               if (block.typeId == Material.LOG.getId() && (!jungleBiome || block.data != 3)) {
                  BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                  BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];
                  BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                  BlockSnapshot downBlock = this.snapshots[x][y][z - 1];
                  if (leftBlock.typeId == Material.LOG.getId()
                     || rightBlock.typeId == Material.LOG.getId()
                     || upBlock.typeId == Material.LOG.getId()
                     || downBlock.typeId == Material.LOG.getId()) {
                     this.snapshots[x][y][z].typeId = Material.AIR.getId();
                  }
               }
            }
         }
      }
   }

   private void removePlayerBlocks() {
      int miny = this.miny;
      if (miny < 1) {
         miny = 1;
      }

      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int y = miny; y < this.snapshots[0].length - 1; y++) {
               BlockSnapshot block = this.snapshots[x][y][z];
               if (this.playerBlocks.contains(block.typeId)) {
                  block.typeId = Material.AIR.getId();
               }
            }
         }
      }
   }

   private void removeHanging() {
      int miny = this.miny;
      if (miny < 1) {
         miny = 1;
      }

      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int y = miny; y < this.snapshots[0].length - 1; y++) {
               BlockSnapshot block = this.snapshots[x][y][z];
               BlockSnapshot underBlock = this.snapshots[x][y - 1][z];
               if ((
                     underBlock.typeId == Material.AIR.getId()
                        || underBlock.typeId == Material.STATIONARY_WATER.getId()
                        || underBlock.typeId == Material.STATIONARY_LAVA.getId()
                        || underBlock.typeId == Material.LEAVES.getId()
                  )
                  && this.notAllowedToHang.contains(block.typeId)) {
                  block.typeId = Material.AIR.getId();
               }
            }
         }
      }
   }

   private void removeWallsAndTowers() {
      int[] excludedBlocksArray = new int[]{
         Material.CACTUS.getId(),
         Material.LONG_GRASS.getId(),
         Material.RED_MUSHROOM.getId(),
         Material.BROWN_MUSHROOM.getId(),
         Material.DEAD_BUSH.getId(),
         Material.SAPLING.getId(),
         Material.YELLOW_FLOWER.getId(),
         Material.RED_ROSE.getId(),
         Material.SUGAR_CANE_BLOCK.getId(),
         Material.VINE.getId(),
         Material.PUMPKIN.getId(),
         Material.WATER_LILY.getId(),
         Material.LEAVES.getId()
      };
      ArrayList<Integer> excludedBlocks = new ArrayList<>();

      for (int i = 0; i < excludedBlocksArray.length; i++) {
         excludedBlocks.add(excludedBlocksArray[i]);
      }

      boolean changed;
      do {
         changed = false;

         for (int x = 1; x < this.snapshots.length - 1; x++) {
            for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
               int thisy = this.highestY(x, z, false);
               if (!excludedBlocks.contains(this.snapshots[x][thisy][z].typeId)) {
                  int righty = this.highestY(x + 1, z, false);

                  for (int lefty = this.highestY(x - 1, z, false); lefty < thisy && righty < thisy; changed = true) {
                     this.snapshots[x][thisy--][z].typeId = Material.AIR.getId();
                  }

                  int upy = this.highestY(x, z + 1, false);

                  for (int downy = this.highestY(x, z - 1, false); upy < thisy && downy < thisy; changed = true) {
                     this.snapshots[x][thisy--][z].typeId = Material.AIR.getId();
                  }
               }
            }
         }
      } while (changed);
   }

   private void coverSurfaceStone() {
      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            int y = this.highestY(x, z, true);
            BlockSnapshot block = this.snapshots[x][y][z];
            if (block.typeId == Material.STONE.getId()
               || block.typeId == Material.GRAVEL.getId()
               || block.typeId == Material.SOIL.getId()
               || block.typeId == Material.DIRT.getId()
               || block.typeId == Material.SANDSTONE.getId()) {
               if (this.biome != Biome.DESERT && this.biome != Biome.DESERT_HILLS && this.biome != Biome.BEACH) {
                  this.snapshots[x][y][z].typeId = Material.GRASS.getId();
               } else {
                  this.snapshots[x][y][z].typeId = Material.SAND.getId();
               }
            }
         }
      }
   }

   private void fillHolesAndTrenches() {
      ArrayList<Integer> fillableBlocks = new ArrayList<>();
      fillableBlocks.add(Material.AIR.getId());
      fillableBlocks.add(Material.STATIONARY_WATER.getId());
      fillableBlocks.add(Material.STATIONARY_LAVA.getId());
      fillableBlocks.add(Material.LONG_GRASS.getId());
      ArrayList<Integer> notSuitableForFillBlocks = new ArrayList<>();
      notSuitableForFillBlocks.add(Material.LONG_GRASS.getId());
      notSuitableForFillBlocks.add(Material.CACTUS.getId());
      notSuitableForFillBlocks.add(Material.STATIONARY_WATER.getId());
      notSuitableForFillBlocks.add(Material.STATIONARY_LAVA.getId());

      boolean changed;
      do {
         changed = false;

         for (int x = 1; x < this.snapshots.length - 1; x++) {
            for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
               for (int y = 0; y < this.snapshots[0].length - 1; y++) {
                  BlockSnapshot block = this.snapshots[x][y][z];
                  if (fillableBlocks.contains(block.typeId)) {
                     BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                     BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];
                     if (!fillableBlocks.contains(leftBlock.typeId)
                        && !fillableBlocks.contains(rightBlock.typeId)
                        && !notSuitableForFillBlocks.contains(rightBlock.typeId)) {
                        block.typeId = rightBlock.typeId;
                        changed = true;
                     }

                     BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                     BlockSnapshot downBlock = this.snapshots[x][y][z - 1];
                     if (!fillableBlocks.contains(upBlock.typeId)
                        && !fillableBlocks.contains(downBlock.typeId)
                        && !notSuitableForFillBlocks.contains(downBlock.typeId)) {
                        block.typeId = downBlock.typeId;
                        changed = true;
                     }
                  }
               }
            }
         }
      } while (changed);
   }

   private void fixWater() {
      int miny = this.miny;
      if (miny < 1) {
         miny = 1;
      }

      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int y = miny; y < this.snapshots[0].length - 1; y++) {
               BlockSnapshot block = this.snapshots[x][y][z];
               BlockSnapshot underBlock = this.snapshots[x][y][z];
               if ((block.typeId == Material.STATIONARY_WATER.getId() || block.typeId == Material.STATIONARY_LAVA.getId())
                  && (underBlock.typeId == Material.AIR.getId() || underBlock.data != 0)) {
                  block.typeId = Material.AIR.getId();
               }
            }
         }
      }

      boolean changed;
      do {
         changed = false;

         for (int y = this.seaLevel - 10; y <= this.seaLevel; y++) {
            for (int x = 1; x < this.snapshots.length - 1; x++) {
               for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
                  BlockSnapshot block = this.snapshots[x][y][z];
                  if (block.typeId == Material.AIR.getId() || block.typeId == Material.STATIONARY_WATER.getId() && block.data != 0) {
                     BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                     BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];
                     BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                     BlockSnapshot downBlock = this.snapshots[x][y][z - 1];
                     BlockSnapshot underBlock = this.snapshots[x][y - 1][z];
                     if (underBlock.typeId == Material.STATIONARY_WATER.getId() && underBlock.data == 0) {
                        byte adjacentSourceWaterCount = 0;
                        if (leftBlock.typeId == Material.STATIONARY_WATER.getId() && leftBlock.data == 0) {
                           adjacentSourceWaterCount++;
                        }

                        if (rightBlock.typeId == Material.STATIONARY_WATER.getId() && rightBlock.data == 0) {
                           adjacentSourceWaterCount++;
                        }

                        if (upBlock.typeId == Material.STATIONARY_WATER.getId() && upBlock.data == 0) {
                           adjacentSourceWaterCount++;
                        }

                        if (downBlock.typeId == Material.STATIONARY_WATER.getId() && downBlock.data == 0) {
                           adjacentSourceWaterCount++;
                        }

                        if (adjacentSourceWaterCount >= 2) {
                           block.typeId = Material.STATIONARY_WATER.getId();
                           block.data = 0;
                           changed = true;
                        }
                     }
                  }
               }
            }
         }
      } while (changed);
   }

   private void removeDumpedFluids() {
      if (this.environment != Environment.NETHER) {
         for (int x = 1; x < this.snapshots.length - 1; x++) {
            for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
               for (int y = this.seaLevel - 1; y < this.snapshots[0].length - 1; y++) {
                  BlockSnapshot block = this.snapshots[x][y][z];
                  if (block.typeId == Material.STATIONARY_WATER.getId()
                     || block.typeId == Material.STATIONARY_LAVA.getId()
                     || block.typeId == Material.WATER.getId()
                     || block.typeId == Material.LAVA.getId()) {
                     block.typeId = Material.AIR.getId();
                  }
               }
            }
         }
      }
   }

   private int highestY(int x, int z, boolean ignoreLeaves) {
      int y;
      for (y = this.snapshots[0].length - 1; y > 0; y--) {
         BlockSnapshot block = this.snapshots[x][y][z];
         if (block.typeId != Material.AIR.getId()
            && (!ignoreLeaves || block.typeId != Material.SNOW.getId())
            && (!ignoreLeaves || block.typeId != Material.LEAVES.getId())
            && block.typeId != Material.STATIONARY_WATER.getId()
            && block.typeId != Material.WATER.getId()
            && block.typeId != Material.LAVA.getId()
            && block.typeId != Material.STATIONARY_LAVA.getId()) {
            return y;
         }
      }

      return y;
   }

   static ArrayList<Integer> getPlayerBlocks(Environment environment, Biome biome) {
      ArrayList<Integer> playerBlocks = new ArrayList<>();
      playerBlocks.add(Material.FIRE.getId());
      playerBlocks.add(Material.BED_BLOCK.getId());
      playerBlocks.add(Material.WOOD.getId());
      playerBlocks.add(Material.BOOKSHELF.getId());
      playerBlocks.add(Material.BREWING_STAND.getId());
      playerBlocks.add(Material.BRICK.getId());
      playerBlocks.add(Material.COBBLESTONE.getId());
      playerBlocks.add(Material.GLASS.getId());
      playerBlocks.add(Material.LAPIS_BLOCK.getId());
      playerBlocks.add(Material.DISPENSER.getId());
      playerBlocks.add(Material.NOTE_BLOCK.getId());
      playerBlocks.add(Material.POWERED_RAIL.getId());
      playerBlocks.add(Material.DETECTOR_RAIL.getId());
      playerBlocks.add(Material.PISTON_STICKY_BASE.getId());
      playerBlocks.add(Material.PISTON_BASE.getId());
      playerBlocks.add(Material.PISTON_EXTENSION.getId());
      playerBlocks.add(Material.WOOL.getId());
      playerBlocks.add(Material.PISTON_MOVING_PIECE.getId());
      playerBlocks.add(Material.GOLD_BLOCK.getId());
      playerBlocks.add(Material.IRON_BLOCK.getId());
      playerBlocks.add(Material.DOUBLE_STEP.getId());
      playerBlocks.add(Material.STEP.getId());
      playerBlocks.add(Material.CROPS.getId());
      playerBlocks.add(Material.TNT.getId());
      playerBlocks.add(Material.MOSSY_COBBLESTONE.getId());
      playerBlocks.add(Material.TORCH.getId());
      playerBlocks.add(Material.FIRE.getId());
      playerBlocks.add(Material.WOOD_STAIRS.getId());
      playerBlocks.add(Material.CHEST.getId());
      playerBlocks.add(Material.REDSTONE_WIRE.getId());
      playerBlocks.add(Material.DIAMOND_BLOCK.getId());
      playerBlocks.add(Material.WORKBENCH.getId());
      playerBlocks.add(Material.FURNACE.getId());
      playerBlocks.add(Material.BURNING_FURNACE.getId());
      playerBlocks.add(Material.WOODEN_DOOR.getId());
      playerBlocks.add(Material.SIGN_POST.getId());
      playerBlocks.add(Material.LADDER.getId());
      playerBlocks.add(Material.RAILS.getId());
      playerBlocks.add(Material.COBBLESTONE_STAIRS.getId());
      playerBlocks.add(Material.WALL_SIGN.getId());
      playerBlocks.add(Material.STONE_PLATE.getId());
      playerBlocks.add(Material.LEVER.getId());
      playerBlocks.add(Material.IRON_DOOR_BLOCK.getId());
      playerBlocks.add(Material.WOOD_PLATE.getId());
      playerBlocks.add(Material.REDSTONE_TORCH_ON.getId());
      playerBlocks.add(Material.REDSTONE_TORCH_OFF.getId());
      playerBlocks.add(Material.STONE_BUTTON.getId());
      playerBlocks.add(Material.SNOW_BLOCK.getId());
      playerBlocks.add(Material.JUKEBOX.getId());
      playerBlocks.add(Material.FENCE.getId());
      playerBlocks.add(Material.PORTAL.getId());
      playerBlocks.add(Material.JACK_O_LANTERN.getId());
      playerBlocks.add(Material.CAKE_BLOCK.getId());
      playerBlocks.add(Material.DIODE_BLOCK_ON.getId());
      playerBlocks.add(Material.DIODE_BLOCK_OFF.getId());
      playerBlocks.add(Material.TRAP_DOOR.getId());
      playerBlocks.add(Material.SMOOTH_BRICK.getId());
      playerBlocks.add(Material.HUGE_MUSHROOM_1.getId());
      playerBlocks.add(Material.HUGE_MUSHROOM_2.getId());
      playerBlocks.add(Material.IRON_FENCE.getId());
      playerBlocks.add(Material.THIN_GLASS.getId());
      playerBlocks.add(Material.MELON_STEM.getId());
      playerBlocks.add(Material.FENCE_GATE.getId());
      playerBlocks.add(Material.BRICK_STAIRS.getId());
      playerBlocks.add(Material.SMOOTH_STAIRS.getId());
      playerBlocks.add(Material.ENCHANTMENT_TABLE.getId());
      playerBlocks.add(Material.BREWING_STAND.getId());
      playerBlocks.add(Material.CAULDRON.getId());
      playerBlocks.add(Material.DIODE_BLOCK_ON.getId());
      playerBlocks.add(Material.DIODE_BLOCK_ON.getId());
      playerBlocks.add(Material.WEB.getId());
      playerBlocks.add(Material.SPONGE.getId());
      playerBlocks.add(Material.GRAVEL.getId());
      playerBlocks.add(Material.EMERALD_BLOCK.getId());
      playerBlocks.add(Material.SANDSTONE.getId());
      playerBlocks.add(Material.WOOD_STEP.getId());
      playerBlocks.add(Material.WOOD_DOUBLE_STEP.getId());
      playerBlocks.add(Material.ENDER_CHEST.getId());
      playerBlocks.add(Material.SANDSTONE_STAIRS.getId());
      playerBlocks.add(Material.SPRUCE_WOOD_STAIRS.getId());
      playerBlocks.add(Material.JUNGLE_WOOD_STAIRS.getId());
      playerBlocks.add(Material.COMMAND.getId());
      playerBlocks.add(Material.BEACON.getId());
      playerBlocks.add(Material.COBBLE_WALL.getId());
      playerBlocks.add(Material.FLOWER_POT.getId());
      playerBlocks.add(Material.CARROT.getId());
      playerBlocks.add(Material.POTATO.getId());
      playerBlocks.add(Material.WOOD_BUTTON.getId());
      playerBlocks.add(Material.SKULL.getId());
      playerBlocks.add(Material.ANVIL.getId());
      if (environment != Environment.NETHER) {
         playerBlocks.add(Material.NETHERRACK.getId());
         playerBlocks.add(Material.SOUL_SAND.getId());
         playerBlocks.add(Material.GLOWSTONE.getId());
         playerBlocks.add(Material.NETHER_BRICK.getId());
         playerBlocks.add(Material.NETHER_FENCE.getId());
         playerBlocks.add(Material.NETHER_BRICK_STAIRS.getId());
      }

      if (environment != Environment.THE_END) {
         playerBlocks.add(Material.OBSIDIAN.getId());
         playerBlocks.add(Material.ENDER_STONE.getId());
         playerBlocks.add(Material.ENDER_PORTAL_FRAME.getId());
      }

      if (biome == Biome.DESERT || biome == Biome.DESERT_HILLS || biome == Biome.BEACH || environment != Environment.NORMAL) {
         playerBlocks.add(Material.LEAVES.getId());
         playerBlocks.add(Material.LOG.getId());
      }

      return playerBlocks;
   }
}
