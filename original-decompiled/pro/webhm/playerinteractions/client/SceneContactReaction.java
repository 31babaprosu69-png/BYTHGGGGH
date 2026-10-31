package pro.webhm.playerinteractions.client;

import java.util.Arrays;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import pro.webhm.playerinteractions.body.SoftContactPhysics;
import pro.webhm.playerinteractions.facial.FacialActing;
import pro.webhm.playerinteractions.facial.FacialPose;
import pro.webhm.playerinteractions.npc.BreastData;

public final class SceneContactReaction {
   public final SoftContactPhysics[] halves = new SoftContactPhysics[]{new SoftContactPhysics(), new SoftContactPhysics()};
   private final Matrix4f inverse = new Matrix4f();
   private final Matrix4f partFrame = new Matrix4f();
   private final Vector3f point = new Vector3f();
   private final Vector3f normal = new Vector3f();
   private final Vector3f[] previous = new Vector3f[]{new Vector3f(), new Vector3f()};
   private final boolean[] touching = new boolean[2];
   private float contact;
   private float pressure;
   private float horizontal;
   private float vertical;
   private double age;
   public boolean separate;

   public void clear() {
      this.contact = this.pressure = this.horizontal = this.vertical = 0.0F;
      this.age = 0.0;
      this.separate = false;

      for (SoftContactPhysics half : this.halves) {
         half.clear();
      }

      Arrays.fill(this.touching, false);
   }

   public void update(SceneTouchRuntime host, ScenePetClient.State state, float dt, BreastData data, boolean visible) {
      float desired = state.weight > 0.0F ? Math.min(1.0F, host.stroke.depth / Math.max(1.0E-4F, state.point.depth / 16.0F)) * state.weight : 0.0F;
      this.expression(dt, desired, state.pressureAmount, state.x, state.y);
      this.separate = false;

      for (int i = 0; i < 2; i++) {
         boolean hit = false;
         float lateral = 0.0F;
         float frontal = 0.0F;
         float pitch = 0.0F;
         float roll = 0.0F;
         if (visible && desired > 0.001F) {
            host.surface.frame(i == 0 ? "right_breast" : "left_breast", this.partFrame);
            this.inverse.set(this.partFrame).invert();

            for (int j = 0; j < 2; j++) {
               this.inverse.transformPosition(this.point.set(host.stroke.squeeze.contact[j])).mul(16.0F);
               if (contains(i, this.point)) {
                  localNormal(this.partFrame, host.stroke.squeeze.contactNormal[j], this.normal);
                  float load = Math.min(0.14F, host.stroke.depth / Math.max(0.06F, host.stroke.squeeze.brushRadius) * (0.18F + 0.6F * this.pressure));
                  lateral = load * Math.abs(this.normal.x);
                  frontal = load * Math.abs(this.normal.z);
                  if (this.touching[i] && dt > 0.0F) {
                     pitch = Math.max(-4.0F, Math.min(4.0F, (this.point.y - this.previous[i].y) / dt * 4.0F)) * desired;
                     roll = Math.max(-4.0F, Math.min(4.0F, (this.point.x - this.previous[i].x) / dt * 4.0F)) * desired;
                  }

                  this.previous[i].set(this.point);
                  hit = true;
                  break;
               }
            }
         }

         this.touching[i] = hit;
         this.separate |= hit;
         this.halves[i].step(dt, lateral, frontal, pitch, roll, data != null && data.physics, data == null ? 1.0F : data.bounce);
      }

      host.stroke.squeeze.separateWeight = host.stroke.squeeze.separateWeight
         + ((float)(this.separate ? 1 : 0) - host.stroke.squeeze.separateWeight) * (1.0F - (float)Math.exp((double)(-Math.max(0.0F, dt) * 0.4F)));
      if (state.weight <= 0.0F) {
         this.contact = 0.0F;
         host.stroke.squeeze.separateWeight = 0.0F;

         for (SoftContactPhysics half : this.halves) {
            half.clear();
         }

         Arrays.fill(this.touching, false);
      }
   }

