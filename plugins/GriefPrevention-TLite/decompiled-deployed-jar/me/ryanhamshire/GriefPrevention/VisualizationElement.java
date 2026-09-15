package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.Material;

public class VisualizationElement {
   public Location location;
   public Material visualizedMaterial;
   public byte visualizedData;

   public VisualizationElement(Location location, Material visualizedMaterial, byte visualizedData) {
      this.location = location;
      this.visualizedMaterial = visualizedMaterial;
      this.visualizedData = visualizedData;
   }
}
