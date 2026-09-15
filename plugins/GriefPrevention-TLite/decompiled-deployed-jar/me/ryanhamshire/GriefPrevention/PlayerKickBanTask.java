package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;

class PlayerKickBanTask implements Runnable {
   private Player player;
   private String banReason;

   public PlayerKickBanTask(Player player, String banReason) {
      this.player = player;
      this.banReason = banReason;
   }

   @Override
   public void run() {
      if (this.banReason != null) {
         GriefPrevention.instance.getServer().getOfflinePlayer(this.player.getName()).setBanned(true);
         if (this.player.isOnline()) {
            this.player.kickPlayer(this.banReason);
         }
      } else if (this.player.isOnline()) {
         this.player.kickPlayer("");
      }
   }
}
