package me.ryanhamshire.GriefPrevention;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import org.bukkit.Location;

public class FlatFileDataStore extends DataStore {
   private static final String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
   private static final String claimDataFolderPath = dataLayerFolderPath + File.separator + "ClaimData";
   private static final String nextClaimIdFilePath = claimDataFolderPath + File.separator + "_nextClaimID";

   static boolean hasData() {
      File playerDataFolder = new File(playerDataFolderPath);
      File claimsDataFolder = new File(claimDataFolderPath);
      return playerDataFolder.exists() || claimsDataFolder.exists();
   }

   FlatFileDataStore() throws Exception {
      this.initialize();
   }

   @Override
   void initialize() throws Exception {
      new File(playerDataFolderPath).mkdirs();
      new File(claimDataFolderPath).mkdirs();
      File playerDataFolder = new File(playerDataFolderPath);
      File[] files = playerDataFolder.listFiles();

      for (int i = 0; i < files.length; i++) {
         File file = files[i];
         if (file.isFile() && file.getName().startsWith("$")) {
            String groupName = file.getName().substring(1);
            if (groupName != null && !groupName.isEmpty()) {
               BufferedReader inStream = null;

               try {
                  inStream = new BufferedReader(new FileReader(file.getAbsolutePath()));
                  String line = inStream.readLine();
                  int groupBonusBlocks = Integer.parseInt(line);
                  this.permissionToBonusBlocksMap.put(groupName, groupBonusBlocks);
               } catch (Exception e) {
                  GriefPrevention.AddLogEntry("Unable to load group bonus block data from file \"" + file.getName() + "\": " + e.getMessage());
               }

               try {
                  if (inStream != null) {
                     inStream.close();
                  }
               } catch (IOException var24) {
               }
            }
         }
      }

      File nextClaimIdFile = new File(nextClaimIdFilePath);
      if (nextClaimIdFile.exists()) {
         BufferedReader inStream = null;

         try {
            inStream = new BufferedReader(new FileReader(nextClaimIdFile.getAbsolutePath()));
            String line = inStream.readLine();
            this.nextClaimID = Long.parseLong(line);
         } catch (Exception var23) {
         }

         try {
            if (inStream != null) {
               inStream.close();
            }
         } catch (IOException var22) {
         }
      }

      File claimDataFolder = new File(claimDataFolderPath);
      files = claimDataFolder.listFiles();

      for (int i = 0; i < files.length; i++) {
         if (files[i].isFile() && !files[i].getName().startsWith("_")) {
            long claimID;
            try {
               claimID = Long.parseLong(files[i].getName());
            } catch (Exception e) {
               claimID = this.nextClaimID;
               this.incrementNextClaimID();
               File newFile = new File(claimDataFolderPath + File.separator + this.nextClaimID);
               files[i].renameTo(newFile);
               files[i] = newFile;
            }

            BufferedReader inStream = null;

            try {
               Claim topLevelClaim = null;
               inStream = new BufferedReader(new FileReader(files[i].getAbsolutePath()));
               String line = inStream.readLine();

               while (line != null) {
                  Location lesserBoundaryCorner = this.locationFromString(line);
                  line = inStream.readLine();
                  Location greaterBoundaryCorner = this.locationFromString(line);
                  line = inStream.readLine();
                  String ownerName = line;
                  line = inStream.readLine();
                  String[] builderNames = line.split(";");
                  line = inStream.readLine();
                  String[] containerNames = line.split(";");
                  line = inStream.readLine();
                  String[] accessorNames = line.split(";");
                  line = inStream.readLine();
                  if (line == null) {
                     line = "";
                  }

                  String[] managerNames = line.split(";");
                  line = inStream.readLine();

                  while (line != null && !line.contains("==========")) {
                     line = inStream.readLine();
                  }

                  if (topLevelClaim == null) {
                     topLevelClaim = new Claim(
                        lesserBoundaryCorner, greaterBoundaryCorner, ownerName, builderNames, containerNames, accessorNames, managerNames, claimID
                     );
                     Claim conflictClaim = this.getClaimAt(topLevelClaim.lesserBoundaryCorner, true, null);
                     if (conflictClaim != null) {
                        inStream.close();
                        files[i].delete();
                        line = null;
                        continue;
                     }

                     topLevelClaim.modifiedDate = new Date(files[i].lastModified());
                     int j = 0;

                     while (j < this.claims.size() && !this.claims.get(j).greaterThan(topLevelClaim)) {
                        j++;
                     }

                     if (j < this.claims.size()) {
                        this.claims.add(j, topLevelClaim);
                     } else {
                        this.claims.add(this.claims.size(), topLevelClaim);
                     }

                     topLevelClaim.inDataStore = true;
                  } else {
                     Claim subdivision = new Claim(
                        lesserBoundaryCorner, greaterBoundaryCorner, "--subdivision--", builderNames, containerNames, accessorNames, managerNames, null
                     );
                     subdivision.modifiedDate = new Date(files[i].lastModified());
                     subdivision.parent = topLevelClaim;
                     topLevelClaim.children.add(subdivision);
                     subdivision.inDataStore = true;
                  }

                  line = inStream.readLine();
               }

               inStream.close();
            } catch (Exception e) {
               GriefPrevention.AddLogEntry("Unable to load data for claim \"" + files[i].getName() + "\": " + e.getMessage());
            }

            try {
               if (inStream != null) {
                  inStream.close();
               }
            } catch (IOException var21) {
            }
         }
      }

      super.initialize();
   }

