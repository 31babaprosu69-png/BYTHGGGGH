package pro.webhm.playerinteractions.client.blockbench;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.body.HairTiltMotion;
import pro.webhm.playerinteractions.npc.HeadDynamicsData;

public class SharedHeadMotion {
   private static final Map<LivingEntity, SharedHeadMotion.State> STATES = new WeakHashMap<>();

   public static void setLocalTarget(LivingEntity e, HeadDynamicsData d, Vector3f target) {
      SharedHeadMotion.State s = STATES.computeIfAbsent(e, key -> new SharedHeadMotion.State());
      float dx = target.x;
      float dy = target.y - (-8.0F + d.eyeY + d.eyeHeight * 0.5F) / 16.0F;
      float forward = -target.z - 0.25F;
      s.targetX = forward <= 0.0F ? 0.0F : Mth.clamp(dx / forward, -1.0F, 1.0F) * d.gazeRange;
      s.targetY = forward <= 0.0F ? 0.0F : Mth.clamp(dy / forward, -1.0F, 1.0F) * d.gazeRange;
      s.localTarget = true;
   }

   public static SharedHeadMotion.State sample(LivingEntity e, HeadDynamicsData d, boolean suppressGaze, boolean lookAtPlayer) {
      SharedHeadMotion.State s = STATES.computeIfAbsent(e, key -> new SharedHeadMotion.State());
      if (s.tick == e.tickCount) {
         return s;
      } else {
         boolean reset = s.tick == Integer.MIN_VALUE || e.tickCount - s.tick > 5 || e.tickCount < s.tick;
         if (reset) {
            s.lastYaw = e.getYHeadRot();
            s.nextBlink = e.tickCount + d.blinkInterval;
            s.hair = s.velocity = 0.0F;
         }

         s.tick = e.tickCount;
         s.oldX = s.x;
         s.oldY = s.y;
         s.oldHair = s.hair;
         double tx = 0.0;
         double ty = 0.0;
         LocalPlayer player = Minecraft.getInstance().player;
         Vec3 camera = player != null ? player.getEyePosition() : Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         Vec3 delta = camera.subtract(e.getEyePosition());
         if (!suppressGaze && (d.gazeEnabled || lookAtPlayer) && delta.lengthSqr() > 1.0E-6) {
            double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
            tx = (double)(Mth.clamp(Mth.wrapDegrees((float)yaw - e.getYHeadRot()) / 45.0F, -1.0F, 1.0F) * d.gazeRange);
            ty = (double)(
               Mth.clamp((float)(-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)))) - e.getXRot(), -35.0F, 35.0F)
                  / 35.0F
                  * d.gazeRange
            );
         }

         if (s.localTarget) {
            tx = suppressGaze || !d.gazeEnabled && !lookAtPlayer ? 0.0 : (double)s.targetX;
            ty = suppressGaze || !d.gazeEnabled && !lookAtPlayer ? 0.0 : (double)s.targetY;
         }

         float alpha = 1.0F / d.gazeSmoothness;
         s.x = (float)((double)s.x + (tx - (double)s.x) * (double)alpha);
         s.y = (float)((double)s.y + (ty - (double)s.y) * (double)alpha);
         float turn = Mth.wrapDegrees(e.getYHeadRot() - s.lastYaw);
         s.lastYaw = e.getYHeadRot();
         s.velocity = (s.velocity - s.hair * 0.18F - turn * d.hairMotion * 0.035F) * d.hairDamping;
         s.hair = Mth.clamp(s.hair + s.velocity, -d.hairMotion, d.hairMotion);
         if (e.tickCount >= s.nextBlink) {
            s.blinkStart = e.tickCount;
            int hash = (e.getUUID().hashCode() ^ e.tickCount) * 73244475;
            float jitter = ((float)(hash & 65535) / 32767.5F - 1.0F) * d.blinkVariation;
            s.nextBlink = e.tickCount + Math.max(d.blinkDuration + 2, Math.round((float)d.blinkInterval * (1.0F + jitter)));
         }

         return s;
      }
   }

   public static float gazeX(SharedHeadMotion.State s, float p) {
      return Mth.lerp(p, s.oldX, s.x);
   }

   public static float gazeY(SharedHeadMotion.State s, float p) {
      return Mth.lerp(p, s.oldY, s.y);
   }

   public static float hair(SharedHeadMotion.State s, float p) {
      return Mth.lerp(p, s.oldHair, s.hair);
   }

   public static float blink(SharedHeadMotion.State s, HeadDynamicsData d, float p) {
      float t = ((float)(s.tick - s.blinkStart) + p) / (float)d.blinkDuration;
      float a = t < 0.0F || t >= 1.0F ? 0.0F : (t < 0.25F ? t / 0.25F : 1.0F - (t - 0.25F) / 0.75F);
      return a * a * (3.0F - 2.0F * a);
   }

   public static final class State {
      public final HairTiltMotion tilt = new HairTiltMotion();
      public float hairPitch;
      public float hairRoll;
      int tick = Integer.MIN_VALUE;
      int nextBlink;
      int blinkStart = -1000;
      float targetX;
      float targetY;
      boolean localTarget;
      float x;
      float y;
      float oldX;
      float oldY;
      float hair;
      float oldHair;
      float velocity;
      float lastYaw;
   }
}
