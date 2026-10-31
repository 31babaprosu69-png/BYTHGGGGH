package pro.webhm.playerinteractions.adornments;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class AdornmentHand {
   public static final float PIXEL = 0.05859375F;
   public static final float PALM_Y = 9.0F;
   public final Matrix4f matrix = new Matrix4f();
   public final Vector3f palm = new Vector3f();
   public final Vector3f shoulder = new Vector3f();
   private final Matrix4f body = new Matrix4f();
   private final Quaternionf basis = new Quaternionf();
   private final Quaternionf turn = new Quaternionf();
   private final Vector3f direction = new Vector3f();
   private final AdornmentReach reach = new AdornmentReach();
   private final Vector3f hint = new Vector3f();
   private float center;
   public float elbow;

   public void update(float yaw, float eyeHeight, int side, boolean slim, Vector3fc anchor) {
      this.center = (float)(-side) * (slim ? 0.5F : 1.0F);
      this.elbow = 0.0F;
      this.body
         .translation(0.0F, eyeHeight - 1.62F, 0.0F)
         .rotateY((float)Math.toRadians((double)(180.0F - yaw)))
         .scale(-0.05859375F, -0.05859375F, 0.05859375F)
         .translate(0.0F, -24.016F, 0.0F);
      this.body.transformPosition(this.shoulder.set((float)(-side * 5) + this.center, slim ? 2.5F : 2.0F, 0.0F));
      this.body.getUnnormalizedRotation(this.basis).normalize();
      this.direction.set(anchor).sub(this.shoulder);
      if (this.direction.isFinite() && !(this.direction.lengthSquared() < 1.0E-8F)) {
         this.direction.normalize();
      } else {
         this.direction.set(0.0F, -1.0F, 0.0F);
      }

      this.basis.transformInverse(this.direction);
      this.turn.rotationTo(0.0F, 1.0F, 0.0F, this.direction.x, this.direction.y, this.direction.z);
      this.matrix.translation(this.shoulder).rotate(this.basis).rotate(this.turn).scale(0.05859375F).translate(-this.center, 0.0F, 0.0F);
      this.matrix.transformPosition(this.palm.set(this.center, 9.0F, 0.0F));
   }

   public float fit(Vector3fc point) {
      this.direction.set(point).sub(this.shoulder).div(0.05859375F);
      this.reach.solve(this.direction, this.hint.set(0.0F, -1.0F, 0.0F), 4.0F, 5.0F, 0.0F);
      this.elbow = this.reach.bend;
      this.matrix.translation(this.shoulder).rotate(this.reach.upper).scale(0.05859375F).translate(-this.center, 0.0F, 0.0F);
      this.palm.set(this.reach.end).mul(0.05859375F).add(this.shoulder);
      return this.palm.distance(point);
   }

   public Vector3f meshPalm(Vector3f out) {
      this.turn.rotationX(this.elbow).transform(out.set(0.0F, 5.0F, 0.0F)).add(this.center, 4.0F, 0.0F);
      return this.matrix.transformPosition(out);
   }

   public static double pull(double distance, double length) {
      return Math.max(0.0, distance - Math.max(0.08, length * 0.82));
   }

   public static Vector3f followStep(Vector3fc anchor, Vector3fc hand, double length, Vector3f out) {
      double pull = pull((double)anchor.distance(hand), length);
      out.set(hand).sub(anchor);
      out.y = 0.0F;
      float horizontal = out.length();
      return !(pull <= 0.035) && !((double)horizontal <= 0.06)
         ? out.mul((float)(Math.min(0.28, Math.min((double)horizontal, pull * 0.55)) / (double)horizontal))
         : out.zero();
   }
}
