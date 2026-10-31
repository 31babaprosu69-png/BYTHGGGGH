package pro.webhm.playerinteractions.facial;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SpeechPerformance {
   private final List<SpeechPerformance.Cue> cues = new ArrayList<>();
   private float units;

   private static float m(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public SpeechPerformance(String text) {
      float next = 0.0F;
      int seed = text.hashCode();
      int[] points = text.codePoints().toArray();
      int i = 0;

      while (i < points.length) {
         int c = points[i];
         if (!Character.isLetterOrDigit(c)) {
            this.units = this.units + TextArticulation.unitDuration(c);
            i++;
         } else {
            int start = i;
            float at = this.units;

            while (i < points.length && Character.isLetterOrDigit(points[i])) {
               this.units = this.units + TextArticulation.unitDuration(points[i++]);
            }

            String word = new String(points, start, i - start);
            if (!(at < next)) {
               int h = seed ^ word.hashCode() ^ i * 31;
               int kind = Set.of("я", "мне", "меня", "i", "me").contains(word) ? 1 : (Set.of("не", "нет", "no", "not").contains(word) ? 2 : 0);
               int end = i;

               while (end < points.length && ".!?…".indexOf(points[end]) < 0) {
                  end++;
               }

               if (kind == 0 && end < points.length && points[end] == 63) {
                  kind = 3;
               }

               this.cues.add(new SpeechPerformance.Cue(at, h, kind));
               next = at + 16.0F + (float)Math.floorMod(h, 14);
            }
         }
      }
   }

   private SpeechPerformance.Beat beat(double age, double duration) {
      if (!(age <= 0.0) && !(duration <= 0.0) && !(age >= duration) && !(this.units <= 0.0F) && !this.cues.isEmpty()) {
         double active = Math.max(1.0, duration - 4.0);
         double position = (age - 1.0) / active * (double)this.units;
         int lo = 0;
         int hi = this.cues.size();

         while (lo < hi) {
            int mid = lo + hi >>> 1;
            if ((double)this.cues.get(mid).at <= position) {
               lo = mid + 1;
            } else {
               hi = mid;
            }
         }

         if (lo == 0) {
            return null;
         } else {
            SpeechPerformance.Cue cue = this.cues.get(lo - 1);
            double at = 1.0 + (double)(cue.at / this.units) * active;
            double end = lo < this.cues.size() ? 1.0 + (double)(this.cues.get(lo).at / this.units) * active : duration;
            double span = Math.min((double)(34 + Math.floorMod(cue.seed, 11)), end - at);
            if (!(span <= 0.0) && !(age >= at + span)) {
               double t = (age - at) / span;
               float move = pulse(t, 0.05, 0.23, 0.45, 1.0);
               float accent = pulse(t, 0.26, 0.43, 0.49, 0.76);
               float glance = pulse(t, 0.0, 0.07, 0.19, 0.38);
               double blinkAt = at + span * 0.3;
               float blink = FacialExpression.ease((age - blinkAt) / 1.15) * (1.0F - FacialExpression.ease((age - blinkAt - 1.35) / 2.6));
               float edge = FacialExpression.ease((end - age) / 3.0) * FacialExpression.ease((duration - age) / 5.0);
               return new SpeechPerformance.Beat(move * edge, accent * edge, glance * edge, blink * edge, (cue.seed & 1) == 0 ? 1.0F : -1.0F, cue.kind);
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static float pulse(double t, double start, double peak, double hold, double end) {
      return FacialExpression.ease((t - start) / (peak - start)) * (1.0F - FacialExpression.ease((t - hold) / (end - hold)));
   }

   public FacialPose face(double age, double duration, FacialPose base, float strength) {
      SpeechPerformance.Beat b = this.beat(age, duration);
      if (b != null && !(strength <= 0.0F)) {
         float w = FacialData.clamp(strength, 0.0F, 1.0F);
         float m = b.move * w;
         float a = b.accent * w;
         FacialActing old = base.acting();
         float glance = b.glance * w * 0.6F;
         FacialActing acting = new FacialActing(
            old.gazeX() * (1.0F - b.glance) + b.side * 0.24F * b.glance,
            old.gazeY() * (1.0F - b.glance) + (b.kind == 1 ? 0.16F : -0.06F) * b.glance,
            Math.max(old.gazeWeight(), glance),
            Math.max(old.blinkLeft(), b.blink),
            Math.max(old.blinkRight(), b.blink),
            old.headYaw() + b.side * (b.kind == 2 ? 2.8F : 1.1F) * a,
            old.headPitch() + (b.kind == 3 ? -1.6F : 1.8F) * a,
            old.headRoll() + b.side * 0.8F * m,
            old.blush(),
            old.asymmetry(),
            old.eyeWide()
         );
         float direction = base.smile() < -0.02F ? -1.0F : 1.0F;
         return base.acting(acting).corners(direction * (0.055F * m + 0.045F * a), b.side * 0.065F * m);
      } else {
         return base;
      }
   }

   public SpeechPerformance.Gesture gesture(double age, double duration, float strength) {
      SpeechPerformance.Beat b = this.beat(age, duration);
      if (b != null && !(strength <= 0.0F)) {
         float m = b.move * FacialData.clamp(strength, 0.0F, 1.0F);
         float a = b.accent * FacialData.clamp(strength, 0.0F, 1.0F);
         float pitch = b.kind == 1 ? -22.0F : -15.0F;
         float elbow = b.kind == 1 ? -72.0F : (b.kind == 3 ? -50.0F : -38.0F);
         float spread = b.kind == 1 ? 5.0F : (b.kind == 3 ? 20.0F : 13.0F);
         SpeechPerformance.Arm right = new SpeechPerformance.Arm(pitch * m - 3.0F * a, -7.0F * m, spread * m, elbow * m - 5.0F * a, -9.0F * m);
         SpeechPerformance.Arm left = new SpeechPerformance.Arm(right.pitch, -right.yaw, -right.roll, right.elbow, -right.turn);
         float other = b.kind == 3 ? 0.7F : 0.14F;
         return new SpeechPerformance.Gesture(right.scale(b.side > 0.0F ? 1.0F : other), left.scale(b.side < 0.0F ? 1.0F : other), b.side * 1.3F * m);
      } else {
         return SpeechPerformance.Gesture.NONE;
      }
   }

   public static FacialPose idle(double time, int seed, FacialPose base, float strength) {
      if (strength <= 0.0F) {
         return base;
      } else {
         double period = (double)(125 + Math.floorMod(seed, 71));
         double cycle = Math.floor(time / period);
         double local = time - cycle * period;
         int h = seed ^ (int)cycle * 73471;
         double at = (double)(28 + Math.floorMod(h, 37));
         float glance = pulse(local, at, at + 2.2, at + 8.0, at + 15.0) * FacialData.clamp(strength, 0.0F, 1.0F);
         float blink = FacialExpression.ease((local - at - 9.0) / 1.3) * (1.0F - FacialExpression.ease((local - at - 10.5) / 2.6));
         if (glance == 0.0F && blink == 0.0F) {
            return base;
         } else {
            float side = (h & 1) == 0 ? 1.0F : -1.0F;
            FacialActing old = base.acting();
            return base.acting(
                  new FacialActing(
                     side * 0.2F,
                     -0.04F,
                     glance * 0.48F,
                     Math.max(old.blinkLeft(), blink),
                     Math.max(old.blinkRight(), blink),
                     old.headYaw(),
                     old.headPitch(),
                     old.headRoll(),
                     old.blush(),
                     old.asymmetry(),
                     old.eyeWide()
                  )
               )
               .corners(0.025F * glance * Math.signum(base.smile()), side * 0.035F * glance);
         }
      }
   }

   public static record Arm(float pitch, float yaw, float roll, float elbow, float turn) {
      public static final SpeechPerformance.Arm NONE = new SpeechPerformance.Arm(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

      SpeechPerformance.Arm scale(float w) {
         return new SpeechPerformance.Arm(this.pitch * w, this.yaw * w, this.roll * w, this.elbow * w, this.turn * w);
      }

      SpeechPerformance.Arm mix(SpeechPerformance.Arm b, float t) {
         return new SpeechPerformance.Arm(
            SpeechPerformance.m(this.pitch, b.pitch, t),
            SpeechPerformance.m(this.yaw, b.yaw, t),
            SpeechPerformance.m(this.roll, b.roll, t),
            SpeechPerformance.m(this.elbow, b.elbow, t),
            SpeechPerformance.m(this.turn, b.turn, t)
         );
      }
   }

   private static record Beat(float move, float accent, float glance, float blink, float side, int kind) {
   }

   private static record Cue(float at, int seed, int kind) {
   }

   public static record Gesture(SpeechPerformance.Arm right, SpeechPerformance.Arm left, float chestYaw) {
      public static final SpeechPerformance.Gesture NONE = new SpeechPerformance.Gesture(SpeechPerformance.Arm.NONE, SpeechPerformance.Arm.NONE, 0.0F);

      public SpeechPerformance.Gesture mix(SpeechPerformance.Gesture b, float t) {
         return new SpeechPerformance.Gesture(this.right.mix(b.right, t), this.left.mix(b.left, t), SpeechPerformance.m(this.chestYaw, b.chestYaw, t));
      }
   }
}
