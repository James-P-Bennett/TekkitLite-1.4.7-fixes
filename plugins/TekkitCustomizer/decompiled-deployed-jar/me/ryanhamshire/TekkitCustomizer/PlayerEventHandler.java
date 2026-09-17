package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class PlayerEventHandler implements Listener {
   @EventHandler(ignoreCancelled = true)
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < inventory.getSize(); i++) {
         ItemStack item = inventory.getItem(i);
         if (item != null
            && TekkitCustomizer.instance.isBanned(ActionType.Ownership, player, item.getTypeId(), item.getData().getData(), player.getLocation()) != null) {
            inventory.setItem(i, new ItemStack(Material.AIR));
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   void onItemCrafted(CraftItemEvent event) {
      Player player = (Player)event.getWhoClicked();
      ItemStack item = event.getRecipe().getResult();
      MaterialInfo bannedInfo = TekkitCustomizer.instance
         .isBanned(ActionType.Ownership, player, item.getTypeId(), item.getData().getData(), player.getLocation());
      if (bannedInfo == null) {
         bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Crafting, player, item.getTypeId(), item.getData().getData(), player.getLocation());
      }

      if (bannedInfo != null) {
         event.setCancelled(true);
         player.sendMessage("Sorry, that item is banned.  Reason: " + bannedInfo.reason);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   void onItemClicked(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      ItemStack item = event.getCurrentItem();
      if (item != null) {
         MaterialInfo bannedInfo = TekkitCustomizer.instance
            .isBanned(ActionType.Ownership, player, item.getTypeId(), item.getData().getData(), player.getLocation());
         if (bannedInfo != null) {
            event.setCancelled(true);
            if (event.getInventory() instanceof PlayerInventory) {
               item.setType(Material.AIR);
               player.sendMessage("Banned item confiscated.  Reason: " + bannedInfo.reason);
            } else {
               player.sendMessage("Sorry, that item is banned.  Reason: " + bannedInfo.reason);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   void onPlayerInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (event.getAction() != Action.PHYSICAL) {
         MaterialInfo bannedInfo = TekkitCustomizer.instance
            .isBanned(ActionType.Ownership, player, player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getLocation());
         if (bannedInfo != null) {
            event.setCancelled(true);
            player.getInventory().setItemInHand(new ItemStack(Material.AIR));
            TekkitCustomizer.AddLogEntry("Confiscated " + bannedInfo.toString() + " from " + player.getName() + ".");
            player.sendMessage("Banned item confiscated.  Reason: " + bannedInfo.reason);
         } else {
            bannedInfo = TekkitCustomizer.instance
               .isBanned(ActionType.Usage, player, player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getLocation());
            if (bannedInfo != null) {
               event.setCancelled(true);
               player.sendMessage("Sorry, usage of that item has been banned.  Reason: " + bannedInfo.reason);
            }

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
               Block block = event.getClickedBlock();
               bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Usage, player, block.getTypeId(), block.getData(), block.getLocation());
               if (bannedInfo != null) {
                  event.setCancelled(true);
                  player.sendMessage("Sorry, usage of that item has been banned.  Reason: " + bannedInfo.reason);
               } else {
                  bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Ownership, player, block.getTypeId(), block.getData(), block.getLocation());
                  if (bannedInfo != null) {
                     event.setCancelled(true);
                     player.sendMessage("Sorry, usage of that item has been banned.  Reason: " + bannedInfo.reason);
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
      Player player = event.getPlayer();
      MaterialInfo usageBannedInfo = TekkitCustomizer.instance
         .isBanned(ActionType.Usage, player, player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getLocation());
      if (usageBannedInfo != null) {
         event.setCancelled(true);
         MaterialInfo bannedInfo = TekkitCustomizer.instance
            .isBanned(ActionType.Ownership, player, player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getLocation());
         if (bannedInfo != null) {
            player.sendMessage("Banned item confiscated.  Reason: " + bannedInfo.reason);
            TekkitCustomizer.AddLogEntry("Confiscated " + bannedInfo.toString() + " from " + player.getName() + ".");
            player.getInventory().setItemInHand(new ItemStack(Material.AIR));
         } else {
            player.sendMessage("Sorry, usage of that item has been banned.  Reason: " + usageBannedInfo.reason);
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   void onPlayerPickupItem(PlayerPickupItemEvent event) {
      Player player = event.getPlayer();
      ItemStack item = event.getItem().getItemStack();
      if (TekkitCustomizer.instance.isBanned(ActionType.Ownership, player, item.getTypeId(), item.getData().getData(), player.getLocation()) != null) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   void onPlayerSwitchInHand(PlayerItemHeldEvent event) {
      InHandContrabandScanTask task = new InHandContrabandScanTask(event.getPlayer(), event.getNewSlot());
      TekkitCustomizer.instance.getServer().getScheduler().scheduleSyncDelayedTask(TekkitCustomizer.instance, task, 10L);
   }
}
