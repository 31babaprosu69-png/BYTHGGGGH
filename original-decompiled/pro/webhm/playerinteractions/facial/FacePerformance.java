package pro.webhm.playerinteractions.facial;

public final class FacePerformance {
   private static float e(double t) {
      return FacialExpression.ease(t);
   }

   private static float bound(float x, float lo, float hi) {
      return FacialData.clamp(x, lo, hi);
   }

   private static float warmth(int bond) {
      return 0.35F + 0.65F * bound((float)(bond - 40) / 60.0F, 0.0F, 1.0F);
   }

   private static float blink(double age, int identity) {
      int period = 77 + Math.floorMod(identity, 29);
      double t = age - 46.0 - (double)Math.floorMod(identity, 17);
      if (t < 0.0) {
         return 0.0F;
      } else {
         t -= Math.floor(t / (double)period) * (double)period;
         return e(t / 2.0) * (1.0F - e((t - 3.0) / 5.0));
      }
   }

   public static FacialPose inspect(FacialPose base, double age, float weight, float strength, int bond, int identity) {
      if (Double.isFinite(age) && !(age < 0.0) && !(weight <= 0.0F) && !(strength <= 0.0F)) {
         boolean wary = MouthResponse.defensive(bond);
         float warm = warmth(bond);
         float closed = blink(age, identity);
         FacialActing acting = new FacialActing(0.0F, 0.0F, 0.0F, closed, closed, 0.0F, 0.0F, 0.0F, wary ? 0.0F : 0.09F * warm, 0.0F, 0.0F);
         FacialPose target = new FacialPose(
            base.open(), base.width(), wary ? -0.04F : 0.07F * warm, wary ? -0.12F : 0.025F, 0.035F, base.jaw(), acting, 0.0F, 0.0F
         );
         return FacialExpression.mix(base, target, bound(weight, 0.0F, 1.0F) * bound(strength, 0.0F, 1.0F) * e(age / 12.0));
      } else {
         return base;
      }
   }

   public static FacePerformance.Frame contact(
      FacialPose base, double age, float contact, float dragX, float dragY, float hand, float strength, int side, int bond, int identity, double warningAge
   ) {
      float w = bound(contact, 0.0F, 1.0F);
      float x = bound(dragX, -0.5F, 0.5F);
      float y = bound(dragY, -0.5F, 0.5F);
      float f = bound(hand, 0.0F, 1.0F);
      float s = bound(strength, 0.0F, 1.0F);
      if (!(w <= 0.0F) && Double.isFinite(age)) {
         float enter = e((age - 1.0) / 15.0);
         float settle = e((age - 12.0) / 24.0);
         float warm = warmth(bond);
         float movement = Math.min(1.0F, (float)Math.hypot((double)x, (double)y) * 1.4F);
         float open = (0.59F + 0.025F * f - 0.025F * settle + 0.18F * y) * enter;
         float jaw = (0.52F + 0.12F * f + 0.09F * y) * e((age - 4.0) / 19.0);
         float width = 1.01F - 0.11F * f + 0.1F * Math.abs(x) - 0.08F * y;
         float smile = (0.085F + 0.11F * movement + 0.025F * f) * warm;
         float closed = blink(age, identity);
         float notice = 1.0F - e((age - 4.0) / 12.0);
         FacialActing acting = new FacialActing(0.0F, 0.0F, 0.0F, closed, closed, 0.0F, 0.0F, 0.0F, (0.1F + 0.12F * movement) * warm, 0.0F, 0.16F * notice);
         FacialPose target = new FacialPose(
            bound(open, 0.0F, 1.0F),
            width,
            base.smile() + (smile - base.smile()) * s,
            base.brow() + (0.1F * notice + 0.055F * movement - base.brow()) * s,
            base.squint() + (0.045F + 0.08F * f + 0.085F * movement - base.squint()) * s,
            bound(jaw, 0.0F, 1.0F),
            base.acting().blend(acting, s),
            -0.08F * y * s,
            0.3F * x * s,
            new LipContact(f * enter, (float)side * 0.58F + x * 0.3F, y, 0.42F + 0.22F * movement)
         );
         boolean defensive = MouthResponse.defensive(bond) || warningAge >= 0.0;
         if (defensive) {
            double t = Math.max(0.0, warningAge);
            HandResponse.Frame response = HandResponse.bite(t);
            float warning = e(t / 9.0);
            float bite = response.close();
            float bring = response.bring();
            float alarm = e(t / 3.0) * (1.0F - e((t - 5.0) / 7.0));
            FacialActing guard = new FacialActing(0.0F, 0.0F, 0.0F, 0.2F * bite, 0.2F * bite, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.42F * alarm);
            float aperture = (0.08F + 0.64F * bring) * (1.0F - bite) + 0.23F * bite;
            target = new FacialPose(
               aperture,
               width + 0.04F * warning,
               base.smile() + (-0.04F - base.smile()) * s,
               base.brow() + (-0.25F * warning - base.brow()) * s,
               base.squint() + (0.12F * warning + 0.18F * bite - base.squint()) * s,
               aperture * 0.85F,
               base.acting().blend(guard, s),
               -0.02F * warning * s,
               (float)side * 0.025F * warning * s,
               new LipContact(bring, 0.0F, 0.0F, 0.55F + 0.3F * bite)
            );
         }

         return new FacePerformance.Frame(FacialExpression.mix(base, target, w), defensive ? FacialTongue.HIDDEN : tongue(age, w, x, y, f, identity));
      } else {
         return new FacePerformance.Frame(base, FacialTongue.HIDDEN);
      }
   }

   public static FacialTongue tongue(double age, float contact, float dragX, float dragY, float hand, int identity) {
      if (Double.isFinite(age) && !(age <= 10.0)) {
         float f = bound(hand, 0.0F, 1.0F);
         float x = bound(dragX, -0.5F, 0.5F);
         float y = bound(dragY, -0.5F, 0.5F);
         float show = (0.66F * e((age - 10.0) / 16.0) * (1.0F - f) + 0.78F * e((age - 10.0) / 10.0) * f) * bound(contact, 0.0F, 1.0F);
         if (show <= 1.0E-4F) {
            return FacialTongue.HIDDEN;
         } else {
            int cycle = (int)Math.floor((age - 20.0) / 79.0);
            double t = age - 20.0 - (double)(cycle * 79);
            double at = (double)(23 + Math.floorMod(cycle * 17, 21));
            float micro = e((t - at) / 6.0) * (1.0F - e((t - at - 9.0) / 11.0)) * f;
            return new FacialTongue(
               show, x * (0.22F + 0.2F * f) + (float)((cycle & 1) == 0 ? 1 : -1) * 0.025F * micro, -y * (0.15F + 0.15F * f) + 0.045F * micro
            );
         }
      } else {
         return FacialTongue.HIDDEN;
      }
   }

   private FacePerformance() {
   }

   public static record Frame(FacialPose pose, FacialTongue tongue) {
   }
}
