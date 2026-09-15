package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;

class SiegeCheckupTask implements Runnable {
   private SiegeData siegeData;

   public SiegeCheckupTask(SiegeData siegeData) {
      this.siegeData = siegeData;
   }

   @Override
   public void run() {
      DataStore dataStore = GriefPrevention.instance.dataStore;
      Player defender = this.siegeData.defender;
      Player attacker = this.siegeData.attacker;
      Claim defenderClaim = dataStore.getClaimAt(defender.getLocation(), false, null);
      if (defenderClaim != null) {
         String noAccessReason = defenderClaim.allowAccess(defender);
         if (defenderClaim.canSiege(defender) && noAccessReason == null) {
            this.siegeData.claims.add(defenderClaim);
            defenderClaim.siegeData = this.siegeData;
         }
      }

      boolean attackerRemains = this.playerRemains(attacker);
      boolean defenderRemains = this.playerRemains(defender);
      if (attackerRemains && defenderRemains) {
         this.scheduleAnotherCheck();
      } else if (attackerRemains && !defenderRemains) {
         dataStore.endSiege(this.siegeData, attacker.getName(), defender.getName(), false);
      } else if (!attackerRemains && defenderRemains) {
         dataStore.endSiege(this.siegeData, defender.getName(), attacker.getName(), false);
      } else if (attacker.getLocation().distanceSquared(defender.getLocation()) < 2500.0) {
         this.scheduleAnotherCheck();
      } else {
         dataStore.endSiege(this.siegeData, attacker.getName(), defender.getName(), false);
      }
   }

   private boolean playerRemains(Player player) {
      for (int i = 0; i < this.siegeData.claims.size(); i++) {
         Claim claim = this.siegeData.claims.get(i);
         if (claim.isNear(player.getLocation(), 25)) {
            return true;
         }
      }

      return false;
   }

   private void scheduleAnotherCheck() {
      this.siegeData.checkupTaskID = GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, this, 600L);
   }
}
