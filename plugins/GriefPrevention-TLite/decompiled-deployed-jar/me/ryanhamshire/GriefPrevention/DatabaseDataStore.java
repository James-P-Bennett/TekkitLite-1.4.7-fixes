package me.ryanhamshire.GriefPrevention;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Properties;
import org.bukkit.Location;

public class DatabaseDataStore extends DataStore {
   private Connection databaseConnection = null;
   private String databaseUrl;
   private String userName;
   private String password;

   DatabaseDataStore(String url, String userName, String password) throws Exception {
      this.databaseUrl = url;
      this.userName = userName;
      this.password = password;
      this.initialize();
   }

   @Override
   void initialize() throws Exception {
      try {
         Class.forName("com.mysql.jdbc.Driver");
      } catch (Exception e) {
         GriefPrevention.AddLogEntry("ERROR: Unable to load Java's mySQL database driver.  Check to make sure you've installed it properly.");
         throw e;
      }

      try {
         this.refreshDataConnection();
      } catch (Exception e2) {
         GriefPrevention.AddLogEntry("ERROR: Unable to connect to database.  Check your config file settings.");
         throw e2;
      }

      try {
         Statement statement = this.databaseConnection.createStatement();
         statement.execute("CREATE TABLE IF NOT EXISTS griefprevention_nextclaimid (nextid INT(15));");
         statement.execute(
            "CREATE TABLE IF NOT EXISTS griefprevention_claimdata (id INT(15), owner VARCHAR(50), lessercorner VARCHAR(100), greatercorner VARCHAR(100), builders VARCHAR(1000), containers VARCHAR(1000), accessors VARCHAR(1000), managers VARCHAR(1000), parentid INT(15));"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS griefprevention_playerdata (name VARCHAR(50), lastlogin DATETIME, accruedblocks INT(15), bonusblocks INT(15));"
         );
      } catch (Exception e3) {
         GriefPrevention.AddLogEntry("ERROR: Unable to create the necessary database table.  Details:");
         GriefPrevention.AddLogEntry(e3.getMessage());
         throw e3;
      }

      Statement statement = this.databaseConnection.createStatement();
      ResultSet results = statement.executeQuery("SELECT * FROM griefprevention_playerdata;");

      while (results.next()) {
         String name = results.getString("name");
         if (name.startsWith("$")) {
            String groupName = name.substring(1);
            if (groupName != null && !groupName.isEmpty()) {
               int groupBonusBlocks = results.getInt("bonusblocks");
               this.permissionToBonusBlocksMap.put(groupName, groupBonusBlocks);
            }
         }
      }

      results = statement.executeQuery("SELECT * FROM griefprevention_nextclaimid;");
      if (!results.next()) {
         statement.execute("INSERT INTO griefprevention_nextclaimid VALUES(0);");
         this.nextClaimID = 0L;
      } else {
         this.nextClaimID = results.getLong("nextid");
      }

      results = statement.executeQuery("SELECT * FROM griefprevention_claimdata;");
      ArrayList<Claim> claimsToRemove = new ArrayList<>();

      while (results.next()) {
         try {
            long parentId = results.getLong("parentid");
            if (parentId == -1L) {
               long claimID = results.getLong("id");
               String lesserCornerString = results.getString("lessercorner");
               Location lesserBoundaryCorner = this.locationFromString(lesserCornerString);
               String greaterCornerString = results.getString("greatercorner");
               Location greaterBoundaryCorner = this.locationFromString(greaterCornerString);
               String ownerName = results.getString("owner");
               String buildersString = results.getString("builders");
               String[] builderNames = buildersString.split(";");
               String containersString = results.getString("containers");
               String[] containerNames = containersString.split(";");
               String accessorsString = results.getString("accessors");
               String[] accessorNames = accessorsString.split(";");
               String managersString = results.getString("managers");
               String[] managerNames = managersString.split(";");
               Claim topLevelClaim = new Claim(
                  lesserBoundaryCorner, greaterBoundaryCorner, ownerName, builderNames, containerNames, accessorNames, managerNames, claimID
               );
               Claim conflictClaim = this.getClaimAt(topLevelClaim.lesserBoundaryCorner, true, null);
               if (conflictClaim != null) {
                  claimsToRemove.add(conflictClaim);
               } else {
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
                  Statement statement2 = this.databaseConnection.createStatement();
                  ResultSet childResults = statement2.executeQuery("SELECT * FROM griefprevention_claimdata WHERE parentid=" + topLevelClaim.id + ";");

                  while (childResults.next()) {
                     lesserCornerString = childResults.getString("lessercorner");
                     lesserBoundaryCorner = this.locationFromString(lesserCornerString);
                     greaterCornerString = childResults.getString("greatercorner");
                     greaterBoundaryCorner = this.locationFromString(greaterCornerString);
                     buildersString = childResults.getString("builders");
                     builderNames = buildersString.split(";");
                     containersString = childResults.getString("containers");
                     containerNames = containersString.split(";");
                     accessorsString = childResults.getString("accessors");
                     accessorNames = accessorsString.split(";");
                     managersString = childResults.getString("managers");
                     managerNames = managersString.split(";");
                     Claim childClaim = new Claim(
                        lesserBoundaryCorner, greaterBoundaryCorner, ownerName, builderNames, containerNames, accessorNames, managerNames, null
                     );
                     childClaim.parent = topLevelClaim;
                     topLevelClaim.children.add(childClaim);
                     childClaim.inDataStore = true;
                  }
               }
            }
         } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to load a claim.  Details: " + e.getMessage() + " ... " + results.toString());
            e.printStackTrace();
         }
      }

