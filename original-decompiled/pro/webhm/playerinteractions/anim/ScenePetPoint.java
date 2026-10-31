package pro.webhm.playerinteractions.anim;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import pro.webhm.playerinteractions.adornments.AdornmentProject;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class ScenePetPoint {
   public String id = "";
   public boolean repeat;
   public int tick;
   public boolean petting = true;
   public int mode;
   public boolean surface = true;
   public boolean anchored;
   public int strokeVersion;
   public float anchorX;
   public float anchorY;
   public float anchorZ;
   public String target = "";
   public String bone = "body";
   public int face;
   public float u = 0.5F;
   public float v = 0.5F;
   public float area = 24.0F;
   public float radius = 3.0F;
   public float depth = 0.45F;
   public float reachLimit = 170.0F;
   public float yawLimit = 18.0F;
   public float pitchLimit = 14.0F;
   public float elbowLimit = 10.0F;

   public boolean twoHanded() {
      return this.mode == 1;
   }

   public String label() {
      return this.twoHanded() ? "Две руки · поглаживание + сжатие" : "Поглаживание";
   }

   public void normalize(int duration) {
      this.tick = Math.max(0, Math.min(Math.max(0, duration - 1), this.tick));
      this.yawLimit = limit(this.yawLimit, 18.0F);
      this.pitchLimit = limit(this.pitchLimit, 14.0F);
      this.elbowLimit = limit(this.elbowLimit, 10.0F);
      this.surface = this.petting = true;
      this.mode = this.mode == 1 ? 1 : 0;
      if (this.strokeVersion < 1) {
         this.area = 24.0F;
         this.reachLimit = 170.0F;
         this.strokeVersion = 1;
      }

      if (this.strokeVersion < 2) {
         if (Math.abs(this.radius - 1.5F) < 0.001F) {
            this.radius = 3.0F;
         }

         if (Math.abs(this.depth - 0.18F) < 0.001F) {
            this.depth = 0.45F;
         }

         this.strokeVersion = 2;
      }

      if (this.id == null || this.id.length() > 64) {
         this.id = "";
      }

      if (!Arrays.asList(AdornmentProject.BONES).contains(this.bone)) {
         this.bone = "body";
      }

      this.face = Math.max(0, Math.min(3, this.face));
      this.u = finite(this.u, 0.5F, 0.0F, 1.0F);
      this.v = finite(this.v, 0.5F, 0.0F, 1.0F);
      this.area = finite(this.area, 24.0F, 4.0F, 64.0F);
      this.radius = finite(this.radius, 3.0F, 0.5F, 4.0F);
      this.depth = finite(this.depth, 0.45F, 0.0F, 0.75F);
      this.reachLimit = finite(this.reachLimit, 170.0F, 10.0F, 180.0F);
      this.anchorX = finite(this.anchorX, 0.0F, -64.0F, 64.0F);
      this.anchorY = finite(this.anchorY, 0.0F, -64.0F, 64.0F);
      this.anchorZ = finite(this.anchorZ, 0.0F, -64.0F, 64.0F);
      if (this.target == null) {
         this.target = "";
      }

      if (this.target.length() > 64) {
         this.target = "";
      }
   }

   private static float finite(float n, float fallback, float min, float max) {
      return Float.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback;
   }

   private static float limit(float x, float fallback) {
      return Float.isFinite(x) ? Math.max(0.0F, Math.min(45.0F, x)) : fallback;
   }

   public ScenePetPoint copy() {
      ScenePetPoint p = new ScenePetPoint();
      p.id = this.id;
      p.repeat = this.repeat;
      p.tick = this.tick;
      p.petting = this.petting;
      p.yawLimit = this.yawLimit;
      p.pitchLimit = this.pitchLimit;
      p.elbowLimit = this.elbowLimit;
      p.mode = this.mode;
      p.surface = this.surface;
      p.strokeVersion = this.strokeVersion;
      p.anchored = this.anchored;
      p.anchorX = this.anchorX;
      p.anchorY = this.anchorY;
      p.anchorZ = this.anchorZ;
      p.target = this.target;
      p.bone = this.bone;
      p.face = this.face;
      p.u = this.u;
      p.v = this.v;
      p.area = this.area;
      p.radius = this.radius;
      p.depth = this.depth;
      p.reachLimit = this.reachLimit;
      return p;
   }

   public Map<String, BoneTransform> apply(Map<String, BoneTransform> original, float x, float y) {
      x = Float.isFinite(x) ? Math.max(-1.0F, Math.min(1.0F, x)) : 0.0F;
      y = Float.isFinite(y) ? Math.max(-1.0F, Math.min(1.0F, y)) : 0.0F;
      HashMap<String, BoneTransform> p = new HashMap<>(original);
      BoneTransform arm = original.getOrDefault("right_arm", new BoneTransform()).copy();
      BoneTransform fore = original.getOrDefault("right_forearm", new BoneTransform()).copy();
      arm.ry = arm.ry + x * this.yawLimit;
      arm.rx = arm.rx + y * this.pitchLimit;
      fore.rx = fore.rx + y * this.elbowLimit;
      p.put("right_arm", NpcBone.RIGHT_ARM.clamped(arm));
      p.put("right_forearm", NpcBone.RIGHT_FOREARM.clamped(fore));
      return p;
   }

   public static final class Hold {
      private long since = Long.MIN_VALUE;
      private long last = Long.MIN_VALUE;

      public void input(long now, boolean down) {
         if (!down) {
            this.reset();
         } else {
            if (this.since == Long.MIN_VALUE || now < this.last || now - this.last > 8L) {
               this.since = now;
            }

            this.last = now;
         }
      }

      public int ticks(long now) {
         return this.since != Long.MIN_VALUE && now >= this.last && now - this.last <= 8L ? (int)Math.min(100L, Math.max(0L, now - this.since)) : 0;
      }

      public boolean complete(long now) {
         return this.ticks(now) >= 100;
      }

      public void reset() {
         this.since = this.last = Long.MIN_VALUE;
      }
   }
}
