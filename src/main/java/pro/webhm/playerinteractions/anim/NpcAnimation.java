package pro.webhm.playerinteractions.anim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;
import pro.webhm.playerinteractions.adornments.AdornmentGripKey;
import pro.webhm.playerinteractions.body.BodyRuntime;
import pro.webhm.playerinteractions.facial.FacialData;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.BoneTransformAdapter;
import pro.webhm.playerinteractions.npc.NpcBone;
import pro.webhm.playerinteractions.npc.NpcEquipment;

public class NpcAnimation {
   private static final Gson GSON = new GsonBuilder().registerTypeAdapter(BoneTransform.class, new BoneTransformAdapter()).create();
   private static final Gson PRETTY = new GsonBuilder().registerTypeAdapter(BoneTransform.class, new BoneTransformAdapter()).setPrettyPrinting().create();
   private static final BoneTransform IDENTITY = new BoneTransform();
   public List<NpcAnimation.Wink> winks = new ArrayList<>();
   public List<SceneCue> cues = new ArrayList<>();
   public List<SceneEyeGlow> eyeGlow = new ArrayList<>();
   public List<SceneEyeGlow> blush = new ArrayList<>();
   public boolean sharedEyeGlow = true;
   public boolean eyeGlowClockOnly;
   public ScenePetPoint petPoint;
   public List<ScenePetPoint> petPoints;
   public List<AdornmentGripKey> adornmentKeys = new ArrayList<>();
   public List<NpcAnimation.FaceKey> faceKeys = new ArrayList<>();
   public String name = "unnamed";
   public boolean bedRelative;
   public boolean smooth = false;
   public float transitionTicks;
   public float transitionStart;
   public Map<String, BoneTransform> transitionPose;
   public int entryTicks;
   public Map<String, BoneTransform> entryPoseVelocity;
   public ActorMotion entryMotionVelocity;
   public boolean withItems = false;
   public int duration = 40;
   public List<Keyframe> frames = new ArrayList<>();
   public String rootPivot;
   public int sceneLoopStart = -1;
   public boolean continuousAction;
   public ActorMotion approachPoint;
   public SceneGuest sceneGuest;
   public SceneGuest sceneCopy;
   public int copyTrackVersion;
   public int sceneHeartTick = -1;
   public long sceneHeartEpoch = -1L;
   public boolean sceneHeartBody;
   public Float sceneScale;
   public List<SceneScaleKey> scaleKeys = new ArrayList<>();
   public boolean headKeysOnly;
   public int playerJoinTick = -1;
   public boolean holdSceneLoopEnd = false;
   public int sceneLoopEnd = -1;
   public Float sceneOffsetX;
   public Float sceneOffsetY;
   public Float sceneOffsetZ;
   public Float sceneOffsetYaw;

   public float wink(float time, boolean loop, int eye) {
      if (this.winks == null) {
         return 0.0F;
      } else {
         float result = 0.0F;

         for (NpcAnimation.Wink w : this.winks) {
            if (w != null && w.eye == eye && w.duration >= 1) {
               float elapsed = time - (float)w.tick;
               if (loop && this.duration > 0) {
                  elapsed = (elapsed % (float)this.duration + (float)this.duration) % (float)this.duration;
               }

               if (!(elapsed < 0.0F) && !(elapsed >= (float)w.duration)) {
                  float t = elapsed / (float)w.duration;
                  float a = t < 0.3F ? t / 0.3F : 1.0F - (t - 0.3F) / 0.7F;
                  result = Math.max(result, a * a * (3.0F - 2.0F * a));
               }
            }
         }

         return result;
      }
   }

   public float eyeGlow(double time, boolean loop) {
      return this.faceEffect(this.eyeGlow, time, loop);
   }

   public float blush(double time, boolean loop) {
      return this.faceEffect(this.blush, time, loop);
   }

