package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;

public class BlockSnapshot {
   public Location location;
   public int typeId;
   public byte data;

   public BlockSnapshot(Location location, int typeId, byte data) {
      this.location = location;
      this.typeId = typeId;
      this.data = data;
   }
}
