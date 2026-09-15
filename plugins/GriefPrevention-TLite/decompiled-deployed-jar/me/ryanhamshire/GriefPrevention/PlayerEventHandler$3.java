package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class PlayerEventHandler$3 implements Runnable {
   PlayerEventHandler$3(PlayerEventHandler var1, Player var2, ItemStack var3) {
      this.this$0 = var1;
      this.val$player = var2;
      this.val$wrathIgniter = var3;
   }

   @Override
   public void run() {
      this.val$player.getInventory().addItem(new ItemStack[]{this.val$wrathIgniter});
      this.val$player.updateInventory();
   }
}
