package pro.webhm.playerinteractions.npc;

import java.util.Objects;

public class ItemPose {
   public String attachment = "";
   public boolean hidden;
   public float rx;
   public float ry;
   public float rz;
   public float ox;
   public float oy;
   public float oz;
   public float scale = 1.0F;

   public void reset() {
      this.attachment = "";
      this.hidden = false;
      this.rx = this.ry = this.rz = 0.0F;
      this.ox = this.oy = this.oz = 0.0F;
      this.scale = 1.0F;
   }

   public static ItemPose lerp(ItemPose a, ItemPose b, float t) {
      if (!Objects.equals(a.attachment, b.attachment)) {
         return (t >= 1.0F ? b : a).copy();
      } else {
         ItemPose r = new ItemPose();
         r.attachment = a.attachment;
         r.hidden = t >= 1.0F ? b.hidden : a.hidden;
         r.rx = l(a.rx, b.rx, t);
         r.ry = l(a.ry, b.ry, t);
         r.rz = l(a.rz, b.rz, t);
         r.ox = l(a.ox, b.ox, t);
         r.oy = l(a.oy, b.oy, t);
         r.oz = l(a.oz, b.oz, t);
         r.scale = l(a.scale, b.scale, t);
         return r;
      }
   }

   private static float l(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public ItemPose copy() {
      ItemPose p = new ItemPose();
      p.rx = this.rx;
      p.ry = this.ry;
      p.rz = this.rz;
      p.ox = this.ox;
      p.oy = this.oy;
      p.oz = this.oz;
      p.scale = this.scale;
      p.attachment = this.attachment;
      p.hidden = this.hidden;
      return p;
   }
}
