package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class PlayerEventHandler$1 implements Runnable {
   PlayerEventHandler$1(PlayerEventHandler var1, Player var2, ItemStack var3) {
      this.this$0 = var1;
      this.val$player = var2;
      this.val$entropyItem = var3;
   }

   @Override
   public void run() {
      this.val$player.getInventory().addItem(new ItemStack[]{this.val$entropyItem});
      this.val$player.updateInventory();
   }
}
