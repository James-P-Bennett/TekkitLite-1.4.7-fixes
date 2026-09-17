package me.ryanhamshire.TekkitCustomizer;

public class MaterialInfo {
   int typeID;
   byte data;
   boolean allDataValues;
   String description;
   String reason;

   public MaterialInfo(int typeID, byte data, String description, String reason) {
      this.typeID = typeID;
      this.data = data;
      this.allDataValues = false;
      this.description = description;
      this.reason = reason;
   }

   public MaterialInfo(int typeID, String description, String reason) {
      this.typeID = typeID;
      this.data = 0;
      this.allDataValues = true;
      this.description = description;
      this.reason = reason;
   }

   private MaterialInfo(int typeID, byte data, boolean allDataValues, String description, String reason) {
      this.typeID = typeID;
      this.data = data;
      this.allDataValues = allDataValues;
      this.description = description;
      this.reason = reason;
   }

   @Override
   public String toString() {
      String returnValue = this.typeID + ":" + (this.allDataValues ? "*" : String.valueOf(this.data));
      if (this.description != null) {
         returnValue = returnValue + ":" + this.description + ":" + this.reason;
      }

      return returnValue;
   }

   public static MaterialInfo fromString(String string) {
      if (string != null && !string.isEmpty()) {
         String[] parts = string.split(":");
         if (parts.length < 2) {
            return null;
         }

         try {
            int typeID = Integer.parseInt(parts[0]);
            byte data;
            boolean allDataValues;
            if (parts[1].equals("*")) {
               allDataValues = true;
               data = 0;
            } else {
               allDataValues = false;
               data = Byte.parseByte(parts[1]);
            }

            return new MaterialInfo(typeID, data, allDataValues, parts.length >= 3 ? parts[2] : "", parts.length >= 4 ? parts[3] : "(No reason provided.)");
         } catch (NumberFormatException exception) {
            return null;
         }
      } else {
         return null;
      }
   }
}
