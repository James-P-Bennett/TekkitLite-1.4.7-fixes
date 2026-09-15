package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;

class EquipShovelProcessingTask implements Runnable {
   private Player player;

   public EquipShovelProcessingTask(Player player) {
      this.player = player;
   }

   @Override
   public void run() {
      if (this.player.isOnline()) {
         if (this.player.getItemInHand().getType() == GriefPrevention.instance.config_claims_modificationTool) {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(this.player.getName());
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            if (playerData.shovelMode == ShovelMode.Basic) {
               GriefPrevention.sendMessage(this.player, TextMode.Instr, Messages.RemainingBlocks, String.valueOf(remainingBlocks));
               if (GriefPrevention.instance.creativeRulesApply(this.player.getLocation())) {
                  GriefPrevention.sendMessage(this.player, TextMode.Instr, Messages.CreativeBasicsDemoAdvertisement);
               } else {
                  GriefPrevention.sendMessage(this.player, TextMode.Instr, Messages.SurvivalBasicsDemoAdvertisement);
               }
            }
         }
      }
   }
}
