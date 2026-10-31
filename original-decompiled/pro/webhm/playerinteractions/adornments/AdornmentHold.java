package pro.webhm.playerinteractions.adornments;

public final class AdornmentHold {
   private double start = Double.NaN;
   private double last = Double.NaN;
   private double stretch = Double.NaN;
   private double obstruction = Double.NaN;

   public boolean release(double now, boolean unreachable, boolean blocked) {
      if (!Double.isFinite(now)) {
         return false;
      } else {
         if (Double.isNaN(this.start) || now < this.last) {
            this.start = now;
            this.stretch = this.obstruction = Double.NaN;
         }

         this.last = now;
         this.stretch = unreachable ? (Double.isNaN(this.stretch) ? now : this.stretch) : Double.NaN;
         this.obstruction = blocked ? (Double.isNaN(this.obstruction) ? now : this.obstruction) : Double.NaN;
         return now - this.start >= 2.0
            && (!Double.isNaN(this.stretch) && now - this.stretch >= 1.5 || !Double.isNaN(this.obstruction) && now - this.obstruction >= 0.8);
      }
   }
}
