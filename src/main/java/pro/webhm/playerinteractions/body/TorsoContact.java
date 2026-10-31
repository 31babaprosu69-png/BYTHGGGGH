package pro.webhm.playerinteractions.body;

import org.joml.Vector3f;

public final class TorsoContact {
   public float top = 4.0F;
   public float bottom = 4.0F;
   public float outer;
   public int face;
   public float a;
   public float y;
   public float radius;
   public float depth;

   public float half(float at) {
      return this.top + (this.bottom - this.top) * (at + 6.0F) / 6.0F;
   }

   public float width() {
      return this.face < 2 ? 2.0F * Math.min(this.top, this.bottom) : 4.0F;
   }

   public float outerU(float horizontal) {
      return this.face < 2
         ? (float)(this.face == 0 ? 24 : 36) + horizontal * 4.0F / Math.max(4.25F, Math.max(this.top, this.bottom) + 0.25F)
         : (float)(this.face == 2 ? 18 : 30) + horizontal * 2.0F / 2.25F;
   }

   public void target(float u, float v, float dx, float dy, float area, float requestedRadius) {
      this.radius = Math.max(0.1F, Math.min(requestedRadius, Math.min(2.8F, this.width() * 0.5F - 0.1F)));
      float bound = this.width() * 0.5F - this.radius - 0.05F;
      this.a = clamp((u - 0.5F) * this.width() + dx * area, -Math.max(0.0F, bound), Math.max(0.0F, bound));
      this.y = clamp(-6.0F + v * 6.0F + dy * area, -6.0F + this.radius + 0.05F, -this.radius - 0.05F);
   }

   public Vector3f position(float inflation, Vector3f out) {
      return this.face < 2
         ? out.set(this.a, this.y, (float)(this.face == 0 ? -1 : 1) * (2.0F + inflation))
         : out.set((float)(this.face == 2 ? -1 : 1) * (this.half(this.y) + inflation), this.y, this.a);
   }

   public Vector3f normal(Vector3f out) {
      return this.face < 2
         ? out.set(0.0F, 0.0F, this.face == 0 ? -1.0F : 1.0F)
         : out.set(this.face == 2 ? -1.0F : 1.0F, -(this.bottom - this.top) / 6.0F, 0.0F).normalize();
   }

   public float amount(float horizontal, float vertical) {
      float x = (horizontal - this.a) / this.radius;
      float z = (vertical - this.y) / this.radius;
      float q = x * x + z * z;
      if (!(q >= 1.0F) && Float.isFinite(q)) {
         float w = 1.0F - q;
         return this.depth * w * w * w;
      } else {
         return 0.0F;
      }
   }

   public Vector3f displaced(float x, float y, float z, Vector3f out) {
      float d = this.amount(this.face < 2 ? x : z, y);
      return out.set(x + (this.face == 2 ? d : (this.face == 3 ? -d : 0.0F)), y, z + (this.face == 0 ? d : (this.face == 1 ? -d : 0.0F)));
   }

   public Vector3f deformedNormal(float x, float y, float z, Vector3f out) {
      float h = (this.face < 2 ? x : z) - this.a;
      float k = y - this.y;
      float q = (h * h + k * k) / (this.radius * this.radius);
      float da = 0.0F;
      float dy = 0.0F;
      if (q < 1.0F) {
         float w = 1.0F - q;
         float f = -6.0F * this.depth * w * w / (this.radius * this.radius);
         da = f * h;
         dy = f * k;
      }

      return this.face < 2
         ? out.set(da, dy, this.face == 0 ? -1.0F : 1.0F).normalize()
         : out.set(this.face == 2 ? -1.0F : 1.0F, dy - (this.bottom - this.top) / 6.0F, da).normalize();
   }

   public static float clamp(float x, float min, float max) {
      return Math.max(min, Math.min(max, x));
   }

   public int pick(Vector3f from, Vector3f to, Vector3f hit) {
      float best = Float.POSITIVE_INFINITY;
      int selected = -1;
      Vector3f n = new Vector3f();
      Vector3f p = new Vector3f();
      Vector3f delta = new Vector3f(to).sub(from);

      for (int f = 0; f < 4; f++) {
         this.face = f;
         this.a = 0.0F;
         this.y = -3.0F;
         this.position(this.outer, p);
         this.normal(n);
         float denom = n.dot(delta);
         if (!(Math.abs(denom) < 1.0E-7F)) {
            float t = n.dot(p.sub(from)) / denom;
            if (!(t < 0.0F) && !(t > 1.0F) && !(t >= best)) {
               p.set(from).fma(t, delta);
               if (!(p.y < -6.0F) && !(p.y > 0.0F) && !(Math.abs(p.x) > this.half(p.y) + this.outer + 0.001F) && !(Math.abs(p.z) > 2.0F + this.outer + 0.001F)) {
                  best = t;
                  selected = f;
                  hit.set(p);
               }
            }
         }
      }

      return selected;
   }
}
