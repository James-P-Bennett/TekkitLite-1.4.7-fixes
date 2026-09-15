package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;

class SecureClaimTask implements Runnable {
   private SiegeData siegeData;

   public SecureClaimTask(SiegeData siegeData) {
      this.siegeData = siegeData;
   }

   @Override
   public void run() {
      for (int i = 0; i < this.siegeData.claims.size(); i++) {
         Claim claim = this.siegeData.claims.get(i);
         claim.doorsOpen = false;
         Player[] onlinePlayers = GriefPrevention.instance.getServer().getOnlinePlayers();

         for (int j = 0; j < onlinePlayers.length; j++) {
            Player player = onlinePlayers[j];
            if (claim.contains(player.getLocation(), false, false) && claim.allowAccess(player) != null) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeDoorsLockedEjection);
               GriefPrevention.instance.ejectPlayer(player);
            }
         }
      }
   }
}
