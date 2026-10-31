package pro.webhm.playerinteractions.body;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

public final class HairTiltMotion {
   private double lastTime = Double.NaN;
   private float pitch;
   private float roll;
   private float pitchVelocity;
   private float rollVelocity;

   public static Vector3f localDown(Matrix4f base, Matrix4f head) {
      Vector3f down = new Matrix4f(head).invert().mul(base).transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
      return down.isFinite() && down.lengthSquared() > 1.0E-8F ? down.normalize() : new Vector3f(0.0F, 1.0F, 0.0F);
   }

   public Vector2f sample(double time, Vector3f down, float limit, float damping) {
      limit = Math.max(0.0F, Math.min(25.0F, limit));
      float targetPitch = clamp((float)Math.toDegrees(Math.atan2((double)down.z, (double)Math.max(0.001F, down.y))), limit);
      float targetRoll = clamp((float)Math.toDegrees(Math.atan2((double)(-down.x), (double)Math.max(0.001F, down.y))), limit);
      if (!Double.isFinite(this.lastTime) || time < this.lastTime || time - this.lastTime > 5.0) {
         this.pitch = targetPitch;
         this.roll = targetRoll;
         this.pitchVelocity = this.rollVelocity = 0.0F;
         this.lastTime = time;
      }

      double elapsed = Math.max(0.0, time - this.lastTime);
      this.lastTime = time;
      int steps = Math.max(1, (int)Math.ceil(elapsed * 4.0));
      float dt = (float)(elapsed / (double)steps);
      float drag = (float)Math.pow((double)Math.max(0.5F, Math.min(0.95F, damping)), (double)dt);

      for (int i = 0; i < steps; i++) {
         this.pitchVelocity = (this.pitchVelocity + (targetPitch - this.pitch) * 0.18F * dt) * drag;
         this.rollVelocity = (this.rollVelocity + (targetRoll - this.roll) * 0.18F * dt) * drag;
         this.pitch = clamp(this.pitch + this.pitchVelocity * dt, limit);
         this.roll = clamp(this.roll + this.rollVelocity * dt, limit);
         if (Math.abs(this.pitch) >= limit && this.pitch * this.pitchVelocity > 0.0F) {
            this.pitchVelocity = 0.0F;
         }

         if (Math.abs(this.roll) >= limit && this.roll * this.rollVelocity > 0.0F) {
            this.rollVelocity = 0.0F;
         }
      }

      this.pitch = clamp(this.pitch, limit);
      this.roll = clamp(this.roll, limit);
      return new Vector2f(this.pitch, this.roll);
   }

   private static float clamp(float value, float limit) {
      return Math.max(-limit, Math.min(limit, value));
   }
}
