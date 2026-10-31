package pro.webhm.playerinteractions.anim;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class AnimationEntry {
   private static final BoneTransform ZERO = new BoneTransform();

   public static NpcAnimation prepend(
      NpcAnimation source, Map<String, BoneTransform> current, Map<String, BoneTransform> previous, ActorMotion arrival, ActorMotion velocity, int ticks
   ) {
      NpcAnimation result = source.copy();
      ticks = Math.max(1, ticks);
      if (result.frames.isEmpty() || result.frames.get(0).tick > 0) {
         result.putFrame(0, source.sample(0.0, false), source.sampleMotion(0.0, false));
      }

      for (Keyframe frame : result.frames) {
         frame.tick += ticks;
      }

      for (NpcAnimation.FaceKey face : result.faceKeys) {
         face.tick += ticks;
      }

      for (SceneCue cue : result.cues) {
         cue.tick += ticks;
      }

      for (NpcAnimation.Wink wink : result.winks) {
         wink.tick += ticks;
      }

      for (SceneEyeGlow point : result.eyeGlow) {
         point.tick += ticks;
      }

      for (SceneEyeGlow point : result.blush) {
         point.tick += ticks;
      }

      for (SceneScaleKey key : result.scaleKeys) {
         key.tick += ticks;
      }

      for (ScenePetPoint point : result.touchPoints()) {
         point.tick += ticks;
      }

      if (result.sceneLoopStart >= 0) {
         result.sceneLoopStart += ticks;
      }

      if (result.sceneLoopEnd >= 0) {
         result.sceneLoopEnd += ticks;
      }

      if (result.playerJoinTick >= 0) {
         result.playerJoinTick += ticks;
      }

      if (result.sceneGuest != null) {
         result.sceneGuest.joinTick += ticks;
      }

      if (result.sceneCopy != null) {
         result.sceneCopy.joinTick += ticks;
      }

      if (result.sceneHeartTick >= 0) {
         result.sceneHeartTick += ticks;
      }

      result.duration += ticks;
      result.transitionTicks = 0.0F;
      result.transitionPose = null;
      result.entryTicks = ticks;
      result.entryPoseVelocity = velocity(previous, current);
      result.entryMotionVelocity = velocity == null ? new ActorMotion() : velocity.copy();
      arrival = arrival == null ? new ActorMotion() : arrival.copy();
      if (arrival.groundOffset != null && !Float.isFinite(arrival.groundOffset)) {
         arrival.groundOffset = null;
      }

      result.putFrame(0, current, arrival);
      result.sort();
      return result;
   }

   public static Map<String, BoneTransform> velocity(Map<String, BoneTransform> before, Map<String, BoneTransform> after) {
      HashMap<String, BoneTransform> result = new HashMap<>();

      for (NpcBone bone : NpcBone.values()) {
         BoneTransform a = before.getOrDefault(bone.id, ZERO);
         BoneTransform b = after.getOrDefault(bone.id, ZERO);
         BoneTransform v = new BoneTransform();
         v.rx = Mth.wrapDegrees(b.rx - a.rx);
         v.ry = Mth.wrapDegrees(b.ry - a.ry);
         v.rz = Mth.wrapDegrees(b.rz - a.rz);
         v.height = b.height - a.height;
         v.shiftX = b.shiftX - a.shiftX;
         v.shiftZ = b.shiftZ - a.shiftZ;
         result.put(bone.id, v);
      }

      return result;
   }

   public static Map<String, BoneTransform> pose(
      Map<String, BoneTransform> start,
      Map<String, BoneTransform> velocity,
      Map<String, BoneTransform> end,
      Map<String, BoneTransform> endVelocity,
      double age,
      int ticks
   ) {
      HashMap<String, BoneTransform> result = new HashMap<>();

      for (NpcBone bone : NpcBone.values()) {
         BoneTransform a = start.getOrDefault(bone.id, ZERO);
         BoneTransform b = end.getOrDefault(bone.id, ZERO);
         BoneTransform v = velocity == null ? ZERO : velocity.getOrDefault(bone.id, ZERO);
         BoneTransform z = endVelocity.getOrDefault(bone.id, ZERO);
         BoneTransform p = new BoneTransform();
         p.rx = curve(a.rx, a.rx + Mth.wrapDegrees(b.rx - a.rx), v.rx, z.rx, age, ticks);
         p.ry = curve(a.ry, a.ry + Mth.wrapDegrees(b.ry - a.ry), v.ry, z.ry, age, ticks);
         p.rz = curve(a.rz, a.rz + Mth.wrapDegrees(b.rz - a.rz), v.rz, z.rz, age, ticks);
         p.height = curve(a.height, b.height, v.height, z.height, age, ticks);
         p.shiftX = curve(a.shiftX, b.shiftX, v.shiftX, z.shiftX, age, ticks);
         p.shiftZ = curve(a.shiftZ, b.shiftZ, v.shiftZ, z.shiftZ, age, ticks);
         result.put(bone.id, p);
      }

      return result;
   }

   public static ActorMotion motion(ActorMotion a, ActorMotion b, ActorMotion velocity, ActorMotion after, double age, int ticks) {
      if (a == null) {
         a = new ActorMotion();
      }

      if (velocity == null) {
         velocity = new ActorMotion();
      }

      return new ActorMotion(
         curve(a.x, b.x, velocity.x, after.x - b.x, age, ticks),
         curve(a.y, b.y, velocity.y, after.y - b.y, age, ticks),
         curve(a.z, b.z, velocity.z, after.z - b.z, age, ticks),
         curve(a.yaw, a.yaw + Mth.wrapDegrees(b.yaw - a.yaw), velocity.yaw, Mth.wrapDegrees(after.yaw - b.yaw), age, ticks),
         age >= (double)ticks ? b.leadPlayer : a.leadPlayer,
         curve(a.groundOffset(), b.groundOffset(), 0.0F, 0.0F, age, ticks)
      );
   }

   public static float curve(float a, float b, float v, float endV, double age, int ticks) {
      float t = Mth.clamp((float)(age / (double)Math.max(1, ticks)), 0.0F, 1.0F);
      float d = b - a;
      if (Math.abs(d) < 1.0E-6F) {
         return a;
      } else {
         float u = Mth.clamp(v * (float)ticks / d, 0.0F, 3.0F);
         float z = Mth.clamp(endV * (float)ticks / d, 0.0F, 3.0F);
         float sum = u + z;
         if (sum > 3.0F) {
            u *= 3.0F / sum;
            z *= 3.0F / sum;
         }

         float t2 = t * t;
         float t3 = t2 * t;
         return a + d * (-2.0F * t3 + 3.0F * t2 + (t3 - 2.0F * t2 + t) * u + (t3 - t2) * z);
      }
   }

   private AnimationEntry() {
   }
}
