package me.ryanhamshire.GriefPrevention;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class GriefPrevention extends JavaPlugin {
   public static GriefPrevention instance;
   private static Logger log = Logger.getLogger("Minecraft");
   public DataStore dataStore;
   public ArrayList<World> config_claims_enabledWorlds;
   public ArrayList<World> config_claims_enabledCreativeWorlds;
   public boolean config_claims_preventTheft;
   public boolean config_claims_protectCreatures;
   public boolean config_claims_preventButtonsSwitches;
   public boolean config_claims_lockWoodenDoors;
   public boolean config_claims_lockTrapDoors;
   public boolean config_claims_lockFenceGates;
   public boolean config_claims_enderPearlsRequireAccessTrust;
   public int config_claims_initialBlocks;
   public int config_claims_blocksAccruedPerHour;
   public int config_claims_maxAccruedBlocks;
   public int config_claims_maxDepth;
   public int config_claims_expirationDays;
   public int config_claims_automaticClaimsForNewPlayersRadius;
   public boolean config_claims_creationRequiresPermission;
   public int config_claims_claimsExtendIntoGroundDistance;
   public int config_claims_minSize;
   public boolean config_claims_allowUnclaimInCreative;
   public boolean config_claims_autoRestoreUnclaimedCreativeLand;
   public boolean config_claims_noBuildOutsideClaims;
   public int config_claims_chestClaimExpirationDays;
   public int config_claims_unusedClaimExpirationDays;
   public boolean config_claims_survivalAutoNatureRestoration;
   public boolean config_claims_creativeAutoNatureRestoration;
   public int config_claims_trappedCooldownHours;
   public Material config_claims_investigationTool;
   public Material config_claims_modificationTool;
   public ArrayList<World> config_siege_enabledWorlds;
   public ArrayList<Material> config_siege_blocks;
   public boolean config_spam_enabled;
   public int config_spam_loginCooldownMinutes;
   public ArrayList<String> config_spam_monitorSlashCommands;
   public boolean config_spam_banOffenders;
   public String config_spam_banMessage;
   public String config_spam_warningMessage;
   public String config_spam_allowedIpAddresses;
   public int config_spam_deathMessageCooldownSeconds;
   public ArrayList<World> config_pvp_enabledWorlds;
   public boolean config_pvp_protectFreshSpawns;
   public boolean config_pvp_punishLogout;
   public int config_pvp_combatTimeoutSeconds;
   public boolean config_pvp_allowCombatItemDrop;
   public ArrayList<String> config_pvp_blockedCommands;
   public boolean config_pvp_noCombatInPlayerLandClaims;
   public boolean config_pvp_noCombatInAdminLandClaims;
   public boolean config_trees_removeFloatingTreetops;
   public boolean config_trees_regrowGriefedTrees;
   public double config_economy_claimBlocksPurchaseCost;
   public double config_economy_claimBlocksSellValue;
   public boolean config_blockSurfaceCreeperExplosions;
   public boolean config_blockSurfaceOtherExplosions;
   public boolean config_blockWildernessWaterBuckets;
   public boolean config_blockSkyTrees;
   public boolean config_fireSpreads;
   public boolean config_fireDestroys;
   public boolean config_addItemsToClaimedChests;
   public boolean config_eavesdrop;
   public ArrayList<String> config_eavesdrop_whisperCommands;
   public boolean config_smartBan;
   public boolean config_endermenMoveBlocks;
   public boolean config_silverfishBreakBlocks;
   public boolean config_creaturesTrampleCrops;
   public boolean config_zombiesBreakDoors;
   public MaterialCollection config_mods_accessTrustIds;
   public MaterialCollection config_mods_containerTrustIds;
   public List<String> config_mods_ignoreClaimsAccounts;
   public MaterialCollection config_mods_explodableIds;
   public boolean config_claims_warnOnBuildOutside;
   public HashMap<String, Integer> config_seaLevelOverride;
   public static Economy economy = null;
   public static final int TREE_RADIUS = 5;
   public static final int NOTIFICATION_SECONDS = 20;

   public static void AddLogEntry(String entry) {
      log.info("GriefPrevention: " + entry);
   }

   public void onEnable() {
      AddLogEntry("Grief Prevention enabled.");
      instance = this;
      FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));
      FileConfiguration outConfig = new YamlConfiguration();
      ArrayList<String> defaultClaimsWorldNames = new ArrayList<>();
      List<World> worlds = this.getServer().getWorlds();

      for (int i = 0; i < worlds.size(); i++) {
         defaultClaimsWorldNames.add(worlds.get(i).getName());
      }

      List<String> claimsEnabledWorldNames = config.getStringList("GriefPrevention.Claims.Worlds");
      if (claimsEnabledWorldNames == null || claimsEnabledWorldNames.size() == 0) {
         claimsEnabledWorldNames = defaultClaimsWorldNames;
      }

      this.config_claims_enabledWorlds = new ArrayList<>();

      for (int i = 0; i < claimsEnabledWorldNames.size(); i++) {
         String worldName = claimsEnabledWorldNames.get(i);
         World world = this.getServer().getWorld(worldName);
         if (world == null) {
            AddLogEntry("Error: Claims Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
         } else {
            this.config_claims_enabledWorlds.add(world);
         }
      }

      List<String> defaultCreativeWorldNames = new ArrayList<>();
      if (this.getServer().getDefaultGameMode() == GameMode.CREATIVE) {
         for (int i = 0; i < defaultClaimsWorldNames.size(); i++) {
            defaultCreativeWorldNames.add(defaultClaimsWorldNames.get(i));
         }
      }

      List<String> creativeClaimsEnabledWorldNames = config.getStringList("GriefPrevention.Claims.CreativeRulesWorlds");
      if (creativeClaimsEnabledWorldNames == null || creativeClaimsEnabledWorldNames.size() == 0) {
         creativeClaimsEnabledWorldNames = defaultCreativeWorldNames;
      }

      this.config_claims_enabledCreativeWorlds = new ArrayList<>();

      for (int i = 0; i < creativeClaimsEnabledWorldNames.size(); i++) {
         String worldName = creativeClaimsEnabledWorldNames.get(i);
         World world = this.getServer().getWorld(worldName);
         if (world == null) {
            AddLogEntry("Error: Claims Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
         } else {
            this.config_claims_enabledCreativeWorlds.add(world);
         }
      }

      ArrayList<String> defaultPvpWorldNames = new ArrayList<>();

      for (int i = 0; i < worlds.size(); i++) {
         World world = worlds.get(i);
         if (world.getPVP()) {
            defaultPvpWorldNames.add(world.getName());
         }
      }

      List<String> pvpEnabledWorldNames = config.getStringList("GriefPrevention.PvP.Worlds");
      if (pvpEnabledWorldNames == null || pvpEnabledWorldNames.size() == 0) {
         pvpEnabledWorldNames = defaultPvpWorldNames;
      }

      this.config_pvp_enabledWorlds = new ArrayList<>();

      for (int i = 0; i < pvpEnabledWorldNames.size(); i++) {
         String worldName = pvpEnabledWorldNames.get(i);
         World world = this.getServer().getWorld(worldName);
         if (world == null) {
            AddLogEntry("Error: PvP Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
         } else {
            this.config_pvp_enabledWorlds.add(world);
         }
      }

      this.config_seaLevelOverride = new HashMap<>();

      for (int i = 0; i < worlds.size(); i++) {
         int seaLevelOverride = config.getInt("GriefPrevention.SeaLevelOverrides." + worlds.get(i).getName(), -1);
         outConfig.set("GriefPrevention.SeaLevelOverrides." + worlds.get(i).getName(), seaLevelOverride);
         this.config_seaLevelOverride.put(worlds.get(i).getName(), seaLevelOverride);
      }

      this.config_claims_preventTheft = config.getBoolean("GriefPrevention.Claims.PreventTheft", true);
      this.config_claims_protectCreatures = config.getBoolean("GriefPrevention.Claims.ProtectCreatures", true);
      this.config_claims_preventButtonsSwitches = config.getBoolean("GriefPrevention.Claims.PreventButtonsSwitches", true);
      this.config_claims_lockWoodenDoors = config.getBoolean("GriefPrevention.Claims.LockWoodenDoors", false);
      this.config_claims_lockTrapDoors = config.getBoolean("GriefPrevention.Claims.LockTrapDoors", false);
      this.config_claims_lockFenceGates = config.getBoolean("GriefPrevention.Claims.LockFenceGates", true);
      this.config_claims_enderPearlsRequireAccessTrust = config.getBoolean("GriefPrevention.Claims.EnderPearlsRequireAccessTrust", true);
      this.config_claims_initialBlocks = config.getInt("GriefPrevention.Claims.InitialBlocks", 100);
      this.config_claims_blocksAccruedPerHour = config.getInt("GriefPrevention.Claims.BlocksAccruedPerHour", 100);
      this.config_claims_maxAccruedBlocks = config.getInt("GriefPrevention.Claims.MaxAccruedBlocks", 80000);
      this.config_claims_automaticClaimsForNewPlayersRadius = config.getInt("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadius", 4);
      this.config_claims_claimsExtendIntoGroundDistance = config.getInt("GriefPrevention.Claims.ExtendIntoGroundDistance", 5);
      this.config_claims_creationRequiresPermission = config.getBoolean("GriefPrevention.Claims.CreationRequiresPermission", false);
      this.config_claims_minSize = config.getInt("GriefPrevention.Claims.MinimumSize", 10);
      this.config_claims_maxDepth = config.getInt("GriefPrevention.Claims.MaximumDepth", 0);
      this.config_claims_trappedCooldownHours = config.getInt("GriefPrevention.Claims.TrappedCommandCooldownHours", 8);
      this.config_claims_noBuildOutsideClaims = config.getBoolean("GriefPrevention.Claims.NoSurvivalBuildingOutsideClaims", false);
      this.config_claims_warnOnBuildOutside = config.getBoolean("GriefPrevention.Claims.WarnWhenBuildingOutsideClaims", true);
      this.config_claims_allowUnclaimInCreative = config.getBoolean("GriefPrevention.Claims.AllowUnclaimingCreativeModeLand", true);
      this.config_claims_autoRestoreUnclaimedCreativeLand = config.getBoolean("GriefPrevention.Claims.AutoRestoreUnclaimedCreativeLand", true);
      this.config_claims_chestClaimExpirationDays = config.getInt("GriefPrevention.Claims.Expiration.ChestClaimDays", 7);
      outConfig.set("GriefPrevention.Claims.Expiration.ChestClaimDays", this.config_claims_chestClaimExpirationDays);
      this.config_claims_unusedClaimExpirationDays = config.getInt("GriefPrevention.Claims.Expiration.UnusedClaimDays", 14);
      outConfig.set("GriefPrevention.Claims.Expiration.UnusedClaimDays", this.config_claims_unusedClaimExpirationDays);
      this.config_claims_expirationDays = config.getInt("GriefPrevention.Claims.Expiration.AllClaimDays", 0);
      outConfig.set("GriefPrevention.Claims.Expiration.AllClaimDays", this.config_claims_expirationDays);
      this.config_claims_survivalAutoNatureRestoration = config.getBoolean("GriefPrevention.Claims.Expiration.AutomaticNatureRestoration.SurvivalWorlds", false);
      outConfig.set("GriefPrevention.Claims.Expiration.AutomaticNatureRestoration.SurvivalWorlds", this.config_claims_survivalAutoNatureRestoration);
      this.config_claims_creativeAutoNatureRestoration = config.getBoolean("GriefPrevention.Claims.Expiration.AutomaticNatureRestoration.CreativeWorlds", true);
      outConfig.set("GriefPrevention.Claims.Expiration.AutomaticNatureRestoration.CreativeWorlds", this.config_claims_creativeAutoNatureRestoration);
      this.config_spam_enabled = config.getBoolean("GriefPrevention.Spam.Enabled", true);
      this.config_spam_loginCooldownMinutes = config.getInt("GriefPrevention.Spam.LoginCooldownMinutes", 2);
      this.config_spam_warningMessage = config.getString("GriefPrevention.Spam.WarningMessage", "Please reduce your noise level.  Spammers will be banned.");
      this.config_spam_allowedIpAddresses = config.getString("GriefPrevention.Spam.AllowedIpAddresses", "1.2.3.4; 5.6.7.8");
      this.config_spam_banOffenders = config.getBoolean("GriefPrevention.Spam.BanOffenders", true);
      this.config_spam_banMessage = config.getString("GriefPrevention.Spam.BanMessage", "Banned for spam.");
      String slashCommandsToMonitor = config.getString("GriefPrevention.Spam.MonitorSlashCommands", "/me;/tell;/global;/local");
      this.config_spam_deathMessageCooldownSeconds = config.getInt("GriefPrevention.Spam.DeathMessageCooldownSeconds", 60);
      this.config_pvp_protectFreshSpawns = config.getBoolean("GriefPrevention.PvP.ProtectFreshSpawns", true);
      this.config_pvp_punishLogout = config.getBoolean("GriefPrevention.PvP.PunishLogout", true);
      this.config_pvp_combatTimeoutSeconds = config.getInt("GriefPrevention.PvP.CombatTimeoutSeconds", 15);
      this.config_pvp_allowCombatItemDrop = config.getBoolean("GriefPrevention.PvP.AllowCombatItemDrop", false);
      String bannedPvPCommandsList = config.getString("GriefPrevention.PvP.BlockedSlashCommands", "/home;/vanish;/spawn;/tpa");
      this.config_trees_removeFloatingTreetops = config.getBoolean("GriefPrevention.Trees.RemoveFloatingTreetops", true);
      this.config_trees_regrowGriefedTrees = config.getBoolean("GriefPrevention.Trees.RegrowGriefedTrees", true);
      this.config_economy_claimBlocksPurchaseCost = config.getDouble("GriefPrevention.Economy.ClaimBlocksPurchaseCost", 0.0);
      this.config_economy_claimBlocksSellValue = config.getDouble("GriefPrevention.Economy.ClaimBlocksSellValue", 0.0);
      this.config_blockSurfaceCreeperExplosions = config.getBoolean("GriefPrevention.BlockSurfaceCreeperExplosions", true);
      this.config_blockSurfaceOtherExplosions = config.getBoolean("GriefPrevention.BlockSurfaceOtherExplosions", true);
      this.config_blockWildernessWaterBuckets = config.getBoolean("GriefPrevention.LimitSurfaceWaterBuckets", true);
      this.config_blockSkyTrees = config.getBoolean("GriefPrevention.LimitSkyTrees", true);
      this.config_fireSpreads = config.getBoolean("GriefPrevention.FireSpreads", false);
      this.config_fireDestroys = config.getBoolean("GriefPrevention.FireDestroys", false);
      this.config_addItemsToClaimedChests = config.getBoolean("GriefPrevention.AddItemsToClaimedChests", true);
      this.config_eavesdrop = config.getBoolean("GriefPrevention.EavesdropEnabled", false);
      String whisperCommandsToMonitor = config.getString("GriefPrevention.WhisperCommands", "/tell;/pm;/r");
      this.config_smartBan = config.getBoolean("GriefPrevention.SmartBan", true);
      this.config_endermenMoveBlocks = config.getBoolean("GriefPrevention.EndermenMoveBlocks", false);
      this.config_silverfishBreakBlocks = config.getBoolean("GriefPrevention.SilverfishBreakBlocks", false);
      this.config_creaturesTrampleCrops = config.getBoolean("GriefPrevention.CreaturesTrampleCrops", false);
      this.config_zombiesBreakDoors = config.getBoolean("GriefPrevention.HardModeZombiesBreakDoors", false);
      this.config_mods_ignoreClaimsAccounts = config.getStringList("GriefPrevention.Mods.PlayersIgnoringAllClaims");
      if (this.config_mods_ignoreClaimsAccounts == null) {
         this.config_mods_ignoreClaimsAccounts = new ArrayList<>();
      }

      this.config_mods_accessTrustIds = new MaterialCollection();
      List<String> accessTrustStrings = config.getStringList("GriefPrevention.Mods.BlockIdsRequiringAccessTrust");
      if (accessTrustStrings != null && accessTrustStrings.size() == 0) {
      }

      this.parseMaterialListFromConfig(accessTrustStrings, this.config_mods_accessTrustIds);
      this.config_mods_containerTrustIds = new MaterialCollection();
      List<String> containerTrustStrings = config.getStringList("GriefPrevention.Mods.BlockIdsRequiringContainerTrust");
      if (containerTrustStrings == null || containerTrustStrings.size() == 0) {
         containerTrustStrings.add(new MaterialInfo(227, "Battery Box").toString());
         containerTrustStrings.add(new MaterialInfo(130, "Transmutation Tablet").toString());
         containerTrustStrings.add(new MaterialInfo(128, "Alchemical Chest and Energy Condenser").toString());
         containerTrustStrings.add(new MaterialInfo(181, "Various Chests").toString());
         containerTrustStrings.add(new MaterialInfo(178, "Ender Chest").toString());
         containerTrustStrings.add(new MaterialInfo(150, "Various BuildCraft Gadgets").toString());
         containerTrustStrings.add(new MaterialInfo(155, "Filler").toString());
         containerTrustStrings.add(new MaterialInfo(157, "Builder").toString());
         containerTrustStrings.add(new MaterialInfo(158, "Template Drawing Table").toString());
         containerTrustStrings.add(new MaterialInfo(126, "Various EE Gadgets").toString());
         containerTrustStrings.add(new MaterialInfo(138, "Various RedPower Gadgets").toString());
         containerTrustStrings.add(new MaterialInfo(137, "BuildCraft Project Table and Furnaces").toString());
         containerTrustStrings.add(new MaterialInfo(250, "Various IC2 Machines").toString());
         containerTrustStrings.add(new MaterialInfo(161, "BuildCraft Engines").toString());
         containerTrustStrings.add(new MaterialInfo(169, "Automatic Crafting Table").toString());
         containerTrustStrings.add(new MaterialInfo(177, "Wireless Components").toString());
         containerTrustStrings.add(new MaterialInfo(183, "Solar Arrays").toString());
         containerTrustStrings.add(new MaterialInfo(187, "Charging Benches").toString());
         containerTrustStrings.add(new MaterialInfo(188, "More IC2 Machines").toString());
         containerTrustStrings.add(new MaterialInfo(190, "Generators, Fabricators, Strainers").toString());
         containerTrustStrings.add(new MaterialInfo(194, "More Gadgets").toString());
         containerTrustStrings.add(new MaterialInfo(207, "Computer").toString());
         containerTrustStrings.add(new MaterialInfo(208, "Computer Peripherals").toString());
         containerTrustStrings.add(new MaterialInfo(246, "IC2 Generators").toString());
         containerTrustStrings.add(new MaterialInfo(24303, "Teleport Pipe").toString());
         containerTrustStrings.add(new MaterialInfo(24304, "Waterproof Teleport Pipe").toString());
         containerTrustStrings.add(new MaterialInfo(24305, "Power Teleport Pipe").toString());
         containerTrustStrings.add(new MaterialInfo(4311, "Diamond Sorting Pipe").toString());
         containerTrustStrings.add(new MaterialInfo(216, "Turtle").toString());
      }

      this.parseMaterialListFromConfig(containerTrustStrings, this.config_mods_containerTrustIds);
      this.config_mods_explodableIds = new MaterialCollection();
      List<String> explodableStrings = config.getStringList("GriefPrevention.Mods.BlockIdsExplodable");
      if (explodableStrings == null || explodableStrings.size() == 0) {
         explodableStrings.add(new MaterialInfo(161, "BuildCraft Engines").toString());
         explodableStrings.add(new MaterialInfo(246, (byte)5, "Nuclear Reactor").toString());
      }

      this.parseMaterialListFromConfig(explodableStrings, this.config_mods_explodableIds);
      String investigationToolMaterialName = Material.STICK.name();
      investigationToolMaterialName = config.getString("GriefPrevention.Claims.InvestigationTool", investigationToolMaterialName);
      this.config_claims_investigationTool = Material.getMaterial(investigationToolMaterialName);
      if (this.config_claims_investigationTool == null) {
         AddLogEntry("ERROR: Material " + investigationToolMaterialName + " not found.  Defaulting to the stick.  Please update your config.yml.");
         this.config_claims_investigationTool = Material.STICK;
      }

      String modificationToolMaterialName = Material.GOLD_SPADE.name();
      modificationToolMaterialName = config.getString("GriefPrevention.Claims.ModificationTool", modificationToolMaterialName);
      this.config_claims_modificationTool = Material.getMaterial(modificationToolMaterialName);
      if (this.config_claims_modificationTool == null) {
         AddLogEntry("ERROR: Material " + modificationToolMaterialName + " not found.  Defaulting to the golden shovel.  Please update your config.yml.");
         this.config_claims_modificationTool = Material.GOLD_SPADE;
      }

      ArrayList<String> defaultSiegeWorldNames = new ArrayList<>();
      List<String> siegeEnabledWorldNames = config.getStringList("GriefPrevention.Siege.Worlds");
      if (siegeEnabledWorldNames == null) {
         siegeEnabledWorldNames = defaultSiegeWorldNames;
      }

      this.config_siege_enabledWorlds = new ArrayList<>();

      for (int i = 0; i < siegeEnabledWorldNames.size(); i++) {
         String worldName = siegeEnabledWorldNames.get(i);
         World world = this.getServer().getWorld(worldName);
         if (world == null) {
            AddLogEntry("Error: Siege Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
         } else {
            this.config_siege_enabledWorlds.add(world);
         }
      }

      this.config_siege_blocks = new ArrayList<>();
      this.config_siege_blocks.add(Material.DIRT);
      this.config_siege_blocks.add(Material.GRASS);
      this.config_siege_blocks.add(Material.LONG_GRASS);
      this.config_siege_blocks.add(Material.COBBLESTONE);
      this.config_siege_blocks.add(Material.GRAVEL);
      this.config_siege_blocks.add(Material.SAND);
      this.config_siege_blocks.add(Material.GLASS);
      this.config_siege_blocks.add(Material.THIN_GLASS);
      this.config_siege_blocks.add(Material.WOOD);
      this.config_siege_blocks.add(Material.WOOL);
      this.config_siege_blocks.add(Material.SNOW);
      ArrayList<String> defaultBreakableBlocksList = new ArrayList<>();

      for (int i = 0; i < this.config_siege_blocks.size(); i++) {
         defaultBreakableBlocksList.add(this.config_siege_blocks.get(i).name());
      }

      List<String> breakableBlocksList = config.getStringList("GriefPrevention.Siege.BreakableBlocks");
      if (breakableBlocksList == null || breakableBlocksList.size() == 0) {
         breakableBlocksList = defaultBreakableBlocksList;
      }

      this.config_siege_blocks = new ArrayList<>();

      for (int i = 0; i < breakableBlocksList.size(); i++) {
         String blockName = breakableBlocksList.get(i);
         Material material = Material.getMaterial(blockName);
         if (material == null) {
            AddLogEntry("Siege Configuration: Material not found: " + blockName + ".");
         } else {
            this.config_siege_blocks.add(material);
         }
      }

      this.config_pvp_noCombatInPlayerLandClaims = config.getBoolean(
         "GriefPrevention.PvP.ProtectPlayersInLandClaims.PlayerOwnedClaims", this.config_siege_enabledWorlds.size() == 0
      );
      this.config_pvp_noCombatInAdminLandClaims = config.getBoolean(
         "GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeClaims", this.config_siege_enabledWorlds.size() == 0
      );
      String databaseUrl = config.getString("GriefPrevention.Database.URL", "");
      String databaseUserName = config.getString("GriefPrevention.Database.UserName", "");
      String databasePassword = config.getString("GriefPrevention.Database.Password", "");
      outConfig.set("GriefPrevention.Claims.Worlds", claimsEnabledWorldNames);
      outConfig.set("GriefPrevention.Claims.CreativeRulesWorlds", creativeClaimsEnabledWorldNames);
      outConfig.set("GriefPrevention.Claims.PreventTheft", this.config_claims_preventTheft);
      outConfig.set("GriefPrevention.Claims.ProtectCreatures", this.config_claims_protectCreatures);
      outConfig.set("GriefPrevention.Claims.PreventButtonsSwitches", this.config_claims_preventButtonsSwitches);
      outConfig.set("GriefPrevention.Claims.LockWoodenDoors", this.config_claims_lockWoodenDoors);
      outConfig.set("GriefPrevention.Claims.LockTrapDoors", this.config_claims_lockTrapDoors);
      outConfig.set("GriefPrevention.Claims.LockFenceGates", this.config_claims_lockFenceGates);
      outConfig.set("GriefPrevention.Claims.EnderPearlsRequireAccessTrust", this.config_claims_enderPearlsRequireAccessTrust);
      outConfig.set("GriefPrevention.Claims.InitialBlocks", this.config_claims_initialBlocks);
      outConfig.set("GriefPrevention.Claims.BlocksAccruedPerHour", this.config_claims_blocksAccruedPerHour);
      outConfig.set("GriefPrevention.Claims.MaxAccruedBlocks", this.config_claims_maxAccruedBlocks);
      outConfig.set("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadius", this.config_claims_automaticClaimsForNewPlayersRadius);
      outConfig.set("GriefPrevention.Claims.ExtendIntoGroundDistance", this.config_claims_claimsExtendIntoGroundDistance);
      outConfig.set("GriefPrevention.Claims.CreationRequiresPermission", this.config_claims_creationRequiresPermission);
      outConfig.set("GriefPrevention.Claims.MinimumSize", this.config_claims_minSize);
      outConfig.set("GriefPrevention.Claims.MaximumDepth", this.config_claims_maxDepth);
      outConfig.set("GriefPrevention.Claims.IdleLimitDays", this.config_claims_expirationDays);
      outConfig.set("GriefPrevention.Claims.TrappedCommandCooldownHours", this.config_claims_trappedCooldownHours);
      outConfig.set("GriefPrevention.Claims.InvestigationTool", this.config_claims_investigationTool.name());
      outConfig.set("GriefPrevention.Claims.ModificationTool", this.config_claims_modificationTool.name());
      outConfig.set("GriefPrevention.Claims.NoSurvivalBuildingOutsideClaims", this.config_claims_noBuildOutsideClaims);
      outConfig.set("GriefPrevention.Claims.WarnWhenBuildingOutsideClaims", this.config_claims_warnOnBuildOutside);
      outConfig.set("GriefPrevention.Claims.AllowUnclaimingCreativeModeLand", this.config_claims_allowUnclaimInCreative);
      outConfig.set("GriefPrevention.Claims.AutoRestoreUnclaimedCreativeLand", this.config_claims_autoRestoreUnclaimedCreativeLand);
      outConfig.set("GriefPrevention.Spam.Enabled", this.config_spam_enabled);
      outConfig.set("GriefPrevention.Spam.LoginCooldownMinutes", this.config_spam_loginCooldownMinutes);
      outConfig.set("GriefPrevention.Spam.MonitorSlashCommands", slashCommandsToMonitor);
      outConfig.set("GriefPrevention.Spam.WarningMessage", this.config_spam_warningMessage);
      outConfig.set("GriefPrevention.Spam.BanOffenders", this.config_spam_banOffenders);
      outConfig.set("GriefPrevention.Spam.BanMessage", this.config_spam_banMessage);
      outConfig.set("GriefPrevention.Spam.AllowedIpAddresses", this.config_spam_allowedIpAddresses);
      outConfig.set("GriefPrevention.Spam.DeathMessageCooldownSeconds", this.config_spam_deathMessageCooldownSeconds);
      outConfig.set("GriefPrevention.PvP.Worlds", pvpEnabledWorldNames);
      outConfig.set("GriefPrevention.PvP.ProtectFreshSpawns", this.config_pvp_protectFreshSpawns);
      outConfig.set("GriefPrevention.PvP.PunishLogout", this.config_pvp_punishLogout);
      outConfig.set("GriefPrevention.PvP.CombatTimeoutSeconds", this.config_pvp_combatTimeoutSeconds);
      outConfig.set("GriefPrevention.PvP.AllowCombatItemDrop", this.config_pvp_allowCombatItemDrop);
      outConfig.set("GriefPrevention.PvP.BlockedSlashCommands", bannedPvPCommandsList);
      outConfig.set("GriefPrevention.PvP.ProtectPlayersInLandClaims.PlayerOwnedClaims", this.config_pvp_noCombatInPlayerLandClaims);
      outConfig.set("GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeClaims", this.config_pvp_noCombatInAdminLandClaims);
      outConfig.set("GriefPrevention.Trees.RemoveFloatingTreetops", this.config_trees_removeFloatingTreetops);
      outConfig.set("GriefPrevention.Trees.RegrowGriefedTrees", this.config_trees_regrowGriefedTrees);
      outConfig.set("GriefPrevention.Economy.ClaimBlocksPurchaseCost", this.config_economy_claimBlocksPurchaseCost);
      outConfig.set("GriefPrevention.Economy.ClaimBlocksSellValue", this.config_economy_claimBlocksSellValue);
      outConfig.set("GriefPrevention.BlockSurfaceCreeperExplosions", this.config_blockSurfaceCreeperExplosions);
      outConfig.set("GriefPrevention.BlockSurfaceOtherExplosions", this.config_blockSurfaceOtherExplosions);
      outConfig.set("GriefPrevention.LimitSurfaceWaterBuckets", this.config_blockWildernessWaterBuckets);
      outConfig.set("GriefPrevention.LimitSkyTrees", this.config_blockSkyTrees);
      outConfig.set("GriefPrevention.FireSpreads", this.config_fireSpreads);
      outConfig.set("GriefPrevention.FireDestroys", this.config_fireDestroys);
      outConfig.set("GriefPrevention.AddItemsToClaimedChests", this.config_addItemsToClaimedChests);
      outConfig.set("GriefPrevention.EavesdropEnabled", this.config_eavesdrop);
      outConfig.set("GriefPrevention.WhisperCommands", whisperCommandsToMonitor);
      outConfig.set("GriefPrevention.SmartBan", this.config_smartBan);
      outConfig.set("GriefPrevention.Siege.Worlds", siegeEnabledWorldNames);
      outConfig.set("GriefPrevention.Siege.BreakableBlocks", breakableBlocksList);
      outConfig.set("GriefPrevention.EndermenMoveBlocks", this.config_endermenMoveBlocks);
      outConfig.set("GriefPrevention.SilverfishBreakBlocks", this.config_silverfishBreakBlocks);
      outConfig.set("GriefPrevention.CreaturesTrampleCrops", this.config_creaturesTrampleCrops);
      outConfig.set("GriefPrevention.HardModeZombiesBreakDoors", this.config_zombiesBreakDoors);
      outConfig.set("GriefPrevention.Database.URL", databaseUrl);
      outConfig.set("GriefPrevention.Database.UserName", databaseUserName);
      outConfig.set("GriefPrevention.Database.Password", databasePassword);
      outConfig.set("GriefPrevention.Mods.BlockIdsRequiringAccessTrust", this.config_mods_accessTrustIds);
      outConfig.set("GriefPrevention.Mods.BlockIdsRequiringContainerTrust", this.config_mods_containerTrustIds);
      outConfig.set("GriefPrevention.Mods.BlockIdsExplodable", this.config_mods_explodableIds);
      outConfig.set("GriefPrevention.Mods.PlayersIgnoringAllClaims", this.config_mods_ignoreClaimsAccounts);
      outConfig.set("GriefPrevention.Mods.BlockIdsRequiringAccessTrust", accessTrustStrings);
      outConfig.set("GriefPrevention.Mods.BlockIdsRequiringContainerTrust", containerTrustStrings);
      outConfig.set("GriefPrevention.Mods.BlockIdsExplodable", explodableStrings);

      try {
         outConfig.save(DataStore.configFilePath);
      } catch (IOException exception) {
         AddLogEntry("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
      }

      this.config_spam_monitorSlashCommands = new ArrayList<>();
      String[] commands = slashCommandsToMonitor.split(";");

      for (int i = 0; i < commands.length; i++) {
         this.config_spam_monitorSlashCommands.add(commands[i].trim());
      }

      this.config_eavesdrop_whisperCommands = new ArrayList<>();
      commands = whisperCommandsToMonitor.split(";");

      for (int i = 0; i < commands.length; i++) {
         this.config_eavesdrop_whisperCommands.add(commands[i].trim());
      }

      this.config_pvp_blockedCommands = new ArrayList<>();
      commands = bannedPvPCommandsList.split(";");

      for (int i = 0; i < commands.length; i++) {
         this.config_pvp_blockedCommands.add(commands[i].trim());
      }

      if (databaseUrl.length() > 0) {
         try {
            DatabaseDataStore databaseStore = new DatabaseDataStore(databaseUrl, databaseUserName, databasePassword);
            if (FlatFileDataStore.hasData()) {
               AddLogEntry("There appears to be some data on the hard drive.  Migrating those data to the database...");
               FlatFileDataStore flatFileStore = new FlatFileDataStore();
               flatFileStore.migrateData(databaseStore);
               AddLogEntry("Data migration process complete.  Reloading data from the database...");
               databaseStore.close();
               databaseStore = new DatabaseDataStore(databaseUrl, databaseUserName, databasePassword);
            }

            this.dataStore = databaseStore;
         } catch (Exception e) {
            AddLogEntry(
               "Because there was a problem with the database, GriefPrevention will not function properly.  Either update the database config settings resolve the issue, or delete those lines from your config.yml so that GriefPrevention can use the file system to store data."
            );
            return;
         }
      }

      if (this.dataStore == null) {
         try {
            this.dataStore = new FlatFileDataStore();
         } catch (Exception e) {
            AddLogEntry("Unable to initialize the file system data store.  Details:");
            AddLogEntry(e.getMessage());
         }
      }

      if (this.config_claims_blocksAccruedPerHour > 0) {
         DeliverClaimBlocksTask task = new DeliverClaimBlocksTask();
         this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 6000L, 6000L);
      }

      EntityCleanupTask task = new EntityCleanupTask(0.0);
      this.getServer().getScheduler().scheduleSyncDelayedTask(instance, task, 20L);
      CleanupUnusedClaimsTask task2 = new CleanupUnusedClaimsTask();
      this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task2, 2400L, 6000L);
      PluginManager pluginManager = this.getServer().getPluginManager();
      PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore, this);
      pluginManager.registerEvents(playerEventHandler, this);
      BlockEventHandler blockEventHandler = new BlockEventHandler(this.dataStore);
      pluginManager.registerEvents(blockEventHandler, this);
      EntityEventHandler entityEventHandler = new EntityEventHandler(this.dataStore);
      pluginManager.registerEvents(entityEventHandler, this);
      if (this.config_economy_claimBlocksPurchaseCost > 0.0 || this.config_economy_claimBlocksSellValue > 0.0) {
         AddLogEntry("GriefPrevention requires Vault for economy integration.");
         AddLogEntry("Attempting to load Vault...");
         RegisteredServiceProvider<Economy> economyProvider = this.getServer().getServicesManager().getRegistration(Economy.class);
         AddLogEntry("Vault loaded successfully!");
         AddLogEntry("Looking for a Vault-compatible economy plugin...");
         if (economyProvider != null) {
            economy = (Economy)economyProvider.getProvider();
            if (economy != null) {
               AddLogEntry("Hooked into economy: " + economy.getName() + ".");
               AddLogEntry("Ready to buy/sell claim blocks!");
            } else {
               AddLogEntry(
                  "ERROR: Vault was unable to find a supported economy plugin.  Either install a Vault-compatible economy plugin, or set both of the economy config variables to zero."
               );
            }
         } else {
            AddLogEntry(
               "ERROR: Vault was unable to find a supported economy plugin.  Either install a Vault-compatible economy plugin, or set both of the economy config variables to zero."
            );
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
      Player player = null;
      if (sender instanceof Player) {
         player = (Player)sender;
      }

      if (cmd.getName().equalsIgnoreCase("abandonclaim") && player != null) {
         return this.abandonClaimHandler(player, false);
      }

      if (cmd.getName().equalsIgnoreCase("abandontoplevelclaim") && player != null) {
         return this.abandonClaimHandler(player, true);
      }

      if (cmd.getName().equalsIgnoreCase("ignoreclaims") && player != null) {
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         playerData.ignoreClaims = !playerData.ignoreClaims;
         if (!playerData.ignoreClaims) {
            sendMessage(player, TextMode.Success, Messages.RespectingClaims);
         } else {
            sendMessage(player, TextMode.Success, Messages.IgnoringClaims);
         }

         return true;
      } else if (!cmd.getName().equalsIgnoreCase("abandonallclaims") || player == null) {
         if (cmd.getName().equalsIgnoreCase("restorenature") && player != null) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            playerData.shovelMode = ShovelMode.RestoreNature;
            sendMessage(player, TextMode.Instr, Messages.RestoreNatureActivate);
            return true;
         }

         if (cmd.getName().equalsIgnoreCase("restorenatureaggressive") && player != null) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            playerData.shovelMode = ShovelMode.RestoreNatureAggressive;
            sendMessage(player, TextMode.Warn, Messages.RestoreNatureAggressiveActivate);
            return true;
         }

         if (cmd.getName().equalsIgnoreCase("restorenaturefill") && player != null) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            playerData.shovelMode = ShovelMode.RestoreNatureFill;
            playerData.fillRadius = 2;
            if (args.length > 0) {
               try {
                  playerData.fillRadius = Integer.parseInt(args[0]);
               } catch (Exception var15) {
               }
            }

            if (playerData.fillRadius < 0) {
               playerData.fillRadius = 2;
            }

            sendMessage(player, TextMode.Success, Messages.FillModeActive, String.valueOf(playerData.fillRadius));
            return true;
         } else if (cmd.getName().equalsIgnoreCase("trust") && player != null) {
            if (args.length != 1) {
               return false;
            }

            this.handleTrustCommand(player, ClaimPermission.Build, args[0]);
            return true;
         } else if (cmd.getName().equalsIgnoreCase("transferclaim") && player != null) {
            if (args.length != 1) {
               return false;
            }

            if (!player.hasPermission("griefprevention.adminclaims")) {
               sendMessage(player, TextMode.Err, Messages.TransferClaimPermission);
               return true;
            }

            Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim == null) {
               sendMessage(player, TextMode.Instr, Messages.TransferClaimMissing);
               return true;
            }

            OfflinePlayer targetPlayer = this.resolvePlayer(args[0]);
            if (targetPlayer == null) {
               sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
               return true;
            }

            try {
               this.dataStore.changeClaimOwner(claim, targetPlayer.getName());
            } catch (Exception e) {
               sendMessage(player, TextMode.Instr, Messages.TransferTopLevel);
               return true;
            }

            sendMessage(player, TextMode.Success, Messages.TransferSuccess);
            AddLogEntry(
               player.getName()
                  + " transferred a claim at "
                  + getfriendlyLocationString(claim.getLesserBoundaryCorner())
                  + " to "
                  + targetPlayer.getName()
                  + "."
            );
            return true;
         } else if (cmd.getName().equalsIgnoreCase("trustlist") && player != null) {
            Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim == null) {
               sendMessage(player, TextMode.Err, Messages.TrustListNoClaim);
               return true;
            }

            String errorMessage = claim.allowGrantPermission(player);
            if (errorMessage != null) {
               sendMessage(player, TextMode.Err, errorMessage);
               return true;
            }

            ArrayList<String> builders = new ArrayList<>();
            ArrayList<String> containers = new ArrayList<>();
            ArrayList<String> accessors = new ArrayList<>();
            ArrayList<String> managers = new ArrayList<>();
            claim.getPermissions(builders, containers, accessors, managers);
            player.sendMessage("Explicit permissions here:");
            StringBuilder permissions = new StringBuilder();
            permissions.append(ChatColor.GOLD + "M: ");
            if (managers.size() > 0) {
               for (int i = 0; i < managers.size(); i++) {
                  permissions.append(managers.get(i) + " ");
               }
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.YELLOW + "B: ");
            if (builders.size() > 0) {
               for (int i = 0; i < builders.size(); i++) {
                  permissions.append(builders.get(i) + " ");
               }
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.GREEN + "C: ");
            if (containers.size() > 0) {
               for (int i = 0; i < containers.size(); i++) {
                  permissions.append(containers.get(i) + " ");
               }
            }

            player.sendMessage(permissions.toString());
            permissions = new StringBuilder();
            permissions.append(ChatColor.BLUE + "A :");
            if (accessors.size() > 0) {
               for (int i = 0; i < accessors.size(); i++) {
                  permissions.append(accessors.get(i) + " ");
               }
            }

            player.sendMessage(permissions.toString());
            player.sendMessage("(M-anager, B-builder, C-ontainers, A-ccess)");
            return true;
         } else if (cmd.getName().equalsIgnoreCase("untrust") && player != null) {
            if (args.length != 1) {
               return false;
            }

            Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
            if (args[0].contains(".")) {
               args[0] = "[" + args[0] + "]";
            }

            boolean clearPermissions = false;
            OfflinePlayer otherPlayer = null;
            if (args[0].equals("all")) {
               if (claim != null && claim.allowEdit(player) != null) {
                  sendMessage(player, TextMode.Err, Messages.ClearPermsOwnerOnly);
                  return true;
               }

               clearPermissions = true;
            } else if (!args[0].startsWith("[") || !args[0].endsWith("]")) {
               otherPlayer = this.resolvePlayer(args[0]);
               if (!clearPermissions && otherPlayer == null && !args[0].equals("public")) {
                  sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                  return true;
               }

               if (otherPlayer != null) {
                  args[0] = otherPlayer.getName();
               }
            }

            if (claim == null) {
               PlayerData playerData = this.dataStore.getPlayerData(player.getName());

               for (int i = 0; i < playerData.claims.size(); i++) {
                  claim = playerData.claims.get(i);
                  if (clearPermissions) {
                     claim.clearPermissions();
                  } else {
                     claim.dropPermission(args[0]);
                     claim.managers.remove(args[0]);
                  }

                  this.dataStore.saveClaim(claim);
               }

               if (args[0].equals("public")) {
                  args[0] = "the public";
               }

               if (!clearPermissions) {
                  sendMessage(player, TextMode.Success, Messages.UntrustIndividualAllClaims, args[0]);
               } else {
                  sendMessage(player, TextMode.Success, Messages.UntrustEveryoneAllClaims);
               }
            } else if (claim.allowGrantPermission(player) != null) {
               sendMessage(player, TextMode.Err, Messages.NoPermissionTrust, claim.getOwnerName());
            } else {
               if (clearPermissions) {
                  claim.clearPermissions();
                  sendMessage(player, TextMode.Success, Messages.ClearPermissionsOneClaim);
               } else {
                  claim.dropPermission(args[0]);
                  if (claim.allowEdit(player) == null) {
                     claim.managers.remove(args[0]);
                     if (args[0].equals("public")) {
                        args[0] = "the public";
                     }

                     sendMessage(player, TextMode.Success, Messages.UntrustIndividualSingleClaim, args[0]);
                  } else {
                     sendMessage(player, TextMode.Success, Messages.UntrustOwnerOnly, claim.getOwnerName());
                  }
               }

               this.dataStore.saveClaim(claim);
            }

            return true;
         } else if (cmd.getName().equalsIgnoreCase("accesstrust") && player != null) {
            if (args.length != 1) {
               return false;
            }

            this.handleTrustCommand(player, ClaimPermission.Access, args[0]);
            return true;
         } else if (cmd.getName().equalsIgnoreCase("containertrust") && player != null) {
            if (args.length != 1) {
               return false;
            }

            this.handleTrustCommand(player, ClaimPermission.Inventory, args[0]);
            return true;
         } else if (cmd.getName().equalsIgnoreCase("permissiontrust") && player != null) {
            if (args.length != 1) {
               return false;
            }

            this.handleTrustCommand(player, null, args[0]);
            return true;
         } else if (!cmd.getName().equalsIgnoreCase("buyclaimblocks") || player == null) {
            if (!cmd.getName().equalsIgnoreCase("sellclaimblocks") || player == null) {
               if (cmd.getName().equalsIgnoreCase("adminclaims") && player != null) {
                  PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                  playerData.shovelMode = ShovelMode.Admin;
                  sendMessage(player, TextMode.Success, Messages.AdminClaimsMode);
                  return true;
               }

               if (cmd.getName().equalsIgnoreCase("basicclaims") && player != null) {
                  PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                  playerData.shovelMode = ShovelMode.Basic;
                  playerData.claimSubdividing = null;
                  sendMessage(player, TextMode.Success, Messages.BasicClaimsMode);
                  return true;
               }

               if (cmd.getName().equalsIgnoreCase("subdivideclaims") && player != null) {
                  PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                  playerData.shovelMode = ShovelMode.Subdivide;
                  playerData.claimSubdividing = null;
                  sendMessage(player, TextMode.Instr, Messages.SubdivisionMode);
                  sendMessage(player, TextMode.Instr, Messages.SubdivisionDemo);
                  return true;
               }

               if (cmd.getName().equalsIgnoreCase("deleteclaim") && player != null) {
                  Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
                  if (claim == null) {
                     sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
                  } else if (claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims")) {
                     sendMessage(player, TextMode.Err, Messages.CantDeleteAdminClaim);
                  } else {
                     PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                     if (claim.children.size() > 0 && !playerData.warnedAboutMajorDeletion) {
                        sendMessage(player, TextMode.Warn, Messages.DeletionSubdivisionWarning);
                        playerData.warnedAboutMajorDeletion = true;
                     } else {
                        claim.removeSurfaceFluids(null);
                        this.dataStore.deleteClaim(claim);
                        if (instance.config_claims_autoRestoreUnclaimedCreativeLand && instance.creativeRulesApply(claim.getLesserBoundaryCorner())) {
                           instance.restoreClaim(claim, 0L);
                        }

                        sendMessage(player, TextMode.Success, Messages.DeleteSuccess);
                        AddLogEntry(
                           player.getName() + " deleted " + claim.getOwnerName() + "'s claim at " + getfriendlyLocationString(claim.getLesserBoundaryCorner())
                        );
                        Visualization.Revert(player);
                        playerData.warnedAboutMajorDeletion = false;
                     }
                  }

                  return true;
               } else if (cmd.getName().equalsIgnoreCase("claimexplosions") && player != null) {
                  Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
                  if (claim == null) {
                     sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
                  } else {
                     String noBuildReason = claim.allowBuild(player);
                     if (noBuildReason != null) {
                        sendMessage(player, TextMode.Err, noBuildReason);
                        return true;
                     }

                     if (claim.areExplosivesAllowed) {
                        claim.areExplosivesAllowed = false;
                        sendMessage(player, TextMode.Success, Messages.ExplosivesDisabled);
                     } else {
                        claim.areExplosivesAllowed = true;
                        sendMessage(player, TextMode.Success, Messages.ExplosivesEnabled);
                     }
                  }

                  return true;
               } else if (cmd.getName().equalsIgnoreCase("deleteallclaims")) {
                  if (args.length != 1) {
                     return false;
                  }

                  OfflinePlayer otherPlayer = this.resolvePlayer(args[0]);
                  if (otherPlayer == null) {
                     sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                     return true;
                  }

                  this.dataStore.deleteClaimsForPlayer(otherPlayer.getName(), true);
                  sendMessage(player, TextMode.Success, Messages.DeleteAllSuccess, otherPlayer.getName());
                  if (player != null) {
                     AddLogEntry(player.getName() + " deleted all claims belonging to " + otherPlayer.getName() + ".");
                     Visualization.Revert(player);
                  }

                  return true;
               } else if (cmd.getName().equalsIgnoreCase("claimslist")) {
                  if (args.length > 1) {
                     return false;
                  }

                  OfflinePlayer otherPlayer;
                  if (args.length < 1) {
                     if (player == null) {
                        return false;
                     }

                     otherPlayer = player;
                  } else {
                     if (player != null && !player.hasPermission("griefprevention.deleteclaims")) {
                        sendMessage(player, TextMode.Err, Messages.ClaimsListNoPermission);
                        return true;
                     }

                     otherPlayer = this.resolvePlayer(args[0]);
                     if (otherPlayer == null) {
                        sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                        return true;
                     }
                  }

                  PlayerData playerData = this.dataStore.getPlayerData(otherPlayer.getName());
                  sendMessage(
                     player,
                     TextMode.Instr,
                     " "
                        + playerData.accruedClaimBlocks
                        + "(+"
                        + (playerData.bonusClaimBlocks + this.dataStore.getGroupBonusBlocks(otherPlayer.getName()))
                        + ")="
                        + (playerData.accruedClaimBlocks + playerData.bonusClaimBlocks + this.dataStore.getGroupBonusBlocks(otherPlayer.getName()))
                  );

                  for (int i = 0; i < playerData.claims.size(); i++) {
                     Claim claim = playerData.claims.get(i);
                     sendMessage(player, TextMode.Instr, "  (-" + claim.getArea() + ") " + getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                  }

                  if (playerData.claims.size() > 0) {
                     sendMessage(player, TextMode.Instr, "   =" + playerData.getRemainingClaimBlocks());
                  }

                  if (!otherPlayer.isOnline()) {
                     this.dataStore.clearCachedPlayerData(otherPlayer.getName());
                  }

                  return true;
               } else if (cmd.getName().equalsIgnoreCase("deathblow")) {
                  if (args.length < 1) {
                     return false;
                  }

                  Player targetPlayer = this.getServer().getPlayer(args[0]);
                  if (targetPlayer == null) {
                     sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                     return true;
                  }

                  Player recipientPlayer = null;
                  if (args.length > 1) {
                     recipientPlayer = this.getServer().getPlayer(args[1]);
                     if (recipientPlayer == null) {
                        sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                        return true;
                     }
                  }

                  if (recipientPlayer != null) {
                     targetPlayer.teleport(recipientPlayer);
                  } else if (targetPlayer.getWorld().getEnvironment() == Environment.NORMAL) {
                     Location location = targetPlayer.getLocation();
                     location.setY(location.getWorld().getMaxHeight());
                     targetPlayer.teleport(location);
                  }

                  targetPlayer.setHealth(0);
                  if (player != null) {
                     AddLogEntry(player.getName() + " used /DeathBlow to kill " + targetPlayer.getName() + ".");
                  } else {
                     AddLogEntry("Killed " + targetPlayer.getName() + ".");
                  }

                  return true;
               } else if (cmd.getName().equalsIgnoreCase("deletealladminclaims")) {
                  if (!player.hasPermission("griefprevention.deleteclaims")) {
                     sendMessage(player, TextMode.Err, Messages.NoDeletePermission);
                     return true;
                  }

                  this.dataStore.deleteClaimsForPlayer("", true);
                  sendMessage(player, TextMode.Success, Messages.AllAdminDeleted);
                  if (player != null) {
                     AddLogEntry(player.getName() + " deleted all administrative claims.");
                     Visualization.Revert(player);
                  }

                  return true;
               } else if (cmd.getName().equalsIgnoreCase("adjustbonusclaimblocks")) {
                  if (args.length != 2) {
                     return false;
                  }

                  int adjustment;
                  try {
                     adjustment = Integer.parseInt(args[1]);
                  } catch (NumberFormatException numberFormatException) {
                     return false;
                  }

                  if (args[0].startsWith("[") && args[0].endsWith("]")) {
                     String permissionIdentifier = args[0].substring(1, args[0].length() - 1);
                     int newTotal = this.dataStore.adjustGroupBonusBlocks(permissionIdentifier, adjustment);
                     sendMessage(
                        player, TextMode.Success, Messages.AdjustGroupBlocksSuccess, permissionIdentifier, String.valueOf(adjustment), String.valueOf(newTotal)
                     );
                     if (player != null) {
                        AddLogEntry(player.getName() + " adjusted " + permissionIdentifier + "'s bonus claim blocks by " + adjustment + ".");
                     }

                     return true;
                  } else {
                     OfflinePlayer targetPlayer = this.resolvePlayer(args[0]);
                     if (targetPlayer == null) {
                        sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                        return true;
                     }

                     PlayerData playerData = this.dataStore.getPlayerData(targetPlayer.getName());
                     playerData.bonusClaimBlocks += adjustment;
                     this.dataStore.savePlayerData(targetPlayer.getName(), playerData);
                     sendMessage(
                        player,
                        TextMode.Success,
                        Messages.AdjustBlocksSuccess,
                        targetPlayer.getName(),
                        String.valueOf(adjustment),
                        String.valueOf(playerData.bonusClaimBlocks)
                     );
                     if (player != null) {
                        AddLogEntry(player.getName() + " adjusted " + targetPlayer.getName() + "'s bonus claim blocks by " + adjustment + ".");
                     }

                     return true;
                  }
               } else if (cmd.getName().equalsIgnoreCase("trapped") && player != null) {
                  PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                  Claim claim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
                  if (playerData.pendingTrapped) {
                     return true;
                  }

                  if (claim != null && claim.allowBuild(player) != null) {
                     if (player.getWorld().getEnvironment() != Environment.NORMAL) {
                        sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                        return true;
                     } else if (claim.isAdminClaim()) {
                        sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                        return true;
                     } else {
                        long lastTrappedUsage = playerData.lastTrappedUsage.getTime();
                        long nextTrappedUsage = lastTrappedUsage + 3600000 * this.config_claims_trappedCooldownHours;
                        long now = Calendar.getInstance().getTimeInMillis();
                        if (now < nextTrappedUsage) {
                           sendMessage(
                              player,
                              TextMode.Err,
                              Messages.TrappedOnCooldown,
                              String.valueOf(this.config_claims_trappedCooldownHours),
                              String.valueOf((nextTrappedUsage - now) / 60000L + 1L)
                           );
                           return true;
                        } else {
                           sendMessage(player, TextMode.Instr, Messages.RescuePending);
                           PlayerRescueTask task = new PlayerRescueTask(player, player.getLocation());
                           this.getServer().getScheduler().scheduleSyncDelayedTask(this, task, 200L);
                           return true;
                        }
                     }
                  } else {
                     sendMessage(player, TextMode.Err, Messages.NotTrappedHere);
                     return true;
                  }
               } else {
                  if (cmd.getName().equalsIgnoreCase("siege") && player != null) {
                     if (!this.siegeEnabledForWorld(player.getWorld())) {
                        sendMessage(player, TextMode.Err, Messages.NonSiegeWorld);
                        return true;
                     }

                     if (args.length > 1) {
                        return false;
                     }

                     Player attacker = player;
                     PlayerData attackerData = this.dataStore.getPlayerData(attacker.getName());
                     if (attackerData.siegeData != null) {
                        sendMessage(player, TextMode.Err, Messages.AlreadySieging);
                        return true;
                     }

                     if (attackerData.pvpImmune) {
                        sendMessage(player, TextMode.Err, Messages.CantFightWhileImmune);
                        return true;
                     }

                     Player defender = null;
                     if (args.length >= 1) {
                        defender = this.getServer().getPlayer(args[0]);
                        if (defender == null) {
                           sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                           return true;
                        }
                     } else {
                        if (attackerData.lastPvpPlayer.length() <= 0) {
                           return false;
                        }

                        defender = this.getServer().getPlayer(attackerData.lastPvpPlayer);
                        if (defender == null) {
                           return false;
                        }
                     }

                     PlayerData defenderData = this.dataStore.getPlayerData(defender.getName());
                     if (defenderData.siegeData != null) {
                        sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegePlayer);
                        return true;
                     }

                     if (defenderData.pvpImmune) {
                        sendMessage(player, TextMode.Err, Messages.NoSiegeDefenseless);
                        return true;
                     }

                     Claim defenderClaim = this.dataStore.getClaimAt(defender.getLocation(), false, null);
                     if (defenderClaim == null || defenderClaim.allowAccess(defender) != null) {
                        sendMessage(player, TextMode.Err, Messages.NotSiegableThere);
                        return true;
                     }

                     if (!defenderClaim.isNear(attacker.getLocation(), 25)) {
                        sendMessage(player, TextMode.Err, Messages.SiegeTooFarAway);
                        return true;
                     }

                     if (defenderClaim.siegeData != null) {
                        sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegeArea);
                        return true;
                     }

                     if (defenderClaim.isAdminClaim()) {
                        sendMessage(player, TextMode.Err, Messages.NoSiegeAdminClaim);
                        return true;
                     }

                     if (this.dataStore.onCooldown(attacker, defender, defenderClaim)) {
                        sendMessage(player, TextMode.Err, Messages.SiegeOnCooldown);
                        return true;
                     }

                     this.dataStore.startSiege(attacker, defender, defenderClaim);
                     sendMessage(defender, TextMode.Warn, Messages.SiegeAlert, attacker.getName());
                     sendMessage(player, TextMode.Success, Messages.SiegeConfirmed, defender.getName());
                  }

                  return false;
               }
            } else {
               if (economy == null) {
                  sendMessage(player, TextMode.Err, Messages.BuySellNotConfigured);
                  return true;
               }

               if (!player.hasPermission("griefprevention.buysellclaimblocks")) {
                  sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                  return true;
               }

               if (instance.config_economy_claimBlocksSellValue == 0.0) {
                  sendMessage(player, TextMode.Err, Messages.OnlyPurchaseBlocks);
                  return true;
               }

               PlayerData playerData = this.dataStore.getPlayerData(player.getName());
               int availableBlocks = playerData.getRemainingClaimBlocks();
               if (args.length != 1) {
                  sendMessage(
                     player,
                     TextMode.Info,
                     Messages.BlockSaleValue,
                     String.valueOf(instance.config_economy_claimBlocksSellValue),
                     String.valueOf(availableBlocks)
                  );
                  return false;
               }

               int blockCount;
               try {
                  blockCount = Integer.parseInt(args[0]);
               } catch (NumberFormatException numberFormatException) {
                  return false;
               }

               if (blockCount <= 0) {
                  return false;
               }

               if (blockCount > availableBlocks) {
                  sendMessage(player, TextMode.Err, Messages.NotEnoughBlocksForSale);
               } else {
                  double totalValue = blockCount * instance.config_economy_claimBlocksSellValue;
                  economy.depositPlayer(player.getName(), totalValue);
                  playerData.accruedClaimBlocks -= blockCount;
                  this.dataStore.savePlayerData(player.getName(), playerData);
                  sendMessage(
                     player, TextMode.Success, Messages.BlockSaleConfirmation, String.valueOf(totalValue), String.valueOf(playerData.getRemainingClaimBlocks())
                  );
               }

               return true;
            }
         } else {
            if (economy == null) {
               sendMessage(player, TextMode.Err, Messages.BuySellNotConfigured);
               return true;
            }

            if (!player.hasPermission("griefprevention.buysellclaimblocks")) {
               sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
               return true;
            }

            if (instance.config_economy_claimBlocksPurchaseCost == 0.0) {
               sendMessage(player, TextMode.Err, Messages.OnlySellBlocks);
               return true;
            }

            if (args.length != 1) {
               sendMessage(
                  player,
                  TextMode.Info,
                  Messages.BlockPurchaseCost,
                  String.valueOf(instance.config_economy_claimBlocksPurchaseCost),
                  String.valueOf(economy.getBalance(player.getName()))
               );
               return false;
            }

            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            int maxPurchasable = instance.config_claims_maxAccruedBlocks - playerData.accruedClaimBlocks;
            if (maxPurchasable <= 0) {
               sendMessage(player, TextMode.Err, Messages.ClaimBlockLimit);
               return true;
            }

            int blockCount;
            try {
               blockCount = Integer.parseInt(args[0]);
            } catch (NumberFormatException numberFormatException) {
               return false;
            }

            if (blockCount <= 0) {
               return false;
            }

            if (blockCount > maxPurchasable) {
               blockCount = maxPurchasable;
            }

            double balance = economy.getBalance(player.getName());
            double totalCost = blockCount * instance.config_economy_claimBlocksPurchaseCost;
            if (totalCost > balance) {
               sendMessage(player, TextMode.Err, Messages.InsufficientFunds, String.valueOf(totalCost), String.valueOf(balance));
            } else {
               economy.withdrawPlayer(player.getName(), totalCost);
               playerData.accruedClaimBlocks += blockCount;
               this.dataStore.savePlayerData(player.getName(), playerData);
               sendMessage(
                  player, TextMode.Success, Messages.PurchaseConfirmation, String.valueOf(totalCost), String.valueOf(playerData.getRemainingClaimBlocks())
               );
            }

            return true;
         }
      } else if (args.length != 0) {
         return false;
      } else if (!instance.config_claims_allowUnclaimInCreative && this.creativeRulesApply(player.getLocation())) {
         sendMessage(player, TextMode.Err, Messages.NoCreativeUnClaim);
         return true;
      } else {
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         int originalClaimCount = playerData.claims.size();
         if (originalClaimCount == 0) {
            sendMessage(player, TextMode.Err, Messages.YouHaveNoClaims);
            return true;
         } else {
            this.dataStore.deleteClaimsForPlayer(player.getName(), false);
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            sendMessage(player, TextMode.Success, Messages.SuccessfulAbandon, String.valueOf(remainingBlocks));
            Visualization.Revert(player);
            return true;
         }
      }
   }

   public static String getfriendlyLocationString(Location location) {
      return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
   }

   private boolean abandonClaimHandler(Player player, boolean deleteTopLevelClaim) {
      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
      if (claim == null) {
         sendMessage(player, TextMode.Instr, Messages.AbandonClaimMissing);
      } else if (claim.allowEdit(player) != null) {
         sendMessage(player, TextMode.Err, Messages.NotYourClaim);
      } else if (!instance.config_claims_allowUnclaimInCreative && this.creativeRulesApply(player.getLocation())) {
         sendMessage(player, TextMode.Err, Messages.NoCreativeUnClaim);
      } else {
         if (claim.children.size() > 0 && !deleteTopLevelClaim) {
            sendMessage(player, TextMode.Instr, Messages.DeleteTopLevelClaim);
            return true;
         }

         if (!playerData.warnedAboutMajorDeletion && claim.hasSurfaceFluids()) {
            sendMessage(player, TextMode.Warn, Messages.ConfirmFluidRemoval);
            playerData.warnedAboutMajorDeletion = true;
         } else {
            claim.removeSurfaceFluids(null);
            this.dataStore.deleteClaim(claim);
            if (instance.creativeRulesApply(claim.getLesserBoundaryCorner())) {
               AddLogEntry(player.getName() + " abandoned a claim @ " + getfriendlyLocationString(claim.getLesserBoundaryCorner()));
               sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
               instance.restoreClaim(claim, 2400L);
            }

            int remainingBlocks = playerData.getRemainingClaimBlocks();
            sendMessage(player, TextMode.Success, Messages.AbandonSuccess, String.valueOf(remainingBlocks));
            Visualization.Revert(player);
            playerData.warnedAboutMajorDeletion = false;
         }
      }

      return true;
   }

   private void handleTrustCommand(Player player, ClaimPermission permissionLevel, String recipientName) {
      Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
      String permission = null;
      OfflinePlayer otherPlayer = null;
      if (recipientName.startsWith("[") && recipientName.endsWith("]")) {
         permission = recipientName.substring(1, recipientName.length() - 1);
         if (permission == null || permission.isEmpty()) {
            sendMessage(player, TextMode.Err, Messages.InvalidPermissionID);
            return;
         }
      } else if (!recipientName.contains(".")) {
         otherPlayer = this.resolvePlayer(recipientName);
         if (otherPlayer == null && !recipientName.equals("public") && !recipientName.equals("all")) {
            sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
            return;
         }

         if (otherPlayer != null) {
            recipientName = otherPlayer.getName();
         } else {
            recipientName = "public";
         }
      }

      ArrayList<Claim> targetClaims = new ArrayList<>();
      if (claim == null) {
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());

         for (int i = 0; i < playerData.claims.size(); i++) {
            targetClaims.add(playerData.claims.get(i));
         }
      } else {
         if (claim.allowGrantPermission(player) != null) {
            sendMessage(player, TextMode.Err, Messages.NoPermissionTrust, claim.getOwnerName());
            return;
         }

         String errorMessage = null;
         if (permissionLevel == null) {
            errorMessage = claim.allowEdit(player);
            if (errorMessage != null) {
               errorMessage = "Only " + claim.getOwnerName() + " can grant /PermissionTrust here.";
            }
         } else {
            switch (permissionLevel) {
               case Access:
                  errorMessage = claim.allowAccess(player);
                  break;
               case Inventory:
                  errorMessage = claim.allowContainers(player);
                  break;
               default:
                  errorMessage = claim.allowBuild(player);
            }
         }

         if (errorMessage != null) {
            sendMessage(player, TextMode.Err, Messages.CantGrantThatPermission);
            return;
         }

         targetClaims.add(claim);
      }

      if (targetClaims.size() == 0) {
         sendMessage(player, TextMode.Err, Messages.GrantPermissionNoClaim);
      } else {
         for (int i = 0; i < targetClaims.size(); i++) {
            Claim currentClaim = targetClaims.get(i);
            if (permissionLevel == null) {
               if (!currentClaim.managers.contains(recipientName)) {
                  currentClaim.managers.add(recipientName);
               }
            } else {
               currentClaim.setPermission(recipientName, permissionLevel);
            }

            this.dataStore.saveClaim(currentClaim);
         }

         if (recipientName.equals("public")) {
            recipientName = this.dataStore.getMessage(Messages.CollectivePublic);
         }

         String permissionDescription;
         if (permissionLevel == null) {
            permissionDescription = this.dataStore.getMessage(Messages.PermissionsPermission);
         } else if (permissionLevel == ClaimPermission.Build) {
            permissionDescription = this.dataStore.getMessage(Messages.BuildPermission);
         } else if (permissionLevel == ClaimPermission.Access) {
            permissionDescription = this.dataStore.getMessage(Messages.AccessPermission);
         } else {
            permissionDescription = this.dataStore.getMessage(Messages.ContainersPermission);
         }

         String location;
         if (claim == null) {
            location = this.dataStore.getMessage(Messages.LocationAllClaims);
         } else {
            location = this.dataStore.getMessage(Messages.LocationCurrentClaim);
         }

         sendMessage(player, TextMode.Success, Messages.GrantPermissionConfirmation, recipientName, permissionDescription, location);
      }
   }

   private OfflinePlayer resolvePlayer(String name) {
      Player player = this.getServer().getPlayer(name);
      if (player != null) {
         return player;
      }

      OfflinePlayer[] offlinePlayers = this.getServer().getOfflinePlayers();

      for (int i = 0; i < offlinePlayers.length; i++) {
         if (offlinePlayers[i].getName().equalsIgnoreCase(name)) {
            return offlinePlayers[i];
         }
      }

      return null;
   }

   public void onDisable() {
      Player[] players = this.getServer().getOnlinePlayers();

      for (int i = 0; i < players.length; i++) {
         Player player = players[i];
         String playerName = player.getName();
         PlayerData playerData = this.dataStore.getPlayerData(playerName);
         this.dataStore.savePlayerData(playerName, playerData);
      }

      this.dataStore.close();
      AddLogEntry("GriefPrevention disabled.");
   }

   public void checkPvpProtectionNeeded(Player player) {
      if (this.config_pvp_enabledWorlds.contains(player.getWorld())) {
         if (player.getGameMode() != GameMode.CREATIVE) {
            if (this.config_pvp_protectFreshSpawns) {
               if (!player.hasPermission("griefprevention.nopvpimmunity")) {
                  PlayerInventory inventory = player.getInventory();
                  ItemStack[] armorStacks = inventory.getArmorContents();

                  for (int i = 0; i < armorStacks.length; i++) {
                     if (armorStacks[i] != null && armorStacks[i].getType() != Material.AIR) {
                        return;
                     }
                  }

                  ItemStack[] generalStacks = inventory.getContents();

                  for (int i = 0; i < generalStacks.length; i++) {
                     if (generalStacks[i] != null && generalStacks[i].getType() != Material.AIR) {
                        return;
                     }
                  }

                  PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                  playerData.pvpImmune = true;
                  sendMessage(player, TextMode.Success, Messages.PvPImmunityStart);
               }
            }
         }
      }
   }

   public boolean claimsEnabledForWorld(World world) {
      return this.config_claims_enabledWorlds.contains(world);
   }

   public boolean siegeEnabledForWorld(World world) {
      return this.config_siege_enabledWorlds.contains(world);
   }

   void handleLogBroken(Block block) {
      Block rootBlock = this.getRootBlock(block);
      if (rootBlock != null) {
         int min_x = rootBlock.getX() - 5;
         int max_x = rootBlock.getX() + 5;
         int min_z = rootBlock.getZ() - 5;
         int max_z = rootBlock.getZ() + 5;
         int max_y = rootBlock.getWorld().getMaxHeight() - 1;
         ArrayList<Block> examinedBlocks = new ArrayList<>();
         ArrayList<Block> treeBlocks = new ArrayList<>();
         ConcurrentLinkedQueue<Block> blocksToExamine = new ConcurrentLinkedQueue<>();
         blocksToExamine.add(rootBlock);
         examinedBlocks.add(rootBlock);
         boolean hasLeaves = false;

         while (!blocksToExamine.isEmpty()) {
            Block currentBlock = blocksToExamine.remove();
            if (currentBlock.getType() == Material.LOG) {
               boolean partOfTree = false;
               if (currentBlock.getX() == block.getX() && currentBlock.getZ() == block.getZ()) {
                  partOfTree = true;
               } else {
                  Block downBlock = currentBlock.getRelative(BlockFace.DOWN);

                  while (downBlock.getType() == Material.LOG) {
                     downBlock = downBlock.getRelative(BlockFace.DOWN);
                  }

                  if (downBlock.getType() != Material.AIR && downBlock.getType() != Material.LEAVES) {
                     if (Math.abs(downBlock.getX() - block.getX()) <= 1 && Math.abs(downBlock.getZ() - block.getZ()) <= 1) {
                        return;
                     }
                  } else {
                     partOfTree = true;
                  }
               }

               if (partOfTree) {
                  treeBlocks.add(currentBlock);
               }
            }

            if (currentBlock.getType() == Material.LOG || currentBlock.getType() == Material.LEAVES) {
               if (currentBlock.getType() == Material.LEAVES) {
                  hasLeaves = true;
               }

               Block[] neighboringBlocks = new Block[]{
                  currentBlock.getRelative(BlockFace.EAST),
                  currentBlock.getRelative(BlockFace.WEST),
                  currentBlock.getRelative(BlockFace.NORTH),
                  currentBlock.getRelative(BlockFace.SOUTH),
                  currentBlock.getRelative(BlockFace.UP),
                  currentBlock.getRelative(BlockFace.DOWN)
               };

               for (int i = 0; i < neighboringBlocks.length; i++) {
                  Block neighboringBlock = neighboringBlocks[i];
                  if (neighboringBlock.getX() >= min_x
                     && neighboringBlock.getX() <= max_x
                     && neighboringBlock.getZ() >= min_z
                     && neighboringBlock.getZ() <= max_z
                     && neighboringBlock.getY() <= max_y
                     && !examinedBlocks.contains(neighboringBlock)) {
                     examinedBlocks.add(neighboringBlock);
                     if (neighboringBlock.getType() != Material.LOG && neighboringBlock.getType() != Material.LEAVES) {
                        if (this.isPlayerBlock(neighboringBlock)) {
                           return;
                        }
                     } else {
                        blocksToExamine.add(neighboringBlock);
                     }
                  }
               }
            }
         }

         if (hasLeaves) {
            TreeCleanupTask cleanupTask = new TreeCleanupTask(block, rootBlock, treeBlocks, rootBlock.getData());
            instance.getServer().getScheduler().scheduleSyncDelayedTask(instance, cleanupTask, 2400L);
         }
      }
   }

   private Block getRootBlock(Block logBlock) {
      if (logBlock.getType() != Material.LOG) {
         return null;
      }

      Block underBlock = logBlock.getRelative(BlockFace.DOWN);

      while (underBlock.getType() == Material.LOG) {
         underBlock = underBlock.getRelative(BlockFace.DOWN);
      }

      if (underBlock.getType() != Material.DIRT) {
         return null;
      }

      Block aboveBlock = logBlock.getRelative(BlockFace.UP);

      while (aboveBlock.getType() == Material.LOG) {
         aboveBlock = aboveBlock.getRelative(BlockFace.UP);
      }

      return aboveBlock.getType() != Material.AIR && aboveBlock.getType() != Material.LEAVES ? null : underBlock.getRelative(BlockFace.UP);
   }

   private boolean isPlayerBlock(Block block) {
      Material material = block.getType();
      return material != Material.AIR
         && material != Material.LEAVES
         && material != Material.LOG
         && material != Material.DIRT
         && material != Material.GRASS
         && material != Material.STATIONARY_WATER
         && material != Material.BROWN_MUSHROOM
         && material != Material.RED_MUSHROOM
         && material != Material.RED_ROSE
         && material != Material.LONG_GRASS
         && material != Material.SNOW
         && material != Material.STONE
         && material != Material.VINE
         && material != Material.WATER_LILY
         && material != Material.YELLOW_FLOWER
         && material != Material.CLAY;
   }

   public Location ejectPlayer(Player player) {
      Location candidateLocation = player.getLocation();

      while (true) {
         Claim claim = null;
         claim = instance.dataStore.getClaimAt(candidateLocation, false, null);
         if (claim == null) {
            GuaranteeChunkLoaded(candidateLocation);
            Block highestBlock = candidateLocation.getWorld().getHighestBlockAt(candidateLocation.getBlockX(), candidateLocation.getBlockZ());
            Location destination = new Location(highestBlock.getWorld(), highestBlock.getX(), highestBlock.getY() + 2, highestBlock.getZ());
            player.teleport(destination);
            return destination;
         }

         candidateLocation = new Location(
            claim.lesserBoundaryCorner.getWorld(),
            claim.lesserBoundaryCorner.getBlockX() - 1,
            claim.lesserBoundaryCorner.getBlockY(),
            claim.lesserBoundaryCorner.getBlockZ() - 1
         );
      }
   }

   private static void GuaranteeChunkLoaded(Location location) {
      Chunk chunk = location.getChunk();

      do {
         while (!chunk.isLoaded()) {
         }
      } while (!chunk.load(true));
   }

   static void sendMessage(Player player, ChatColor color, Messages messageID, String... args) {
      sendMessage(player, color, messageID, 0L, args);
   }

   static void sendMessage(Player player, ChatColor color, Messages messageID, long delayInTicks, String... args) {
      String message = instance.dataStore.getMessage(messageID, args);
      sendMessage(player, color, message, delayInTicks);
   }

   static void sendMessage(Player player, ChatColor color, String message) {
      if (player == null) {
         AddLogEntry(color + message);
      } else {
         player.sendMessage(color + message);
      }
   }

   static void sendMessage(Player player, ChatColor color, String message, long delayInTicks) {
      SendPlayerMessageTask task = new SendPlayerMessageTask(player, color, message);
      if (delayInTicks > 0L) {
         instance.getServer().getScheduler().runTaskLater(instance, task, delayInTicks);
      } else {
         task.run();
      }
   }

   boolean creativeRulesApply(Location location) {
      return this.config_claims_enabledCreativeWorlds.contains(location.getWorld());
   }

   public String allowBuild(Player player, Location location) {
      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);
      if (playerData.ignoreClaims || instance.config_mods_ignoreClaimsAccounts.contains(player.getName())) {
         return null;
      }

      if (claim != null) {
         playerData.lastClaim = claim;
         return claim.allowBuild(player);
      }

      if (this.creativeRulesApply(location)) {
         String reason = this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.CreativeBasicsDemoAdvertisement);
         if (player.hasPermission("griefprevention.ignoreclaims")) {
            reason = reason + "  " + this.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
         }

         return reason;
      } else {
         return this.config_claims_noBuildOutsideClaims && this.config_claims_enabledWorlds.contains(location.getWorld())
            ? this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.SurvivalBasicsDemoAdvertisement)
            : null;
      }
   }

   public String allowBreak(Player player, Location location) {
      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);
      if (playerData.ignoreClaims || instance.config_mods_ignoreClaimsAccounts.contains(player.getName())) {
         return null;
      }

      if (claim != null) {
         playerData.lastClaim = claim;
         return claim.allowBreak(player, location.getBlock().getType());
      }

      if (this.creativeRulesApply(location)) {
         String reason = this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.CreativeBasicsDemoAdvertisement);
         if (player.hasPermission("griefprevention.ignoreclaims")) {
            reason = reason + "  " + this.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
         }

         return reason;
      } else {
         return this.config_claims_noBuildOutsideClaims && this.config_claims_enabledWorlds.contains(location.getWorld())
            ? this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.SurvivalBasicsDemoAdvertisement)
            : null;
      }
   }

   public void restoreClaim(Claim claim, long delayInTicks) {
      if (!claim.isAdminClaim()) {
         if (claim.getArea() <= 10000) {
            Chunk lesserChunk = claim.getLesserBoundaryCorner().getChunk();
            Chunk greaterChunk = claim.getGreaterBoundaryCorner().getChunk();

            for (int x = lesserChunk.getX(); x <= greaterChunk.getX(); x++) {
               for (int z = lesserChunk.getZ(); z <= greaterChunk.getZ(); z++) {
                  Chunk chunk = lesserChunk.getWorld().getChunkAt(x, z);
                  this.restoreChunk(chunk, this.getSeaLevel(chunk.getWorld()) - 15, false, delayInTicks, null);
               }
            }
         }
      }
   }

   public void restoreChunk(Chunk chunk, int miny, boolean aggressiveMode, long delayInTicks, Player playerReceivingVisualization) {
      int maxHeight = chunk.getWorld().getMaxHeight();
      BlockSnapshot[][][] snapshots = new BlockSnapshot[18][maxHeight][18];
      Block startBlock = chunk.getBlock(0, 0, 0);
      Location startLocation = new Location(chunk.getWorld(), startBlock.getX() - 1, 0.0, startBlock.getZ() - 1);

      for (int x = 0; x < snapshots.length; x++) {
         for (int z = 0; z < snapshots[0][0].length; z++) {
            for (int y = 0; y < snapshots[0].length; y++) {
               Block block = chunk.getWorld().getBlockAt(startLocation.getBlockX() + x, startLocation.getBlockY() + y, startLocation.getBlockZ() + z);
               snapshots[x][y][z] = new BlockSnapshot(block.getLocation(), block.getTypeId(), block.getData());
            }
         }
      }

      Location lesserBoundaryCorner = chunk.getBlock(0, 0, 0).getLocation();
      Location greaterBoundaryCorner = chunk.getBlock(15, 0, 15).getLocation();
      RestoreNatureProcessingTask task = new RestoreNatureProcessingTask(
         snapshots,
         miny,
         chunk.getWorld().getEnvironment(),
         lesserBoundaryCorner.getBlock().getBiome(),
         lesserBoundaryCorner,
         greaterBoundaryCorner,
         this.getSeaLevel(chunk.getWorld()),
         aggressiveMode,
         instance.creativeRulesApply(lesserBoundaryCorner),
         playerReceivingVisualization
      );
      instance.getServer().getScheduler().runTaskLaterAsynchronously(instance, task, delayInTicks);
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

   public int getSeaLevel(World world) {
      Integer overrideValue = this.config_seaLevelOverride.get(world.getName());
      return overrideValue != null && overrideValue != -1 ? overrideValue : world.getSeaLevel();
   }
}
