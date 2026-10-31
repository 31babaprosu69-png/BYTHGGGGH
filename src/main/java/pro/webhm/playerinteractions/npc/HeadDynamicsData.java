package pro.webhm.playerinteractions.npc;

public class HeadDynamicsData {
   public BlushData blush = new BlushData();
   public CheekData rightCheek;
   public CheekData cheeks = new CheekData();
   public FaceTouchData touchAreas = new FaceTouchData();
   public boolean skinGlowEnabled = true;
   public String glowPixels = "";
   public transient boolean portraitEyes = false;
   public transient float sceneWinkLeft;
   public transient float sceneWinkRight;
   public boolean customRigEnabled = false;
   public float pupilLeftU = -1.0F;
   public float pupilLeftV = -1.0F;
   public float pupilRightU = -1.0F;
   public float pupilRightV = -1.0F;
   public float eyeWhiteU = 10.0F;
   public float eyeWhiteV = 12.0F;
   public float eyelidU = 10.0F;
   public float eyelidV = 10.0F;
   public float pupilWidth = 0.5F;
   public float pupilHeight = 1.0F;
   public boolean gazeEnabled = false;
   public float gazeRange = 0.6F;
   public float gazeSmoothness = 8.0F;
   public float hairDamping = 0.8F;
   public float blinkVariation = 0.35F;
   public boolean blinkEnabled = false;
   public float eyeY = 3.0F;
   public float eyeWidth = 2.0F;
   public float eyeHeight = 1.0F;
   public float eyeSpacing = 2.0F;
   public int blinkInterval = 70;
   public int blinkDuration = 6;
   public boolean hairEnabled = false;
   public String hairPixels = "";
   public int hairScale = 0;
   public float hairAxisY = 2.0F;
   public float hairMotion = 8.0F;
   public float hairAngle = 0.0F;
   public int hairBones = 4;
   public boolean earsEnabled = false;
   public float earLength = 3.5F;
   public float earAngle = 8.0F;
   public float earMotion = 4.0F;

   public CheekData cheek(int side) {
      if (this.rightCheek == null) {
         this.rightCheek = this.cheeks.copy();
      }

      return side < 0 ? this.rightCheek : this.cheeks;
   }

   public void syncCheekAreas() {
      for (int side : new int[]{1, -1}) {
         CheekData c = this.cheek(side);
         FaceTouchData.Region r = side > 0 ? this.touchAreas.left : this.touchAreas.right;
         r.x = (float)side * c.x;
         r.y = c.y;
         r.width = Math.min(c.width, 2.0F * (3.95F - c.x));
         r.height = c.height;
      }
   }

   public boolean overlapsEye(float x0, float y0, float x1, float y1) {
      for (int side : new int[]{-1, 1}) {
         float x = (float)side * this.eyeSpacing;
         if (x1 > x - this.eyeWidth / 2.0F - 0.12F
            && x0 < x + this.eyeWidth / 2.0F + 0.12F
            && y1 > this.eyeY - 0.12F
            && y0 < this.eyeY + this.eyeHeight + 0.12F) {
            return true;
         }
      }

      return false;
   }

