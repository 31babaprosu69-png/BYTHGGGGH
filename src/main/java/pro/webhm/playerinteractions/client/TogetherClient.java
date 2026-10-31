package pro.webhm.playerinteractions.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.Actions;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.TouchFacing;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.body.CarryPose;
import pro.webhm.playerinteractions.body.HandWalkGait;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class TogetherClient {
   private static final Map<UUID, TogetherClient.Frame> FRAMES = new HashMap<>();

   public static void receive(SessionState s) {
      if (Actions.together(s.action)) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            double now = (double)((float)mc.level.getGameTime() + mc.getFrameTime());
            SessionState.Actor a = s.recipient();
            TogetherClient.Frame f = FRAMES.get(s.id);
            if (f == null) {
               f = new TogetherClient.Frame(s, now);
               FRAMES.put(s.id, f);
            } else {
               f.from = f.position(now);
               f.oldYaw = f.yaw(now);
               f.oldDistance = f.distance(now);
            }

            f.to = new Vec3(a.x, a.y, a.z);
            f.yaw = s.pairYaw;
            f.distance = s.pairDistance;
            f.at = now;
         }
      }
   }

   private static double now(AbstractClientPlayer p, float partial) {
      return (double)p.level().getGameTime() + (double)partial;
   }

   static float yaw(SessionState s, AbstractClientPlayer p, float partial) {
      if (s.action == 6) {
         Player leader = p.level().getPlayerByUUID(s.initiator().id);
         if (leader == Minecraft.getInstance().player) {
            return leader.getViewYRot(partial) + (float)(s.recipient().id.equals(p.getUUID()) ? 90 : 0);
         }
      }

      TogetherClient.Frame f = FRAMES.get(s.id);
      float yaw = f == null ? s.pairYaw : f.yaw(now(p, partial));
      return yaw + (float)(s.action == 6 && s.recipient().id.equals(p.getUUID()) ? 90 : 0);
   }

   static Vec3 position(SessionState s, AbstractClientPlayer p, float partial) {
      if (s.initiator().id.equals(p.getUUID())) {
         return AvatarRenderer.renderPosition(p, partial);
      } else {
         TogetherClient.Frame f = FRAMES.get(s.id);
         if (s.action == 5) {
            return f == null ? AvatarRenderer.renderPosition(p, partial) : f.position(now(p, partial));
         } else if (p.level().getPlayerByUUID(s.initiator().id) instanceof AbstractClientPlayer a) {
            Vec3 goal = AvatarRenderer.renderPosition(a, partial).add(CarryPose.offset(yaw(s, a, partial), 0.9375F, 0.0F));
            return s.recipient().from().lerp(goal, (double)Clip.smooth(s.time(p.level().getGameTime(), partial) / 12.0));
         } else {
            return AvatarRenderer.renderPosition(p, partial);
         }
      }
   }

   static Map<String, BoneTransform> pose(SessionState s, AbstractClientPlayer p, Map<String, BoneTransform> base, float partial) {
      Map<String, BoneTransform> var19 = new HashMap<>(base);
      BoneTransform head = var19.getOrDefault("head", new BoneTransform()).copy();
      head.ry = TouchFacing.relative(p.getViewYRot(partial), yaw(s, p, partial));
      var19.put("head", head);
      float weight = Clip.smooth(s.time(p.level().getGameTime(), partial) / 12.0);
      boolean owner = s.initiator().id.equals(p.getUUID());
      TogetherClient.Frame f = FRAMES.get(s.id);
      double distance = f == null ? s.pairDistance : f.distance(now(p, partial));
      if (s.action == 6) {
         return !owner
            ? carriedPose(var19, weight, (float)(distance * 2.0 * Math.PI / 1.45), s.pairSpeed)
            : carryArms(var19, SkinCompat.slim(p), weight);
      } else {
         if (!owner) {
            var19 = new HashMap(var19);
            float swing = HandWalkGait.swing(distance, s.pairSpeed);
            BoneTransform left = new BoneTransform();
            left.rx = swing;
            BoneTransform right = new BoneTransform();
            right.rx = -swing;
            var19.put("left_leg", left);
            var19.put("right_leg", right);
         }

         if (p.level().getPlayerByUUID(owner ? s.recipient().id : s.initiator().id) instanceof AbstractClientPlayer partner) {
            Vec3 var22 = position(s, p, partial);
            Vec3 theirs = position(s, partner, partial);
            boolean slim = SkinCompat.slim(p);
            boolean partnerSlim = SkinCompat.slim(partner);
            float yaw = yaw(s, p, partial);
            float otherYaw = yaw(s, partner, partial);
            Vector3f hand = owner
               ? HandHoldPose.target(var22, yaw, slim, theirs, otherYaw, partnerSlim)
               : HandHoldPose.target(theirs, otherYaw, partnerSlim, var22, yaw, slim);
            return HandHoldPose.apply(var19, var22, yaw, slim, !owner, hand, weight);
         } else {
            return var19;
         }
      }
   }

   static Map<String, BoneTransform> carriedPose(Map<String, BoneTransform> base, float weight, float phase, float speed) {
      Map<String, BoneTransform> pose = CarryPose.walking(base, weight, phase, speed);
      BoneTransform head = pose.get("head").copy();
      float from = base.getOrDefault("head", new BoneTransform()).ry;
      head.ry = Mth.lerp(weight, from, 35.0F);
      pose.put("head", head);
      return pose;
   }

   static Map<String, BoneTransform> carryArms(Map<String, BoneTransform> base, boolean slim, float weight) {
      HashMap<String, BoneTransform> result = new HashMap<>(base);
      HashMap<String, BoneTransform> reference = new HashMap<>(base);
      reference.putAll(CarryPose.playerArms());

      for (boolean left : new boolean[]{false, true}) {
         SceneTouchArm arm = new SceneTouchArm(left);
         arm.begin(reference, 0.0F, 0.0F, 0.0F, 0.0F, slim);
         Vector3f goal = new Vector3f(arm.palm.x, left ? 1.04F : 0.9F, left ? 0.43F : 0.34F);
         arm.solve(goal, 180.0F, 1.0F);
         String upper = left ? "left_arm" : "right_arm";
         String lower = left ? "left_forearm" : "right_forearm";
         Map<String, BoneTransform> solved = arm.blend(base.getOrDefault(upper, new BoneTransform()), base.getOrDefault(lower, new BoneTransform()), weight);
         result.put(upper, solved.get(upper).copy());
         result.put(lower, solved.get(lower).copy());
      }

      return result;
   }

   public static void clear(UUID id) {
      FRAMES.remove(id);
   }

   public static void clear() {
      FRAMES.clear();
   }

   private TogetherClient() {
   }

   static final class Frame {
      Vec3 from;
      Vec3 to;
      float oldYaw;
      float yaw;
      double oldDistance;
      double distance;
      double at;

      Frame(SessionState s, double now) {
         SessionState.Actor a = s.recipient();
         this.to = new Vec3(a.x, a.y, a.z);
         this.from = s.elapsed < 12.0 ? a.from() : this.to;
         this.oldYaw = this.yaw = s.pairYaw;
         this.oldDistance = this.distance = s.pairDistance;
         this.at = now;
      }

      float weight(double now) {
         return (float)Math.max(0.0, Math.min(1.0, (now - this.at) / 2.0));
      }

      Vec3 position(double now) {
         return this.from.lerp(this.to, (double)this.weight(now));
      }

      float yaw(double now) {
         return Mth.rotLerp(this.weight(now), this.oldYaw, this.yaw);
      }

      double distance(double now) {
         return Mth.lerp((double)this.weight(now), this.oldDistance, this.distance);
      }
   }
}
