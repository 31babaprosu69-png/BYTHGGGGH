package pro.webhm.playerinteractions.adornments;

import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class AdornmentContact {
   private final Vector3f center = new Vector3f();
   private final Vector3f extent = new Vector3f();
   private final Vector3f x = new Vector3f();
   private final Vector3f y = new Vector3f();
   private final Vector3f z = new Vector3f();
   private final Vector3f c = new Vector3f();
   private final Vector3f e = new Vector3f();
   private final Vector3f u = new Vector3f();
   private final Vector3f v = new Vector3f();
   private final Vector3f w = new Vector3f();
   private final Vector3f axis = new Vector3f();
   private final Vector3f normal = new Vector3f();
   private final Vector3f minimumNormal = new Vector3f();
   private Matrix4fc measuredInverse;
   private float minimumDepth;

   public void set(Vector3fc start, Quaternionfc orientation, float width, float length, float depth, float inset) {
      inset = Math.min(inset, length * 0.5F);
      orientation.transform(this.x.set(width * 0.5F + 0.015F, 0.0F, 0.0F));
      orientation.transform(this.y.set(0.0F, (length - inset) * 0.5F, 0.0F));
      orientation.transform(this.z.set(0.0F, 0.0F, depth * 0.5F + 0.015F));
      orientation.transform(this.center.set(0.0F, (length + inset) * 0.5F, 0.0F)).add(start);
      this.extent
         .set(
            Math.abs(this.x.x) + Math.abs(this.y.x) + Math.abs(this.z.x),
            Math.abs(this.x.y) + Math.abs(this.y.y) + Math.abs(this.z.y),
            Math.abs(this.x.z) + Math.abs(this.y.z) + Math.abs(this.z.z)
         );
   }

   public boolean intersects(AdornmentFrame.Box box) {
      return this.intersects(box, box.hi.y);
   }

   public boolean separation(AdornmentFrame.Box box, float maxY, Vector3f out) {
      this.measuredInverse = box.inverse;
      this.minimumDepth = Float.POSITIVE_INFINITY;

      boolean hit;
      try {
         hit = this.intersects(box, maxY);
      } finally {
         this.measuredInverse = null;
      }

      if (hit && Float.isFinite(this.minimumDepth)) {
         out.set(this.minimumNormal).mul(this.minimumDepth + 1.0E-4F);
      } else {
         out.zero();
      }

      return hit;
   }

   public boolean intersects(AdornmentFrame.Box box, float maxY) {
      if (!box.overlaps(this.center, this.extent)) {
         return false;
      } else {
         maxY = Math.min(box.hi.y, maxY);
         if (maxY <= box.lo.y) {
            return false;
         } else {
            box.inverse.transformPosition(this.center, this.c);
            this.c.sub((box.lo.x + box.hi.x) * 0.5F, (box.lo.y + maxY) * 0.5F, (box.lo.z + box.hi.z) * 0.5F);
            this.e.set(box.hi).sub(box.lo).mul(0.5F);
            this.e.y = (maxY - box.lo.y) * 0.5F;
            box.inverse.transformDirection(this.x, this.u);
            box.inverse.transformDirection(this.y, this.v);
            box.inverse.transformDirection(this.z, this.w);
            if (this.separated(this.axis.set(1.0F, 0.0F, 0.0F))
               || this.separated(this.axis.set(0.0F, 1.0F, 0.0F))
               || this.separated(this.axis.set(0.0F, 0.0F, 1.0F))) {
               return false;
            } else {
               return !this.separated(this.u.cross(this.v, this.axis))
                     && !this.separated(this.v.cross(this.w, this.axis))
                     && !this.separated(this.w.cross(this.u, this.axis))
                  ? !this.crossSeparated(this.u) && !this.crossSeparated(this.v) && !this.crossSeparated(this.w)
                  : false;
            }
         }
      }
   }

   private boolean crossSeparated(Vector3f a) {
      return this.separated(this.axis.set(0.0F, a.z, -a.y)) || this.separated(this.axis.set(-a.z, 0.0F, a.x)) || this.separated(this.axis.set(a.y, -a.x, 0.0F));
   }

   private boolean separated(Vector3f a) {
      if (a.lengthSquared() < 1.0E-14F) {
         return false;
      } else {
         float radius = this.e.x * Math.abs(a.x)
            + this.e.y * Math.abs(a.y)
            + this.e.z * Math.abs(a.z)
            + Math.abs(this.u.dot(a))
            + Math.abs(this.v.dot(a))
            + Math.abs(this.w.dot(a));
         float signed = this.c.dot(a);
         if (Math.abs(signed) >= radius - 1.0E-6F * (float)Math.sqrt((double)a.lengthSquared())) {
            return true;
         } else {
            if (this.measuredInverse != null) {
               Matrix4fc m = this.measuredInverse;
               this.normal
                  .set(
                     m.m00() * a.x + m.m01() * a.y + m.m02() * a.z,
                     m.m10() * a.x + m.m11() * a.y + m.m12() * a.z,
                     m.m20() * a.x + m.m21() * a.y + m.m22() * a.z
                  );
               float norm = this.normal.length();
               if (!Float.isFinite(norm) || norm < 1.0E-8F) {
                  return false;
               }

               float depth = (radius - Math.abs(signed)) / norm;
               if (depth < this.minimumDepth) {
                  this.minimumDepth = depth;
                  this.minimumNormal.set(this.normal).mul((float)(signed < 0.0F ? -1 : 1) / norm);
               }
            }

            return false;
         }
      }
   }
}
