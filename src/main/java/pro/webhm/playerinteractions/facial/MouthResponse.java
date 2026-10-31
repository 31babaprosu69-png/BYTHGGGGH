package pro.webhm.playerinteractions.facial;

public final class MouthResponse {
   public static final int BITE_THRESHOLD = 40;
   public static final int SNAP_TICK = 40;
   public static final int RELEASE_TICK = 82;
   public static final int COOLDOWN_TICKS = 36;
   private long warningAt = -1L;
   private boolean bitten;

   public static boolean defensive(int bond) {
      return bond <= 40;
   }

   public boolean update(long age, int bond) {
      if (!this.bitten && !defensive(bond)) {
         this.warningAt = -1L;
         return false;
      } else {
         if (this.warningAt < 0L) {
            this.warningAt = Math.max(0L, age);
         }

         if (!this.bitten && age - this.warningAt >= 40L) {
            this.bitten = true;
            return true;
         } else {
            return false;
         }
      }
   }

   public long warningAt() {
      return this.warningAt;
   }

   public boolean bitten() {
      return this.bitten;
   }

   public boolean release(long age) {
      return this.bitten && age - this.warningAt >= 82L;
   }
}
