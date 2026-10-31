package pro.webhm.playerinteractions;

public final class WheelHold {
   public static final int WAIT = -2;
   public static final int CANCEL = -1;
   private boolean finished;

   public int release(boolean down, int sector) {
      if (!this.finished && !down) {
         this.finished = true;
         return sector >= 0 && sector < 5 ? sector : -1;
      } else {
         return -2;
      }
   }

   public void cancel() {
      this.finished = true;
   }

   public static int sector(double x, double y, double centerX, double centerY, int size) {
      double dx = x - centerX;
      double dy = y - centerY;
      double distance = Math.hypot(dx, dy);
      return size > 0 && Double.isFinite(distance) && !(distance < (double)size * 0.18) && !(distance > (double)size * 0.49)
         ? Math.floorMod((int)Math.floor((Math.atan2(dy, dx) + (Math.PI / 2) + (Math.PI / 5)) / (Math.PI * 2.0 / 5.0)), 5)
         : -1;
   }
}
