package pro.webhm.playerinteractions;

public final class WheelActions {
   public static final int ANIMATIONS = -1;
   public static final int APPEARANCE = -2;

   public static int at(int sector) {
      return switch (sector) {
         case 0 -> -1;
         case 1 -> 5;
         case 2 -> 6;
         case 3 -> -2;
         case 4 -> 4;
         default -> throw new IllegalArgumentException("Invalid sector");
      };
   }

   private WheelActions() {
   }
}