   @Override
   synchronized void writeClaimToStorage(Claim claim) {
      String claimID = String.valueOf(claim.id);
      BufferedWriter outStream = null;

      try {
         File claimFile = new File(claimDataFolderPath + File.separator + claimID);
         claimFile.createNewFile();
         outStream = new BufferedWriter(new FileWriter(claimFile));
         this.writeClaimData(claim, outStream);

         for (int i = 0; i < claim.children.size(); i++) {
            this.writeClaimData(claim.children.get(i), outStream);
         }
      } catch (Exception e) {
         GriefPrevention.AddLogEntry("Unexpected exception saving data for claim \"" + claimID + "\": " + e.getMessage());
      }

      try {
         if (outStream != null) {
            outStream.close();
         }
      } catch (IOException var6) {
      }
   }

   private synchronized void writeClaimData(Claim claim, BufferedWriter outStream) throws IOException {
      outStream.write(this.locationToString(claim.getLesserBoundaryCorner()));
      outStream.newLine();
      outStream.write(this.locationToString(claim.getGreaterBoundaryCorner()));
      outStream.newLine();
      outStream.write(claim.ownerName);
      outStream.newLine();
      ArrayList<String> builders = new ArrayList<>();
      ArrayList<String> containers = new ArrayList<>();
      ArrayList<String> accessors = new ArrayList<>();
      ArrayList<String> managers = new ArrayList<>();
      claim.getPermissions(builders, containers, accessors, managers);

      for (int i = 0; i < builders.size(); i++) {
         outStream.write(builders.get(i) + ";");
      }

      outStream.newLine();

      for (int i = 0; i < containers.size(); i++) {
         outStream.write(containers.get(i) + ";");
      }

      outStream.newLine();

      for (int i = 0; i < accessors.size(); i++) {
         outStream.write(accessors.get(i) + ";");
      }

      outStream.newLine();

      for (int i = 0; i < managers.size(); i++) {
         outStream.write(managers.get(i) + ";");
      }

      outStream.newLine();
      outStream.write("==========");
      outStream.newLine();
   }

   @Override
   synchronized void deleteClaimFromSecondaryStorage(Claim claim) {
      String claimID = String.valueOf(claim.id);
      File claimFile = new File(claimDataFolderPath + File.separator + claimID);
      if (claimFile.exists() && !claimFile.delete()) {
         GriefPrevention.AddLogEntry("Error: Unable to delete claim file \"" + claimFile.getAbsolutePath() + "\".");
      }
   }

   @Override
   synchronized PlayerData getPlayerDataFromStorage(String playerName) {
      File playerFile = new File(playerDataFolderPath + File.separator + playerName);
      PlayerData playerData = new PlayerData();
      playerData.playerName = playerName;
      if (!playerFile.exists()) {
         this.savePlayerData(playerName, playerData);
      } else {
         BufferedReader inStream = null;

         try {
            inStream = new BufferedReader(new FileReader(playerFile.getAbsolutePath()));
            String lastLoginTimestampString = inStream.readLine();
            DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");

            try {
               playerData.lastLogin = dateFormat.parse(lastLoginTimestampString);
            } catch (ParseException parseException) {
               GriefPrevention.AddLogEntry("Unable to load last login for \"" + playerFile.getName() + "\".");
               playerData.lastLogin = null;
            }

            String accruedBlocksString = inStream.readLine();
            playerData.accruedClaimBlocks = Integer.parseInt(accruedBlocksString);
            String bonusBlocksString = inStream.readLine();
            playerData.bonusClaimBlocks = Integer.parseInt(bonusBlocksString);
            inStream.readLine();
            inStream.close();
         } catch (Exception e) {
            GriefPrevention.AddLogEntry("Unable to load data for player \"" + playerName + "\": " + e.getMessage());
         }

         try {
            if (inStream != null) {
               inStream.close();
            }
         } catch (IOException var9) {
         }
      }

      return playerData;
   }