   private float faceEffect(List<SceneEyeGlow> points, double time, boolean loop) {
      if (points != null && !points.isEmpty()) {
         if (loop && this.hasSceneLoop()) {
            time = this.sceneLoopTime(time);
         } else if (loop && this.duration > 0) {
            time = (time % (double)this.duration + (double)this.duration) % (double)this.duration;
         }

         return SceneEyeGlow.sample(points, time);
      } else {
         return Float.NaN;
      }
   }

   public List<ScenePetPoint> touchPoints() {
      return this.petPoints != null ? this.petPoints : (this.petPoint == null ? List.of() : List.of(this.petPoint));
   }

   public void setTouchPoints(List<ScenePetPoint> points) {
      this.petPoints = new ArrayList<>();
      if (points != null) {
         for (ScenePetPoint p : points) {
            if (p != null) {
               this.petPoints.add(p.copy());
            }
         }
      }

      this.normalizeTouchPoints();
   }

   public void normalizeTouchPoints() {
      if (this.petPoints == null) {
         this.petPoints = new ArrayList<>(this.petPoint == null ? List.of() : List.of(this.petPoint));
      }

      this.petPoints.removeIf(px -> px == null);
      if (this.petPoints.size() > 32) {
         this.petPoints = new ArrayList<>(this.petPoints.subList(0, 32));
      }

      HashSet<String> ids = new HashSet<>();
      int index = 0;

      for (ScenePetPoint p : this.petPoints) {
         p.normalize(this.duration);
         if (p.id.isBlank() || !ids.add(p.id)) {
            String base = "scene-touch:" + index++ + ":" + p.tick;
            int salt = 0;

            while (true) {
               p.id = UUID.nameUUIDFromBytes((base + ":" + salt++).getBytes(StandardCharsets.UTF_8)).toString();
               if (ids.add(p.id)) {
                  break;
               }
            }
         }
      }

      this.petPoints.sort(Comparator.comparingInt(px -> px.tick));
      this.petPoint = this.petPoints.isEmpty() ? null : this.petPoints.get(0);
   }

   public NpcAnimation.FaceKey sampleFace(float time, boolean loop) {
      if (this.faceKeys != null && !this.faceKeys.isEmpty()) {
         if (loop && this.hasSceneLoop()) {
            time = (float)this.sceneLoopTime((double)time);
         } else if (loop && this.duration > 0) {
            time = (time % (float)this.duration + (float)this.duration) % (float)this.duration;
         }

         NpcAnimation.FaceKey before = null;
         NpcAnimation.FaceKey after = null;

         for (NpcAnimation.FaceKey key : this.faceKeys) {
            if (key != null) {
               if ((float)key.tick <= time && (before == null || key.tick > before.tick)) {
                  before = key;
               }

               if ((float)key.tick >= time && (after == null || key.tick < after.tick)) {
                  after = key;
               }
            }
         }

         if (before == null) {
            return null;
         } else {
            if (after == null) {
               after = before;
            }

            float t = after.tick == before.tick ? 0.0F : (time - (float)before.tick) / (float)(after.tick - before.tick);
            if (this.smooth) {
               t = t * t * (3.0F - 2.0F * t);
            }

            NpcAnimation.FaceKey result = new NpcAnimation.FaceKey();
            result.left = Mth.clamp(Mth.lerp(t, before.left, after.left), 0.0F, 1.0F);
            result.right = Mth.clamp(Mth.lerp(t, before.right, after.right), 0.0F, 1.0F);
            result.x = Mth.clamp(Mth.lerp(t, before.x, after.x), -1.0F, 1.0F);
            result.y = Mth.clamp(Mth.lerp(t, before.y, after.y), -1.0F, 1.0F);
            result.facialKey = before.facialKey;
            result.manualMouth = before.manualMouth;
            result.open = FacialData.clamp(Mth.lerp(t, before.open, after.open), 0.0F, 1.0F);
            result.smile = FacialData.clamp(Mth.lerp(t, before.smile, after.smile), -1.0F, 1.0F);
            result.brow = FacialData.clamp(Mth.lerp(t, before.brow, after.brow), -1.0F, 1.0F);
            result.squint = FacialData.clamp(Mth.lerp(t, before.squint, after.squint), 0.0F, 1.0F);
            result.aimHead = before.aimHead;
            result.headYaw = Mth.clamp(Mth.lerp(t, before.headYaw, after.headYaw), -90.0F, 90.0F);
            result.headPitch = Mth.clamp(Mth.lerp(t, before.headPitch, after.headPitch), -90.0F, 90.0F);
            return result;
         }
      } else {
         return null;
      }
   }

