package pro.webhm.playerinteractions.npc;

public class BreastData {
   public static final int MASK_WIDTH = 8;
   public static final int MASK_HEIGHT = 5;
   public static final long FULL_VOLUME_MASK = 1099511627775L;
   public boolean enabled = false;
   public float size = 0.8F;
   public float separation = 0.0F;
   public float height = -0.5F;
   public float depth = -2.4F;
   public float rotation = 25.0F;
   public float leftPitch;
   public float leftYaw;
   public float leftRoll;
   public float rightPitch;
   public float rightYaw;
   public float rightRoll;
   public boolean physics = true;
   public boolean dualPhysics = false;
   public float bounce = 1.0F;
   public boolean jacket = true;
   public boolean pixelVolume = false;
   public long volumeMask = 1099511627775L;
   public String volumePixels = "";
   public int volumePixelsScale = 0;

   public void normalizeRotations() {
      this.leftPitch = angle(this.leftPitch);
      this.leftYaw = angle(this.leftYaw);
      this.leftRoll = angle(this.leftRoll);
      this.rightPitch = angle(this.rightPitch);
      this.rightYaw = angle(this.rightYaw);
      this.rightRoll = angle(this.rightRoll);
   }

   private static float angle(float v) {
      return Float.isFinite(v) ? Math.max(-180.0F, Math.min(180.0F, v)) : 0.0F;
   }

   public boolean volumeAt(int x, int y) {
      return x >= 0 && x < 8 && y >= 0 && y < 5 ? (this.volumeMask & 1L << y * 8 + x) != 0L : false;
   }

   public void setVolumeAt(int x, int y, boolean value) {
      if (x >= 0 && x < 8 && y >= 0 && y < 5) {
         long bit = 1L << y * 8 + x;
         if (value) {
            this.volumeMask |= bit;
         } else {
            this.volumeMask &= ~bit;
         }

         this.volumeMask &= 1099511627775L;
      }
   }

   public static int safePixelScale(int scale) {
      return Math.max(1, Math.min(8, scale));
   }

   public int pixelMaskWidth(int scale) {
      return 8 * safePixelScale(scale);
   }

   public int pixelMaskHeight(int scale) {
      return 5 * safePixelScale(scale);
   }

   public void ensureVolumePixelScale(int requestedScale) {
      int targetScale = safePixelScale(requestedScale);
      int targetWidth = this.pixelMaskWidth(targetScale);
      int targetHeight = this.pixelMaskHeight(targetScale);
      int oldScale = safePixelScale(this.volumePixelsScale);
      int oldWidth = this.pixelMaskWidth(oldScale);
      int oldHeight = this.pixelMaskHeight(oldScale);
      boolean hasExact = this.volumePixels != null && this.volumePixelsScale > 0 && this.volumePixels.length() == oldWidth * oldHeight;
      if (!hasExact || oldScale != targetScale) {
         StringBuilder converted = new StringBuilder(targetWidth * targetHeight);

         for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
               boolean selected;
               if (hasExact) {
                  int sourceX = x * oldWidth / targetWidth;
                  int sourceY = y * oldHeight / targetHeight;
                  selected = this.volumePixels.charAt(sourceY * oldWidth + sourceX) == '1';
               } else {
                  selected = this.volumeAt(x / targetScale, y / targetScale);
               }

               converted.append((char)(selected ? '1' : '0'));
            }
         }

         this.volumePixels = converted.toString();
         this.volumePixelsScale = targetScale;
      }
   }

   public boolean volumePixelAt(int x, int y, int scale) {
      this.ensureVolumePixelScale(scale);
      int width = this.pixelMaskWidth(scale);
      int height = this.pixelMaskHeight(scale);
      return x >= 0 && x < width && y >= 0 && y < height && this.volumePixels.charAt(y * width + x) == '1';
   }

   public void setVolumePixelAt(int x, int y, int scale, boolean value) {
      this.ensureVolumePixelScale(scale);
      int width = this.pixelMaskWidth(scale);
      int height = this.pixelMaskHeight(scale);
      if (x >= 0 && x < width && y >= 0 && y < height) {
         char[] pixels = this.volumePixels.toCharArray();
         pixels[y * width + x] = (char)(value ? 49 : 48);
         this.volumePixels = new String(pixels);
      }
   }

   public void fillVolumePixels(int scale, boolean value) {
      int safeScale = safePixelScale(scale);
      int count = this.pixelMaskWidth(safeScale) * this.pixelMaskHeight(safeScale);
      this.volumePixels = String.valueOf((char)(value ? '1' : '0')).repeat(count);
      this.volumePixelsScale = safeScale;
      this.volumeMask = value ? 1099511627775L : 0L;
   }

   public float depthScale() {
      return 0.15F + Math.max(0.0F, Math.min(1.0F, this.size)) * 3.35F;
   }

   public void reset() {
      this.enabled = false;
      this.size = 0.8F;
      this.separation = 0.0F;
      this.height = -0.5F;
      this.depth = -2.4F;
      this.rotation = 25.0F;
      this.leftPitch = this.leftYaw = this.leftRoll = this.rightPitch = this.rightYaw = this.rightRoll = 0.0F;
      this.physics = true;
      this.dualPhysics = false;
      this.bounce = 1.0F;
      this.jacket = true;
      this.pixelVolume = false;
      this.volumeMask = 1099511627775L;
      this.volumePixels = "";
      this.volumePixelsScale = 0;
   }
}
