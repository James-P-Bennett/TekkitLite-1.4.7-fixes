package me.ryanhamshire.TekkitCustomizer;

import java.util.ArrayList;

public class MaterialCollection {
   ArrayList<MaterialInfo> materials = new ArrayList<>();

   void Add(MaterialInfo material) {
      int i = 0;

      while (i < this.materials.size() && this.materials.get(i).typeID <= material.typeID) {
         i++;
      }

      this.materials.add(i, material);
   }

   MaterialInfo Contains(MaterialInfo material) {
      for (int i = 0; i < this.materials.size(); i++) {
         MaterialInfo thisMaterial = this.materials.get(i);
         if (material.typeID == thisMaterial.typeID && (thisMaterial.allDataValues || material.data == thisMaterial.data)) {
            return thisMaterial;
         }

         if (thisMaterial.typeID > material.typeID) {
            return null;
         }
      }

      return null;
   }

   @Override
   public String toString() {
      StringBuilder stringBuilder = new StringBuilder();

      for (int i = 0; i < this.materials.size(); i++) {
         stringBuilder.append(this.materials.get(i).toString() + " ");
      }

      return stringBuilder.toString();
   }

   public int size() {
      return this.materials.size();
   }

   public void clear() {
      this.materials.clear();
   }
}
