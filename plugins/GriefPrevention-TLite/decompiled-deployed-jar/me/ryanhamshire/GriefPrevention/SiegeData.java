package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import org.bukkit.entity.Player;

public class SiegeData {
   public Player defender;
   public Player attacker;
   public ArrayList<Claim> claims;
   public int checkupTaskID;

   public SiegeData(Player attacker, Player defender, Claim claim) {
      this.defender = defender;
      this.attacker = attacker;
      this.claims = new ArrayList<>();
      this.claims.add(claim);
   }
}
