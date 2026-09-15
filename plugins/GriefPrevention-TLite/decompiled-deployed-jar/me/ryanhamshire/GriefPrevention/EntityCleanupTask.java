package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;

class EntityCleanupTask implements Runnable {
   private double percentageStart;

   public EntityCleanupTask(double percentageStart) {
      this.percentageStart = percentageStart;
   }

   @Override
   public void run() {
      ArrayList<World> worlds = GriefPrevention.instance.config_claims_enabledCreativeWorlds;

      for (int i = 0; i < worlds.size(); i++) {
         World world = worlds.get(i);
         List<Entity> entities = world.getEntities();
         int j = (int)(entities.size() * this.percentageStart);
         int k = (int)(entities.size() * (this.percentageStart + 0.1));
         Claim cachedClaim = null;

         while (j < entities.size() && j < k) {
            Entity entity = entities.get(j);
            boolean remove = false;
            if (entity instanceof Boat) {
               Boat boat = (Boat)entity;
               if (boat.isEmpty()) {
                  remove = true;
               }
            } else if (entity instanceof Vehicle) {
               Vehicle vehicle = (Vehicle)entity;
               if (vehicle.getVelocity().lengthSquared() != 0.0) {
                  if (vehicle.isEmpty() || !(vehicle.getPassenger() instanceof Player)) {
                     remove = true;
                  }
               } else {
                  Material material = world.getBlockAt(vehicle.getLocation()).getType();
                  if (material != Material.RAILS && material != Material.POWERED_RAIL && material != Material.DETECTOR_RAIL) {
                     remove = true;
                  }
               }
            } else if (!(entity instanceof Player)) {
               Claim claim = GriefPrevention.instance.dataStore.getClaimAt(entity.getLocation(), false, cachedClaim);
               if (claim != null) {
                  cachedClaim = claim;
               } else {
                  remove = true;
               }
            }

            if (remove) {
               entity.remove();
            }

            j++;
         }
      }

      List<Claim> claims = GriefPrevention.instance.dataStore.claims;
      int j = (int)(claims.size() * this.percentageStart);

      for (int k = (int)(claims.size() * (this.percentageStart + 0.05)); j < claims.size() && j < k; j++) {
         Claim claim = claims.get(j);
         if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner())) {
            claim.allowMoreEntities();
         }
      }

      double nextRunPercentageStart = this.percentageStart + 0.05;
      if (nextRunPercentageStart > 0.99) {
         nextRunPercentageStart = 0.0;
         System.gc();
      }

      EntityCleanupTask task = new EntityCleanupTask(nextRunPercentageStart);
      GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 1200L);
   }
}
