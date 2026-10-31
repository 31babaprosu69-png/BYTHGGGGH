package pro.webhm.playerinteractions.npc;

public enum NpcBone {
   ROOT("root", "Всё тело", true, 180.0F, 180.0F, 180.0F, -180.0F, -180.0F, -180.0F),
   HEAD("head", "Голова", false, 65.0F, 90.0F, 40.0F, -65.0F, -90.0F, -40.0F),
   CHEST("chest", "Грудь", false, 45.0F, 40.0F, 30.0F, -35.0F, -40.0F, -30.0F),
   BODY("body", "Пояс", false, 55.0F, 45.0F, 35.0F, -30.0F, -45.0F, -35.0F),
   RIGHT_ARM("right_arm", "П. плечо", false, 180.0F, 100.0F, 180.0F, -180.0F, -100.0F, -180.0F),
   RIGHT_FOREARM("right_forearm", "П. локоть", false, 0.0F, 35.0F, 42.0F, -155.0F, -35.0F, -42.0F),
   LEFT_ARM("left_arm", "Л. плечо", false, 180.0F, 100.0F, 180.0F, -180.0F, -100.0F, -180.0F),
   LEFT_FOREARM("left_forearm", "Л. локоть", false, 0.0F, 35.0F, 42.0F, -155.0F, -35.0F, -42.0F),
   RIGHT_LEG("right_leg", "П. бедро", false, 95.0F, 55.0F, 70.0F, -120.0F, -55.0F, -70.0F),
   RIGHT_SHIN("right_shin", "П. колено", false, 155.0F, 20.0F, 15.0F, 0.0F, -20.0F, -15.0F),
   LEFT_LEG("left_leg", "Л. бедро", false, 95.0F, 55.0F, 70.0F, -120.0F, -55.0F, -70.0F),
   LEFT_SHIN("left_shin", "Л. колено", false, 155.0F, 20.0F, 15.0F, 0.0F, -20.0F, -15.0F);

   public final String id;
   public final String label;
   public final boolean root;
   public final float maxX;
   public final float maxY;
   public final float maxZ;
   public final float minX;
   public final float minY;
   public final float minZ;

   private NpcBone(String id, String label, boolean root, float maxX, float maxY, float maxZ, float minX, float minY, float minZ) {
      this.id = id;
      this.label = label;
      this.root = root;
      this.maxX = maxX;
      this.maxY = maxY;
      this.maxZ = maxZ;
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
   }

   public boolean isHinge() {
      return this == RIGHT_FOREARM || this == LEFT_FOREARM || this == RIGHT_SHIN || this == LEFT_SHIN;
   }

   public float hingeSign() {
      return this != RIGHT_FOREARM && this != LEFT_FOREARM ? 1.0F : -1.0F;
   }

   public float hingeMax() {
      return Math.max(Math.abs(this.minX), Math.abs(this.maxX));
   }

   public static NpcBone byId(String id) {
      for (NpcBone b : values()) {
         if (b.id.equals(id)) {
            return b;
         }
      }

      return HEAD;
   }

   public boolean hasOffsets() {
      return switch (this) {
         case ROOT, HEAD, CHEST, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG -> true;
         default -> false;
      };
   }

   public void clamp(BoneTransform t) {
      t.rx = Math.max(this.minX, Math.min(this.maxX, t.rx));
      t.ry = Math.max(this.minY, Math.min(this.maxY, t.ry));
      t.rz = Math.max(this.minZ, Math.min(this.maxZ, t.rz));
      if (!this.hasOffsets()) {
         t.height = 0.0F;
         t.shiftX = 0.0F;
         t.shiftZ = 0.0F;
      } else if (this.root) {
         t.height = Math.max(-24.0F, Math.min(24.0F, t.height));
         t.shiftX = Math.max(-32.0F, Math.min(32.0F, t.shiftX));
         t.shiftZ = Math.max(-32.0F, Math.min(32.0F, t.shiftZ));
      } else {
         t.height = Math.max(-16.0F, Math.min(16.0F, t.height));
         t.shiftX = Math.max(-16.0F, Math.min(16.0F, t.shiftX));
         t.shiftZ = Math.max(-16.0F, Math.min(16.0F, t.shiftZ));
      }
   }

   public BoneTransform clamped(BoneTransform t) {
      BoneTransform c = t.copy();
      this.clamp(c);
      return c;
   }
}
