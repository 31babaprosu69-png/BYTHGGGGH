package pro.webhm.playerinteractions.npc;

public class PortraitData {
   public static final int MODE_LIVE = 0;
   public static final int MODE_FRAME = 1;
   public static final int MODE_POSE = 2;
   public static final int MODE_CLIP = 3;
   public int mode = 0;
   public String animation = "";
   public int frameTick = 0;
   public float animSpeed = 1.0F;
   public boolean animLoop = true;
   public float zoom = 2.6F;
   public float crop = 0.9F;
   public float yaw = 0.0F;
   public float pitch = 0.0F;
   public boolean followMouse = false;
   public boolean breastPhysics = false;

   public static String modeLabel(int m) {
      return switch (m) {
         case 1 -> "стоп-кадр";
         case 2 -> "статичная поза";
         case 3 -> "своя анимация";
         default -> "живая анимация";
      };
   }

   public String modeLabel() {
      return modeLabel(this.mode);
   }

   public int nextMode() {
      return (this.mode + 1) % 4;
   }

   public boolean usesClip() {
      return this.mode == 1 || this.mode == 3;
   }

   public String clipLabel() {
      return this.animation != null && !this.animation.isBlank() ? this.animation : "клип самого NPC";
   }

   public void framingWaist() {
      this.zoom = 2.6F;
      this.crop = 0.9F;
   }

   public void framingFull() {
      this.zoom = 1.0F;
      this.crop = -0.15F;
   }

   public PortraitData copy() {
      PortraitData c = new PortraitData();
      c.mode = this.mode;
      c.animation = this.animation;
      c.frameTick = this.frameTick;
      c.animSpeed = this.animSpeed;
      c.animLoop = this.animLoop;
      c.zoom = this.zoom;
      c.crop = this.crop;
      c.yaw = this.yaw;
      c.pitch = this.pitch;
      c.followMouse = this.followMouse;
      c.breastPhysics = this.breastPhysics;
      return c;
   }
}
