package pro.webhm.playerinteractions.anim;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class HandReach {
   public final Quaternionf upper = new Quaternionf();
   public final Vector3f end = new Vector3f();
   public final Vector3f elbow = new Vector3f();
   public float bend;
   public float error;
   private final Vector3f direction = new Vector3f();
   private final Vector3f pole = new Vector3f();
   private final Vector3f y = new Vector3f();
   private final Vector3f z = new Vector3f();
   private final Vector3f x = new Vector3f();
   private final Vector3f local = new Vector3f();
   private final Matrix3f axes = new Matrix3f();
   private final Quaternionf correction = new Quaternionf();

   public void solve(Vector3fc target, Vector3fc hint, float first, float second, float centerX) {
      this.direction.set(target);
      float requested = this.direction.length();
      if (this.direction.isFinite() && !(requested < 1.0E-6F)) {
         this.direction.div(requested);
      } else {
         this.direction.set(0.0F, 1.0F, 0.0F);
         requested = 0.0F;
      }

      float axial = (float)Math.sqrt((double)Math.max(0.0F, requested * requested - centerX * centerX));
      float d = Math.max(Math.abs(first - second) + 0.001F, Math.min(first + second - 1.0E-4F, axial));
      float a = (first * first - second * second + d * d) / (2.0F * d);
      float h = (float)Math.sqrt((double)Math.max(0.0F, first * first - a * a));
      this.pole.set(hint).fma(-this.direction.dot(hint), this.direction);
      if (this.pole.lengthSquared() < 1.0E-8F) {
         this.pole.set(Math.abs(this.direction.x) < 0.8F ? 1.0F : 0.0F, Math.abs(this.direction.x) < 0.8F ? 0.0F : 1.0F, 0.0F);
         this.pole.fma(-this.pole.dot(this.direction), this.direction);
      }

      this.pole.normalize();
      this.elbow.set(this.direction).mul(a).fma(h, this.pole);
      this.y.set(this.elbow).normalize();
      this.z.set(this.direction).mul(d).sub(this.elbow).normalize();
      this.x.set(this.z).cross(this.y);
      if (this.x.lengthSquared() < 1.0E-8F) {
         this.x.set(this.pole).cross(this.y);
      }

      this.x.normalize();
      this.z.set(this.x).cross(this.y).normalize();
      this.axes.setColumn(0, this.x).setColumn(1, this.y).setColumn(2, this.z);
      this.upper.setFromNormalized(this.axes).normalize();
      this.bend = -((float)Math.acos((double)Math.max(-1.0F, Math.min(1.0F, (d * d - first * first - second * second) / (2.0F * first * second)))));
      this.local.set(centerX, first + second * (float)Math.cos((double)this.bend), second * (float)Math.sin((double)this.bend));
      this.upper.transform(this.local, this.end);
      if (centerX != 0.0F) {
         this.x.set(this.end).normalize();
         this.x.cross(this.direction, this.y);
         this.correction.set(this.y.x, this.y.y, this.y.z, 1.0F + this.x.dot(this.direction)).normalize();
         this.upper.premul(this.correction).normalize();
         this.upper.transform(this.local, this.end);
      }

      this.upper.transform(this.elbow.set(0.0F, first, 0.0F));
      this.error = Math.abs(requested - this.end.length());
   }
}
