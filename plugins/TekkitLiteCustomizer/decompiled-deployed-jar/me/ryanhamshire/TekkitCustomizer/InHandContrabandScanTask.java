package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

class InHandContrabandScanTask implements Runnable {
   Player player;
   int slotNumber;

   public InHandContrabandScanTask(Player player, int slotNumber) {
      this.player = player;
      this.slotNumber = slotNumber;
   }

   @Override
   public void run() {
      if (this.player.isOnline()) {
         PlayerInventory inventory = this.player.getInventory();
         if (inventory.getHeldItemSlot() == this.slotNumber) {
            ItemStack inHandStack = this.player.getItemInHand();
            if (inHandStack != null) {
               MaterialInfo bannedInfo = TekkitCustomizer.instance
                  .isBanned(ActionType.Ownership, this.player, inHandStack.getTypeId(), inHandStack.getData().getData(), this.player.getLocation());
               if (bannedInfo != null) {
                  inventory.setItem(this.slotNumber, new ItemStack(Material.AIR));
                  TekkitCustomizer.AddLogEntry("Confiscated " + bannedInfo.toString() + " from " + this.player.getName() + ".");
                  this.player.sendMessage("Banned item confiscated.  Reason: " + bannedInfo.reason);
               }
            }
         }
      }
   }
}
