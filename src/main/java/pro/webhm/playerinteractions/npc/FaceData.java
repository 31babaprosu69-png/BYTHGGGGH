package pro.webhm.playerinteractions.npc;

public class FaceData {
   public static final int SIZE = 8;
   public static final int FRAMES = 4;
   public static final int EYES_STATIC = 0;
   public static final int EYES_BLINK = 1;
   public static final int EYES_LOOP = 2;
   public boolean enabled = false;
   public int eyeMode = 1;
   public boolean followPlayer = true;
   public boolean skinBlink = false;
   public String skinEyePixels = "";
   public int skinEyeScale = 0;
   public int frameTicks = 4;
   public int[][] eyeFrames = frames();

   private static int[][] frames() {
      int[][] result = new int[4][];

      for (int i = 0; i < 4; i++) {
         result[i] = new int[64];
      }

      return result;
   }

   public void normalize() {
      this.eyeMode = Math.max(0, Math.min(2, this.eyeMode));
      this.frameTicks = Math.max(1, Math.min(20, this.frameTicks));
      this.eyeFrames = normalizeFrames(this.eyeFrames);
      if (this.skinEyePixels == null) {
         this.skinEyePixels = "";
      }
   }

   private static int[][] normalizeFrames(int[][] source) {
      if (source != null && source.length == 4) {
         boolean valid = true;

         for (int[] frame : source) {
            if (frame == null || frame.length != 64) {
               valid = false;
               break;
            }
         }

         if (valid) {
            return source;
         }
      }

      int[][] result = frames();
      if (source == null) {
         return result;
      } else {
         for (int framex = 0; framex < Math.min(4, source.length); framex++) {
            if (source[framex] != null) {
               System.arraycopy(source[framex], 0, result[framex], 0, Math.min(64, source[framex].length));
            }
         }

         return result;
      }
   }

   public boolean empty() {
      this.normalize();

      for (int[] frame : this.eyeFrames) {
         for (int color : frame) {
            if (color >>> 24 != 0) {
               return false;
            }
         }
      }

      return true;
   }

   public static int safeSkinScale(int scale) {
      return Math.max(1, Math.min(8, scale));
   }

   public void ensureSkinEyeScale(int requestedScale) {
      int targetScale = safeSkinScale(requestedScale);
      int targetSide = 8 * targetScale;
      int oldScale = safeSkinScale(this.skinEyeScale);
      int oldSide = 8 * oldScale;
      boolean exact = this.skinEyeScale > 0 && this.skinEyePixels != null && this.skinEyePixels.length() == oldSide * oldSide;
      if (!exact || oldScale != targetScale) {
         StringBuilder converted = new StringBuilder(targetSide * targetSide);

         for (int y = 0; y < targetSide; y++) {
            for (int x = 0; x < targetSide; x++) {
               boolean selected = exact && this.skinEyePixels.charAt(y * oldSide / targetSide * oldSide + x * oldSide / targetSide) == '1';
               converted.append((char)(selected ? '1' : '0'));
            }
         }

         this.skinEyePixels = converted.toString();
         this.skinEyeScale = targetScale;
      }
   }

   public boolean skinEyeAt(int x, int y, int scale) {
      this.ensureSkinEyeScale(scale);
      int side = 8 * safeSkinScale(scale);
      return x >= 0 && x < side && y >= 0 && y < side && this.skinEyePixels.charAt(y * side + x) == '1';
   }

   public void setSkinEyeAt(int x, int y, int scale, boolean value) {
      this.ensureSkinEyeScale(scale);
      int side = 8 * safeSkinScale(scale);
      if (x >= 0 && x < side && y >= 0 && y < side) {
         char[] pixels = this.skinEyePixels.toCharArray();
         pixels[y * side + x] = (char)(value ? 49 : 48);
         this.skinEyePixels = new String(pixels);
      }
   }

   public void clearSkinEyes(int scale) {
      int safe = safeSkinScale(scale);
      this.skinEyeScale = safe;
      this.skinEyePixels = "0".repeat(8 * safe * 8 * safe);
   }
}
