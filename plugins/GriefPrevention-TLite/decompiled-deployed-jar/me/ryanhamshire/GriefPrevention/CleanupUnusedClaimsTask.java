package me.ryanhamshire.GriefPrevention;

import java.util.Calendar;
import java.util.Random;
import java.util.Vector;
import org.bukkit.Chunk;
import org.bukkit.World;

class CleanupUnusedClaimsTask implements Runnable {
   int nextClaimIndex;

   CleanupUnusedClaimsTask() {
      if (GriefPrevention.instance.dataStore.claims.size() == 0) {
         this.nextClaimIndex = 0;
      } else {
         Random randomNumberGenerator = new Random();
         this.nextClaimIndex = randomNumberGenerator.nextInt(GriefPrevention.instance.dataStore.claims.size());
      }
   }

   @Override
   public void run() {
      if (GriefPrevention.instance.dataStore.claims.size() != 0) {
         if (this.nextClaimIndex >= GriefPrevention.instance.dataStore.claims.size()) {
            this.nextClaimIndex = 0;
         }

         Claim claim = GriefPrevention.instance.dataStore.claims.get(this.nextClaimIndex++);
         if (!claim.isAdminClaim()) {
            boolean cleanupChunks = false;
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(claim.ownerName);
            int areaOfDefaultClaim = 0;
            if (GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius >= 0) {
               areaOfDefaultClaim = (int)Math.pow(GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius * 2 + 1, 2.0);
            }

            Calendar sevenDaysAgo = Calendar.getInstance();
            sevenDaysAgo.add(5, -GriefPrevention.instance.config_claims_chestClaimExpirationDays);
            boolean newPlayerClaimsExpired = sevenDaysAgo.getTime().after(playerData.lastLogin);
            if (newPlayerClaimsExpired && playerData.claims.size() == 1) {
               if (claim.getArea() <= areaOfDefaultClaim && GriefPrevention.instance.config_claims_chestClaimExpirationDays > 0) {
                  claim.removeSurfaceFluids(null);
                  GriefPrevention.instance.dataStore.deleteClaim(claim);
                  cleanupChunks = true;
                  if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner())
                        && GriefPrevention.instance.config_claims_creativeAutoNatureRestoration
                     || GriefPrevention.instance.config_claims_survivalAutoNatureRestoration) {
                     GriefPrevention.instance.restoreClaim(claim, 0L);
                  }

                  GriefPrevention.AddLogEntry(" " + claim.getOwnerName() + "'s new player claim expired.");
               }
            } else if (GriefPrevention.instance.config_claims_expirationDays > 0) {
               Calendar earliestPermissibleLastLogin = Calendar.getInstance();
               earliestPermissibleLastLogin.add(5, -GriefPrevention.instance.config_claims_expirationDays);
               if (earliestPermissibleLastLogin.getTime().after(playerData.lastLogin)) {
                  Vector<Claim> claims = new Vector<>();

                  for (int i = 0; i < playerData.claims.size(); i++) {
                     claims.add(playerData.claims.get(i));
                  }

                  GriefPrevention.instance.dataStore.deleteClaimsForPlayer(claim.getOwnerName(), true);
                  GriefPrevention.AddLogEntry(" All of " + claim.getOwnerName() + "'s claims have expired.");

                  for (int i = 0; i < claims.size(); i++) {
                     if (GriefPrevention.instance.creativeRulesApply(claims.get(i).getLesserBoundaryCorner())
                           && GriefPrevention.instance.config_claims_creativeAutoNatureRestoration
                        || GriefPrevention.instance.config_claims_survivalAutoNatureRestoration) {
                        GriefPrevention.instance.restoreClaim(claims.get(i), 0L);
                        cleanupChunks = true;
                     }
                  }
               }
            } else if (GriefPrevention.instance.config_claims_unusedClaimExpirationDays > 0) {
               Calendar earliestAllowedLoginDate = Calendar.getInstance();
               earliestAllowedLoginDate.add(5, -GriefPrevention.instance.config_claims_unusedClaimExpirationDays);
               boolean needsInvestmentScan = earliestAllowedLoginDate.getTime().after(playerData.lastLogin);
               if (claim.isAdminClaim() || claim.getWidth() > 25 || claim.getHeight() > 25) {
                  return;
               }

               if (needsInvestmentScan || GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner())) {
                  int minInvestment;
                  if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner())) {
                     minInvestment = 400;
                  } else {
                     minInvestment = 100;
                  }

                  long investmentScore = claim.getPlayerInvestmentScore();
                  cleanupChunks = true;
                  boolean removeClaim = false;
                  if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner()) && investmentScore < -5000L) {
                     removeClaim = true;
                  } else if (needsInvestmentScan && investmentScore < minInvestment) {
                     removeClaim = true;
                  }

                  if (removeClaim) {
                     GriefPrevention.instance.dataStore.deleteClaim(claim);
                     GriefPrevention.AddLogEntry(
                        "Removed " + claim.getOwnerName() + "'s unused claim @ " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner())
                     );
                     if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner())
                           && GriefPrevention.instance.config_claims_creativeAutoNatureRestoration
                        || GriefPrevention.instance.config_claims_survivalAutoNatureRestoration) {
                        GriefPrevention.instance.restoreClaim(claim, 0L);
                     }
                  }
               }
            }

            if (!GriefPrevention.instance.getServer().getOfflinePlayer(claim.ownerName).isOnline()) {
               GriefPrevention.instance.dataStore.clearCachedPlayerData(claim.ownerName);
            }

            if (cleanupChunks) {
               World world = claim.getLesserBoundaryCorner().getWorld();
               Chunk[] chunks = world.getLoadedChunks();

               for (int i = 0; i < chunks.length; i++) {
                  Chunk chunk = chunks[i];
                  chunk.unload(true, true);
               }
            }

            if (this.nextClaimIndex % 5 == 0) {
               System.gc();
            }
         }
      }
   }
}
