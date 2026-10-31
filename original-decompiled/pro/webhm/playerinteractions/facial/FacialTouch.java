package pro.webhm.playerinteractions.facial;

public final class FacialTouch {
   public static FacialPose sample(FacialPose base, int zone, int side, double age, float contact, float movement, float strength) {
      float w = FacialData.clamp(contact, 0.0F, 1.0F);
      float s = FacialData.clamp(strength, 0.0F, 1.0F);
      if (zone == 4) {
         float notice = FacialExpression.ease(age / 4.0) * (1.0F - FacialExpression.ease((age - 9.0) / 12.0));
         float relax = FacialExpression.ease((age - 10.0) / 18.0);
         float blink = pulse(age - 22.0, 5.0);
         FacialActing acting = new FacialActing(
            (float)side * 0.16F * notice,
            0.05F * notice,
            0.45F * notice,
            blink,
            blink,
            (float)(side * 2) * notice,
            -2.0F * notice + 1.2F * relax,
            (float)side * 1.5F * relax,
            0.0F,
            0.0F,
            0.45F * notice
         );
         FacialPose target = new FacialPose(0.24F * notice, 1.0F - 0.2F * notice, 0.24F * relax, 0.32F * notice, 0.08F * relax, 0.15F * notice, acting);
         return FacialExpression.mix(base, target, w * s);
      } else {
         float settle = FacialExpression.ease((age - 7.0) / 18.0);
         boolean stroke = zone == 12;
         boolean mouth = zone == 11;
         float shy = FacialExpression.ease((age - 3.0) / 14.0);
         float glance = 1.0F - FacialExpression.ease((age - 16.0) / 18.0);
         float blink = pulse(age - 9.0, 6.0) + pulse(age - 72.0, 7.0);
         float smile = stroke ? 0.24F + 0.09F * settle : (mouth ? 0.07F : 0.2F + 0.2F * movement);
         float squint = stroke ? 0.12F + 0.22F * settle + 0.08F * movement : (mouth ? 0.18F + 0.1F * settle : 0.16F + 0.14F * movement);
         FacialActing a = new FacialActing(
            (float)side * 0.18F * glance,
            0.18F * shy,
            0.9F,
            blink,
            blink,
            0.0F,
            0.0F,
            0.0F,
            (0.2F + 0.42F * shy) * s,
            stroke ? (float)side * 0.035F : 0.0F,
            mouth ? 0.16F * (1.0F - settle) : 0.0F
         );
         FacialPose target = base.expression(smile * s, (0.17F - 0.08F * settle) * s, squint * s).acting(a);
         return FacialExpression.mix(base, target, w);
      }
   }

   private static float pulse(double t, double duration) {
      return !(t <= 0.0) && !(t >= duration) ? (float)Math.pow(Math.sin(Math.PI * t / duration), 2.0) : 0.0F;
   }

   private FacialTouch() {
   }
}