   public NpcAnimation withTransition(Map<String, BoneTransform> pose, float ticks) {
      NpcAnimation result = this.copy();
      result.transitionPose = deepCopy(pose);
      result.transitionStart = 0.0F;
      result.transitionTicks = Math.max(0.0F, ticks);
      return result;
   }

   private Map<String, BoneTransform> blendEntry(Map<String, BoneTransform> target, double time) {
      time -= (double)this.transitionStart;
      if (this.transitionPose != null && !(this.transitionTicks <= 0.0F) && !(time >= (double)this.transitionTicks)) {
         float t = (float)Math.max(0.0, time / (double)this.transitionTicks);
         t = t * t * (3.0F - 2.0F * t);
         Map<String, BoneTransform> result = new HashMap<>();

         for (NpcBone bone : NpcBone.values()) {
            BoneTransform a = this.transitionPose.getOrDefault(bone.id, IDENTITY);
            BoneTransform b = target.getOrDefault(bone.id, IDENTITY);
            BoneTransform mixed = BoneTransform.lerp(a, b, t);
            mixed.rx = a.rx + Mth.wrapDegrees(b.rx - a.rx) * t;
            mixed.ry = a.ry + Mth.wrapDegrees(b.ry - a.ry) * t;
            mixed.rz = a.rz + Mth.wrapDegrees(b.rz - a.rz) * t;
            result.put(bone.id, mixed);
         }

         return result;
      } else {
         return target;
      }
   }

   public boolean hasScaleKeys() {
      return this.scaleKeys != null && !this.scaleKeys.isEmpty();
   }

   public float actorScaleAt(float base, double time, boolean loop) {
      if (!this.hasScaleKeys()) {
         return this.actorScale(base);
      } else {
         if (loop && this.hasSceneLoop()) {
            time = this.sceneLoopTime(time);
         } else if (loop && this.duration > 0) {
            time = (time % (double)this.duration + (double)this.duration) % (double)this.duration;
         }

         return SceneScaleKey.sample(this.scaleKeys, base, time);
      }
   }

   public float actorScale(float fallback) {
      return this.sceneScale != null && Float.isFinite(this.sceneScale) ? Mth.clamp(this.sceneScale, 0.1F, 8.0F) : fallback;
   }

   public double nextActionExit(double elapsed) {
      if (!this.hasSceneLoop()) {
         return (double)this.duration;
      } else if (elapsed < (double)this.sceneLoopEnd) {
         return (double)this.sceneLoopEnd;
      } else {
         double length = (double)(this.sceneLoopEnd - this.sceneLoopStart);
         return (double)this.sceneLoopEnd + (Math.floor((elapsed - (double)this.sceneLoopEnd) / length) + 1.0) * length;
      }
   }