   public void normalize() {
      this.glowPixels = SkinGlowMask.normalize(this.glowPixels);
      if (this.blush == null) {
         this.blush = new BlushData();
      }

      this.blush.normalize();
      if (this.cheeks == null) {
         this.cheeks = new CheekData();
      }

      this.cheeks.normalize();
      if (this.rightCheek == null) {
         this.rightCheek = this.cheeks.copy();
      }

      this.rightCheek.normalize();
      this.rightCheek.mirrorShape(this.cheeks);
      if (this.touchAreas == null) {
         this.touchAreas = new FaceTouchData();
      }

      this.touchAreas.normalize();
      this.syncCheekAreas();
      this.eyeWhiteU = clamp(this.eyeWhiteU, 0.0F, 63.0F);
      this.eyeWhiteV = clamp(this.eyeWhiteV, 0.0F, 63.0F);
      this.eyelidU = clamp(this.eyelidU, 0.0F, 63.0F);
      this.eyelidV = clamp(this.eyelidV, 0.0F, 63.0F);
      this.pupilWidth = clamp(this.pupilWidth, 0.25F, 2.0F);
      this.pupilHeight = clamp(this.pupilHeight, 0.25F, 3.0F);
      this.gazeRange = clamp(this.gazeRange, 0.0F, 2.0F);
      this.gazeSmoothness = clamp(this.gazeSmoothness, 1.0F, 20.0F);
      this.hairDamping = clamp(this.hairDamping, 0.5F, 0.95F);
      this.blinkVariation = clamp(this.blinkVariation, 0.0F, 0.8F);
      if (this.hairPixels == null) {
         this.hairPixels = "";
      }

      this.eyeY = clamp(this.eyeY, 0.0F, 7.0F);
      this.eyeWidth = clamp(this.eyeWidth, 0.5F, 4.0F);
      this.eyeHeight = clamp(this.eyeHeight, 0.5F, 3.0F);
      this.eyeSpacing = clamp(this.eyeSpacing, 0.5F, 3.5F);
      this.blinkInterval = Math.max(20, Math.min(200, this.blinkInterval));
      this.blinkDuration = Math.max(2, Math.min(16, this.blinkDuration));
      this.hairAxisY = clamp(this.hairAxisY, 0.0F, 8.0F);
      this.hairMotion = clamp(this.hairMotion, 0.0F, 25.0F);
      this.hairAngle = clamp(this.hairAngle, -35.0F, 35.0F);
      this.hairBones = Math.max(2, Math.min(8, this.hairBones));
      this.earLength = clamp(this.earLength, 1.0F, 8.0F);
      this.earAngle = clamp(this.earAngle, -45.0F, 45.0F);
      this.earMotion = clamp(this.earMotion, 0.0F, 20.0F);
   }

   public boolean hasHair() {
      this.normalize();
      return this.hairEnabled;
   }

   public void ensureHairScale(int requestedScale) {
      int targetScale = FaceData.safeSkinScale(requestedScale);
      int targetSide = 8 * targetScale;
      int oldScale = FaceData.safeSkinScale(this.hairScale);
      int oldSide = 8 * oldScale;
      boolean exact = this.hairScale > 0 && this.hairPixels.length() == oldSide * oldSide;
      if (!exact || oldScale != targetScale) {
         StringBuilder converted = new StringBuilder(targetSide * targetSide);

         for (int y = 0; y < targetSide; y++) {
            for (int x = 0; x < targetSide; x++) {
               boolean selected = exact && this.hairPixels.charAt(y * oldSide / targetSide * oldSide + x * oldSide / targetSide) == '1';
               converted.append((char)(selected ? '1' : '0'));
            }
         }

         this.hairPixels = converted.toString();
         this.hairScale = targetScale;
      }
   }

   public boolean hairAt(int x, int y, int scale) {
      this.ensureHairScale(scale);
      int side = 8 * FaceData.safeSkinScale(scale);
      return x >= 0 && x < side && y >= 0 && y < side && this.hairPixels.charAt(y * side + x) == '1';
   }

   public void setHairAt(int x, int y, int scale, boolean value) {
      this.ensureHairScale(scale);
      int side = 8 * FaceData.safeSkinScale(scale);
      if (x >= 0 && x < side && y >= 0 && y < side) {
         char[] pixels = this.hairPixels.toCharArray();
         pixels[y * side + x] = (char)(value ? 49 : 48);
         this.hairPixels = new String(pixels);
      }
   }

   public void clearHair(int scale) {
      int safe = FaceData.safeSkinScale(scale);
      this.hairScale = safe;
      this.hairPixels = "0".repeat(8 * safe * 8 * safe);
   }

   public int visualHash() {
      this.normalize();
      int result = Boolean.hashCode(this.hairEnabled);
      result = 31 * result + this.hairPixels.hashCode();
      return 31 * result + this.hairScale;
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }
}
