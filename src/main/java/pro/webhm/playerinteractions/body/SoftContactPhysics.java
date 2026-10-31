package pro.webhm.playerinteractions.body;

import java.util.Arrays;

public final class SoftContactPhysics {
   private final float[] value = new float[4];
   private final float[] velocity = new float[4];

   public float lateral() {
      return this.value[0];
   }

   public float frontal() {
      return this.value[1];
   }

   public float pitch() {
      return this.value[2];
   }

   public float roll() {
      return this.value[3];
   }

   public float scaleX() {
      return (1.0F - this.lateral()) / (float)Math.sqrt((double)(1.0F - this.frontal()));
   }

   public float scaleY() {
      return 1.0F / (float)Math.sqrt((double)((1.0F - this.lateral()) * (1.0F - this.frontal())));
   }

   public float scaleZ() {
      return (1.0F - this.frontal()) / (float)Math.sqrt((double)(1.0F - this.lateral()));
   }

   public void clear() {
      Arrays.fill(this.value, 0.0F);
      Arrays.fill(this.velocity, 0.0F);
   }

   public void step(float dt, float side, float front, float pitch, float roll, boolean inertia, float bounce) {
      float time = Math.max(0.0F, Math.min(2.0F, dt));
      float omega = 0.5F + 0.1F * Math.max(0.05F, Math.min(3.0F, bounce));
      float damping = inertia ? 0.86F : 1.0F;
      this.spring(0, Math.max(0.0F, Math.min(0.14F, side)), time, omega, damping);
      this.spring(1, Math.max(0.0F, Math.min(0.14F, front)), time, omega, damping);
      this.spring(2, inertia ? Math.max(-5.0F, Math.min(5.0F, pitch)) : 0.0F, time, omega, damping);
      this.spring(3, inertia ? Math.max(-5.0F, Math.min(5.0F, roll)) : 0.0F, time, omega, damping);

      for (int i = 0; i < 2; i++) {
         if (this.value[i] < 0.0F || this.value[i] > 0.16F) {
            this.value[i] = Math.max(0.0F, Math.min(0.16F, this.value[i]));
            this.velocity[i] = 0.0F;
         }
      }
   }

   private void spring(int i, float target, float dt, float omega, float damping) {
      float x = this.value[i] - target;
      float v = this.velocity[i];
      float decay = (float)Math.exp((double)(-omega * damping * dt));
      if (damping == 1.0F) {
         float j = v + omega * x;
         this.value[i] = target + (x + j * dt) * decay;
         this.velocity[i] = (v - omega * j * dt) * decay;
      } else {
         float a = omega * damping;
         float w = omega * (float)Math.sqrt((double)(1.0F - damping * damping));
         float c = (float)Math.cos((double)(w * dt));
         float s = (float)Math.sin((double)(w * dt));
         float j = (v + a * x) / w;
         this.value[i] = target + decay * (x * c + j * s);
         this.velocity[i] = decay * (v * c - (a * v + omega * omega * x) / w * s);
      }

      if (Math.abs(this.value[i]) < 1.0E-7F && Math.abs(this.velocity[i]) < 1.0E-7F && target == 0.0F) {
         this.value[i] = this.velocity[i] = 0.0F;
      }
   }
}
