package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.PoweredMinecart;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

class PlayerEventHandler implements Listener {
   private DataStore dataStore;
   private ArrayList<IpBanInfo> tempBannedIps = new ArrayList<>();
   private final long MILLISECONDS_IN_DAY = 86400000L;
   private ArrayList<Long> recentLoginLogoutNotifications = new ArrayList<>();
   private Pattern howToClaimPattern = null;

   PlayerEventHandler(DataStore dataStore, GriefPrevention plugin) {
      this.dataStore = dataStore;
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   synchronized void onPlayerChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      if (!player.isOnline()) {
         event.setCancelled(true);
      } else {
         String message = event.getMessage();
         event.setCancelled(this.handlePlayerChat(player, message, event));
      }
   }

   private boolean handlePlayerChat(Player player, String message, PlayerEvent event) {
      if (this.howToClaimPattern == null) {
         this.howToClaimPattern = Pattern.compile(this.dataStore.getMessage(Messages.HowToClaimRegex), 2);
      }

      if (this.howToClaimPattern.matcher(message).matches()) {
         if (GriefPrevention.instance.creativeRulesApply(player.getLocation())) {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.CreativeBasicsDemoAdvertisement, 10L);
         } else {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.SurvivalBasicsDemoAdvertisement, 10L);
         }
      }

      if (!message.contains("/trapped")
         && (message.contains("trapped") || message.contains("stuck") || message.contains(this.dataStore.getMessage(Messages.TrappedChatKeyword)))) {
         GriefPrevention.sendMessage(player, TextMode.Info, Messages.TrappedInstructions, 10L);
      }

      if (!GriefPrevention.instance.config_spam_enabled) {
         return false;
      }

      if (player.hasPermission("griefprevention.spam")) {
         return false;
      }

      boolean spam = false;
      boolean muted = false;
      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      if (message.length() > 4 && this.stringsAreSimilar(message.toUpperCase(), message) && event instanceof AsyncPlayerChatEvent && !message.contains("/")) {
         ((AsyncPlayerChatEvent)event).setMessage(message.toLowerCase());
      }

      message = message.toLowerCase();
      long millisecondsSinceLastMessage = new Date().getTime() - playerData.lastMessageTimestamp.getTime();
      if (millisecondsSinceLastMessage < 1500L) {
         playerData.spamCount++;
         spam = true;
      }

      if (!muted && this.stringsAreSimilar(message, playerData.lastMessage)) {
         playerData.spamCount++;
         spam = true;
         muted = true;
      }

      if (!muted) {
         Pattern ipAddressPattern = Pattern.compile("\\d{1,4}\\D{1,3}\\d{1,4}\\D{1,3}\\d{1,4}\\D{1,3}\\d{1,4}");
         Matcher matcher = ipAddressPattern.matcher(message);
         if (matcher.find() && !GriefPrevention.instance.config_spam_allowedIpAddresses.contains(matcher.group())) {
            GriefPrevention.AddLogEntry("Muted IP address from " + player.getName() + ": " + message);
            playerData.spamCount++;
            spam = true;
            muted = true;
         }
      }

      if (!muted && message.length() > 5) {
         int symbolsCount = 0;
         int whitespaceCount = 0;

         for (int i = 0; i < message.length(); i++) {
            char character = message.charAt(i);
            if (!Character.isLetterOrDigit(character)) {
               symbolsCount++;
            }

            if (Character.isWhitespace(character)) {
               whitespaceCount++;
            }
         }

         if (symbolsCount > message.length() / 2 || message.length() > 15 && whitespaceCount < message.length() / 10) {
            spam = true;
            if (playerData.spamCount > 0) {
               muted = true;
            }

            playerData.spamCount++;
         }
      }

      if (!muted && message.length() < 5 && millisecondsSinceLastMessage < 3000L) {
         spam = true;
         playerData.spamCount++;
      }

      if (spam) {
         if (playerData.spamCount > 8 && playerData.spamWarned) {
            if (GriefPrevention.instance.config_spam_banOffenders) {
               GriefPrevention.AddLogEntry("Banning " + player.getName() + " for spam.");
               PlayerKickBanTask task = new PlayerKickBanTask(player, GriefPrevention.instance.config_spam_banMessage);
               GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 1L);
            } else {
               GriefPrevention.AddLogEntry("Banning " + player.getName() + " for spam.");
               PlayerKickBanTask task = new PlayerKickBanTask(player, null);
               GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 1L);
            }

            return true;
         }

         if (playerData.spamCount >= 4) {
            muted = true;
            if (!playerData.spamWarned) {
               GriefPrevention.sendMessage(player, TextMode.Warn, GriefPrevention.instance.config_spam_warningMessage, 10L);
               GriefPrevention.AddLogEntry("Warned " + player.getName() + " about spam penalties.");
               playerData.spamWarned = true;
            }
         }

         if (muted) {
            GriefPrevention.AddLogEntry("Muted spam from " + player.getName() + ": " + message);
            player.sendMessage("<" + player.getName() + "> " + message);
            return true;
         }
      } else {
         playerData.spamCount = 0;
         playerData.spamWarned = false;
      }

      playerData.lastMessageTimestamp = new Date();
      playerData.lastMessage = message;
      return false;
   }

   private boolean stringsAreSimilar(String message, String lastMessage) {
      String shorterString;
      String longerString;
      if (lastMessage.length() < message.length()) {
         shorterString = lastMessage;
         longerString = message;
      } else {
         shorterString = message;
         longerString = lastMessage;
      }

      if (shorterString.length() <= 5) {
         return shorterString.equals(longerString);
      }

      int maxIdenticalCharacters = longerString.length() - longerString.length() / 4;
      if (shorterString.length() < maxIdenticalCharacters) {
         return false;
      }

      int identicalCount = 0;

      for (int i = 0; i < shorterString.length(); i++) {
         if (shorterString.charAt(i) == longerString.charAt(i)) {
            identicalCount++;
         }

         if (identicalCount > maxIdenticalCharacters) {
            return true;
         }
      }

      for (int i = 0; i < shorterString.length(); i++) {
         if (shorterString.charAt(shorterString.length() - i - 1) == longerString.charAt(longerString.length() - i - 1)) {
            identicalCount++;
         }

         if (identicalCount > maxIdenticalCharacters) {
            return true;
         }
      }

      return false;
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   synchronized void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
      String[] args = event.getMessage().split(" ");
      String command = args[0].toLowerCase();
      if (GriefPrevention.instance.config_eavesdrop
         && GriefPrevention.instance.config_eavesdrop_whisperCommands.contains(command)
         && !event.getPlayer().hasPermission("griefprevention.eavesdrop")
         && args.length > 1) {
         StringBuilder logMessageBuilder = new StringBuilder();
         logMessageBuilder.append("[[").append(event.getPlayer().getName()).append("]] ");

         for (int i = 1; i < args.length; i++) {
            logMessageBuilder.append(args[i]).append(" ");
         }

         String logMessage = logMessageBuilder.toString();
         Player[] players = GriefPrevention.instance.getServer().getOnlinePlayers();

         for (int i = 0; i < players.length; i++) {
            Player player = players[i];
            if (player.hasPermission("griefprevention.eavesdrop") && !player.getName().equalsIgnoreCase(args[1])) {
               player.sendMessage(ChatColor.GRAY + logMessage);
            }
         }
      }

      PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getName());
      if ((playerData.inPvpCombat() || playerData.siegeData != null) && GriefPrevention.instance.config_pvp_blockedCommands.contains(command)) {
         event.setCancelled(true);
         GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, Messages.CommandBannedInPvP);
      } else if (GriefPrevention.instance.config_spam_enabled) {
         boolean isMonitoredCommand = false;

         for (String monitoredCommand : GriefPrevention.instance.config_spam_monitorSlashCommands) {
            if (args[0].equalsIgnoreCase(monitoredCommand)) {
               isMonitoredCommand = true;
               break;
            }
         }

         if (isMonitoredCommand) {
            event.setCancelled(this.handlePlayerChat(event.getPlayer(), event.getMessage(), event));
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   void onPlayerLogin(PlayerLoginEvent event) {
      Player player = event.getPlayer();
      if (GriefPrevention.instance.config_spam_enabled) {
         if (GriefPrevention.instance.config_spam_loginCooldownMinutes > 0 && event.getResult() == Result.ALLOWED) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            long millisecondsSinceLastLogin = new Date().getTime() - playerData.lastLogin.getTime();
            long minutesSinceLastLogin = millisecondsSinceLastLogin / 1000L / 60L;
            long cooldownRemaining = GriefPrevention.instance.config_spam_loginCooldownMinutes - minutesSinceLastLogin;
            if (cooldownRemaining > 0L && !player.hasPermission("griefprevention.spam")) {
               event.setResult(Result.KICK_OTHER);
               event.setKickMessage("You must wait " + cooldownRemaining + " more minutes before logging-in again.");
               event.disallow(event.getResult(), event.getKickMessage());
               return;
            }
         }

         long now = Calendar.getInstance().getTimeInMillis();
         if (GriefPrevention.instance.config_smartBan && event.getResult() == Result.KICK_BANNED) {
            this.tempBannedIps.add(new IpBanInfo(event.getAddress(), now + 86400000L, player.getName()));
         }
      }

      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      playerData.ipAddress = event.getAddress();
   }

   @EventHandler(ignoreCancelled = true)
   void onPlayerRespawn(PlayerRespawnEvent event) {
      PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(event.getPlayer().getName());
      playerData.lastSpawn = Calendar.getInstance().getTimeInMillis();
      GriefPrevention.instance.checkPvpProtectionNeeded(event.getPlayer());
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
   void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      String playerName = player.getName();
      long now = Calendar.getInstance().getTimeInMillis();
      PlayerData playerData = this.dataStore.getPlayerData(playerName);
      playerData.lastSpawn = now;
      playerData.lastLogin = new Date();
      this.dataStore.savePlayerData(playerName, playerData);
      if (!player.hasPlayedBefore()) {
         GriefPrevention.instance.checkPvpProtectionNeeded(player);
      }

      if (event.getJoinMessage() != null && this.shouldSilenceNotification()) {
         event.setJoinMessage(null);
      }

      if (GriefPrevention.instance.config_smartBan && !player.hasPlayedBefore()) {
         for (int i = 0; i < this.tempBannedIps.size(); i++) {
            IpBanInfo info = this.tempBannedIps.get(i);
            String address = info.address.toString();
            if (now > info.expirationTimestamp) {
               this.tempBannedIps.remove(i--);
            } else if (address.equals(playerData.ipAddress.toString())) {
               OfflinePlayer bannedPlayer = GriefPrevention.instance.getServer().getOfflinePlayer(info.bannedAccountName);
               if (!bannedPlayer.isBanned()) {
                  for (int j = 0; j < this.tempBannedIps.size(); j++) {
                     IpBanInfo info2 = this.tempBannedIps.get(j);
                     if (info2.address.toString().equals(address)) {
                        OfflinePlayer bannedAccount = GriefPrevention.instance.getServer().getOfflinePlayer(info2.bannedAccountName);
                        bannedAccount.setBanned(false);
                        this.tempBannedIps.remove(j--);
                     }
                  }
               } else {
                  GriefPrevention.AddLogEntry(
                     "Auto-banned "
                        + player.getName()
                        + " because that account is using an IP address very recently used by banned player "
                        + info.bannedAccountName
                        + " ("
                        + info.address.toString()
                        + ")."
                  );
                  Player[] players = GriefPrevention.instance.getServer().getOnlinePlayers();

                  for (int k = 0; k < players.length; k++) {
                     if (players[k].isOp()) {
                        GriefPrevention.sendMessage(players[k], TextMode.Success, Messages.AutoBanNotify, player.getName(), info.bannedAccountName);
                     }
                  }

                  PlayerKickBanTask task = new PlayerKickBanTask(player, "");
                  GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 10L);
                  event.setJoinMessage("");
               }
               break;
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   void onPlayerDeath(PlayerDeathEvent event) {
      PlayerData playerData = this.dataStore.getPlayerData(event.getEntity().getName());
      long now = Calendar.getInstance().getTimeInMillis();
      if (now - playerData.lastDeathTimeStamp < GriefPrevention.instance.config_spam_deathMessageCooldownSeconds * 1000) {
         event.setDeathMessage("");
      }

      playerData.lastDeathTimeStamp = now;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      if (player.isBanned() && playerData.ipAddress != null) {
         long now = Calendar.getInstance().getTimeInMillis();
         this.tempBannedIps.add(new IpBanInfo(playerData.ipAddress, now + 86400000L, player.getName()));
      }

      if (event.getQuitMessage() != null && this.shouldSilenceNotification()) {
         event.setQuitMessage(null);
      }

      this.dataStore.savePlayerData(player.getName(), playerData);
      this.onPlayerDisconnect(event.getPlayer(), event.getQuitMessage());
   }

   private void onPlayerDisconnect(Player player, String notificationMessage) {
      String playerName = player.getName();
      PlayerData playerData = this.dataStore.getPlayerData(playerName);

      for (Claim claim : playerData.claims) {
         claim.areExplosivesAllowed = false;
      }

      if (GriefPrevention.instance.config_pvp_punishLogout && playerData.inPvpCombat()) {
         player.setHealth(0);
      }

      if (playerData.siegeData != null && player.getHealth() > 0) {
         player.setHealth(0);
      }

      this.dataStore.clearCachedPlayerData(player.getName());
   }

   private boolean shouldSilenceNotification() {
      long ONE_MINUTE = 60000L;
      int MAX_ALLOWED = 20;
      Long now = Calendar.getInstance().getTimeInMillis();

      for (int i = 0; i < this.recentLoginLogoutNotifications.size(); i++) {
         Long notificationTimestamp = this.recentLoginLogoutNotifications.get(i);
         if (now - notificationTimestamp <= 60000L) {
            break;
         }

         this.recentLoginLogoutNotifications.remove(i--);
      }

      this.recentLoginLogoutNotifications.add(now);
      return this.recentLoginLogoutNotifications.size() > 20;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onPlayerDropItem(PlayerDropItemEvent event) {
      Player player = event.getPlayer();
      if (GriefPrevention.instance.creativeRulesApply(player.getLocation())) {
         event.setCancelled(true);
      } else {
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         if (!GriefPrevention.instance.config_pvp_allowCombatItemDrop && playerData.inPvpCombat()) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.PvPNoDrop);
            event.setCancelled(true);
         } else if (playerData.siegeData != null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeNoDrop);
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onPlayerTeleport(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      if (event.getCause() == TeleportCause.ENDER_PEARL && GriefPrevention.instance.config_claims_enderPearlsRequireAccessTrust) {
         Claim toClaim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
         if (toClaim != null) {
            playerData.lastClaim = toClaim;
            String noAccessReason = toClaim.allowAccess(player);
            if (noAccessReason != null) {
               GriefPrevention.sendMessage(player, TextMode.Err, noAccessReason);
               event.setCancelled(true);
            }
         }
      }

      if (event.getCause() != TeleportCause.ENDER_PEARL) {
         Location source = event.getFrom();
         Claim sourceClaim = this.dataStore.getClaimAt(source, false, playerData.lastClaim);
         if (sourceClaim != null && sourceClaim.siegeData != null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeNoTeleport);
            event.setCancelled(true);
         } else {
            Location destination = event.getTo();
            Claim destinationClaim = this.dataStore.getClaimAt(destination, false, null);
            if (destinationClaim != null && destinationClaim.siegeData != null) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.BesiegedNoTeleport);
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
      Player player = event.getPlayer();
      Entity entity = event.getRightClicked();
      PlayerData playerData = this.dataStore.getPlayerData(player.getName());
      if (entity instanceof Hanging) {
         String noBuildReason = GriefPrevention.instance.allowBuild(player, entity.getLocation());
         if (noBuildReason != null) {
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
            event.setCancelled(true);
            return;
         }
      }

      if (entity instanceof StorageMinecart || entity instanceof PoweredMinecart) {
         if (playerData.siegeData != null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeNoContainers);
            event.setCancelled(true);
            return;
         }

         if (playerData.inPvpCombat()) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.PvPNoContainers);
            event.setCancelled(true);
            return;
         }
      }

      if (GriefPrevention.instance.config_claims_preventTheft && entity instanceof Vehicle) {
         Claim claim = this.dataStore.getClaimAt(entity.getLocation(), false, null);
         if (claim != null) {
            if (entity instanceof StorageMinecart || entity instanceof PoweredMinecart) {
               String noContainersReason = claim.allowContainers(player);
               if (noContainersReason != null) {
                  GriefPrevention.sendMessage(player, TextMode.Err, noContainersReason);
                  event.setCancelled(true);
               }
            } else if (entity instanceof Boat) {
               String noAccessReason = claim.allowAccess(player);
               if (noAccessReason != null) {
                  player.sendMessage(noAccessReason);
                  event.setCancelled(true);
               }
            } else if (entity instanceof Animals && claim.allowContainers(player) != null) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoDamageClaimedEntity);
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onPlayerPickupItem(PlayerPickupItemEvent event) {
      Player player = event.getPlayer();
      if (event.getPlayer().getWorld().getPVP()) {
         if (GriefPrevention.instance.config_pvp_protectFreshSpawns && player.getItemInHand().getType() == Material.AIR) {
            PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getName());
            if (playerData.pvpImmune) {
               long now = Calendar.getInstance().getTimeInMillis();
               long elapsedSinceLastSpawn = now - playerData.lastSpawn;
               if (elapsedSinceLastSpawn < 10000L) {
                  event.setCancelled(true);
                  return;
               }

               playerData.pvpImmune = false;
               GriefPrevention.sendMessage(player, TextMode.Warn, Messages.PvPImmunityEnd);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onItemHeldChange(PlayerItemHeldEvent event) {
      Player player = event.getPlayer();
      ItemStack newItemStack = player.getInventory().getItem(event.getNewSlot());
      if (newItemStack != null && newItemStack.getType() == GriefPrevention.instance.config_claims_modificationTool) {
         PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getName());
         if (playerData.shovelMode != ShovelMode.Basic) {
            playerData.shovelMode = ShovelMode.Basic;
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.ShovelBasicClaimMode);
         }

         playerData.lastShovelLocation = null;
         playerData.claimResizing = null;
         if (GriefPrevention.instance.claimsEnabledForWorld(player.getWorld())) {
            EquipShovelProcessingTask task = new EquipShovelProcessingTask(player);
            GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 15L);
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onPlayerBedEnter(PlayerBedEnterEvent bedEvent) {
      if (GriefPrevention.instance.config_claims_preventButtonsSwitches) {
         Player player = bedEvent.getPlayer();
         Block block = bedEvent.getBed();
         Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, null);
         if (claim != null && claim.allowAccess(player) != null) {
            bedEvent.setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoBedPermission, claim.getOwnerName());
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onEntropyItemUse(PlayerInteractEvent interactEvent) {
      if (interactEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Player player = interactEvent.getPlayer();
         Block clickedBlock = interactEvent.getClickedBlock();
         int itemID = player.getInventory().getItemInHand().getType().getId();
         if (itemID == 4363 || itemID == 4364) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim == null || !claim.allowAccess(player).isEmpty()) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.Entropy);
               interactEvent.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onMiniumItemUse(PlayerInteractEvent interactEvent) {
      if (interactEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Player player = interactEvent.getPlayer();
         Block clickedBlock = interactEvent.getClickedBlock();
         int itemID = player.getInventory().getItemInHand().getType().getId();
         if (itemID == 27002) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim == null || !claim.allowAccess(player).isEmpty()) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.Minium);
               interactEvent.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onWrathIgniterItemUse(PlayerInteractEvent interactEvent) {
      if (interactEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Player player = interactEvent.getPlayer();
         Block clickedBlock = interactEvent.getClickedBlock();
         int itemID = player.getInventory().getItemInHand().getType().getId();
         if (itemID == 19263) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getName());
            Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null && claim.allowAccess(player).isEmpty()) {
               Location playerLocation = player.getLocation();
               Location claimCenter = claim.getLesserBoundaryCorner().add(claim.getGreaterBoundaryCorner()).multiply(0.5);
               double distance = playerLocation.distance(claimCenter);
               if (distance < 10.0) {
                  GriefPrevention.sendMessage(player, TextMode.Err, Messages.WrathIgnitersBorder);
                  interactEvent.setCancelled(true);
               }
            } else {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.WrathIgniters);
               interactEvent.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onPlayerBucketEmpty(PlayerBucketEmptyEvent bucketEvent) {
      Player player = bucketEvent.getPlayer();
      Block block = bucketEvent.getBlockClicked().getRelative(bucketEvent.getBlockFace());
      int minLavaDistance = 10;
      String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation());
      if (noBuildReason != null) {
         GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
         bucketEvent.setCancelled(true);
      } else {
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, playerData.lastClaim);
         if (claim != null) {
            minLavaDistance = 3;
         } else if (GriefPrevention.instance.config_claims_enabledWorlds.contains(block.getWorld())
            && block.getY() >= GriefPrevention.instance.getSeaLevel(block.getWorld()) - 5
            && !player.hasPermission("griefprevention.lava")
            && (bucketEvent.getBucket() == Material.LAVA_BUCKET || GriefPrevention.instance.config_blockWildernessWaterBuckets)) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoWildernessBuckets);
            bucketEvent.setCancelled(true);
            return;
         }

         if (!GriefPrevention.instance.config_pvp_enabledWorlds.contains(block.getWorld())
            && !player.hasPermission("griefprevention.lava")
            && bucketEvent.getBucket() == Material.LAVA_BUCKET) {
            List<Player> players = block.getWorld().getPlayers();

            for (int i = 0; i < players.size(); i++) {
               Player otherPlayer = players.get(i);
               Location location = otherPlayer.getLocation();
               if (!otherPlayer.equals(player)
                  && block.getY() >= location.getBlockY() - 1
                  && location.distanceSquared(block.getLocation()) < minLavaDistance * minLavaDistance) {
                  GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoLavaNearOtherPlayer, otherPlayer.getName());
                  bucketEvent.setCancelled(true);
                  return;
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onPlayerBucketFill(PlayerBucketFillEvent bucketEvent) {
      Player player = bucketEvent.getPlayer();
      Block block = bucketEvent.getBlockClicked();
      String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation());
      if (noBuildReason != null) {
         GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
         bucketEvent.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   void onPlayerInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block clickedBlock = null;

      try {
         clickedBlock = event.getClickedBlock();
         if (clickedBlock == null || clickedBlock.getType() == Material.SNOW) {
            HashSet<Byte> transparentMaterials = new HashSet<>();
            transparentMaterials.add((byte)Material.AIR.getId());
            transparentMaterials.add((byte)Material.SNOW.getId());
            transparentMaterials.add((byte)Material.LONG_GRASS.getId());
            clickedBlock = player.getTargetBlock(transparentMaterials, 250);
         }
      } catch (Exception e) {
         return;
      }

      if (clickedBlock != null) {
         Material clickedBlockType = clickedBlock.getType();
         PlayerData playerData = this.dataStore.getPlayerData(player.getName());
         if (event.getClickedBlock() != null && event.getClickedBlock().getRelative(event.getBlockFace()).getType() == Material.FIRE) {
            Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
               playerData.lastClaim = claim;
               String noBuildReason = claim.allowBuild(player);
               if (noBuildReason != null) {
                  event.setCancelled(true);
                  GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                  return;
               }
            }
         }

         if (!GriefPrevention.instance.config_claims_preventTheft
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || !(clickedBlock.getState() instanceof InventoryHolder)
               && clickedBlockType != Material.WORKBENCH
               && clickedBlockType != Material.ENDER_CHEST
               && clickedBlockType != Material.DISPENSER
               && clickedBlockType != Material.ANVIL
               && clickedBlockType != Material.BREWING_STAND
               && clickedBlockType != Material.JUKEBOX
               && clickedBlockType != Material.ENCHANTMENT_TABLE
               && !GriefPrevention.instance.config_mods_containerTrustIds.Contains(new MaterialInfo(clickedBlock.getTypeId(), clickedBlock.getData(), null))) {
            if ((!GriefPrevention.instance.config_claims_lockWoodenDoors || clickedBlockType != Material.WOODEN_DOOR)
               && (!GriefPrevention.instance.config_claims_lockTrapDoors || clickedBlockType != Material.TRAP_DOOR)
               && (!GriefPrevention.instance.config_claims_lockFenceGates || clickedBlockType != Material.FENCE_GATE)) {
               if (!GriefPrevention.instance.config_claims_preventButtonsSwitches
                  || clickedBlockType != null
                     && clickedBlockType != Material.STONE_BUTTON
                     && clickedBlockType != Material.WOOD_BUTTON
                     && clickedBlockType != Material.LEVER
                     && !GriefPrevention.instance.config_mods_accessTrustIds.Contains(new MaterialInfo(clickedBlock.getTypeId(), clickedBlock.getData(), null))
                  )
                {
                  if (event.getAction() == Action.PHYSICAL && clickedBlockType == Material.SOIL) {
                     event.setCancelled(true);
                     return;
                  }

                  if (clickedBlockType != Material.NOTE_BLOCK && clickedBlockType != Material.DIODE_BLOCK_ON && clickedBlockType != Material.DIODE_BLOCK_OFF) {
                     Action action = event.getAction();
                     if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
                        return;
                     }

                     Material materialInHand = player.getItemInHand().getType();
                     if (materialInHand == Material.INK_SACK) {
                        String noBuildReason = GriefPrevention.instance.allowBuild(player, clickedBlock.getLocation());
                        if (noBuildReason != null) {
                           GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                           event.setCancelled(true);
                        }

                        return;
                     }

                     if (materialInHand == Material.BOAT) {
                        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                        if (claim != null) {
                           String noAccessReason = claim.allowAccess(player);
                           if (noAccessReason != null) {
                              GriefPrevention.sendMessage(player, TextMode.Err, noAccessReason);
                              event.setCancelled(true);
                           }
                        }

                        return;
                     }

                     if ((
                           materialInHand == Material.MONSTER_EGG
                              || materialInHand == Material.MINECART
                              || materialInHand == Material.POWERED_MINECART
                              || materialInHand == Material.STORAGE_MINECART
                              || materialInHand == Material.BOAT
                        )
                        && GriefPrevention.instance.creativeRulesApply(clickedBlock.getLocation())) {
                        String noBuildReason = GriefPrevention.instance.allowBuild(player, clickedBlock.getLocation());
                        if (noBuildReason != null) {
                           GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                           event.setCancelled(true);
                           return;
                        }

                        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                        if (claim == null) {
                           return;
                        }

                        String noEntitiesReason = claim.allowMoreEntities();
                        if (noEntitiesReason != null) {
                           GriefPrevention.sendMessage(player, TextMode.Err, noEntitiesReason);
                           event.setCancelled(true);
                           return;
                        }

                        return;
                     }

                     if (materialInHand == GriefPrevention.instance.config_claims_investigationTool) {
                        if (clickedBlockType == Material.AIR) {
                           GriefPrevention.sendMessage(player, TextMode.Err, Messages.TooFarAway);
                           return;
                        }

                        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                        if (claim == null) {
                           GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockNotClaimed);
                           Visualization.Revert(player);
                        } else {
                           playerData.lastClaim = claim;
                           GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockClaimed, claim.getOwnerName());
                           Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation());
                           Visualization.Apply(player, visualization);
                           if (claim.allowEdit(player) == null) {
                              GriefPrevention.sendMessage(player, TextMode.Info, "  " + claim.getWidth() + "x" + claim.getHeight() + "=" + claim.getArea());
                           }

                           if (!claim.isAdminClaim() && player.hasPermission("griefprevention.deleteclaims")) {
                              PlayerData otherPlayerData = this.dataStore.getPlayerData(claim.getOwnerName());
                              Date lastLogin = otherPlayerData.lastLogin;
                              Date now = new Date();
                              long daysElapsed = (now.getTime() - lastLogin.getTime()) / 86400000L;
                              GriefPrevention.sendMessage(player, TextMode.Info, Messages.PlayerOfflineTime, String.valueOf(daysElapsed));
                              if (GriefPrevention.instance.getServer().getPlayerExact(claim.getOwnerName()) == null) {
                                 this.dataStore.clearCachedPlayerData(claim.getOwnerName());
                              }
                           }
                        }

                        return;
                     }

                     if (materialInHand != GriefPrevention.instance.config_claims_modificationTool) {
                        return;
                     }

                     if (playerData.siegeData != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeNoShovel);
                        event.setCancelled(true);
                        return;
                     }

                     if (clickedBlockType == Material.AIR) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.TooFarAway);
                        return;
                     }

                     String playerName = player.getName();
                     playerData = this.dataStore.getPlayerData(player.getName());
                     if (playerData.shovelMode == ShovelMode.RestoreNature || playerData.shovelMode == ShovelMode.RestoreNatureAggressive) {
                        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                        if (claim != null) {
                           GriefPrevention.sendMessage(player, TextMode.Err, Messages.BlockClaimed, claim.getOwnerName());
                           Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation());
                           Visualization.Apply(player, visualization);
                           return;
                        }

                        Chunk chunk = player.getWorld().getChunkAt(clickedBlock.getLocation());
                        int miny = clickedBlock.getY();
                        if (playerData.shovelMode != ShovelMode.RestoreNatureAggressive && miny > GriefPrevention.instance.getSeaLevel(chunk.getWorld()) - 10) {
                           miny = GriefPrevention.instance.getSeaLevel(chunk.getWorld()) - 10;
                        }

                        GriefPrevention.instance.restoreChunk(chunk, miny, playerData.shovelMode == ShovelMode.RestoreNatureAggressive, 0L, player);
                        return;
                     }

                     if (playerData.shovelMode == ShovelMode.RestoreNatureFill) {
                        ArrayList<Material> allowedFillBlocks = new ArrayList<>();
                        Environment environment = clickedBlock.getWorld().getEnvironment();
                        if (environment == Environment.NETHER) {
                           allowedFillBlocks.add(Material.NETHERRACK);
                        } else if (environment == Environment.THE_END) {
                           allowedFillBlocks.add(Material.ENDER_STONE);
                        } else {
                           allowedFillBlocks.add(Material.GRASS);
                           allowedFillBlocks.add(Material.DIRT);
                           allowedFillBlocks.add(Material.STONE);
                           allowedFillBlocks.add(Material.SAND);
                           allowedFillBlocks.add(Material.SANDSTONE);
                           allowedFillBlocks.add(Material.ICE);
                        }

                        Block centerBlock = clickedBlock;
                        int maxHeight = centerBlock.getY();
                        int minx = centerBlock.getX() - playerData.fillRadius;
                        int maxx = centerBlock.getX() + playerData.fillRadius;
                        int minz = centerBlock.getZ() - playerData.fillRadius;
                        int maxz = centerBlock.getZ() + playerData.fillRadius;
                        int minHeight = maxHeight - 10;
                        if (minHeight < 0) {
                           minHeight = 0;
                        }

                        Claim cachedClaim = null;

                        for (int x = minx; x <= maxx; x++) {
                           for (int z = minz; z <= maxz; z++) {
                              Location location = new Location(centerBlock.getWorld(), x, centerBlock.getY(), z);
                              if (!(location.distance(centerBlock.getLocation()) > playerData.fillRadius)) {
                                 Material defaultFiller = allowedFillBlocks.get(0);
                                 if (allowedFillBlocks.contains(centerBlock.getType())) {
                                    defaultFiller = centerBlock.getType();
                                 } else if (centerBlock.getType() == Material.WATER || centerBlock.getType() == Material.STATIONARY_WATER) {
                                    Block block = centerBlock.getWorld().getBlockAt(centerBlock.getLocation());

                                    while (!allowedFillBlocks.contains(block.getType()) && block.getY() > centerBlock.getY() - 10) {
                                       block = block.getRelative(BlockFace.DOWN);
                                    }

                                    if (allowedFillBlocks.contains(block.getType())) {
                                       defaultFiller = block.getType();
                                    }
                                 }

                                 for (int y = minHeight; y <= maxHeight; y++) {
                                    Block block = centerBlock.getWorld().getBlockAt(x, y, z);
                                    Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, cachedClaim);
                                    if (claim != null) {
                                       cachedClaim = claim;
                                       break;
                                    }

                                    if (block.getType() == Material.AIR
                                       || block.getType() == Material.SNOW
                                       || block.getType() == Material.STATIONARY_WATER && block.getData() != 0
                                       || block.getType() == Material.LONG_GRASS) {
                                       if (y == maxHeight) {
                                          block.setType(defaultFiller);
                                       } else {
                                          Block eastBlock = block.getRelative(BlockFace.EAST);
                                          Block westBlock = block.getRelative(BlockFace.WEST);
                                          Block northBlock = block.getRelative(BlockFace.NORTH);
                                          Block southBlock = block.getRelative(BlockFace.SOUTH);
                                          if (allowedFillBlocks.contains(eastBlock.getType())) {
                                             block.setType(eastBlock.getType());
                                          } else if (allowedFillBlocks.contains(westBlock.getType())) {
                                             block.setType(westBlock.getType());
                                          } else if (allowedFillBlocks.contains(northBlock.getType())) {
                                             block.setType(northBlock.getType());
                                          } else if (allowedFillBlocks.contains(southBlock.getType())) {
                                             block.setType(southBlock.getType());
                                          } else {
                                             block.setType(defaultFiller);
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }

                        return;
                     }

                     if (GriefPrevention.instance.config_claims_creationRequiresPermission && !player.hasPermission("griefprevention.createclaims")) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoCreateClaimPermission);
                        return;
                     }

                     if (playerData.claimResizing != null && playerData.claimResizing.inDataStore) {
                        if (clickedBlock.getLocation().equals(playerData.lastShovelLocation)) {
                           return;
                        }

                        int newx1;
                        if (playerData.lastShovelLocation.getBlockX() == playerData.claimResizing.getLesserBoundaryCorner().getBlockX()) {
                           newx1 = clickedBlock.getX();
                        } else {
                           newx1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockX();
                        }

                        int newx2;
                        if (playerData.lastShovelLocation.getBlockX() == playerData.claimResizing.getGreaterBoundaryCorner().getBlockX()) {
                           newx2 = clickedBlock.getX();
                        } else {
                           newx2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockX();
                        }

                        int newz1;
                        if (playerData.lastShovelLocation.getBlockZ() == playerData.claimResizing.getLesserBoundaryCorner().getBlockZ()) {
                           newz1 = clickedBlock.getZ();
                        } else {
                           newz1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockZ();
                        }

                        int newz2;
                        if (playerData.lastShovelLocation.getBlockZ() == playerData.claimResizing.getGreaterBoundaryCorner().getBlockZ()) {
                           newz2 = clickedBlock.getZ();
                        } else {
                           newz2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockZ();
                        }

                        int newy1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockY();
                        int newy2 = clickedBlock.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance;
                        if (playerData.claimResizing.parent == null) {
                           int newWidth = Math.abs(newx1 - newx2) + 1;
                           int newHeight = Math.abs(newz1 - newz2) + 1;
                           if (!playerData.claimResizing.isAdminClaim()
                              && (newWidth < GriefPrevention.instance.config_claims_minSize || newHeight < GriefPrevention.instance.config_claims_minSize)) {
                              GriefPrevention.sendMessage(
                                 player, TextMode.Err, Messages.ResizeClaimTooSmall, String.valueOf(GriefPrevention.instance.config_claims_minSize)
                              );
                              return;
                           }

                           if (!playerData.claimResizing.isAdminClaim() && player.getName().equals(playerData.claimResizing.getOwnerName())) {
                              int newArea = newWidth * newHeight;
                              int blocksRemainingAfter = playerData.getRemainingClaimBlocks() + playerData.claimResizing.getArea() - newArea;
                              if (blocksRemainingAfter < 0) {
                                 GriefPrevention.sendMessage(
                                    player, TextMode.Err, Messages.ResizeNeedMoreBlocks, String.valueOf(Math.abs(blocksRemainingAfter))
                                 );
                                 return;
                              }
                           }
                        }

                        Claim oldClaim = playerData.claimResizing;
                        boolean smaller = false;
                        if (oldClaim.parent == null) {
                           Claim newClaim = new Claim(
                              new Location(oldClaim.getLesserBoundaryCorner().getWorld(), newx1, newy1, newz1),
                              new Location(oldClaim.getLesserBoundaryCorner().getWorld(), newx2, newy2, newz2),
                              "",
                              new String[0],
                              new String[0],
                              new String[0],
                              new String[0],
                              null
                           );
                           if (!newClaim.contains(oldClaim.getLesserBoundaryCorner(), true, false)
                              || !newClaim.contains(oldClaim.getGreaterBoundaryCorner(), true, false)) {
                              smaller = true;
                              if (!GriefPrevention.instance.config_claims_allowUnclaimInCreative
                                 && !player.hasPermission("griefprevention.deleteclaims")
                                 && GriefPrevention.instance.creativeRulesApply(player.getLocation())) {
                                 GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoCreativeUnClaim);
                                 return;
                              }

                              oldClaim.removeSurfaceFluids(newClaim);
                           }
                        }

                        CreateClaimResult result = GriefPrevention.instance
                           .dataStore
                           .resizeClaim(playerData.claimResizing, newx1, newx2, newy1, newy2, newz1, newz2);
                        if (result.succeeded) {
                           GriefPrevention.sendMessage(
                              player, TextMode.Success, Messages.ClaimResizeSuccess, String.valueOf(playerData.getRemainingClaimBlocks())
                           );
                           Visualization visualization = Visualization.FromClaim(
                              result.claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation()
                           );
                           Visualization.Apply(player, visualization);
                           if (!playerData.claimResizing.ownerName.equals(playerName)) {
                              GriefPrevention.AddLogEntry(
                                 playerName
                                    + " resized "
                                    + playerData.claimResizing.getOwnerName()
                                    + "'s claim at "
                                    + GriefPrevention.getfriendlyLocationString(playerData.claimResizing.lesserBoundaryCorner)
                                    + "."
                              );
                           }

                           if (smaller
                              && GriefPrevention.instance.config_claims_autoRestoreUnclaimedCreativeLand
                              && GriefPrevention.instance.creativeRulesApply(oldClaim.getLesserBoundaryCorner())) {
                              GriefPrevention.sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
                              GriefPrevention.instance.restoreClaim(oldClaim, 2400L);
                              GriefPrevention.AddLogEntry(
                                 player.getName()
                                    + " shrank a claim @ "
                                    + GriefPrevention.getfriendlyLocationString(playerData.claimResizing.getLesserBoundaryCorner())
                              );
                           }

                           playerData.claimResizing = null;
                           playerData.lastShovelLocation = null;
                        } else {
                           GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlap);
                           Visualization visualization = Visualization.FromClaim(
                              result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation()
                           );
                           Visualization.Apply(player, visualization);
                        }

                        return;
                     }

                     Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), true, playerData.lastClaim);
                     if (claim != null) {
                        String noEditReason = claim.allowEdit(player);
                        if (noEditReason == null) {
                           if (clickedBlock.getX() != claim.getLesserBoundaryCorner().getBlockX()
                                 && clickedBlock.getX() != claim.getGreaterBoundaryCorner().getBlockX()
                              || clickedBlock.getZ() != claim.getLesserBoundaryCorner().getBlockZ()
                                 && clickedBlock.getZ() != claim.getGreaterBoundaryCorner().getBlockZ()) {
                              if (playerData.shovelMode == ShovelMode.Subdivide) {
                                 if (playerData.lastShovelLocation == null) {
                                    if (claim.parent != null) {
                                       GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlapSubdivision);
                                    } else {
                                       GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionStart);
                                       playerData.lastShovelLocation = clickedBlock.getLocation();
                                       playerData.claimSubdividing = claim;
                                    }
                                 } else {
                                    if (!playerData.lastShovelLocation.getWorld().equals(clickedBlock.getWorld())) {
                                       playerData.lastShovelLocation = null;
                                       this.onPlayerInteract(event);
                                       return;
                                    }

                                    CreateClaimResult result = this.dataStore
                                       .createClaim(
                                          player.getWorld(),
                                          playerData.lastShovelLocation.getBlockX(),
                                          clickedBlock.getX(),
                                          playerData.lastShovelLocation.getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance,
                                          clickedBlock.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance,
                                          playerData.lastShovelLocation.getBlockZ(),
                                          clickedBlock.getZ(),
                                          "--subdivision--",
                                          playerData.claimSubdividing,
                                          null
                                       );
                                    if (!result.succeeded) {
                                       GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateSubdivisionOverlap);
                                       Visualization visualization = Visualization.FromClaim(
                                          result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation()
                                       );
                                       Visualization.Apply(player, visualization);
                                       return;
                                    }

                                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.SubdivisionSuccess);
                                    Visualization visualization = Visualization.FromClaim(
                                       result.claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation()
                                    );
                                    Visualization.Apply(player, visualization);
                                    playerData.lastShovelLocation = null;
                                    playerData.claimSubdividing = null;
                                 }
                              } else {
                                 GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlap);
                                 Visualization visualization = Visualization.FromClaim(
                                    claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation()
                                 );
                                 Visualization.Apply(player, visualization);
                              }
                           } else {
                              playerData.claimResizing = claim;
                              playerData.lastShovelLocation = clickedBlock.getLocation();
                              GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ResizeStart);
                           }
                        } else {
                           GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapOtherPlayer, claim.getOwnerName());
                           Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation());
                           Visualization.Apply(player, visualization);
                        }

                        return;
                     }

                     Location lastShovelLocation = playerData.lastShovelLocation;
                     if (lastShovelLocation == null) {
                        if (!GriefPrevention.instance.claimsEnabledForWorld(player.getWorld()) && playerData.shovelMode != ShovelMode.Admin) {
                           GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
                           return;
                        }

                        playerData.lastShovelLocation = clickedBlock.getLocation();
                        GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimStart);
                        Visualization visualization = Visualization.FromClaim(
                           new Claim(
                              clickedBlock.getLocation(), clickedBlock.getLocation(), "", new String[0], new String[0], new String[0], new String[0], null
                           ),
                           clickedBlock.getY(),
                           VisualizationType.RestoreNature,
                           player.getLocation()
                        );
                        Visualization.Apply(player, visualization);
                     } else {
                        if (!lastShovelLocation.getWorld().equals(clickedBlock.getWorld())) {
                           playerData.lastShovelLocation = null;
                           this.onPlayerInteract(event);
                           return;
                        }

                        int newClaimWidth = Math.abs(playerData.lastShovelLocation.getBlockX() - clickedBlock.getX()) + 1;
                        int newClaimHeight = Math.abs(playerData.lastShovelLocation.getBlockZ() - clickedBlock.getZ()) + 1;
                        if (playerData.shovelMode != ShovelMode.Admin
                           && (
                              newClaimWidth < GriefPrevention.instance.config_claims_minSize || newClaimHeight < GriefPrevention.instance.config_claims_minSize
                           )) {
                           GriefPrevention.sendMessage(
                              player, TextMode.Err, Messages.NewClaimTooSmall, String.valueOf(GriefPrevention.instance.config_claims_minSize)
                           );
                           return;
                        }

                        if (playerData.shovelMode != ShovelMode.Admin) {
                           int newClaimArea = newClaimWidth * newClaimHeight;
                           int remainingBlocks = playerData.getRemainingClaimBlocks();
                           if (newClaimArea > remainingBlocks) {
                              GriefPrevention.sendMessage(
                                 player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(newClaimArea - remainingBlocks)
                              );
                              GriefPrevention.sendMessage(player, TextMode.Instr, Messages.AbandonClaimAdvertisement);
                              return;
                           }
                        } else {
                           playerName = "";
                        }

                        CreateClaimResult result = this.dataStore
                           .createClaim(
                              player.getWorld(),
                              lastShovelLocation.getBlockX(),
                              clickedBlock.getX(),
                              lastShovelLocation.getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance,
                              clickedBlock.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance,
                              lastShovelLocation.getBlockZ(),
                              clickedBlock.getZ(),
                              playerName,
                              null,
                              null
                           );
                        if (!result.succeeded) {
                           GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);
                           Visualization visualization = Visualization.FromClaim(
                              result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim, player.getLocation()
                           );
                           Visualization.Apply(player, visualization);
                           return;
                        }

                        GriefPrevention.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);
                        Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.Claim, player.getLocation());
                        Visualization.Apply(player, visualization);
                        playerData.lastShovelLocation = null;
                     }
                  } else {
                     Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                     if (claim != null) {
                        String noBuildReason = claim.allowBuild(player);
                        if (noBuildReason != null) {
                           event.setCancelled(true);
                           GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                           return;
                        }
                     }
                  }
               } else {
                  Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
                  if (claim != null) {
                     playerData.lastClaim = claim;
                     String noAccessReason = claim.allowAccess(player);
                     if (noAccessReason != null) {
                        event.setCancelled(true);
                        GriefPrevention.sendMessage(player, TextMode.Err, noAccessReason);
                        return;
                     }
                  }
               }
            } else {
               Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
               if (claim != null) {
                  playerData.lastClaim = claim;
                  String noAccessReason = claim.allowAccess(player);
                  if (noAccessReason != null) {
                     event.setCancelled(true);
                     GriefPrevention.sendMessage(player, TextMode.Err, noAccessReason);
                     return;
                  }
               }
            }
         } else {
            if (playerData.siegeData != null) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeNoContainers);
               event.setCancelled(true);
               return;
            }

            if (playerData.inPvpCombat()) {
               GriefPrevention.sendMessage(player, TextMode.Err, Messages.PvPNoContainers);
               event.setCancelled(true);
               return;
            }

            Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
            if (claim != null) {
               playerData.lastClaim = claim;
               String noContainersReason = claim.allowContainers(player);
               if (noContainersReason != null) {
                  event.setCancelled(true);
                  GriefPrevention.sendMessage(player, TextMode.Err, noContainersReason);
                  return;
               }
            }

            if (playerData.pvpImmune) {
               playerData.pvpImmune = false;
               GriefPrevention.sendMessage(player, TextMode.Warn, Messages.PvPImmunityEnd);
            }
         }
      }
   }
}
