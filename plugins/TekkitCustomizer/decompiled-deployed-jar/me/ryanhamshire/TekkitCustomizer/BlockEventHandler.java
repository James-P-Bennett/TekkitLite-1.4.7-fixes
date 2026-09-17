package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockEventHandler implements Listener {
   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onBlockPlace(BlockPlaceEvent placeEvent) {
      Player player = placeEvent.getPlayer();
      Block block = placeEvent.getBlock();
      MaterialInfo bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Ownership, player, block.getTypeId(), block.getData(), block.getLocation());
      if (bannedInfo == null) {
         bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Placement, player, block.getTypeId(), block.getData(), block.getLocation());
      }

      if (bannedInfo != null) {
         placeEvent.setCancelled(true);
         player.sendMessage("Sorry, that block is banned.  Reason: " + bannedInfo.reason);
      }
   }
}
