package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

class TreeCleanupTask implements Runnable {
   private Block originalChoppedBlock;
   private Block originalRootBlock;
   private byte originalRootBlockData;
   private ArrayList<Block> originalTreeBlocks;

   public TreeCleanupTask(Block originalChoppedBlock, Block originalRootBlock, ArrayList<Block> originalTreeBlocks, byte originalRootBlockData) {
      this.originalChoppedBlock = originalChoppedBlock;
      this.originalRootBlock = originalRootBlock;
      this.originalTreeBlocks = originalTreeBlocks;
      this.originalRootBlockData = originalRootBlockData;
   }

   @Override
   public void run() {
      Chunk chunk = this.originalChoppedBlock.getWorld().getChunkAt(this.originalChoppedBlock);
      if (!chunk.isLoaded()) {
         chunk.load();
         GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, this, 100L);
      } else if (this.originalChoppedBlock.getWorld().getBlockAt(this.originalChoppedBlock.getLocation()).getType() == Material.AIR) {
         for (int i = 0; i < this.originalTreeBlocks.size(); i++) {
            Location location = this.originalTreeBlocks.get(i).getLocation();
            Block currentBlock = location.getBlock();
            if (currentBlock.getType() != Material.LOG && currentBlock.getType() != Material.AIR) {
               return;
            }
         }

         boolean logsRemaining = false;

         for (int i = 0; i < this.originalTreeBlocks.size(); i++) {
            Location location = this.originalTreeBlocks.get(i).getLocation();
            Block currentBlock = location.getBlock();
            if (currentBlock.getType() == Material.LOG) {
               logsRemaining = true;
               currentBlock.setType(Material.AIR);
            }
         }

         if (logsRemaining && GriefPrevention.instance.config_trees_regrowGriefedTrees) {
            Block currentBlock = this.originalRootBlock.getLocation().getBlock();
            if (currentBlock.getType() == Material.AIR
               && (currentBlock.getRelative(BlockFace.DOWN).getType() == Material.DIRT || currentBlock.getRelative(BlockFace.DOWN).getType() == Material.GRASS)
               )
             {
               currentBlock.setType(Material.SAPLING);
               currentBlock.setData(this.originalRootBlockData);
            }
         }
      }
   }
}
