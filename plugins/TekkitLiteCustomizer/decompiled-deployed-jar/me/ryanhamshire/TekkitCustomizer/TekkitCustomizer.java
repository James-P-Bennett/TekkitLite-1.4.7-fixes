package me.ryanhamshire.TekkitCustomizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TekkitCustomizer extends JavaPlugin {
   public static TekkitCustomizer instance;
   private static Logger log = Logger.getLogger("Minecraft");
   private static final String dataLayerFolderPath = "plugins" + File.separator + "TekkitCustomizerData";
   public static final String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
   ArrayList<World> config_enforcementWorlds = new ArrayList<>();
   MaterialCollection config_usageBanned = new MaterialCollection();
   MaterialCollection config_ownershipBanned = new MaterialCollection();
   MaterialCollection config_placementBanned = new MaterialCollection();
   MaterialCollection config_worldBanned = new MaterialCollection();
   MaterialCollection config_craftingBanned = new MaterialCollection();
   MaterialCollection config_recipesBanned = new MaterialCollection();
   boolean config_protectSurfaceFromExplosions;
   boolean config_removeUUMatterToNonRenewableRecipes;

   public static synchronized void AddLogEntry(String entry) {
      log.info("TekkitCustomizer: " + entry);
   }

   public void onEnable() {
      AddLogEntry("TekkitCustomizer enabled.");
      instance = this;
      PluginManager pluginManager = this.getServer().getPluginManager();
      PlayerEventHandler playerEventHandler = new PlayerEventHandler();
      pluginManager.registerEvents(playerEventHandler, this);
      BlockEventHandler blockEventHandler = new BlockEventHandler();
      pluginManager.registerEvents(blockEventHandler, this);
      AdjacentBlockDupePatch adjacentBlockDupePatch = new AdjacentBlockDupePatch();
      pluginManager.registerEvents(adjacentBlockDupePatch, this);
      EntityEventHandler entityEventHandler = new EntityEventHandler();
      pluginManager.registerEvents(entityEventHandler, this);
      this.loadConfiguration();
      Server server = this.getServer();
      ContrabandScannerTask task = new ContrabandScannerTask();
      server.getScheduler().scheduleSyncRepeatingTask(this, task, 1200L, 1200L);
   }

   private void loadConfiguration() {
      FileConfiguration config = YamlConfiguration.loadConfiguration(new File(configFilePath));
      this.config_protectSurfaceFromExplosions = config.getBoolean("TekkitCustomizer.ProtectSurfaceFromExplosives", true);
      config.set("TekkitCustomizer.ProtectSurfaceFromExplosives", this.config_protectSurfaceFromExplosions);
      this.config_removeUUMatterToNonRenewableRecipes = config.getBoolean("TekkitCustomizer.RemoveUUMatterToNonRenewableItemRecipes", true);
      config.set("TekkitCustomizer.RemoveUUMatterToNonRenewableItemRecipes", this.config_removeUUMatterToNonRenewableRecipes);
      if (this.config_removeUUMatterToNonRenewableRecipes) {
         Server server = this.getServer();
         Iterator<Recipe> iterator = server.recipeIterator();

         while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof ShapedRecipe) {
               ShapedRecipe shapedRecipe = (ShapedRecipe)recipe;
               Map<Character, ItemStack> ingredients = shapedRecipe.getIngredientMap();

               for (ItemStack ingredient : ingredients.values()) {
                  if (ingredient != null && ingredient.getTypeId() == 30188) {
                     ItemStack result = shapedRecipe.getResult();
                     if (result.getType() == Material.DIAMOND
                        || result.getType() == Material.COAL
                        || result.getType() == Material.IRON_ORE
                        || result.getType() == Material.GOLD_ORE
                        || result.getType() == Material.REDSTONE_ORE
                        || result.getType() == Material.OBSIDIAN
                        || result.getType() == Material.MYCEL
                        || result.getType() == Material.GRASS
                        || result.getType() == Material.REDSTONE
                        || result.getType() == Material.SULPHUR
                        || result.getTypeId() == 140
                        || result.getTypeId() == 30217) {
                        iterator.remove();
                        break;
                     }
                  }
               }
            }
         }
      }

      ArrayList<String> defaultWorldNames = new ArrayList<>();
      List<World> worlds = this.getServer().getWorlds();

      for (int i = 0; i < worlds.size(); i++) {
         defaultWorldNames.add(worlds.get(i).getName());
      }

      List<String> worldNames = config.getStringList("TekkitCustomizer.EnforcementWorlds");
      if (worldNames == null || worldNames.size() == 0) {
         worldNames = defaultWorldNames;
      }

      this.config_enforcementWorlds = new ArrayList<>();

      for (int i = 0; i < worldNames.size(); i++) {
         String worldName = worldNames.get(i);
         World world = this.getServer().getWorld(worldName);
         if (world == null) {
            AddLogEntry("Error: There's no world named \"" + worldName + "\".  Please update your config.yml.");
         } else {
            this.config_enforcementWorlds.add(world);
         }
      }

      config.set("TekkitCustomizer.EnforcementWorlds", worldNames);
      List<String> dontUseStrings = config.getStringList("TekkitCustomizer.Bans.UsageBanned");
      if (dontUseStrings == null || dontUseStrings.size() == 0) {
         dontUseStrings.add(new MaterialInfo(27585, (byte)2, "Divining Rod III", "Makes mining trivial, undermining the server economy.").toString());
         dontUseStrings.add(
            new MaterialInfo(27526, "Philosopher's Stone", "Bypasses anti-grief to change blocks in protected areas without permission.").toString()
         );
      }

      this.parseMaterialListFromConfig(dontUseStrings, this.config_usageBanned);
      config.set("TekkitCustomizer.Bans.UsageBanned", dontUseStrings);
      List<String> dontOwnStrings = config.getStringList("TekkitCustomizer.Bans.OwnershipBanned");
      if (dontOwnStrings == null || dontOwnStrings.size() == 0) {
         dontOwnStrings.add(new MaterialInfo(27556, "Catalytic Lens", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(27527, "Destruction Catalyst", "Bypasses anti-grief to change blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(new MaterialInfo(27530, "Evertide Amulet", "Bypasses anti-grief to place water in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(27579, "Infernal Armor", "Bypasses anti-grief to destroy blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(new MaterialInfo(27580, "Abyss Helmet", "Calls lightning to injure players even when PvP is off.").toString());
         dontOwnStrings.add(
            new MaterialInfo(27537, "Harvest Goddess Band", "Bypasses anti-grief to grow and harvest in protected areas without permission.").toString()
         );
         dontOwnStrings.add(new MaterialInfo(27532, "Black Hole Band", "Bypasses anti-grief to remove water in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(
                  27533,
                  "Ring of Ignition",
                  "Bypasses anti-grief to injure claimed animals and set fires in protected areas without permission.  May injure players even when PvP is off."
               )
               .toString()
         );
         dontOwnStrings.add(new MaterialInfo(27593, "Void Ring", "Key ingredient in an item duplication exploit.").toString());
         dontOwnStrings.add(new MaterialInfo(27583, "Mercurial Eye", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
         dontOwnStrings.add(new MaterialInfo(27584, "Ring of Arcana", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(
                  27536, "Swiftwolf's Ring", "Calls lightning to injure players even when PvP is off, and the knockback effect enables animal theft."
               )
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(27538, "Watch of Flowing Time", "Can be used to sabotage another player's automated processes by altering speed of components.")
               .toString()
         );
         dontOwnStrings.add(new MaterialInfo(27531, "Volcanite Amulet", "Bypasses anti-grief to place lava in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(27546, "Dark Matter Sword", "Power attack injures players when PvP is off, and protected animals without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(27543, "Dark Matter Pickaxe", "Bypasses anti-grief to collect ore in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(27544, "Dark Matter Shovel", "Bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(27545, "Dark Matter Hoe", "Bypasses anti-grief to change blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(new MaterialInfo(27547, "Dark Matter Axe", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(27555, "Dark Matter Hammer", "Bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(27564, "Red Matter Pickaxe", "Bypasses anti-grief to collect ores in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(27565, "Red Matter Shovel", "Bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(new MaterialInfo(27566, "Red Matter Hoe", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(27567, "Red Matter Sword", "Power attack injuures players when PvP is off, and protected animals without permission.").toString()
         );
         dontOwnStrings.add(new MaterialInfo(27568, "Red Matter Axe", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(27570, "Red Matter Hammer", "Bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(
                  27572,
                  "Red Matter Katar",
                  "Bypasses anti-grief to change blocks in protected areas without permission, and may injure players even when PvP is off."
               )
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(
                  27573,
                  "Red Matter Morning Star",
                  "Bypasses anti-grief to change blocks in protected areas without permission, and may injure players even when PvP is off."
               )
               .toString()
         );
         dontOwnStrings.add(new MaterialInfo(126, (byte)4, "Red Matter Furnace", "Key ingredient in an item duplication exploit.").toString());
         dontOwnStrings.add(new MaterialInfo(26524, "Cannon", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
         dontOwnStrings.add(
            new MaterialInfo(126, (byte)10, "Nova Catalyst", "Bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(126, (byte)11, "Nova Cataclysm", "Bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(new MaterialInfo(4095, "Dimensional Anchor", "Can easily crash the server by consuming all hard drive allocation.").toString());
         dontOwnStrings.add(new MaterialInfo(7312, "Tank Cart", "Key ingredient in an item duplication exploit.").toString());
         dontOwnStrings.add(new MaterialInfo(216, "Turtle", "Bypasses anti-grief to build in protected areas without permission.").toString());
         dontOwnStrings.add(new MaterialInfo(150, (byte)12, "Igniter", "Bypasses anti-grief to set fire in protected areas without permission.").toString());
         dontOwnStrings.add(new MaterialInfo(214, (byte)0, "World Anchor", "Consumes extra server memory, which may slow or crash the server.").toString());
         dontOwnStrings.add(new MaterialInfo(7303, "Anchor Cart", "Consumes extra server memory, which may slow or crash the server.").toString());
         dontOwnStrings.add(
            new MaterialInfo(213, (byte)11, "Feed Station", "Unattended animal breeding can result in animal overload, severely slowing the server.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(7310, "Tunnel Bore", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(7308, "Iron Bore Head", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(7272, "Steel Bore Head", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(7314, "Diamond Bore Head", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(173, (byte)3, "Project Table", "May craft banned items which would then be confiscated, wasting your ingredients.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(169, "Automatic Crafting Table", "May craft banned items which would then be confiscated, wasting your ingredients.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(194, (byte)1, "Automatic Crafting Table MkII", "May craft banned items which would then be confiscated, wasting your ingredients.")
               .toString()
         );
         dontOwnStrings.add(new MaterialInfo(7281, "Work Cart", "May craft banned items which would then be confiscated, wasting your ingredients.").toString());
         dontOwnStrings.add(
            new MaterialInfo(192, (byte)1, "Industrial Alarm", "May be turned up very loud and then hidden or protected to grief players.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(192, (byte)2, "Howler Alarm", "May be turned up very loud and then hidden or protected to grief players.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(150, (byte)7, "Frame Motor", "Bypasses anti-grief to move blocks into and out of protected areas without permission.").toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(
                  127,
                  "Dark Matter Pedestal",
                  "Fills the server log very quickly when activated, potentially running the server out of storage allocation and causing a crash."
               )
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(126, (byte)0, "Energy Collector I", "Converting renewable energy sources to non-renewable ores undermines the server economy.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(126, (byte)1, "Energy Collector II", "Converting renewable energy sources to non-renewable ores undermines the server economy.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(126, (byte)2, "Energy Collector III", "Converting renewable energy sources to non-renewable ores undermines the server economy.")
               .toString()
         );
         dontOwnStrings.add(new MaterialInfo(30208, "Mining Laser", "May catch other players on fire even when PvP is off.").toString());
         dontOwnStrings.add(new MaterialInfo(223, (byte)1, "Tesla Coil", "May kill other players even when PvP is off.").toString());
         dontOwnStrings.add(
            new MaterialInfo(26498, "Wooden Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(26499, "Stone Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(26500, "Iron Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(26501, "Diamond Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.")
               .toString()
         );
         dontOwnStrings.add(
            new MaterialInfo(26502, "Golden Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.")
               .toString()
         );
      }

      this.parseMaterialListFromConfig(dontOwnStrings, this.config_ownershipBanned);
      config.set("TekkitCustomizer.Bans.OwnershipBanned", dontOwnStrings);
      List<String> dontPlaceStrings = config.getStringList("TekkitCustomizer.Bans.PlacementBanned");
      if (dontPlaceStrings != null && dontPlaceStrings.size() == 0) {
      }

      this.parseMaterialListFromConfig(dontPlaceStrings, this.config_placementBanned);
      config.set("TekkitCustomizer.Bans.PlacementBanned", dontPlaceStrings);
      List<String> removeInWorldStrings = config.getStringList("TekkitCustomizer.Bans.WorldBanned");
      if (removeInWorldStrings != null && removeInWorldStrings.size() == 0) {
      }

      this.parseMaterialListFromConfig(removeInWorldStrings, this.config_worldBanned);
      config.set("TekkitCustomizer.Bans.WorldBanned", removeInWorldStrings);
      List<String> dontCraftStrings = config.getStringList("TekkitCustomizer.Bans.CraftingBanned");
      if (dontCraftStrings != null && dontCraftStrings.size() == 0) {
      }

      this.parseMaterialListFromConfig(dontCraftStrings, this.config_craftingBanned);
      config.set("TekkitCustomizer.Bans.CraftingBanned", dontCraftStrings);

      try {
         config.save(configFilePath);
      } catch (IOException exception) {
         AddLogEntry("Unable to write to the configuration file at \"" + configFilePath + "\"");
      }
   }

   private void parseMaterialListFromConfig(List<String> stringsToParse, MaterialCollection materialCollection) {
      materialCollection.clear();

      for (int i = 0; i < stringsToParse.size(); i++) {
         MaterialInfo materialInfo = MaterialInfo.fromString(stringsToParse.get(i));
         if (materialInfo == null) {
            AddLogEntry("ERROR: Unable to read a material entry from the config file.  Please update your config.yml.");
            if (!stringsToParse.get(i).contains("can't")) {
               stringsToParse.set(i, stringsToParse.get(i) + "     <-- can't understand this entry, see BukkitDev documentation");
            }
         } else {
            materialCollection.Add(materialInfo);
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
      Player player = null;
      if (sender instanceof Player) {
         player = (Player)sender;
      }

      if (cmd.getName().equalsIgnoreCase("blockinfo") && player != null) {
         boolean messageSent = false;
         ItemStack handStack = player.getItemInHand();
         if (handStack.getType() != Material.AIR) {
            MaterialInfo inHand = new MaterialInfo(handStack.getTypeId(), handStack.getData().getData(), null, null);
            player.sendMessage("In Hand: " + inHand.toString());
            messageSent = true;
         }

         HashSet<Byte> transparentMaterials = new HashSet<>();
         transparentMaterials.add((byte)Material.AIR.getId());
         Block targetBlock = player.getTargetBlock(transparentMaterials, 50);
         if (targetBlock != null && targetBlock.getType() != Material.AIR) {
            player.sendMessage("Targeted: " + new MaterialInfo(targetBlock.getTypeId(), targetBlock.getData(), null, null).toString());
            messageSent = true;
         }

         if (!messageSent) {
            player.sendMessage("To get information about a material, either hold it in your hand or move close and point at it with your crosshair.");
         }

         return true;
      } else if (cmd.getName().equalsIgnoreCase("reloadbanneditems")) {
         this.loadConfiguration();
         if (player != null) {
            player.sendMessage("Banned item configuration reloaded.");
         } else {
            AddLogEntry("Banned item configuration reloaded.");
         }

         return true;
      } else {
         return false;
      }
   }

   public void onDisable() {
      AddLogEntry("TekkitCustomizer disabled.");
   }

   public MaterialInfo isBanned(ActionType actionType, Player player, int typeId, byte data, Location location) {
      if (!this.config_enforcementWorlds.contains(location.getWorld())) {
         return null;
      }

      if (player.hasPermission("tekkitcustomizer.*")) {
         return null;
      }

      MaterialCollection collectionToSearch;
      String permissionNode;
      if (actionType == ActionType.Usage) {
         collectionToSearch = this.config_usageBanned;
         permissionNode = "use";
      } else if (actionType == ActionType.Placement) {
         collectionToSearch = this.config_placementBanned;
         permissionNode = "place";
      } else if (actionType == ActionType.Crafting) {
         collectionToSearch = this.config_craftingBanned;
         permissionNode = "craft";
      } else {
         collectionToSearch = this.config_ownershipBanned;
         permissionNode = "own";
      }

      MaterialInfo bannedInfo = collectionToSearch.Contains(new MaterialInfo(typeId, data, null, null));
      if (bannedInfo != null) {
         if (player.hasPermission("tekkitcustomizer." + typeId + ".*.*")) {
            return null;
         } else if (player.hasPermission("tekkitcustomizer." + typeId + ".*." + permissionNode)) {
            return null;
         } else if (player.hasPermission("tekkitcustomizer." + typeId + "." + data + "." + permissionNode)) {
            return null;
         } else {
            return player.hasPermission("tekkitcustomizer." + typeId + "." + data + ".*") ? null : bannedInfo;
         }
      } else {
         return null;
      }
   }

   public static String getFriendlyLocationString(Location location) {
      return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
   }
}
