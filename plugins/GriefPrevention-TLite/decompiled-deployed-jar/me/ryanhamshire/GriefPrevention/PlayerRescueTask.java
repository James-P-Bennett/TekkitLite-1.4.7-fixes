package me.ryanhamshire.GriefPrevention;

import java.util.Calendar;
import org.bukkit.Location;
import org.bukkit.entity.Player;

class PlayerRescueTask implements Runnable {
   private Location location;
   private Player player;

   public PlayerRescueTask(Player player, Location location) {
      this.player = player;
      this.location = location;
   }

   @Override
   public void run() {
      if (this.player.isOnline()) {
         PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(this.player.getName());
         playerData.pendingTrapped = false;
         if (this.player.getLocation().distance(this.location) > 3.0) {
            GriefPrevention.sendMessage(this.player, TextMode.Err, Messages.RescueAbortedMoved);
         } else {
            Location destination = GriefPrevention.instance.ejectPlayer(this.player);
            GriefPrevention.AddLogEntry(
               "Rescued trapped player "
                  + this.player.getName()
                  + " from "
                  + GriefPrevention.getfriendlyLocationString(this.location)
                  + " to "
                  + GriefPrevention.getfriendlyLocationString(destination)
                  + "."
            );
            playerData.lastTrappedUsage = Calendar.getInstance().getTime();
         }
      }
   }
}