   void expression(float dt, float amount, float held, float x, float y) {
      this.contact = this.contact + (amount - this.contact) * (1.0F - (float)Math.exp((double)(-Math.max(0.0F, dt) * 0.25F)));
      this.pressure = held;
      this.horizontal = x;
      this.vertical = y;
      this.age = this.age + (double)Math.max(0.0F, dt);
   }

   static boolean contains(int half, Vector3fc p) {
      float lo = half == 0 ? -4.0F : 0.0F;
      float hi = half == 0 ? 0.0F : 4.0F;
      return p.x() >= lo - 0.45F && p.x() <= hi + 0.45F && p.y() >= -0.45F && p.y() <= 5.45F && p.z() >= -4.6F && p.z() <= 0.45F;
   }

   static Vector3f localNormal(Matrix4fc f, Vector3fc n, Vector3f out) {
      return out.set(
            (f.m00() * n.x() + f.m01() * n.y() + f.m02() * n.z()) / (float)Math.sqrt((double)(f.m00() * f.m00() + f.m01() * f.m01() + f.m02() * f.m02())),
            (f.m10() * n.x() + f.m11() * n.y() + f.m12() * n.z()) / (float)Math.sqrt((double)(f.m10() * f.m10() + f.m11() * f.m11() + f.m12() * f.m12())),
            (f.m20() * n.x() + f.m21() * n.y() + f.m22() * n.z()) / (float)Math.sqrt((double)(f.m20() * f.m20() + f.m21() * f.m21() + f.m22() * f.m22()))
         )
         .normalize();
   }

   public void apply(ModelPart right, ModelPart left) {
      apply(right, this.halves[0]);
      apply(left, this.halves[1]);
   }

   private static void apply(ModelPart part, SoftContactPhysics motion) {
      part.xScale = part.xScale * motion.scaleX();
      part.yScale = part.yScale * motion.scaleY();
      part.zScale = part.zScale * motion.scaleZ();
      part.xRot = part.xRot + (float)Math.toRadians((double)motion.pitch());
      part.zRot = part.zRot + (float)Math.toRadians((double)motion.roll());
   }

   public FacialPose face(FacialPose base, float strength, int seed) {
      float w = this.contact * Math.max(0.0F, Math.min(1.0F, strength));
      if (w <= 0.0F) {
         return base;
      } else {
         double phase = this.age + (double)Math.floorMod(seed, 61);
         float blink = pulse(phase % 83.0, 4.5);
         FacialActing a = base.acting();
         FacialActing acting = new FacialActing(
            this.horizontal * 0.12F, 0.07F + this.vertical * 0.05F, 0.35F, blink, blink, 0.0F, 0.0F, 0.0F, 0.12F, 0.0F, 0.0F
         );
         FacialPose target = base.expression(0.2F + 0.09F * this.pressure, 0.09F - 0.04F * this.pressure, 0.12F + 0.09F * this.pressure)
            .acting(a.blend(acting, w));
         return base.expression(
               base.smile() + (target.smile() - base.smile()) * w,
               base.brow() + (target.brow() - base.brow()) * w,
               base.squint() + (target.squint() - base.squint()) * w
            )
            .acting(target.acting());
      }
   }

   public void head(ModelPart head, float strength, int seed) {
      if (head != null && !(this.contact <= 0.0F)) {
         double phase = (double)Math.floorMod(seed, 97) * 0.11;
         float w = this.contact * Math.max(0.0F, Math.min(1.0F, strength)) * (float) Math.PI / 180.0F;
         head.yRot = head.yRot + (this.horizontal * 3.0F + 4.0F * (float)Math.sin(this.age / 47.0 + phase)) * w;
         head.xRot = head.xRot + (1.5F + this.vertical * 2.0F + this.pressure * 1.5F + 1.8F * (float)Math.sin(this.age / 61.0 + phase)) * w;
         head.zRot = head.zRot + 2.5F * (float)Math.sin(this.age / 73.0 + phase + 0.7) * w;
      }
   }

   private static float pulse(double t, double duration) {
      return t >= duration ? 0.0F : (float)Math.pow(Math.sin(Math.PI * t / duration), 2.0);
   }
}
