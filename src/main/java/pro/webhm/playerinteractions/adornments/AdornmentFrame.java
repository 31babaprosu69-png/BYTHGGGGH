package pro.webhm.playerinteractions.adornments;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class AdornmentFrame {
   public final Matrix4f[] bones = new Matrix4f[AdornmentProject.BONES.length];
   public final Matrix4f toModel = new Matrix4f();
   public final AdornmentFrame.Box[] boxes = new AdornmentFrame.Box[64];
   public int count;
   public float scale = 1.0F;
   private final Vector3f position = new Vector3f();
   private final Vector3f otherPosition = new Vector3f();
   private final Vector3f size = new Vector3f();
   private final Vector3f otherSize = new Vector3f();
   private final Quaternionf orientation = new Quaternionf();
   private final Quaternionf otherOrientation = new Quaternionf();
   private final Matrix4f blended = new Matrix4f();
   private final Vector3f surfaceStart = new Vector3f();
   private final Vector3f surfaceCandidate = new Vector3f();
   private final Vector3f surfaceDirection = new Vector3f();

   public AdornmentFrame() {
      for (int i = 0; i < this.bones.length; i++) {
         this.bones[i] = new Matrix4f();
      }

      for (int i = 0; i < this.boxes.length; i++) {
         this.boxes[i] = new AdornmentFrame.Box();
      }
   }

   public void clear() {
      this.count = 0;
   }

   public boolean samePose(AdornmentFrame other) {
      if (this.count == other.count && this.scale == other.scale) {
         for (int i = 0; i < this.bones.length; i++) {
            if (!this.bones[i].equals(other.bones[i], 1.0E-5F)) {
               return false;
            }
         }

         for (int ix = 0; ix < this.count; ix++) {
            AdornmentFrame.Box a = this.boxes[ix];
            AdornmentFrame.Box b = other.boxes[ix];
            if (!a.matrix.equals(b.matrix, 1.0E-5F) || !a.lo.equals(b.lo) || !a.hi.equals(b.hi)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public void add(Matrix4fc matrix, float x0, float y0, float z0, float x1, float y1, float z1) {
      if (this.count == this.boxes.length) {
         throw new IllegalStateException("Too many body colliders");
      } else {
         AdornmentFrame.Box box = this.boxes[this.count++];
         box.set(matrix, x0, y0, z0, x1, y1, z1);
         box.bone = -1;

         for (int i = 0; i < this.bones.length; i++) {
            if (matrix == this.bones[i]) {
               box.bone = i;
               break;
            }
         }
      }
   }

   public void set(AdornmentFrame source) {
      this.scale = source.scale;
      this.count = source.count;
      this.toModel.set(source.toModel);

      for (int i = 0; i < this.bones.length; i++) {
         this.bones[i].set(source.bones[i]);
      }

      for (int i = 0; i < this.count; i++) {
         AdornmentFrame.Box b = source.boxes[i];
         this.boxes[i].set(b.matrix, b.lo.x, b.lo.y, b.lo.z, b.hi.x, b.hi.y, b.hi.z);
         this.boxes[i].bone = b.bone;
      }
   }

   public void translate(float x, float y, float z) {
      for (Matrix4f b : this.bones) {
         b.m30(b.m30() + x).m31(b.m31() + y).m32(b.m32() + z);
      }

      for (int i = 0; i < this.count; i++) {
         AdornmentFrame.Box b = this.boxes[i];
         b.matrix.m30(b.matrix.m30() + x).m31(b.matrix.m31() + y).m32(b.matrix.m32() + z);
         b.set(b.matrix, b.lo.x, b.lo.y, b.lo.z, b.hi.x, b.hi.y, b.hi.z);
      }
   }

   public void interpolate(AdornmentFrame a, AdornmentFrame b, float t) {
      if (a.count != b.count) {
         this.set(b);
      } else {
         this.count = b.count;
         this.scale = a.scale + (b.scale - a.scale) * t;

         for (int i = 0; i < this.bones.length; i++) {
            this.blend(a.bones[i], b.bones[i], t, this.bones[i]);
         }

         for (int i = 0; i < this.count; i++) {
            AdornmentFrame.Box lo = a.boxes[i];
            AdornmentFrame.Box hi = b.boxes[i];
            this.blend(lo.matrix, hi.matrix, t, this.blended);
            this.boxes[i]
               .set(
                  this.blended,
                  lerp(lo.lo.x, hi.lo.x, t),
                  lerp(lo.lo.y, hi.lo.y, t),
                  lerp(lo.lo.z, hi.lo.z, t),
                  lerp(lo.hi.x, hi.hi.x, t),
                  lerp(lo.hi.y, hi.hi.y, t),
                  lerp(lo.hi.z, hi.hi.z, t)
               );
            this.boxes[i].bone = hi.bone;
         }
      }
   }

   private void blend(Matrix4f a, Matrix4f b, float t, Matrix4f out) {
      a.getTranslation(this.position);
      b.getTranslation(this.otherPosition);
      a.getScale(this.size);
      b.getScale(this.otherSize);
      a.getUnnormalizedRotation(this.orientation).normalize();
      b.getUnnormalizedRotation(this.otherOrientation).normalize();
      out.translationRotateScale(this.position.lerp(this.otherPosition, t), this.orientation.slerp(this.otherOrientation, t), this.size.lerp(this.otherSize, t));
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public Vector3f attachment(AdornmentProject.Rig rig, Vector3f out) {
      int bone = rig.boneIndex();
      this.bones[bone].transformPosition(out.set(rig.x, rig.y, rig.z));
      if (rig.linked() && !rig.preciseAnchor) {
         this.surface(out, rig.links.get(0).radius() * this.scale, this.bones[bone], out);
      }

      return out;
   }

   public boolean reparent(AdornmentProject.Rig rig, int targetBone) {
      if (targetBone >= 0 && targetBone < this.bones.length) {
         Vector3f at = this.attachment(rig, new Vector3f());
         new Matrix4f(this.bones[targetBone]).invert().transformPosition(at);
         if (at.isFinite() && !(Math.abs(at.x) > 32.0F) && !(Math.abs(at.y) > 32.0F) && !(Math.abs(at.z) > 32.0F)) {
            Quaternionf world = this.bones[rig.boneIndex()]
               .getUnnormalizedRotation(new Quaternionf())
               .normalize()
               .mul(AdornmentSimulation.angles(rig.pitch, rig.yaw, rig.roll));
            Quaternionf local = this.bones[targetBone].getUnnormalizedRotation(new Quaternionf()).normalize().invert().mul(world);
            Matrix3f rotation = new Matrix3f().set(local);
            float cy = (float)Math.hypot((double)rotation.m00(), (double)rotation.m01());
            Vector3f angles = new Vector3f(
               cy > 1.0E-6F ? (float)Math.atan2((double)rotation.m12(), (double)rotation.m22()) : 0.0F,
               (float)Math.atan2((double)(-rotation.m02()), (double)cy),
               cy > 1.0E-6F
                  ? (float)Math.atan2((double)rotation.m01(), (double)rotation.m00())
                  : (float)Math.atan2((double)(-rotation.m10()), (double)rotation.m11())
            );
            if (!angles.isFinite()) {
               return false;
            } else {
               rig.bone = AdornmentProject.BONES[targetBone];
               rig.x = at.x;
               rig.y = at.y;
               rig.z = at.z;
               rig.pitch = (float)Math.toDegrees((double)angles.x);
               rig.yaw = (float)Math.toDegrees((double)angles.y);
               rig.roll = (float)Math.toDegrees((double)angles.z);
               rig.preciseAnchor = true;
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public Vector3f surface(Vector3fc point, float radius, Matrix4fc axes, Vector3f out) {
      this.surfaceStart.set(point);
      out.set(point);
      boolean inside = false;

      for (int i = 0; i < this.count; i++) {
         if (this.boxes[i].intersects(point, point, radius)) {
            inside = true;
            break;
         }
      }

      if (!inside) {
         return out;
      } else {
         float nearest = Float.POSITIVE_INFINITY;

         for (int face = 0; face < 6; face++) {
            int axis = face < 2 ? 2 : (face < 4 ? 0 : 1);
            this.surfaceDirection.zero().setComponent(axis, face % 2 == 0 ? -1.0F : 1.0F);
            axes.transformDirection(this.surfaceDirection).normalize();
            this.surfaceCandidate.set(this.surfaceStart);
            float moved = 0.0F;

            for (int iteration = 0; iteration <= this.count; iteration++) {
               float exit = 0.0F;

               for (int ix = 0; ix < this.count; ix++) {
                  exit = Math.max(exit, this.boxes[ix].exitDistance(this.surfaceCandidate, this.surfaceDirection, radius));
               }

               if (exit <= 0.0F) {
                  if (moved < nearest) {
                     nearest = moved;
                     out.set(this.surfaceCandidate);
                  }
                  break;
               }

               float travel = exit + 0.025F * this.scale;
               moved += travel;
               if (moved >= nearest) {
                  break;
               }

               this.surfaceCandidate.fma(travel, this.surfaceDirection);
            }
         }

         return out;
      }
   }

   public AdornmentFrame.Pick pick(Vector3fc from, Vector3fc to, float clearance) {
      return this.pick(from, to, clearance, 0, -1);
   }

   public AdornmentFrame.Pick pick(Vector3fc from, Vector3fc to, float clearance, int hidden, int selectedBone) {
      float nearest = Float.POSITIVE_INFINITY;
      AdornmentFrame.Box hit = null;

      for (int i = 0; i < this.count; i++) {
         AdornmentFrame.Box b = this.boxes[i];
         if (b.bone >= 0 && (hidden & 1 << b.bone) == 0 && (selectedBone < 0 || b.bone == selectedBone)) {
            float t = b.ray(from, to, clearance);
            if (t < nearest) {
               nearest = t;
               hit = b;
            }
         }
      }

      if (hit == null) {
         return null;
      } else {
         Vector3f point = new Vector3f(from).lerp(to, nearest);
         new Matrix4f(this.bones[hit.bone]).invert().transformPosition(point);
         return point.isFinite() ? new AdornmentFrame.Pick(hit.bone, point) : null;
      }
   }

   public static final class Box {
      public int bone = -1;
      public final Matrix4f matrix = new Matrix4f();
      public final Matrix4f inverse = new Matrix4f();
      public final Vector3f lo = new Vector3f();
      public final Vector3f hi = new Vector3f();
      private final Vector3f worldLo = new Vector3f();
      private final Vector3f worldHi = new Vector3f();
      private final Vector3f a = new Vector3f();
      private final Vector3f b = new Vector3f();
      private final Vector3f inflation = new Vector3f();

      public boolean overlaps(Vector3fc center, Vector3fc half) {
         return center.x() + half.x() > this.worldLo.x
            && center.x() - half.x() < this.worldHi.x
            && center.y() + half.y() > this.worldLo.y
            && center.y() - half.y() < this.worldHi.y
            && center.z() + half.z() > this.worldLo.z
            && center.z() - half.z() < this.worldHi.z;
      }

      public boolean contains(Vector3fc point) {
         this.inverse.transformPosition(point, this.a);
         return this.a.x > this.lo.x && this.a.x < this.hi.x && this.a.y > this.lo.y && this.a.y < this.hi.y && this.a.z > this.lo.z && this.a.z < this.hi.z;
      }

      public void set(Matrix4fc m, float x0, float y0, float z0, float x1, float y1, float z1) {
         this.matrix.set(m);
         this.inverse.set(m).invert();
         this.lo.set(x0, y0, z0);
         this.hi.set(x1, y1, z1);
         this.matrix.transformAab(x0, y0, z0, x1, y1, z1, this.worldLo, this.worldHi);
         this.inflation
            .set(
               (float)Math.sqrt(
                  (double)(this.inverse.m00() * this.inverse.m00() + this.inverse.m10() * this.inverse.m10() + this.inverse.m20() * this.inverse.m20())
               ),
               (float)Math.sqrt(
                  (double)(this.inverse.m01() * this.inverse.m01() + this.inverse.m11() * this.inverse.m11() + this.inverse.m21() * this.inverse.m21())
               ),
               (float)Math.sqrt(
                  (double)(this.inverse.m02() * this.inverse.m02() + this.inverse.m12() * this.inverse.m12() + this.inverse.m22() * this.inverse.m22())
               )
            );
      }

      private float ray(Vector3fc from, Vector3fc to, float radius) {
         this.inverse.transformPosition(from, this.a);
         this.inverse.transformPosition(to, this.b);
         this.b.sub(this.a);
         float enter = 0.0F;
         float exit = 1.0F;

         for (int axis = 0; axis < 3; axis++) {
            float min = this.lo.get(axis) - this.inflation.get(axis) * radius;
            float max = this.hi.get(axis) + this.inflation.get(axis) * radius;
            float start = this.a.get(axis);
            float delta = this.b.get(axis);
            if (Math.abs(delta) < 1.0E-8F) {
               if (start < min || start > max) {
                  return Float.POSITIVE_INFINITY;
               }
            } else {
               float first = (min - start) / delta;
               float last = (max - start) / delta;
               if (first > last) {
                  float swap = first;
                  first = last;
                  last = swap;
               }

               enter = Math.max(enter, first);
               exit = Math.min(exit, last);
               if (enter > exit) {
                  return Float.POSITIVE_INFINITY;
               }
            }
         }

         return enter;
      }

      private float exitDistance(Vector3fc point, Vector3fc direction, float radius) {
         this.inverse.transformPosition(point, this.a);
         this.inverse.transformDirection(direction, this.b);
         float exit = Float.POSITIVE_INFINITY;

         for (int axis = 0; axis < 3; axis++) {
            float min = this.lo.get(axis) - this.inflation.get(axis) * radius;
            float max = this.hi.get(axis) + this.inflation.get(axis) * radius;
            float start = this.a.get(axis);
            float d = this.b.get(axis);
            if (start <= min || start >= max) {
               return 0.0F;
            }

            if (Math.abs(d) > 1.0E-8F) {
               exit = Math.min(exit, ((d > 0.0F ? max : min) - start) / d);
            }
         }

         return Float.isFinite(exit) ? Math.max(0.0F, exit) : 0.0F;
      }

      public boolean intersects(Vector3fc from, Vector3fc to, float radius) {
         if (!(Math.max(from.x(), to.x()) < this.worldLo.x - radius)
            && !(Math.min(from.x(), to.x()) > this.worldHi.x + radius)
            && !(Math.max(from.y(), to.y()) < this.worldLo.y - radius)
            && !(Math.min(from.y(), to.y()) > this.worldHi.y + radius)
            && !(Math.max(from.z(), to.z()) < this.worldLo.z - radius)
            && !(Math.min(from.z(), to.z()) > this.worldHi.z + radius)) {
            this.inverse.transformPosition(from, this.a);
            this.inverse.transformPosition(to, this.b);
            this.b.sub(this.a);
            float enter = 0.0F;
            float exit = 1.0F;

            for (int axis = 0; axis < 3; axis++) {
               float min = this.lo.get(axis) - this.inflation.get(axis) * radius;
               float max = this.hi.get(axis) + this.inflation.get(axis) * radius;
               float start = this.a.get(axis);
               float delta = this.b.get(axis);
               if (Math.abs(delta) < 1.0E-8F) {
                  if (start <= min || start >= max) {
                     return false;
                  }
               } else {
                  float t0 = (min - start) / delta;
                  float t1 = (max - start) / delta;
                  if (t0 > t1) {
                     float swap = t0;
                     t0 = t1;
                     t1 = swap;
                  }

                  enter = Math.max(enter, t0);
                  exit = Math.min(exit, t1);
                  if (enter >= exit) {
                     return false;
                  }
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }

   public static record Pick(int bone, Vector3f local) {
   }
}
