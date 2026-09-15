package me.ryanhamshire.TekkitCustomizer;

import java.util.ArrayList;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

class ContrabandScannerTask implements Runnable {
   private int nextChunkPercentile = 0;
   private int nextPlayerPercentile = 0;

   @Override
   public void run() {
      if (TekkitCustomizer.instance.config_worldBanned.size() > 0) {
         ArrayList<World> worlds = TekkitCustomizer.instance.config_enforcementWorlds;

         for (int i = 0; i < worlds.size(); i++) {
            World world = worlds.get(i);
            Chunk[] chunks = world.getLoadedChunks();
            int firstChunk = (int)(chunks.length * (this.nextChunkPercentile / 100.0F));
            int lastChunk = (int)(chunks.length * ((this.nextChunkPercentile + 5) / 100.0F));

            for (int j = firstChunk; j < lastChunk; j++) {
               Chunk chunk = chunks[j];

               for (int x = 0; x < 16; x++) {
                  for (int y = 0; y < chunk.getWorld().getMaxHeight(); y++) {
                     for (int z = 0; z < 16; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        MaterialInfo materialInfo = new MaterialInfo(block.getTypeId(), block.getData(), null, null);
                        MaterialInfo bannedInfo = TekkitCustomizer.instance.config_worldBanned.Contains(materialInfo);
                        if (bannedInfo != null) {
                           block.setType(Material.AIR);
                           TekkitCustomizer.AddLogEntry(
                              "Removed " + bannedInfo.toString() + " @ " + TekkitCustomizer.getFriendlyLocationString(block.getLocation())
                           );
                        }
                     }
                  }
               }
            }
         }

         this.nextChunkPercentile += 5;
         if (this.nextChunkPercentile >= 100) {
            this.nextChunkPercentile = 0;
         }
      }

      if (TekkitCustomizer.instance.config_ownershipBanned.size() > 0) {
         Server server = TekkitCustomizer.instance.getServer();
         Player[] players = server.getOnlinePlayers();
         if (players.length == 0) {
            return;
         }

         int firstPlayer = (int)(players.length * (this.nextPlayerPercentile / 100.0F));
         int lastPlayer = (int)(players.length * ((this.nextPlayerPercentile + 5) / 100.0F));
         if (lastPlayer == firstPlayer) {
            lastPlayer = players.length;
         }

         for (int j = firstPlayer; j < lastPlayer; j++) {
            Player player = players[j];
            PlayerInventory inventory = player.getInventory();

            for (int i = 0; i < inventory.getSize(); i++) {
               ItemStack itemStack = inventory.getItem(i);
               if (itemStack != null) {
                  MaterialInfo bannedInfo = TekkitCustomizer.instance
                     .isBanned(ActionType.Ownership, player, itemStack.getTypeId(), itemStack.getData().getData(), player.getLocation());
                  if (bannedInfo != null) {
                     inventory.setItem(i, new ItemStack(Material.AIR));
                     TekkitCustomizer.AddLogEntry("Confiscated " + bannedInfo.toString() + " from " + player.getName() + ".");
                  }
               }
            }

            ItemStack[] armor = inventory.getArmorContents();

            for (int i = 0; i < armor.length; i++) {
               ItemStack itemStack = armor[i];
               if (itemStack != null) {
                  MaterialInfo bannedInfo = TekkitCustomizer.instance
                     .isBanned(ActionType.Ownership, player, itemStack.getTypeId(), itemStack.getData().getData(), player.getLocation());
                  if (bannedInfo != null) {
                     itemStack.setType(Material.AIR);
                     itemStack.setAmount(0);
                     armor[i] = itemStack;
                     TekkitCustomizer.AddLogEntry("Confiscated " + bannedInfo.toString() + " from " + player.getName() + ".");
                  }
               }
            }

            inventory.setArmorContents(armor);
         }
      }

      this.nextPlayerPercentile += 5;
      if (this.nextPlayerPercentile >= 100) {
         this.nextPlayerPercentile = 0;
      }
   }
}
