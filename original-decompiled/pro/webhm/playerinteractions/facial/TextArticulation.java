package pro.webhm.playerinteractions.facial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TextArticulation {
   private final List<TextArticulation.Segment> segments = new ArrayList<>();
   public final SpeechPerformance performance;
   private float units;

   public TextArticulation(String text) {
      String clean = (text == null ? "" : text).replaceAll("§.", "").toLowerCase(Locale.ROOT);
      clean = clean.substring(0, Math.min(clean.length(), 4096));
      this.performance = new SpeechPerformance(clean);
      float originalUnits = 0.0F;
      int[] points = clean.codePoints().toArray();
      int phraseEnd = -1;
      float phraseTempo = 1.0F;

      for (int index = 0; index < points.length; index++) {
         int c = points[index];
         if (index > phraseEnd) {
            phraseEnd = index;

            while (phraseEnd < points.length - 1 && ".!?…;".indexOf(points[phraseEnd]) < 0) {
               phraseEnd++;
            }

            String phrase = new String(points, index, phraseEnd - index + 1);
            phraseTempo = phrase.endsWith("!") ? 0.86F : (phrase.endsWith("?") ? 1.08F : 1.0F);
            if (phrase.contains("тише")
               || phrase.contains("нежно")
               || phrase.contains("спокойно")
               || phrase.contains("не торопись")
               || phrase.contains("люблю")) {
               phraseTempo = 1.18F;
            }

            if (phrase.contains("скорее") || phrase.contains("быстрее") || phrase.contains("стой!")) {
               phraseTempo = 0.82F;
            }
         }

         float duration = unitDuration(c);
         TextArticulation.Shape shape = shape(c);
         originalUnits += duration;
         if (Character.isLetter(c)) {
            duration *= (shape.vowel() ? 1.18F : (shape == TextArticulation.Shape.SEAL ? 0.92F : 0.72F)) * phraseTempo;
            if (shape.vowel() && (index + 1 == points.length || !Character.isLetter(points[index + 1]))) {
               duration *= 1.25F;
            }

            if (phraseEnd - index <= 2) {
               duration *= 1.18F;
            }
         }

         this.segments.add(new TextArticulation.Segment(this.units, this.units + duration, shape));
         this.units += duration;
      }

      if (this.units > 0.0F) {
         float scale = originalUnits / this.units;

         for (int i = 0; i < this.segments.size(); i++) {
            TextArticulation.Segment v = this.segments.get(i);
            this.segments.set(i, new TextArticulation.Segment(v.start * scale, v.end * scale, v.shape));
         }

         this.units = originalUnits;
      }
   }

   static float unitDuration(int c) {
      if (Character.isWhitespace(c)) {
         return 0.65F;
      } else if (".,!?;:…—–".indexOf(c) >= 0) {
         return ",;:".indexOf(c) >= 0 ? 2.0F : 4.0F;
      } else {
         return Character.isLetterOrDigit(c) ? 1.0F : 0.3F;
      }
   }

   private static TextArticulation.Shape shape(int c) {
      if (!Character.isLetterOrDigit(c) || "ьъ".indexOf(c) >= 0) {
         return TextArticulation.Shape.REST;
      } else if ("аяэa".indexOf(c) >= 0) {
         return TextArticulation.Shape.A;
      } else if ("оёo".indexOf(c) >= 0) {
         return TextArticulation.Shape.O;
      } else if ("уюuwq".indexOf(c) >= 0) {
         return TextArticulation.Shape.U;
      } else if ("еe".indexOf(c) >= 0) {
         return TextArticulation.Shape.E;
      } else if ("иыйiy".indexOf(c) >= 0) {
         return TextArticulation.Shape.WIDE;
      } else if ("мбпmbp".indexOf(c) >= 0) {
         return TextArticulation.Shape.SEAL;
      } else if ("фвfv".indexOf(c) >= 0) {
         return TextArticulation.Shape.FV;
      } else if ("шжщчj".indexOf(c) >= 0) {
         return TextArticulation.Shape.SIBILANT;
      } else if ("цсзрдтszrdtc".indexOf(c) >= 0) {
         return TextArticulation.Shape.DENTAL;
      } else if ("лl".indexOf(c) >= 0) {
         return TextArticulation.Shape.L;
      } else {
         return "нn".indexOf(c) >= 0 ? TextArticulation.Shape.N : TextArticulation.Shape.SLIGHT;
      }
   }

   public float duration(float lettersPerSecond) {
      return this.units * 20.0F / (FacialData.clamp(lettersPerSecond, 4.0F, 30.0F) * 0.7F) + 4.0F;
   }

   public FacialPose sample(double elapsed, double ticks, FacialPose expression) {
      if (!(elapsed < 0.0) && !(elapsed >= ticks) && !(ticks <= 0.0) && this.units != 0.0F) {
         double active = Math.max(1.0, ticks - 4.0);
         float open = this.target(elapsed, active, false);
         float width = this.target(elapsed + 0.32, active, true);
         float edge = FacialExpression.ease(elapsed / 0.8) * FacialExpression.ease((ticks - elapsed) / 2.0);
         width = 1.0F + (width - 1.0F) * edge;
         float jaw = 0.0F;

         for (int i = 0; i < 5; i++) {
            jaw += this.target(elapsed - (double)i * 0.55, active, false) * (float)(5 - i) / 15.0F;
         }

         float activity = ticks >= 160.0 ? 0.6F : 1.0F;
         open *= activity;
         jaw *= activity;
         width = 1.0F + (width - 1.0F) * (ticks >= 160.0 ? 0.85F : 1.0F);
         return new FacialPose(
            open,
            width,
            expression.smile(),
            expression.brow(),
            expression.squint(),
            jaw * 0.82F,
            expression.acting(),
            expression.cornerLift(),
            expression.cornerAsymmetry()
         );
      } else {
         return new FacialPose(
            0.0F,
            1.0F,
            expression.smile(),
            expression.brow(),
            expression.squint(),
            0.0F,
            expression.acting(),
            expression.cornerLift(),
            expression.cornerAsymmetry()
         );
      }
   }

   private float target(double elapsed, double active, boolean width) {
      float rest = width ? 1.0F : 0.0F;
      if (!(elapsed <= 0.0) && !this.segments.isEmpty()) {
         double position = elapsed / active * (double)this.units;
         int lo = 0;
         int hi = this.segments.size();

         while (lo < hi) {
            int mid = lo + hi >>> 1;
            TextArticulation.Segment s = this.segments.get(mid);
            if ((double)(s.start + s.end) * 0.5 < position) {
               lo = mid + 1;
            } else {
               hi = mid;
            }
         }

         TextArticulation.Segment before = lo == 0 ? null : this.segments.get(lo - 1);
         TextArticulation.Segment after = lo == this.segments.size() ? null : this.segments.get(lo);
         double a = before == null ? 0.0 : (double)(before.start + before.end) * 0.5 * active / (double)this.units;
         double b = after == null ? active + 2.0 : (double)(after.start + after.end) * 0.5 * active / (double)this.units;
         float from = before == null ? rest : (width ? before.shape.width : before.shape.open);
         float to = after == null ? rest : (width ? after.shape.width : after.shape.open);
         if (before != null) {
            a += (double)(before.end - before.start) * active / (double)this.units * (double)before.shape.hold * 0.5;
         }

         if (after != null) {
            b -= (double)(after.end - after.start) * active / (double)this.units * (double)after.shape.hold * 0.5;
         }

         float t = FacialData.clamp((float)((elapsed - a) / Math.max(0.001, b - a)), 0.0F, 1.0F);
         if (!width && Math.abs(to - from) > 0.3F && Math.min(from, to) < 0.25F) {
            return inbetween(from, to, t);
         } else {
            t = t * t * (3.0F - 2.0F * t);
            return from + (to - from) * t;
         }
      } else {
         return rest;
      }
   }

   private static float inbetween(float from, float to, float t) {
      boolean opening = to > from;
      float cut = opening ? 0.42F : 0.62F;
      float mid = Math.min(from, to) + Math.abs(to - from) * 0.22F;
      float a = (mid - from) / cut;
      float b = (to - mid) / (1.0F - cut);
      float slope = 2.0F * a * b / (a + b);
      return t < cut ? hermite(from, mid, 0.0F, slope * cut, t / cut) : hermite(mid, to, slope * (1.0F - cut), 0.0F, (t - cut) / (1.0F - cut));
   }

   private static float hermite(float a, float b, float va, float vb, float t) {
      float t2 = t * t;
      float t3 = t2 * t;
      return (2.0F * t3 - 3.0F * t2 + 1.0F) * a + (t3 - 2.0F * t2 + t) * va + (-2.0F * t3 + 3.0F * t2) * b + (t3 - t2) * vb;
   }

   private static record Segment(float start, float end, TextArticulation.Shape shape) {
   }

   private static enum Shape {
      REST(0.0F, 1.0F, 0.66F),
      SEAL(0.0F, 0.97F, 0.62F),
      SLIGHT(0.18F, 0.96F, 0.14F),
      A(0.95F, 1.08F, 0.48F),
      O(0.78F, 0.58F, 0.56F),
      U(0.52F, 0.5F, 0.6F),
      WIDE(0.3F, 1.3F, 0.46F),
      E(0.48F, 1.23F, 0.45F),
      FV(0.12F, 1.06F, 0.28F),
      SIBILANT(0.3F, 0.81F, 0.3F),
      DENTAL(0.16F, 1.16F, 0.26F),
      L(0.58F, 1.04F, 0.32F),
      N(0.2F, 1.1F, 0.28F);

      final float open;
      final float width;
      final float hold;

      private Shape(float open, float width, float hold) {
         this.open = open;
         this.width = width;
         this.hold = hold;
      }

      boolean vowel() {
         return this == A || this == O || this == U || this == WIDE || this == E;
      }
   }
}