   @Override
   public synchronized void savePlayerData(String playerName, PlayerData playerData) {
      if (playerName.length() != 0) {
         BufferedWriter outStream = null;

         try {
            File playerDataFile = new File(playerDataFolderPath + File.separator + playerName);
            playerDataFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(playerDataFile));
            if (playerData.lastLogin == null) {
               playerData.lastLogin = new Date();
            }

            DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
            outStream.write(dateFormat.format(playerData.lastLogin));
            outStream.newLine();
            outStream.write(String.valueOf(playerData.accruedClaimBlocks));
            outStream.newLine();
            outStream.write(String.valueOf(playerData.bonusClaimBlocks));
            outStream.newLine();
            if (playerData.claims.size() > 0) {
               outStream.write(this.locationToString(playerData.claims.get(0).getLesserBoundaryCorner()));

               for (int i = 1; i < playerData.claims.size(); i++) {
                  outStream.write(";;" + this.locationToString(playerData.claims.get(i).getLesserBoundaryCorner()));
               }
            }

            outStream.newLine();
         } catch (Exception e) {
            GriefPrevention.AddLogEntry("GriefPrevention: Unexpected exception saving data for player \"" + playerName + "\": " + e.getMessage());
         }

         try {
            if (outStream != null) {
               outStream.close();
            }
         } catch (IOException var7) {
         }
      }
   }

   @Override
   synchronized void incrementNextClaimID() {
      FlatFileDataStore outStream = this;
      Long e = outStream.nextClaimID;
      Long var3 = outStream.nextClaimID = outStream.nextClaimID + 1L;
      BufferedWriter outStreamx = null;

      try {
         File nextClaimIdFile = new File(nextClaimIdFilePath);
         nextClaimIdFile.createNewFile();
         outStreamx = new BufferedWriter(new FileWriter(nextClaimIdFile));
         outStreamx.write(String.valueOf(this.nextClaimID));
      } catch (Exception ex) {
         GriefPrevention.AddLogEntry("Unexpected exception saving next claim ID: " + ex.getMessage());
      }

      try {
         if (outStreamx != null) {
            outStreamx.close();
         }
      } catch (IOException var4) {
      }
   }

   @Override
   synchronized void saveGroupBonusBlocks(String groupName, int currentValue) {
      BufferedWriter outStream = null;

      try {
         File groupDataFile = new File(playerDataFolderPath + File.separator + "$" + groupName);
         groupDataFile.createNewFile();
         outStream = new BufferedWriter(new FileWriter(groupDataFile));
         outStream.write(String.valueOf(currentValue));
         outStream.newLine();
      } catch (Exception e) {
         GriefPrevention.AddLogEntry("Unexpected exception saving data for group \"" + groupName + "\": " + e.getMessage());
      }

      try {
         if (outStream != null) {
            outStream.close();
         }
      } catch (IOException var5) {
      }
   }

   synchronized void migrateData(DatabaseDataStore databaseStore) {
      for (int i = 0; i < this.claims.size(); i++) {
         Claim claim = this.claims.get(i);
         databaseStore.addClaim(claim);
      }

      for (String groupName : this.permissionToBonusBlocksMap.keySet()) {
         databaseStore.saveGroupBonusBlocks(groupName, this.permissionToBonusBlocksMap.get(groupName));
      }

      File playerDataFolder = new File(playerDataFolderPath);
      File[] files = playerDataFolder.listFiles();

      for (int i = 0; i < files.length; i++) {
         File file = files[i];
         if (file.isFile() && !file.getName().startsWith("$")) {
            String playerName = file.getName();
            databaseStore.savePlayerData(playerName, this.getPlayerData(playerName));
            this.clearCachedPlayerData(playerName);
         }
      }

      if (this.nextClaimID > databaseStore.nextClaimID) {
         databaseStore.setNextClaimID(this.nextClaimID);
      }

      int i = 0;

      File claimsBackupFolder;
      File playersBackupFolder;
      do {
         String claimsFolderBackupPath = claimDataFolderPath;
         if (i > 0) {
            claimsFolderBackupPath = claimsFolderBackupPath + String.valueOf(i);
         }

         claimsBackupFolder = new File(claimsFolderBackupPath);
         String playersFolderBackupPath = playerDataFolderPath;
         if (i > 0) {
            playersFolderBackupPath = playersFolderBackupPath + String.valueOf(i);
         }

         playersBackupFolder = new File(playersFolderBackupPath);
         i++;
      } while (claimsBackupFolder.exists() || playersBackupFolder.exists());

      File claimsFolder = new File(claimDataFolderPath);
      File playersFolder = new File(playerDataFolderPath);
      claimsFolder.renameTo(claimsBackupFolder);
      playersFolder.renameTo(playersBackupFolder);
      GriefPrevention.AddLogEntry("Backed your file system data up to " + claimsBackupFolder.getName() + " and " + playersBackupFolder.getName() + ".");
      GriefPrevention.AddLogEntry("If your migration encountered any problems, you can restore those data with a quick copy/paste.");
      GriefPrevention.AddLogEntry("When you're satisfied that all your data have been safely migrated, consider deleting those folders.");
   }

   @Override
   synchronized void close() {
   }
}
