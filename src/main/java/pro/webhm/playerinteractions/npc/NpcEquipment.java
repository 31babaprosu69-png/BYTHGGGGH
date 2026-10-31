package pro.webhm.playerinteractions.npc;

public class NpcEquipment {
   public static final int MAIN = 0;
   public static final int OFF = 1;
   public static final int HEAD = 2;
   public static final int HELMET = 3;
   public static final int CHEST = 4;
   public static final int LEGS = 5;
   public static final int BOOTS = 6;
   public static final int SLOTS = 7;
   public String mainHand = "";
   public String offHand = "";
   public String head = "";
   public String helmet = "";
   public String chestplate = "";
   public String leggings = "";
   public String boots = "";
   public ItemPose mainPose = new ItemPose();
   public ItemPose offPose = new ItemPose();
   public ItemPose headPose = new ItemPose();

   public NpcEquipment copy() {
      NpcEquipment c = new NpcEquipment();
      c.mainHand = this.mainHand;
      c.offHand = this.offHand;
      c.head = this.head;
      c.helmet = this.helmet;
      c.chestplate = this.chestplate;
      c.leggings = this.leggings;
      c.boots = this.boots;
      c.mainPose = this.pose(0).copy();
      c.offPose = this.pose(1).copy();
      c.headPose = this.pose(2).copy();
      return c;
   }

   public static NpcEquipment lerp(NpcEquipment a, NpcEquipment b, float t) {
      if (t >= 1.0F) {
         return b.copy();
      } else {
         NpcEquipment r = new NpcEquipment();
         r.mainHand = a.mainHand;
         r.offHand = a.offHand;
         r.head = a.head;
         r.helmet = a.helmet;
         r.chestplate = a.chestplate;
         r.leggings = a.leggings;
         r.boots = a.boots;
         r.mainPose = ItemPose.lerp(a.pose(0), b.pose(0), t);
         r.offPose = ItemPose.lerp(a.pose(1), b.pose(1), t);
         r.headPose = ItemPose.lerp(a.pose(2), b.pose(2), t);
         return r;
      }
   }

   public String id(int slot) {
      return switch (slot) {
         case 1 -> this.offHand;
         case 2 -> this.head;
         case 3 -> this.helmet;
         case 4 -> this.chestplate;
         case 5 -> this.leggings;
         case 6 -> this.boots;
         default -> this.mainHand;
      };
   }

   public void setId(int slot, String id) {
      switch (slot) {
         case 1:
            this.offHand = id;
            break;
         case 2:
            this.head = id;
            break;
         case 3:
            this.helmet = id;
            break;
         case 4:
            this.chestplate = id;
            break;
         case 5:
            this.leggings = id;
            break;
         case 6:
            this.boots = id;
            break;
         default:
            this.mainHand = id;
      }
   }

   public ItemPose pose(int slot) {
      return switch (slot) {
         case 1 -> this.offPose == null ? (this.offPose = new ItemPose()) : this.offPose;
         case 2 -> this.headPose == null ? (this.headPose = new ItemPose()) : this.headPose;
         default -> this.mainPose == null ? (this.mainPose = new ItemPose()) : this.mainPose;
      };
   }

   public static boolean posable(int slot) {
      return slot == 0 || slot == 1 || slot == 2;
   }

   public static String slotName(int slot) {
      return switch (slot) {
         case 1 -> "Левая рука";
         case 2 -> "На голове";
         case 3 -> "Шлем";
         case 4 -> "Нагрудник";
         case 5 -> "Поножи";
         case 6 -> "Ботинки";
         default -> "Правая рука";
      };
   }

   public static String slotShort(int slot) {
      return switch (slot) {
         case 1 -> "лев";
         case 2 -> "верх";
         case 3 -> "шлем";
         case 4 -> "грудь";
         case 5 -> "ноги";
         case 6 -> "боты";
         default -> "прав";
      };
   }

   public boolean any() {
      return !isBlank(this.mainHand) || !isBlank(this.offHand) || !isBlank(this.head);
   }

   public boolean anyArmor() {
      return !isBlank(this.helmet) || !isBlank(this.chestplate) || !isBlank(this.leggings) || !isBlank(this.boots);
   }

   private static boolean isBlank(String s) {
      return s == null || s.isBlank();
   }
}