   public void closeSceneLoop() {
      if (this.hasSceneLoop()) {
         Map<String, BoneTransform> pose = this.sample((double)this.sceneLoopStart, false);
         ActorMotion motion = this.sampleMotion((double)this.sceneLoopStart, false);
         NpcEquipment items = this.sampleItems((double)this.sceneLoopStart, false);
         NpcAnimation.FaceKey face = this.sampleFace((float)this.sceneLoopStart, false);
         this.putFrame(this.sceneLoopEnd, pose, motion);

         for (Keyframe frame : this.frames) {
            if (frame.tick == this.sceneLoopEnd) {
               frame.items = items == null ? null : items.copy();
            }
         }

         if (face != null) {
            face.tick = this.sceneLoopEnd;
            this.faceKeys.removeIf(f -> f.tick == this.sceneLoopEnd);
            this.faceKeys.add(face);
         }
      }
   }

   public String sceneLoopWarning() {
      if (!this.hasSceneLoop()) {
         return "Цикл не задан — клип проиграется один раз";
      } else {
         ActorMotion a = this.sampleMotion((double)this.sceneLoopStart, false);
         ActorMotion b = this.sampleMotion((double)this.sceneLoopEnd, false);
         if (!(Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z) > 0.01F)
            && !(Math.abs(Mth.wrapDegrees(a.yaw - b.yaw)) > 0.5F)
            && !(Math.abs(a.groundOffset() - b.groundOffset()) > 0.1F)) {
            Map<String, BoneTransform> from = this.sample((double)this.sceneLoopStart, false);
            Map<String, BoneTransform> to = this.sample((double)this.sceneLoopEnd, false);

            for (NpcBone bone : NpcBone.values()) {
               BoneTransform x = from.getOrDefault(bone.id, IDENTITY);
               BoneTransform y = to.getOrDefault(bone.id, IDENTITY);
               if (Math.abs(Mth.wrapDegrees(x.rx - y.rx)) > 0.5F
                  || Math.abs(Mth.wrapDegrees(x.ry - y.ry)) > 0.5F
                  || Math.abs(Mth.wrapDegrees(x.rz - y.rz)) > 0.5F
                  || Math.abs(x.height - y.height) + Math.abs(x.shiftX - y.shiftX) + Math.abs(x.shiftZ - y.shiftZ) > 0.1F) {
                  return "Разрыв позы на стыке цикла";
               }
            }

            return this.sceneLoopEnd == this.duration ? "После конца цикла нет кадров выхода" : "Поза и путь на стыке совпадают";
         } else {
            return "Разрыв пути на стыке цикла";
         }
      }
   }

   public boolean hasSceneOrigin() {
      return this.sceneOffsetX != null && this.sceneOffsetY != null && this.sceneOffsetZ != null && this.sceneOffsetYaw != null;
   }

   public void setSceneOrigin(float x, float y, float z, float yaw) {
      this.sceneOffsetX = x;
      this.sceneOffsetY = y;
      this.sceneOffsetZ = z;
      this.sceneOffsetYaw = Mth.wrapDegrees(yaw);
   }

   public String toJson() {
      return GSON.toJson(this);
   }

   public String toPrettyJson() {
      return PRETTY.toJson(this);
   }

   public static NpcAnimation fromJson(String json) {
      if (json != null && !json.isEmpty()) {
         try {
            NpcAnimation a = (NpcAnimation)GSON.fromJson(json, NpcAnimation.class);
            if (a == null) {
               return new NpcAnimation();
            } else {
               if (a.frames == null) {
                  a.frames = new ArrayList<>();
               }

               if (a.cues == null) {
                  a.cues = new ArrayList<>();
               }

               a.cues.removeIf(c -> c == null);
               if (a.cues.size() > 128) {
                  a.cues = new ArrayList<>(a.cues.subList(0, 128));
               }

               a.cues.forEach(SceneCue::normalize);
               if (a.faceKeys == null) {
                  a.faceKeys = new ArrayList<>();
               }

               if (a.winks == null) {
                  a.winks = new ArrayList<>();
               }

               a.eyeGlow = SceneEyeGlow.normalize(a.eyeGlow);
               a.blush = SceneEyeGlow.normalize(a.blush);
               a.scaleKeys = SceneScaleKey.normalize(a.scaleKeys);
               if (a.sceneScale != null) {
                  a.sceneScale = Float.isFinite(a.sceneScale) ? Mth.clamp(a.sceneScale, 0.1F, 8.0F) : null;
               }

               a.frames.removeIf(f -> f == null || f.pose == null);
               if (a.duration < 1) {
                  a.duration = 1;
               }

               a.sort();
               a.fixDuration();
               a.fixSceneLoop();
               a.fixPlayerJoin();
               if (a.sceneGuest != null && (!a.sceneGuest.valid() || !a.sceneGuest.recoverSpawnPoint(a.entryTicks))) {
                  a.sceneGuest = null;
               }

               if (a.sceneCopy != null && (a.sceneGuest == null || !a.sceneCopy.valid())) {
                  a.sceneCopy = null;
               }

               if (a.sceneCopy != null) {
                  a.sceneCopy.joinTick = Math.max(a.sceneGuest.joinTick, a.sceneCopy.joinTick);
               }

               a.normalizeTouchPoints();
               if (a.adornmentKeys == null) {
                  a.adornmentKeys = new ArrayList<>();
               }

               int gripDuration = a.duration;
               a.adornmentKeys.removeIf(k -> k == null || !k.valid(gripDuration));
               if (a.adornmentKeys.size() > 256) {
                  a.adornmentKeys = new ArrayList<>(a.adornmentKeys.subList(0, 256));
               }

               return a;
            }
         } catch (Exception var3) {
            BodyRuntime.LOG.error("Битый JSON анимации, клип пропущен", var3);
            return new NpcAnimation();
         }
      } else {
         return new NpcAnimation();
      }
   }

   public void sort() {
      this.frames.sort(Comparator.comparingInt(f -> f.tick));
   }

   public int lastTick() {
      int last = this.frames.isEmpty() ? 0 : this.frames.get(this.frames.size() - 1).tick;
      if (this.cues != null) {
         for (SceneCue cue : this.cues) {
            if (cue != null) {
               last = Math.max(last, cue.tick + cue.length - 1);
            }
         }
      }

      if (this.faceKeys != null) {
         for (NpcAnimation.FaceKey key : this.faceKeys) {
            if (key != null) {
               last = Math.max(last, key.tick);
            }
         }
      }

      if (this.winks != null) {
         for (NpcAnimation.Wink keyx : this.winks) {
            if (keyx != null) {
               last = Math.max(last, keyx.tick + Math.max(1, keyx.duration) - 1);
            }
         }
      }

      if (this.eyeGlow != null) {
         for (SceneEyeGlow point : this.eyeGlow) {
            if (point != null) {
               last = Math.max(last, point.tick + point.fadeTicks);
            }
         }
      }

      if (this.blush != null) {
         for (SceneEyeGlow pointx : this.blush) {
            if (pointx != null) {
               last = Math.max(last, pointx.tick + pointx.fadeTicks);
            }
         }
      }

      if (this.scaleKeys != null) {
         for (SceneScaleKey keyxx : this.scaleKeys) {
            if (keyxx != null) {
               last = Math.max(last, keyxx.tick);
            }
         }
      }

      if (this.sceneCopy != null && this.sceneCopy.joinTick >= 0 && this.sceneCopy.joinTick <= 1000000) {
         last = Math.max(last, this.sceneCopy.joinTick);
      }

      for (ScenePetPoint pointxx : this.touchPoints()) {
         if (pointxx != null) {
            last = Math.max(last, Math.min(Math.max(0, this.duration - 1), Math.max(0, pointxx.tick)));
         }
      }

      return last;
   }

   public void fixDuration() {
      if (this.duration <= this.lastTick()) {
         this.duration = this.lastTick() + 1;
      }

      this.fixSceneLoop();
      this.fixPlayerJoin();
   }

   public boolean setDuration(int v) {
      if (v <= this.lastTick()) {
         return false;
      } else {
         this.duration = Math.max(1, v);
         this.fixSceneLoop();
         this.fixPlayerJoin();
         return true;
      }
   }

   public void fixSceneLoop() {
      if (this.sceneLoopStart < 0 || this.sceneLoopStart >= this.duration) {
         this.sceneLoopStart = -1;
      }

      if (this.sceneLoopEnd <= 0 || this.sceneLoopEnd > this.duration) {
         this.sceneLoopEnd = -1;
      }

      if (this.sceneLoopStart >= 0 && this.sceneLoopEnd >= 0 && this.sceneLoopEnd <= this.sceneLoopStart) {
         this.sceneLoopEnd = -1;
      }
   }

   public boolean hasSceneLoop() {
      return this.sceneLoopStart >= 0 && this.sceneLoopEnd > this.sceneLoopStart && this.sceneLoopEnd <= this.duration;
   }

   public void fixPlayerJoin() {
      if (this.playerJoinTick < 0 || this.playerJoinTick >= this.duration) {
         this.playerJoinTick = -1;
      }
   }

   public boolean hasPlayerJoin() {
      return this.playerJoinTick >= 0 && this.playerJoinTick < this.duration;
   }

   public double sceneLoopTime(double time) {
      if (this.holdSceneLoopEnd && this.hasSceneLoop()) {
         return Math.min(time, (double)this.sceneLoopEnd);
      } else if (this.hasSceneLoop() && !(time < (double)this.sceneLoopEnd)) {
         double length = (double)(this.sceneLoopEnd - this.sceneLoopStart);
         return (double)this.sceneLoopStart + ((time - (double)this.sceneLoopStart) % length + length) % length;
      } else {
         return time;
      }
   }

   public boolean isEmpty() {
      return this.frames.isEmpty();
   }

   public boolean hasMotion() {
      for (Keyframe frame : this.frames) {
         if (frame.motion != null) {
            return true;
         }
      }

      return false;
   }

   public void putFrame(int tick, Map<String, BoneTransform> pose, NpcEquipment items) {
      this.putFrame(tick, pose);

      for (Keyframe k : this.frames) {
         if (k.tick == tick) {
            k.items = items;
            break;
         }
      }
   }

   public void putFrame(int tick, Map<String, BoneTransform> pose) {
      this.frames.removeIf(f -> f.tick == tick);
      this.frames.add(new Keyframe(tick, deepCopy(pose)));
      this.sort();
      if (tick >= this.duration) {
         this.duration = tick + 1;
      }
   }

   public void putFrame(int tick, Map<String, BoneTransform> pose, ActorMotion motion) {
      this.putFrame(tick, pose);

      for (Keyframe k : this.frames) {
         if (k.tick == tick) {
            k.motion = motion == null ? null : motion.copy();
            break;
         }
      }
   }

   private static Map<String, BoneTransform> deepCopy(Map<String, BoneTransform> src) {
      Map<String, BoneTransform> m = new HashMap<>();
      if (src != null) {
         src.forEach((k, v) -> {
            if (v != null) {
               m.put(k, v.copy());
            }
         });
      }

      return m;
   }

   public void removeFrame(int tick) {
      this.frames.removeIf(f -> f.tick == tick);
   }

   public void appendReversed() {
      if (!this.frames.isEmpty()) {
         this.sort();
         int len = this.duration;
         List<Keyframe> mirrored = new ArrayList<>();

         for (Keyframe k : this.frames) {
            int tick = len * 2 - k.tick;
            if (tick > len && tick < len * 2) {
               Keyframe mk = new Keyframe(tick, deepCopy(k.pose));
               mk.items = k.items == null ? null : k.items.copy();
               mk.motion = k.motion == null ? null : k.motion.copy();
               mirrored.add(mk);
            }
         }

         this.frames.addAll(mirrored);
         this.duration = len * 2;
         this.sort();
      }
   }

   public Map<String, BoneTransform> sample(double time, boolean loop) {
      if (this.entryTicks > 0 && time >= 0.0 && time < (double)this.entryTicks && !this.frames.isEmpty()) {
         Map<String, BoneTransform> target = this.sample((double)this.entryTicks, false);
         return AnimationEntry.pose(
            this.frames.get(0).pose,
            this.entryPoseVelocity,
            target,
            AnimationEntry.velocity(target, this.sample((double)(this.entryTicks + 1), false)),
            time,
            this.entryTicks
         );
      } else {
         Map<String, BoneTransform> out = new HashMap<>();
         if (this.frames.isEmpty()) {
            return this.blendEntry(out, time);
         } else if (this.frames.size() == 1) {
            this.frames.get(0).pose.forEach((k, v) -> out.put(k, v.copy()));
            return this.blendEntry(out, time);
         } else {
            List<Keyframe> list = this.frames;
            if (loop && this.hasSceneLoop()) {
               time = this.sceneLoopTime(time);
            } else if (loop) {
               time = (time % (double)this.duration + (double)this.duration) % (double)this.duration;
               Keyframe first = this.frames.get(0);
               Keyframe last = this.frames.get(this.frames.size() - 1);
               if (last.tick < this.duration || first.tick > 0) {
                  list = new ArrayList<>(this.frames);
                  boolean tail = last.tick < this.duration;
                  if (tail) {
                     list.add(new Keyframe(this.duration, first.pose));
                  }

                  if (first.tick > 0) {
                     list.add(0, new Keyframe(0, tail ? first.pose : last.pose));
                  }
               }
            } else {
               time = Math.min(time, (double)list.get(list.size() - 1).tick);
            }

            Keyframe prev = list.get(0);
            Keyframe next = list.get(list.size() - 1);

            for (int i = 0; i < list.size() - 1; i++) {
               if (time >= (double)list.get(i).tick && time <= (double)list.get(i + 1).tick) {
                  prev = list.get(i);
                  next = list.get(i + 1);
                  break;
               }
            }

            double span = (double)(next.tick - prev.tick);
            double td = span <= 0.0 ? 0.0 : (time - (double)prev.tick) / span;
            td = Math.max(0.0, Math.min(1.0, td));
            if (this.smooth) {
               td = td * td * (3.0 - 2.0 * td);
            }

            float t = (float)td;

            for (NpcBone bone : NpcBone.values()) {
               BoneTransform a = prev.pose.getOrDefault(bone.id, IDENTITY);
               BoneTransform b = next.pose.getOrDefault(bone.id, IDENTITY);
               out.put(bone.id, BoneTransform.lerp(a, b, t));
            }

            return this.blendEntry(out, time);
         }
      }
   }

   public ActorMotion sampleMotion(double time, boolean loop) {
      if (this.entryTicks > 0 && time >= 0.0 && time < (double)this.entryTicks && !this.frames.isEmpty()) {
         return AnimationEntry.motion(
            this.frames.get(0).motion,
            this.sampleMotion((double)this.entryTicks, false),
            this.entryMotionVelocity,
            this.sampleMotion((double)(this.entryTicks + 1), false),
            time,
            this.entryTicks
         );
      } else if (this.frames.isEmpty()) {
         return new ActorMotion();
      } else if (this.frames.size() == 1) {
         ActorMotion m = this.frames.get(0).motion;
         return m == null ? new ActorMotion() : m.copy();
      } else {
         List<Keyframe> list = this.frames;
         if (loop && this.hasSceneLoop()) {
            time = this.sceneLoopTime(time);
         } else if (loop) {
            time = (time % (double)this.duration + (double)this.duration) % (double)this.duration;
            Keyframe first = this.frames.get(0);
            Keyframe last = this.frames.get(this.frames.size() - 1);
            if (last.tick < this.duration || first.tick > 0) {
               list = new ArrayList<>(this.frames);
               boolean tail = last.tick < this.duration;
               if (tail) {
                  Keyframe wrap = new Keyframe(this.duration, first.pose);
                  wrap.motion = first.motion == null ? null : first.motion.copy();
                  list.add(wrap);
               }

               if (first.tick > 0) {
                  Keyframe source = tail ? first : last;
                  Keyframe head = new Keyframe(0, source.pose);
                  head.motion = source.motion == null ? null : source.motion.copy();
                  list.add(0, head);
               }
            }
         } else {
            time = Math.min(time, (double)list.get(list.size() - 1).tick);
         }

         Keyframe prev = list.get(0);
         Keyframe next = list.get(list.size() - 1);

         for (int i = 0; i < list.size() - 1; i++) {
            if (time >= (double)list.get(i).tick && time <= (double)list.get(i + 1).tick) {
               prev = list.get(i);
               next = list.get(i + 1);
               break;
            }
         }

         double span = (double)(next.tick - prev.tick);
         double td = span <= 0.0 ? 0.0 : (time - (double)prev.tick) / span;
         td = Math.max(0.0, Math.min(1.0, td));
         if (this.smooth) {
            td = td * td * (3.0 - 2.0 * td);
         }

         return ActorMotion.lerp(prev.motion, next.motion, (float)td);
      }
   }

   public NpcEquipment sampleItems(double time, boolean loop) {
      if (this.withItems && !this.frames.isEmpty()) {
         if (this.frames.size() == 1) {
            return this.frames.get(0).items == null ? null : this.frames.get(0).items.copy();
         } else {
            List<Keyframe> list = this.frames;
            if (loop && this.hasSceneLoop()) {
               time = this.sceneLoopTime(time);
            } else if (loop) {
               time = (time % (double)this.duration + (double)this.duration) % (double)this.duration;
               Keyframe first = this.frames.get(0);
               Keyframe last = this.frames.get(this.frames.size() - 1);
               if (last.tick < this.duration || first.tick > 0) {
                  list = new ArrayList<>(this.frames);
                  boolean tail = last.tick < this.duration;
                  if (tail) {
                     Keyframe wrap = new Keyframe(this.duration, first.pose);
                     wrap.items = first.items;
                     list.add(wrap);
                  }

                  if (first.tick > 0) {
                     Keyframe head = new Keyframe(0, tail ? first.pose : last.pose);
                     head.items = tail ? first.items : last.items;
                     list.add(0, head);
                  }
               }
            } else {
               time = Math.min(time, (double)list.get(list.size() - 1).tick);
            }

            Keyframe prev = list.get(0);
            Keyframe next = list.get(list.size() - 1);

            for (int i = 0; i < list.size() - 1; i++) {
               if (time >= (double)list.get(i).tick && time <= (double)list.get(i + 1).tick) {
                  prev = list.get(i);
                  next = list.get(i + 1);
                  break;
               }
            }

            if (prev.items == null) {
               return null;
            } else {
               double span = (double)(next.tick - prev.tick);
               double td = span <= 0.0 ? 0.0 : (time - (double)prev.tick) / span;
               td = Math.max(0.0, Math.min(1.0, td));
               if (this.smooth) {
                  td = td * td * (3.0 - 2.0 * td);
               }

               float t = (float)td;
               return next.items == null ? prev.items.copy() : NpcEquipment.lerp(prev.items, next.items, t);
            }
         }
      } else {
         return null;
      }
   }

   public NpcAnimation copy() {
      return fromJson(this.toJson());
   }

   public static class FaceKey {
      public int tick;
      public float left;
      public float right;
      public float x;
      public float y;
      public float headYaw;
      public float headPitch;
      public boolean aimHead;
      public boolean facialKey;
      public boolean manualMouth;
      public float open;
      public float smile;
      public float brow;
      public float squint;
   }

   public static class Wink {
      public int tick;
      public int eye;
      public int duration = 8;
   }
}
