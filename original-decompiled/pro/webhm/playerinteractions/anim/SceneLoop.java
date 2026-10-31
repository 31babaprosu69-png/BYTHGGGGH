package pro.webhm.playerinteractions.anim;

public final class SceneLoop {
   public static final int LIMIT = 12;
   public static final int CELEBRATE_TICKS = 16;
   public static final int INTRO = 0;
   public static final int WAITING = 1;
   public static final int REPEATING = 2;
   public static final int CELEBRATING = 3;
   public static final int OUTRO = 4;
   public final int start;
   public final int end;
   public final boolean continuous;
   public int phase = 0;
   public int count;
   public int pass;
   public boolean exitRequested;
   public long celebrationAt = -1L;

   public SceneLoop(int start, int end, boolean continuous) {
      if (start >= 0 && end > start) {
         this.start = start;
         this.end = end;
         this.continuous = continuous;
      } else {
         throw new IllegalArgumentException("Invalid loop markers");
      }
   }

   public boolean held() {
      return this.phase == 1 || this.phase == 3;
   }

   public boolean ready() {
      return !this.continuous && this.phase == 1 && this.count < 12;
   }

   public boolean repeat(int expectedPass) {
      if (this.ready() && expectedPass == this.pass) {
         this.phase = 2;
         this.pass++;
         return true;
      } else {
         return false;
      }
   }

   public boolean requestExit() {
      if (!this.continuous) {
         return false;
      } else {
         this.exitRequested = true;
         return true;
      }
   }

   public double advance(double time, long now) {
      if (this.phase == 4) {
         return time;
      } else if (this.phase == 3) {
         if (now - this.celebrationAt >= 16L) {
            this.phase = 4;
         }

         return (double)this.end;
      } else if (this.phase == 1) {
         return (double)this.end;
      } else if (time < (double)this.end) {
         return time;
      } else if (this.continuous) {
         if (this.exitRequested) {
            this.phase = 4;
            return (double)this.end;
         } else {
            this.phase = 2;
            this.pass++;
            return (double)this.start + (time - (double)this.end) % (double)(this.end - this.start);
         }
      } else {
         if (this.phase == 2) {
            this.count = Math.min(12, this.count + 1);
            if (this.count == 12) {
               this.phase = 3;
               this.celebrationAt = now;
            } else {
               this.phase = 1;
            }
         } else {
            this.phase = 1;
         }

         return (double)this.end;
      }
   }

   public double sample(double time) {
      if (this.phase == 4) {
         return time;
      } else if (this.continuous && time >= (double)this.end) {
         return this.exitRequested ? (double)this.end : (double)this.start + (time - (double)this.end) % (double)(this.end - this.start);
      } else {
         return Math.min(time, (double)this.end);
      }
   }
}
