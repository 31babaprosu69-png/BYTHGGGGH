package pro.webhm.playerinteractions.facial;

public final class HandResponse {
   public static final int BITE_TICK = 40;
   public static final int BITE_END = 82;
   public static final int REFUSE_END = 58;

   public static float ease(double t) {
      t = Math.max(0.0, Math.min(1.0, t));
      return (float)(t * t * t * (t * (t * 6.0 - 15.0) + 10.0));
   }

   public static boolean refusesChest(int bond) {
      return bond < 40;
   }

   public static HandResponse.Frame bite(double t) {
      if (Double.isFinite(t) && !(t < 0.0) && !(t >= 82.0)) {
         float weight = ease(t / 10.0) * (1.0F - ease((t - 70.0) / 12.0));
         float grasp = ease((t - 5.0) / 12.0) * (1.0F - ease((t - 65.0) / 13.0));
         float bring = ease((t - 17.0) / 13.0) * (1.0F - ease((t - 57.0) / 15.0));
         float close = ease((t - 32.0) / 8.0) * (1.0F - ease((t - 48.0) / 9.0));
         return new HandResponse.Frame(grasp, bring, close, ease((t - 57.0) / 15.0), weight);
      } else {
         return HandResponse.Frame.NONE;
      }
   }

   public static HandResponse.Frame refuse(double t) {
      return Double.isFinite(t) && !(t < 0.0) && !(t >= 58.0)
         ? new HandResponse.Frame(
            ease((t - 5.0) / 10.0) * (1.0F - ease((t - 37.0) / 13.0)), 0.0F, 0.0F, ease((t - 17.0) / 17.0), ease(t / 8.0) * (1.0F - ease((t - 44.0) / 14.0))
         )
         : HandResponse.Frame.NONE;
   }

   public static FacialPose refusal(FacialPose base, double age, float contact, float strength, int side) {
      HandResponse.Frame f = refuse(age);
      float notice = ease(age / 5.0) * (1.0F - ease((age - 7.0) / 10.0));
      FacialActing a = new FacialActing(
         (float)side * 0.12F,
         0.12F,
         0.6F * f.weight(),
         0.0F,
         0.0F,
         (float)(side * 7) * f.withdraw(),
         -3.0F * f.weight(),
         (float)(-side * 2) * f.withdraw(),
         0.0F,
         0.0F,
         0.18F * notice
      );
      FacialPose target = new FacialPose(0.12F * notice, 0.94F, -0.18F, -0.55F, 0.25F, 0.06F * notice, a, -0.025F, 0.0F);
      return FacialExpression.mix(base, target, FacialData.clamp(contact * strength, 0.0F, 1.0F) * f.weight());
   }

   private HandResponse() {
   }

   public static record Frame(float grasp, float bring, float close, float withdraw, float weight) {
      public static final HandResponse.Frame NONE = new HandResponse.Frame(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }
}
