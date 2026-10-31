package pro.webhm.playerinteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import pro.webhm.playerinteractions.anim.ActorMotion;
import pro.webhm.playerinteractions.anim.AnimationEntry;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.anim.NpcAnimation;
import pro.webhm.playerinteractions.anim.PlaybackClock;
import pro.webhm.playerinteractions.anim.PlayerRigPose;
import pro.webhm.playerinteractions.anim.SceneLoop;
import pro.webhm.playerinteractions.anim.StaminaVisual;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class SessionState {
   public static final int ENTRY = 12;
   public static final int EXIT = 12;
   public UUID id;
   public int action;
   public long clock;
   public double elapsed;
   public boolean paused;
   public int progress;
   public List<SessionState.Actor> actors = new ArrayList<>();
   public int zone = -1;
   public int side = 1;
   public int mode;
   public int touchSerial;
   public float dragX;
   public float dragY;
   public float dragZ;
   public float aimX;
   public float aimY;
   public long touchStarted;
   public double contactX;
   public double contactY;
   public double contactZ;
   public float touchBodyYaw;
   public float touchHeadYaw;
   public float touchPitch;
   public boolean touching;
   public boolean pressure;
   public boolean pauseCompleted;
   public SceneLoop loop;
   public float pairYaw;
   public float pairSpeed;
   public double pairDistance;
   public boolean waitingForJoin;
   public boolean joinStarted;
   public boolean joinComplete;
   public double joinElapsed;
   private transient PlaybackClock playback;
   private transient StaminaVisual staminaVisual;

   public void initializeScene() {
      if (Actions.scene(this.action)) {
         NpcAnimation clip = Clip.get(this.action, false).original;
         if (clip.hasSceneLoop()) {
            this.loop = new SceneLoop(clip.sceneLoopStart, clip.sceneLoopEnd, clip.continuousAction);
         }
      }
   }

   public boolean loopHeld() {
      return this.loop != null && this.loop.held();
   }

   public boolean canRepeat(UUID player) {
      return this.loop != null
         && this.loop.ready()
         && !this.paused
         && !this.waitingForJoin
         && !this.joining()
         && this.initiator().id.equals(player)
         && this.elapsed >= (double)(12 + this.loop.end);
   }

   public boolean repeat(UUID player, int expectedPass) {
      if (this.canRepeat(player) && this.loop.repeat(expectedPass)) {
         this.elapsed = (double)(12 + this.loop.start);
         this.resetLoopPause();
         return true;
      } else {
         return false;
      }
   }

   private void resetLoopPause() {
      if (this.loop != null && Clip.get(this.action, false).repeatPause(this.loop.start, this.loop.end)) {
         this.pauseCompleted = false;
      }
   }

   public long maximumTicks() {
      return this.loop != null && !this.loop.continuous ? 6012L + (long)this.duration() + 12L + 12L * (long)(this.loop.end - this.loop.start) + 16L : 6000L;
   }

   public int reactionZone() {
      return this.zone != 0 && this.zone != 12 ? this.zone : (this.mode == 0 ? 12 : 0);
   }

   public int joinTick() {
      return Actions.scene(this.action) ? Clip.get(this.action, false).joinTick() : -1;
   }

   public boolean joining() {
      return this.joinTick() >= 0 && this.joinStarted && !this.joinComplete;
   }

   public boolean sceneActive(UUID player) {
      return Actions.scene(this.action) && this.actor(player) != null && (this.recipient().id.equals(player) || this.joinTick() < 0 || this.joinStarted);
   }

   public boolean controlsMovement(UUID player) {
      return this.sceneActive(player) || Actions.together(this.action) && this.recipient().id.equals(player);
   }

   public double joinTime(long now, float partial) {
      return this.joining() ? Math.min(12.0, this.joinElapsed + (double)Math.max(0L, now - this.clock) + (double)partial) : 12.0;
   }

   public boolean beginJoin(UUID player, Vec3 from, float yaw) {
      if (this.waitingForJoin && !this.joinStarted && this.initiator().id.equals(player)) {
         SessionState.Actor a = this.initiator();
         a.fromX = from.x;
         a.fromY = from.y;
         a.fromZ = from.z;
         a.fromYaw = yaw;
         this.waitingForJoin = false;
         this.joinStarted = true;
         this.joinElapsed = 0.0;
         return true;
      } else {
         return false;
      }
   }

   public void advanceScene() {
      this.advanceScene(this.clock + 1L);
   }

   public void advanceScene(long now) {
      if (this.joining()) {
         this.joinElapsed = Math.min(12.0, this.joinElapsed + 1.0);
         if (this.joinElapsed >= 12.0) {
            this.joinComplete = true;
         }
      } else if (!this.waitingForJoin && !this.paused) {
         if (this.loop == null) {
            this.elapsed++;
         } else {
            int before = this.loop.pass;
            this.elapsed = 12.0 + this.loop.advance(this.elapsed - 12.0 + (double)(this.loop.held() ? 0 : 1), now);
            if (this.loop.pass != before) {
               this.resetLoopPause();
            }
         }

         if (this.joinTick() >= 0 && !this.joinStarted && this.elapsed >= (double)(12 + this.joinTick())) {
            this.elapsed = (double)(12 + this.joinTick());
            this.waitingForJoin = true;
         }
      }
   }

   public Vec3 target(SessionState.Actor actor, double time, double approach) {
      Vec3 target = actor.target(this.action, time);
      return actor.slot == 0 && this.joinStarted && approach < 12.0 ? actor.from().lerp(target, (double)Clip.smooth(approach / 12.0)) : target;
   }

   public float heading(SessionState.Actor actor, double time, double approach) {
      float yaw = actor.heading(this.action, time);
      return actor.slot == 0 && this.joinStarted && approach < 12.0 ? Mth.rotLerp(Clip.smooth(approach / 12.0), actor.fromYaw, yaw) : yaw;
   }

   public Map<String, BoneTransform> rigPose(SessionState.Actor actor, double time, double approach) {
      if (actor.slot == 0 && this.joinStarted && !(approach >= 12.0)) {
         Map<String, BoneTransform> pose = actor.pose(this.action, time, this.duration());
         float w = Clip.smooth(approach / 12.0);
         BoneTransform var10000 = pose.computeIfAbsent("root", k -> new BoneTransform());
         var10000.height = var10000.height - actor.clip(this.action).motion(Math.max(0.0, time - 12.0)).groundOffset();
         pose.replaceAll((key, value) -> BoneTransform.lerp(new BoneTransform(), value, w));
         return PlayerRigPose.convert(pose);
      } else {
         return actor.rigPose(this.action, time, this.duration());
      }
   }

   public void bindClient(SessionState previous, long now) {
      boolean same = previous != null && previous.id.equals(this.id);
      boolean samePass = same && (this.loop == null ? previous.loop == null : previous.loop != null && this.loop.pass == previous.loop.pass);
      this.playback = samePass ? previous.playback : null;
      if (this.playback == null) {
         this.playback = new PlaybackClock();
      }

      this.playback.update(this.elapsed, this.clock, this.paused || this.waitingForJoin || this.joining() || this.loopHeld(), (double)now);
      this.staminaVisual = same ? previous.staminaVisual : null;
      if (this.loop != null && !this.loop.continuous) {
         if (this.staminaVisual == null) {
            this.staminaVisual = new StaminaVisual();
         }

         this.staminaVisual.update(this.loop.count, (double)now);
      }
   }

   public float visualStamina(double now) {
      return this.staminaVisual == null ? (float)(this.loop == null ? 0 : this.loop.count) : this.staminaVisual.sample(now);
   }

   public int duration() {
      return Math.max(Clip.get(this.action, true).duration, Clip.get(this.action, false).duration);
   }

   public double time(long now, float partial) {
      double t = this.playback == null
         ? this.elapsed
            + (double)(!this.paused && !this.waitingForJoin && !this.joining() && !this.loopHeld() ? (float)Math.max(0L, now - this.clock) + partial : 0.0F)
         : this.playback.sample((double)now + (double)partial);
      if (this.loop != null) {
         t = 12.0 + this.loop.sample(t - 12.0);
      }

      if (this.joinTick() >= 0 && !this.joinComplete) {
         t = Math.min(t, (double)(12 + this.joinTick()));
      }

      if (Actions.scene(this.action) && !this.pauseCompleted) {
         int pause = Clip.get(this.action, false).pauseTick();
         if (pause >= 0) {
            t = Math.min(t, (double)(12 + pause));
         }
      }

      return t;
   }

   public SessionState.Actor actor(UUID id) {
      return this.actors.stream().filter(a -> a.id.equals(id)).findFirst().orElse(null);
   }

   public SessionState.Actor initiator() {
      return this.actors.get(0);
   }

   public SessionState.Actor recipient() {
      return this.actors.get(1);
   }

   public boolean receivingTouch(UUID player, long now) {
      return this.action == 4 && this.touching && this.actors.size() > 1 && this.recipient().id.equals(player) && now - this.clock <= 12L;
   }

   public static class Actor {
      public UUID id;
      public String name;
      public int slot;
      public double x;
      public double y;
      public double z;
      public double fromX;
      public double fromY;
      public double fromZ;
      public float yaw;
      public float fromYaw;

      public Actor() {
      }

      public Actor(UUID id, String name, int slot, Vec3 origin, float yaw, Vec3 from, float fromYaw) {
         this.id = id;
         this.name = name;
         this.slot = slot;
         this.x = origin.x;
         this.y = origin.y;
         this.z = origin.z;
         this.yaw = yaw;
         this.fromX = from.x;
         this.fromY = from.y;
         this.fromZ = from.z;
         this.fromYaw = fromYaw;
      }

      public Clip clip(int action) {
         return Clip.get(action, this.slot == 0);
      }

      public Vec3 from() {
         return new Vec3(this.fromX, this.fromY, this.fromZ);
      }

      public Vec3 target(int action, double elapsed) {
         ActorMotion motion = this.clip(action).motion(Math.max(0.0, elapsed - 12.0));
         double a = Math.toRadians((double)this.yaw);
         Vec3 target = new Vec3(
            this.x + (double)motion.x * Math.cos(a) - (double)motion.z * Math.sin(a),
            this.y + (double)motion.y,
            this.z + (double)motion.z * Math.cos(a) + (double)motion.x * Math.sin(a)
         );
         return elapsed < 12.0 ? this.from().lerp(target, (double)Clip.smooth(elapsed / 12.0)) : target;
      }

      public float heading(int action, double elapsed) {
         float target = this.yaw + this.clip(action).motion(Math.max(0.0, elapsed - 12.0)).yaw;
         return elapsed < 12.0 ? Mth.rotLerp(Clip.smooth(elapsed / 12.0), this.fromYaw, target) : target;
      }

      public Map<String, BoneTransform> pose(int action, double elapsed, int duration) {
         Clip source = this.clip(action);
         Map<String, BoneTransform> pose = this.slot == 1 && elapsed < 12.0
            ? AnimationEntry.pose(Map.of(), Map.of(), source.pose(0.0), AnimationEntry.velocity(source.pose(0.0), source.pose(1.0)), elapsed, 12)
            : source.pose(Math.max(0.0, elapsed - 12.0));
         float weight = elapsed < 12.0 ? (this.slot == 1 ? 1.0F : Clip.smooth(elapsed / 12.0)) : 1.0F - Clip.smooth((elapsed - 12.0 - (double)duration) / 12.0);
         pose.replaceAll((key, value) -> BoneTransform.lerp(new BoneTransform(), value, weight));
         return pose;
      }

      public Map<String, BoneTransform> rigPose(int action, double elapsed, int duration) {
         Map<String, BoneTransform> pose = this.pose(action, elapsed, duration);
         BoneTransform var10000 = pose.computeIfAbsent("root", k -> new BoneTransform());
         var10000.height = var10000.height - this.clip(action).motion(Math.max(0.0, elapsed - 12.0)).groundOffset();
         return this.slot == 0 ? PlayerRigPose.convert(pose) : pose;
      }
   }
}
