package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.entity.Player;

class DeliverClaimBlocksTask implements Runnable {
   @Override
   public void run() {
      Player[] players = GriefPrevention.instance.getServer().getOnlinePlayers();
      int accruedBlocks = GriefPrevention.instance.config_claims_blocksAccruedPerHour / 12;
      if (accruedBlocks < 0) {
         accruedBlocks = 1;
      }

      for (int i = 0; i < players.length; i++) {
         Player player = players[i];
         DataStore dataStore = GriefPrevention.instance.dataStore;
         PlayerData playerData = dataStore.getPlayerData(player.getName());
         Location lastLocation = playerData.lastAfkCheckLocation;

         try {
            if (!player.isInsideVehicle()
               && (lastLocation == null || lastLocation.distanceSquared(player.getLocation()) >= 9.0)
               && !player.getLocation().getBlock().isLiquid()) {
               if (playerData.accruedClaimBlocks > GriefPrevention.instance.config_claims_maxAccruedBlocks) {
                  continue;
               }

               playerData.accruedClaimBlocks += accruedBlocks;
               if (playerData.accruedClaimBlocks > GriefPrevention.instance.config_claims_maxAccruedBlocks) {
                  playerData.accruedClaimBlocks = GriefPrevention.instance.config_claims_maxAccruedBlocks;
               }
            }
         } catch (Exception var9) {
         }

         playerData.lastAfkCheckLocation = player.getLocation();
      }
   }
}
