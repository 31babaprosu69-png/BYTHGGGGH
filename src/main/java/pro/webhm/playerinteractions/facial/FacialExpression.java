package pro.webhm.playerinteractions.facial;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class FacialExpression {
   public static final String[] IDS = new String[]{"auto", "warm", "joy", "shy", "playful", "surprise", "concern", "focused", "neutral"};
   public static final String[] LABELS = new String[]{
      "По контексту", "Нежность", "Радость", "Смущение", "Игривость", "Удивление", "Забота", "Сосредоточенность", "Спокойствие"
   };
   private static final Pattern CONCERN = Pattern.compile(
      "(?iu)(?<!\\p{L})(?:больно|болит|больн\\p{L}*|ран[аыу]|ранен\\p{L}*|груст\\p{L}*|прости\\p{L}*|извини\\p{L}*|страшно|не\\s+(?:рада|хочу|нравится|смешно)|не\\s+трогай|перестань|хватит)(?!\\p{L})"
   );
   private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

   private static boolean words(String s, String pattern) {
      return PATTERNS.computeIfAbsent(pattern, p -> Pattern.compile("(?iu)(?<!\\p{L})(?:" + p + ")(?!\\p{L})")).matcher(s).find();
   }

   public static FacialExpression.Mood select(String text, String context) {
      String c = context == null ? "" : context.toLowerCase(Locale.ROOT);

      try {
         if (!c.isBlank() && !c.equals("auto")) {
            return FacialExpression.Mood.valueOf(c.toUpperCase(Locale.ROOT));
         }
      } catch (IllegalArgumentException var4) {
      }

      String s = text == null ? "" : text.replaceAll("§.", "").toLowerCase(Locale.ROOT);
      if (s.isBlank()) {
         return FacialExpression.Mood.NEUTRAL;
      } else if (CONCERN.matcher(s).find()) {
         return FacialExpression.Mood.CONCERN;
      } else if (words(s, "смуща\\p{L}*|стесня\\p{L}*|покрасне\\p{L}*|комплимент\\p{L}*")) {
         return FacialExpression.Mood.SHY;
      } else if (words(s, "ого|неужели|ничего себе|вот это да|сюрприз")) {
         return FacialExpression.Mood.SURPRISE;
      } else if (words(s, "дразни\\p{L}*|хитр\\p{L}*|подмиг\\p{L}*|шути\\p{L}*|улыб\\p{L}*|щекотно")) {
         return FacialExpression.Mood.PLAYFUL;
      } else if (words(s, "спасибо|ура|рада|радуюсь|здорово|привет|ха-ха|хаха")) {
         return FacialExpression.Mood.JOY;
      } else if (words(s, "атак\\p{L}*|враг\\p{L}*|опасно|сраж\\p{L}*|бой")) {
         return FacialExpression.Mood.FOCUSED;
      } else {
         return s.contains("?") ? FacialExpression.Mood.SURPRISE : FacialExpression.Mood.WARM;
      }
   }

   public static String event(String event) {
      String var1 = event == null ? "" : event;

      return switch (var1) {
         case "touch_head", "carry_pet", "follow", "come", "return", "hand_walk", "carry" -> "warm";
         case "touch_cheek", "touch_sensitive", "touch_mouth" -> "shy";
         case "touch", "touch_pull" -> "playful";
         case "gift_offer" -> "playful";
         case "gift_given", "accept", "healed" -> "joy";
         case "heal_offer", "touch_refuse", "refuse_attack", "blocked", "touch_busy" -> "concern";
         case "attack" -> "focused";
         default -> "auto";
      };
   }

   public static FacialPose blend(FacialPose base, float smile, float brow, float squint, float weight) {
      float w = FacialData.clamp(weight, 0.0F, 1.0F);
      return base.expression(m(base.smile(), smile, w), m(base.brow(), brow, w), m(base.squint(), squint, w));
   }

   public static FacialPose mix(FacialPose a, FacialPose b, float weight) {
      float w = FacialData.clamp(weight, 0.0F, 1.0F);
      return new FacialPose(
         m(a.open(), b.open(), w),
         m(a.width(), b.width(), w),
         m(a.smile(), b.smile(), w),
         m(a.brow(), b.brow(), w),
         m(a.squint(), b.squint(), w),
         m(a.jaw(), b.jaw(), w),
         a.acting().blend(b.acting(), w),
         m(a.cornerLift(), b.cornerLift(), w),
         m(a.cornerAsymmetry(), b.cornerAsymmetry(), w),
         a.contact().mix(b.contact(), w)
      );
   }

   private static float m(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public static float ease(double t) {
      float x = FacialData.clamp((float)t, 0.0F, 1.0F);
      return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
   }

   private static float beat(double t, double start, double rise, double hold, double fall) {
      return ease((t - start) / rise) * (1.0F - ease((t - start - rise - hold) / fall));
   }

   private static float blink(double t, double at) {
      return beat(t, at, 1.5, 0.5, 3.5);
   }

   public static FacialPose dialogue(String text, double age, double duration, FacialPose base) {
      return dialogue(text, "auto", age, duration, base, 1.0F);
   }

   public static FacialPose dialogue(String text, String context, double age, double duration, FacialPose base, float strength) {
      return perform(select(text, context), age, duration, base, strength, text == null ? 0 : text.hashCode());
   }

   public static FacialPose perform(FacialExpression.Mood mood, double age, double duration, FacialPose base, float strength, int seed) {
      return perform(mood, age, duration, base, strength, seed, false);
   }

   public static FacialPose perform(FacialExpression.Mood mood, double age, double duration, FacialPose base, float strength, int seed, boolean speech) {
      if (!(age <= 0.0) && !(age >= duration) && !(duration <= 0.0) && !(strength <= 0.0F) && mood != FacialExpression.Mood.NEUTRAL) {
         float fade = ease((duration - age) / 12.0) * FacialData.clamp(strength, 0.0F, 1.0F);
         float eyes = ease(age / 5.0) * fade;
         float mouth = ease((age - 2.0) / 10.0) * fade;
         float hold = beat(age, 7.0, 7.0, 13.0, 12.0);
         float returnLook = beat(age, 30.0, 5.0, 8.0, 8.0);
         float side = (seed & 1) == 0 ? 1.0F : -1.0F;
         float smile = 0.28F;
         float brow = 0.05F;
         float squint = 0.08F;
         float lookX = 0.0F;
         float lookY = 0.0F;
         float lookWeight = 0.0F;
         float yaw = 0.0F;
         float pitch = 0.0F;
         float roll = 0.0F;
         float blush = 0.0F;
         float asym = 0.0F;
         float wide = 0.0F;
         float opening = 0.0F;
         float leftBlink = 0.0F;
         float rightBlink = 0.0F;
         switch (mood) {
            case WARM:
               smile = 0.38F;
               pitch = 1.2F;
               roll = side * 1.8F;
               leftBlink = rightBlink = blink(age, 24.0);
               break;
            case JOY:
               smile = 0.62F;
               squint = 0.16F;
               brow = 0.13F;
               roll = side * 2.0F;
               opening = 0.06F;
               leftBlink = rightBlink = blink(age, 27.0);
               break;
            case SHY:
               smile = 0.36F;
               brow = 0.14F;
               squint = 0.12F;
               lookX = side * 0.32F * hold;
               lookY = 0.45F * hold;
               lookWeight = 0.8F * hold;
               pitch = 3.2F * hold;
               roll = side * 3.0F;
               blush = 0.48F;
               leftBlink = rightBlink = Math.max(blink(age, 8.0), blink(age, 32.0));
               break;
            case PLAYFUL:
               smile = 0.48F;
               brow = 0.09F;
               squint = 0.1F;
               lookX = -side * 0.2F * hold;
               roll = -side * 2.7F;
               asym = side * 0.12F * hold;
               leftBlink = blink(age, 34.0);
               rightBlink = blink(age, 34.0);
               if ((seed & 3) == 0) {
                  if (side > 0.0F) {
                     leftBlink = Math.max(leftBlink, blink(age, 18.0));
                  } else {
                     rightBlink = Math.max(rightBlink, blink(age, 18.0));
                  }
               }
               break;
            case SURPRISE:
               float pop = beat(age, 0.0, 4.0, 8.0, 16.0);
               smile = 0.06F;
               brow = 0.45F * pop;
               squint = 0.0F;
               wide = 0.8F * pop;
               opening = 0.32F * pop;
               pitch = -1.8F * pop;
               leftBlink = rightBlink = blink(age, 23.0);
               break;
            case CONCERN:
               smile = -0.12F;
               brow = 0.22F;
               squint = 0.04F;
               pitch = 1.5F;
               roll = side;
               leftBlink = rightBlink = blink(age, 28.0);
               break;
            case FOCUSED:
               smile = 0.0F;
               brow = -0.22F;
               squint = 0.04F;
               lookWeight = 0.8F;
         }

         lookX *= 1.0F - returnLook;
         lookY *= 1.0F - returnLook;
         FacialPose pose = blend(base, smile, brow, squint, mouth);
         if (speech) {
            rightBlink = 0.0F;
            leftBlink = 0.0F;
         }

         FacialActing acting = new FacialActing(lookX, lookY, lookWeight, leftBlink, rightBlink, yaw, pitch, roll, blush, asym, wide);
         pose = pose.acting(base.acting().blend(acting, eyes));
         return new FacialPose(
            m(base.open(), opening, mouth),
            m(base.width(), mood == FacialExpression.Mood.SURPRISE ? 0.72F : 1.0F, mouth),
            pose.smile(),
            m(base.brow(), brow, eyes),
            pose.squint(),
            m(base.jaw(), opening * 0.6F, mouth),
            pose.acting()
         );
      } else {
         return base;
      }
   }

   private FacialExpression() {
   }

   public static enum Mood {
      NEUTRAL,
      WARM,
      JOY,
      SHY,
      PLAYFUL,
      SURPRISE,
      CONCERN,
      FOCUSED;
   }
}
