package me.ryanhamshire.GriefPrevention;

import java.net.InetAddress;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;
import org.bukkit.Location;

public class PlayerData {
   public String playerName;
   public Vector<Claim> claims = new Vector<>();
   public int accruedClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;
   public Location lastAfkCheckLocation = null;
   public int bonusClaimBlocks = 0;
   public ShovelMode shovelMode = ShovelMode.Basic;
   int fillRadius = 0;
   public Location lastShovelLocation = null;
   public Claim claimResizing = null;
   public Claim claimSubdividing = null;
   public Date lastTrappedUsage;
   public boolean pendingTrapped = false;
   public Location lastChestDamageLocation = null;
   int unclaimedBlockPlacementsUntilWarning = 1;
   long lastDeathTimeStamp = 0L;
   public Date lastLogin;
   public String lastMessage = "";
   public Date lastMessageTimestamp = new Date();
   public int spamCount = 0;
   public boolean spamWarned = false;
   public Visualization currentVisualization = null;
   public boolean pvpImmune = false;
   public long lastSpawn = 0L;
   public boolean ignoreClaims = false;
   public Claim lastClaim = null;
   public SiegeData siegeData = null;
   public long lastPvpTimestamp = 0L;
   public String lastPvpPlayer = "";
   public boolean warnedAboutMajorDeletion = false;
   public InetAddress ipAddress;

   PlayerData() {
      Calendar lastYear = Calendar.getInstance();
      lastYear.add(1, -1);
      this.lastLogin = lastYear.getTime();
      this.lastTrappedUsage = lastYear.getTime();
   }

   public boolean inPvpCombat() {
      if (this.lastPvpTimestamp == 0L) {
         return false;
      } else {
         long now = Calendar.getInstance().getTimeInMillis();
         long elapsed = now - this.lastPvpTimestamp;
         if (elapsed > GriefPrevention.instance.config_pvp_combatTimeoutSeconds * 1000) {
            this.lastPvpTimestamp = 0L;
            return false;
         } else {
            return true;
         }
      }
   }

   public int getRemainingClaimBlocks() {
      int remainingBlocks = this.accruedClaimBlocks + this.bonusClaimBlocks;

      for (int i = 0; i < this.claims.size(); i++) {
         Claim claim = this.claims.get(i);
         remainingBlocks -= claim.getArea();
      }

      return remainingBlocks + GriefPrevention.instance.dataStore.getGroupBonusBlocks(this.playerName);
   }
}
