package pro.webhm.playerinteractions.npc;

public final class FaceTouchData {
   public FaceTouchData.Region left = new FaceTouchData.Region(2.5F, 6.5F, 1.7F, 1.1F, 0);
   public FaceTouchData.Region right = new FaceTouchData.Region(-2.5F, 6.5F, 1.7F, 1.1F, 0);
   public FaceTouchData.Region forehead = new FaceTouchData.Region(0.0F, 1.3F, 3.0F, 1.6F, 7);
   public FaceTouchData.Region chin = new FaceTouchData.Region(0.0F, 7.4F, 2.0F, 0.6F, 7);

   public FaceTouchData.Region[] regions() {
      return new FaceTouchData.Region[]{this.left, this.right, this.forehead, this.chin};
   }

   public void normalize() {
      if (this.left == null) {
         this.left = new FaceTouchData.Region(2.5F, 6.5F, 1.7F, 1.1F, 0);
      }

      if (this.right == null) {
         this.right = new FaceTouchData.Region(-2.5F, 6.5F, 1.7F, 1.1F, 0);
      }

      if (this.forehead == null) {
         this.forehead = new FaceTouchData.Region(0.0F, 1.3F, 3.0F, 1.6F, 7);
      }

      if (this.chin == null) {
         this.chin = new FaceTouchData.Region(0.0F, 7.4F, 2.0F, 0.6F, 7);
      }

      for (FaceTouchData.Region r : this.regions()) {
         r.normalize();
      }
   }

   private static float c(float v, float a, float b) {
      return Float.isFinite(v) ? Math.max(a, Math.min(b, v)) : a;
   }

   public static final class Region {
      public boolean enabled = true;
      public float x;
      public float y;
      public float width;
      public float height;
      public int reaction;

      public Region() {
      }

      public Region(float x, float y, float w, float h, int r) {
         this.x = x;
         this.y = y;
         this.width = w;
         this.height = h;
         this.reaction = r;
      }

      public void normalize() {
         this.x = FaceTouchData.c(this.x, -3.5F, 3.5F);
         this.y = FaceTouchData.c(this.y, 0.0F, 8.0F);
         this.width = FaceTouchData.c(this.width, 0.2F, 5.0F);
         this.height = FaceTouchData.c(this.height, 0.2F, 4.0F);
         this.reaction = this.reaction == 0 ? 0 : 7;
      }
   }
}
