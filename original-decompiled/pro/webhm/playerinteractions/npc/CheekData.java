package pro.webhm.playerinteractions.npc;

public final class CheekData {
   public boolean enabled = true;
   public float x = 2.5F;
   public float y = 6.5F;
   public float width = 1.7F;
   public float height = 1.1F;
   public float pull = 0.12F;
   public float sideways = 0.04F;
   public float vertical = 0.0F;
   public float softness = 2.0F;

   public void normalize() {
      this.x = c(this.x, 0.5F, 3.5F);
      this.y = c(this.y, 0.5F, 7.5F);
      this.width = c(this.width, 0.3F, Math.min(3.0F, 2.0F * (3.95F - this.x)));
      this.height = c(this.height, 0.3F, Math.min(2.5F, 2.0F * Math.min(this.y - 0.05F, 7.95F - this.y)));
      this.pull = c(this.pull, 0.0F, 0.18F);
      this.sideways = c(this.sideways, -0.08F, 0.08F);
      this.vertical = c(this.vertical, -0.06F, 0.06F);
      this.softness = c(this.softness, 1.0F, 5.0F);
   }

   public float top(HeadDynamicsData h) {
      return Math.max(0.05F, this.y - this.height / 2.0F);
   }

   public float bottom(HeadDynamicsData h) {
      return Math.min(7.95F, this.y + this.height / 2.0F);
   }

   public void mirrorShape(CheekData source) {
      this.x = source.x;
      this.y = source.y;
      this.enabled = source.enabled;
      this.width = source.width;
      this.height = source.height;
      this.pull = source.pull;
      this.sideways = source.sideways;
      this.vertical = source.vertical;
      this.softness = source.softness;
   }

   public CheekData copy() {
      CheekData c = new CheekData();
      c.enabled = this.enabled;
      c.x = this.x;
      c.y = this.y;
      c.width = this.width;
      c.height = this.height;
      c.pull = this.pull;
      c.sideways = this.sideways;
      c.vertical = this.vertical;
      c.softness = this.softness;
      return c;
   }

   public float bottom() {
      return Math.min(7.85F, this.y + this.height / 2.0F);
   }

   private static float c(float v, float a, float b) {
      return Float.isFinite(v) ? Math.max(a, Math.min(b, v)) : a;
   }
}
