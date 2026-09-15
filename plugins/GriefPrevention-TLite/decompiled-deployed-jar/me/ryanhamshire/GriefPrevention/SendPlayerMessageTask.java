package me.ryanhamshire.GriefPrevention;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

class SendPlayerMessageTask implements Runnable {
   private Player player;
   private ChatColor color;
   private String message;

   public SendPlayerMessageTask(Player player, ChatColor color, String message) {
      this.player = player;
      this.color = color;
      this.message = message;
   }

   @Override
   public void run() {
      GriefPrevention.sendMessage(this.player, this.color, this.message);
   }
}
