package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;

class VisualizationApplicationTask implements Runnable {
   private Visualization visualization;
   private Player player;
   private PlayerData playerData;

   public VisualizationApplicationTask(Player player, PlayerData playerData, Visualization visualization) {
      this.visualization = visualization;
      this.playerData = playerData;
      this.player = player;
   }

   @Override
   public void run() {
      for (int i = 0; i < this.visualization.elements.size(); i++) {
         VisualizationElement element = this.visualization.elements.get(i);
         this.player.sendBlockChange(element.location, element.visualizedMaterial, element.visualizedData);
      }

      this.playerData.currentVisualization = this.visualization;
   }
}
