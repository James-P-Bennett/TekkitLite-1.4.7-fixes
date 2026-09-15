package me.ryanhamshire.GriefPrevention;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;

class RestoreNatureExecutionTask implements Runnable {
   private BlockSnapshot[][][] snapshots;
   private int miny;
   private Location lesserCorner;
   private Location greaterCorner;
   private Player player;

   public RestoreNatureExecutionTask(BlockSnapshot[][][] snapshots, int miny, Location lesserCorner, Location greaterCorner, Player player) {
      this.snapshots = snapshots;
      this.miny = miny;
      this.lesserCorner = lesserCorner;
      this.greaterCorner = greaterCorner;
      this.player = player;
   }

   @Override
   public void run() {
      Claim cachedClaim = null;

      for (int x = 1; x < this.snapshots.length - 1; x++) {
         for (int z = 1; z < this.snapshots[0][0].length - 1; z++) {
            for (int y = this.miny; y < this.snapshots[0].length; y++) {
               BlockSnapshot blockUpdate = this.snapshots[x][y][z];
               Block currentBlock = blockUpdate.location.getBlock();
               if (blockUpdate.typeId != currentBlock.getTypeId() || blockUpdate.data != currentBlock.getData()) {
                  Claim claim = GriefPrevention.instance.dataStore.getClaimAt(blockUpdate.location, false, cachedClaim);
                  if (claim != null) {
                     cachedClaim = claim;
                     break;
                  }

                  currentBlock.setTypeId(blockUpdate.typeId);
                  currentBlock.setData(blockUpdate.data);
               }
            }
         }
      }

      Chunk chunk = this.lesserCorner.getChunk();
      Entity[] entities = chunk.getEntities();

      for (int i = 0; i < entities.length; i++) {
         Entity entity = entities[i];
         if (!(entity instanceof Player) && !(entity instanceof Animals)) {
            if (!(entity instanceof Hanging) || GriefPrevention.instance.dataStore.getClaimAt(entity.getLocation(), false, null) == null) {
               entity.remove();
            }
         } else {
            Block feetBlock = entity.getLocation().getBlock();
            feetBlock.setType(Material.AIR);
            feetBlock.getRelative(BlockFace.UP).setType(Material.AIR);
         }
      }

      if (this.player != null) {
         Claim claim = new Claim(this.lesserCorner, this.greaterCorner, "", new String[0], new String[0], new String[0], new String[0], null);
         Visualization visualization = Visualization.FromClaim(
            claim, this.player.getLocation().getBlockY(), VisualizationType.RestoreNature, this.player.getLocation()
         );
         Visualization.Apply(this.player, visualization);
      }
   }
}