      for (int i = 0; i < claimsToRemove.size(); i++) {
         this.deleteClaimFromSecondaryStorage(claimsToRemove.get(i));
      }

      super.initialize();
   }

   @Override
   synchronized void writeClaimToStorage(Claim claim) {
      try {
         this.refreshDataConnection();
         this.deleteClaimFromSecondaryStorage(claim);
         this.writeClaimData(claim);

         for (int i = 0; i < claim.children.size(); i++) {
            this.writeClaimData(claim.children.get(i));
         }
      } catch (SQLException e) {
         GriefPrevention.AddLogEntry("Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner) + ".  Details:");
         GriefPrevention.AddLogEntry(e.getMessage());
      }
   }

   private synchronized void writeClaimData(Claim claim) throws SQLException {
      String lesserCornerString = this.locationToString(claim.getLesserBoundaryCorner());
      String greaterCornerString = this.locationToString(claim.getGreaterBoundaryCorner());
      String owner = claim.ownerName;
      ArrayList<String> builders = new ArrayList<>();
      ArrayList<String> containers = new ArrayList<>();
      ArrayList<String> accessors = new ArrayList<>();
      ArrayList<String> managers = new ArrayList<>();
      claim.getPermissions(builders, containers, accessors, managers);
      String buildersString = "";

      for (int i = 0; i < builders.size(); i++) {
         buildersString = buildersString + builders.get(i) + ";";
      }

      String containersString = "";

      for (int i = 0; i < containers.size(); i++) {
         containersString = containersString + containers.get(i) + ";";
      }

      String accessorsString = "";

      for (int i = 0; i < accessors.size(); i++) {
         accessorsString = accessorsString + accessors.get(i) + ";";
      }

      String managersString = "";

      for (int i = 0; i < managers.size(); i++) {
         managersString = managersString + managers.get(i) + ";";
      }

      long parentId;
      if (claim.parent == null) {
         parentId = -1L;
      } else {
         parentId = claim.parent.id;
      }

      long id;
      if (claim.id == null) {
         id = -1L;
      } else {
         id = claim.id;
      }

      try {
         this.refreshDataConnection();
         Statement statement = this.databaseConnection.createStatement();
         statement.execute(
            "INSERT INTO griefprevention_claimdata VALUES("
               + id
               + ", '"
               + owner
               + "', '"
               + lesserCornerString
               + "', '"
               + greaterCornerString
               + "', '"
               + buildersString
               + "', '"
               + containersString
               + "', '"
               + accessorsString
               + "', '"
               + managersString
               + "', "
               + parentId
               + ");"
         );
      } catch (SQLException e) {
         GriefPrevention.AddLogEntry("Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner) + ".  Details:");
         GriefPrevention.AddLogEntry(e.getMessage());
      }
   }

   @Override
   synchronized void deleteClaimFromSecondaryStorage(Claim claim) {
      try {
         this.refreshDataConnection();
         Statement statement = this.databaseConnection.createStatement();
         statement.execute("DELETE FROM griefprevention_claimdata WHERE id=" + claim.id + ";");
         statement.execute("DELETE FROM griefprevention_claimdata WHERE parentid=" + claim.id + ";");
      } catch (SQLException e) {
         GriefPrevention.AddLogEntry("Unable to delete data for claim at " + this.locationToString(claim.lesserBoundaryCorner) + ".  Details:");
         GriefPrevention.AddLogEntry(e.getMessage());
      }
   }

   @Override
   synchronized PlayerData getPlayerDataFromStorage(String playerName) {
      PlayerData playerData = new PlayerData();
      playerData.playerName = playerName;

      try {
         this.refreshDataConnection();
         Statement statement = this.databaseConnection.createStatement();
         ResultSet results = statement.executeQuery("SELECT * FROM griefprevention_playerdata WHERE name='" + playerName + "';");
         if (!results.next()) {
            this.savePlayerData(playerName, playerData);
         } else {
            playerData.lastLogin = results.getTimestamp("lastlogin");
            playerData.accruedClaimBlocks = results.getInt("accruedblocks");
            playerData.bonusClaimBlocks = results.getInt("bonusblocks");
         }
      } catch (SQLException e) {
         GriefPrevention.AddLogEntry("Unable to retrieve data for player " + playerName + ".  Details:");
         GriefPrevention.AddLogEntry(e.getMessage());
      }

      return playerData;
   }

   @Override
   public synchronized void savePlayerData(String playerName, PlayerData playerData) {
      if (playerName.length() != 0) {
         try {
            this.refreshDataConnection();
            SimpleDateFormat sqlFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateString = sqlFormat.format(playerData.lastLogin);
            Statement statement = this.databaseConnection.createStatement();
            statement.execute("DELETE FROM griefprevention_playerdata WHERE name='" + playerName + "';");
            statement.execute(
               "INSERT INTO griefprevention_playerdata VALUES ('"
                  + playerName
                  + "', '"
                  + dateString
                  + "', "
                  + playerData.accruedClaimBlocks
                  + ", "
                  + playerData.bonusClaimBlocks
                  + ");"
            );
         } catch (SQLException e) {
            GriefPrevention.AddLogEntry("Unable to save data for player " + playerName + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
         }
      }
   }

   @Override
   synchronized void incrementNextClaimID() {
      this.setNextClaimID(this.nextClaimID + 1L);
   }

   synchronized void setNextClaimID(long nextID) {
      this.nextClaimID = nextID;

      try {
         this.refreshDataConnection();
         Statement statement = this.databaseConnection.createStatement();
         statement.execute("DELETE FROM griefprevention_nextclaimid;");
         statement.execute("INSERT INTO griefprevention_nextclaimid VALUES (" + nextID + ");");
      } catch (SQLException e) {
         GriefPrevention.AddLogEntry("Unable to set next claim ID to " + nextID + ".  Details:");
         GriefPrevention.AddLogEntry(e.getMessage());
      }
   }

   @Override
   synchronized void saveGroupBonusBlocks(String groupName, int currentValue) {
      String playerName = "$" + groupName;
      PlayerData playerData = new PlayerData();
      playerData.bonusClaimBlocks = currentValue;
      this.savePlayerData(playerName, playerData);
   }

   @Override
   synchronized void close() {
      if (this.databaseConnection != null) {
         try {
            if (!this.databaseConnection.isClosed()) {
               this.databaseConnection.close();
            }
         } catch (SQLException var2) {
         }
      }

      this.databaseConnection = null;
   }

   private void refreshDataConnection() throws SQLException {
      if (this.databaseConnection == null || this.databaseConnection.isClosed()) {
         Properties connectionProps = new Properties();
         connectionProps.put("user", this.userName);
         connectionProps.put("password", this.password);
         this.databaseConnection = DriverManager.getConnection(this.databaseUrl, connectionProps);
      }
   }
}
