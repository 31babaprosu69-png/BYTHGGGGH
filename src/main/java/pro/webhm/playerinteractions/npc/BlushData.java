package pro.webhm.playerinteractions.npc;

public final class BlushData {
   public boolean enabled = true;
   public boolean onPet = true;
   public boolean onChest = true;
   public float strength = 0.55F;
   public float softness = 1.6F;
   public float riseTicks = 50.0F;
   public float fadeTicks = 40.0F;
   public int color = -955528;
   public float colorBoost = 1.0F;
   public BlushData.Region left = new BlushData.Region(2.4F);
   public BlushData.Region right = new BlushData.Region(-2.4F);

   public boolean respondsTo(int zone) {
      return this.enabled && (zone == 7 && this.onPet || zone == 4 && this.onChest);
   }

   public BlushData.Region region(int side) {
      return side < 0 ? this.right : this.left;
   }

   public void normalize() {
      if (this.left == null) {
         this.left = new BlushData.Region(2.4F);
      }

      if (this.right == null) {
         this.right = new BlushData.Region(-2.4F);
      }

      this.left.normalize();
      this.right.normalize();
      this.right.width = this.left.width;
      this.right.height = this.left.height;
      this.strength = c(this.strength, 0.0F, 1.0F);
      this.colorBoost = c(this.colorBoost, 1.0F, 4.0F);
      this.softness = c(this.softness, 0.5F, 4.0F);
      this.riseTicks = c(this.riseTicks, 40.0F, 160.0F);
      this.fadeTicks = c(this.fadeTicks, 1.0F, 80.0F);
      this.color |= -16777216;
   }

   private static float c(float x, float a, float b) {
      return Float.isFinite(x) ? Math.max(a, Math.min(b, x)) : a;
   }

   public static final class Region {
      public float x;
      public float y = 6.1F;
      public float width = 2.0F;
      public float height = 1.2F;

      public Region() {
      }

      public Region(float x) {
         this.x = x;
      }

      public void normalize() {
         this.x = BlushData.c(this.x, -3.5F, 3.5F);
         this.y = BlushData.c(this.y, 0.0F, 8.0F);
         this.width = BlushData.c(this.width, 0.2F, 5.0F);
         this.height = BlushData.c(this.height, 0.2F, 3.0F);
      }
   }
}
