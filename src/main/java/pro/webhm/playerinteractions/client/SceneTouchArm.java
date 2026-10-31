package pro.webhm.playerinteractions.client;

import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import pro.webhm.playerinteractions.adornments.AdornmentReach;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class SceneTouchArm {
   private final int side;
   private final String armKey;
   private final String foreKey;
   private static final BoneTransform ZERO = new BoneTransform();
   private static final float RAD = (float) (Math.PI / 180.0);
   private static final float PIXEL = 0.05859375F;
   public final Matrix4f shoulderFrame = new Matrix4f();
   public final Vector3f shoulder = new Vector3f();
   public final Vector3f elbow = new Vector3f();
   public final Vector3f palm = new Vector3f();
   private final Matrix4f inverse = new Matrix4f();
   private final Quaternionf authored = new Quaternionf();
   private final Quaternionf rotation = new Quaternionf();
   private final Quaternionf lower = new Quaternionf();
   private final Quaternionf difference = new Quaternionf();
   private final Quaternionf interpolationStart = new Quaternionf();
   private final Quaternionf angularDelta = new Quaternionf();
   private final Quaternionf motionGoal = new Quaternionf();
   private final Quaternionf motionStep = new Quaternionf();
   private final Vector3f angularVelocity = new Vector3f();
   private final Vector3f bendVelocity = new Vector3f();
   private final Vector3f motionError = new Vector3f();
   private final Vector3f acceleration = new Vector3f();
   private final Vector3f bend = new Vector3f();
   private final Vector3f bendGoal = new Vector3f();
   private final Vector3f target = new Vector3f();
   private final Vector3f hint = new Vector3f();
   private final Vector3f angles = new Vector3f();
   private final Vector3f scratch = new Vector3f();
   private final Vector3f edgeStart = new Vector3f();
   private final Vector3f edgeEnd = new Vector3f();
   private final Vector3f pointerStart = new Vector3f();
   private final Vector3f pointerTrial = new Vector3f();
   private final Vector3f naturalPole = new Vector3f();
   private final Quaternionf torso = new Quaternionf();
   private final Quaternionf torsoInverse = new Quaternionf();
   private final Quaternionf swivelRotation = new Quaternionf();
   private boolean stroking;
   private boolean paired;
   private boolean routeLocked;
   private int fitSlide;
   private final Vector3f fitContact = new Vector3f();
   private final Vector3f fitTangent = new Vector3f();
   private float swivel;
   private static final float[] SWIVEL_OFFSETS = new float[]{0.0F, 15.0F, -15.0F, 30.0F, -30.0F, 45.0F, -45.0F, 60.0F, -60.0F, 90.0F, -90.0F};
   private final AdornmentReach reach = new AdornmentReach();
   private final Map<String, BoneTransform> result = new HashMap<>();
   private float center;
   private float width;
   private float length = 1.0F;
   private float thickness = 1.0F;
   private BoneTransform body;
   private BoneTransform chest;
   private BoneTransform originalArm;
   private BoneTransform originalFore;

   public SceneTouchArm() {
      this(false);
   }

   public SceneTouchArm(boolean left) {
      this.side = left ? -1 : 1;
      this.armKey = left ? "left_arm" : "right_arm";
      this.foreKey = left ? "left_forearm" : "right_forearm";
   }

   public void paired() {
      this.paired = true;
   }

   void route(float angle, int slide, boolean locked) {
      this.swivel = angle;
      this.fitSlide = slide;
      this.routeLocked = locked;
   }

   public static float thickness(float weight) {
      return 1.0F - 0.25F * Math.max(0.0F, Math.min(1.0F, weight));
   }

   public void begin(Map<String, BoneTransform> pose, float x, float y, float z, float yaw, boolean slim) {
      this.begin(pose, x, y, z, yaw, slim, 0.0F);
   }

   public void begin(Map<String, BoneTransform> pose, float x, float y, float z, float yaw, boolean slim, float stroke) {
      BoneTransform root = pose.getOrDefault("root", ZERO);
      this.body = pose.getOrDefault("body", ZERO);
      this.chest = pose.getOrDefault("chest", ZERO);
      this.originalArm = pose.getOrDefault(this.armKey, ZERO);
      this.originalFore = pose.getOrDefault(this.foreKey, ZERO);
      float originalWidth = slim ? 3.0F : 4.0F;
      this.thickness = this.length = 1.0F;
      this.center = 1.0F - originalWidth * 0.5F;
      this.width = originalWidth;
      this.shoulderFrame
         .identity()
         .translate(x, y, z)
         .rotateY((180.0F - yaw) * (float) (Math.PI / 180.0))
         .scale(-0.9375F, -0.9375F, 0.9375F)
         .translate(0.0F, -1.501F, 0.0F)
         .mul(AvatarKinematics.shoulder(pose, slim, this.side < 0, new Matrix4f()))
         .scale(0.0625F)
         .scale((float)this.side, 1.0F, 1.0F);
      this.inverse.set(this.shoulderFrame).invert();
      this.shoulderFrame.transformPosition(this.shoulder.zero());
      this.upper(this.originalArm, this.originalFore, this.authored);
      this.authored.transform(this.hint.set(0.0F, 1.0F, 0.0F));
      this.stroking = stroke > 0.0F;
      this.torso.identity();
      this.torsoInverse.set(this.torso).conjugate();
      this.torso.transform(this.naturalPole.set(-0.25F, 1.0F, 0.0F).normalize());
      if (this.stroking) {
         this.hint.set(this.naturalPole);
      }

      this.result.clear();
      this.result.putAll(pose);
      this.sample(this.originalArm, this.originalFore);
   }

   private Quaternionf upper(BoneTransform a, BoneTransform f, Quaternionf out) {
      return out.rotationZYX(
         (float)this.side * a.rz * (float) (Math.PI / 180.0), (float)this.side * a.ry * (float) (Math.PI / 180.0), a.rx * (float) (Math.PI / 180.0)
      );
   }

   public void seed(BoneTransform arm, BoneTransform fore) {
      if (!this.stroking) {
         this.upper(arm, fore, this.difference).transform(this.hint.set(0.0F, 1.0F, 0.0F));
      }

      this.hold(arm, fore);
   }

   public void pole(Vector3fc worldDirection) {
      this.inverse.transformDirection(this.hint.set(worldDirection)).normalize();
   }

   public boolean followPointer(SceneTouchSurface surface, Vector3fc goal, float limit, BoneTransform previousArm, BoneTransform previousFore) {
      this.sample(previousArm, previousFore);
      this.pointerStart.set(this.palm);
      if (this.solveClear(surface, goal, limit)) {
         return true;
      } else {
         BoneTransform bestArm = previousArm.copy();
         BoneTransform bestFore = previousFore.copy();
         float lo = 0.0F;
         float hi = 1.0F;

         for (int i = 0; i < 7; i++) {
            float t = (lo + hi) * 0.5F;
            this.pointerTrial.set(this.pointerStart).lerp(goal, t);
            if (this.solveClear(surface, this.pointerTrial, limit)) {
               lo = t;
               bestArm = this.result.get(this.armKey).copy();
               bestFore = this.result.get(this.foreKey).copy();
            } else {
               hi = t;
            }
         }

         this.hold(bestArm, bestFore);
         return lo > 0.001F;
      }
   }

   public boolean fit(SceneTouchSurface surface, Vector3fc contact, Vector3fc normal, float limit, Vector3f goal) {
      BoneTransform savedArm = this.result.get(this.armKey).copy();
      BoneTransform savedFore = this.result.get(this.foreKey).copy();
      Vector3f savedHint = new Vector3f(this.hint);
      float previousSwivel = this.swivel;

      for (int attempt = 0; attempt < (this.routeLocked ? 1 : (this.stroking ? SWIVEL_OFFSETS.length : 8)); attempt++) {
         if (this.stroking) {
            float candidate = previousSwivel + SWIVEL_OFFSETS[attempt];
            if (Math.abs(candidate) > 45.0F) {
               continue;
            }

            this.swivel = candidate;
         } else if (attempt > 0) {
            switch (attempt) {
               case 1:
                  this.scratch.set(normal);
                  break;
               case 2:
                  this.scratch.set(0.0F, 1.0F, 0.0F);
                  break;
               case 3:
                  this.scratch.set(1.0F, 0.0F, 0.0F);
                  break;
               case 4:
                  this.scratch.set(-1.0F, 0.0F, 0.0F);
                  break;
               case 5:
                  this.scratch.set(0.0F, -1.0F, 0.0F);
                  break;
               case 6:
                  this.scratch.set(0.0F, 0.0F, 1.0F);
                  break;
               default:
                  this.scratch.set(0.0F, 0.0F, -1.0F);
            }

            this.pole(this.scratch);
         }

         this.fitTangent.set(contact).sub(this.shoulder);
         this.fitTangent.fma(-this.fitTangent.dot(normal), normal);
         if ((double)this.fitTangent.lengthSquared() > 1.0E-8) {
            this.fitTangent.normalize();
         } else {
            this.fitTangent.zero();
         }

         for (int slide = 0; slide < (this.paired && !this.routeLocked ? 4 : 1); slide++) {
            int candidateSlide = this.paired ? (this.fitSlide + slide) % 4 : 0;
            this.fitContact.set(contact).fma((float)candidateSlide * 0.025F, this.fitTangent);
            float distance = this.clearance(normal);

            for (int i = 0; i < 10; i++) {
               goal.set(this.fitContact).fma(distance, normal);
               this.solve(goal, limit, 1.0F);
               distance += (this.clearance(normal) - distance) * 0.5F;
            }

            goal.set(this.fitContact).fma(distance, normal);
            this.solve(goal, limit, 1.0F);
            if (this.palm.distance(goal) < 0.025F && (!this.paired || this.skinGap(contact) < 0.018F) && this.clear(surface)) {
               this.fitSlide = candidateSlide;
               return true;
            }
         }
      }

      this.swivel = previousSwivel;
      this.hint.set(savedHint);
      this.hold(savedArm, savedFore);
      return false;
   }

   private boolean solveClear(SceneTouchSurface surface, Vector3fc goal, float limit) {
      float previous = this.swivel;

      for (float offset : SWIVEL_OFFSETS) {
         if ((this.routeLocked || !this.stroking) && offset != 0.0F) {
            break;
         }

         float candidate = previous + offset;
         if (!(Math.abs(candidate) > 45.0F)) {
            this.swivel = candidate;
            this.solve(goal, limit, 1.0F);
            if (this.clear(surface)) {
               return true;
            }
         }
      }

      this.swivel = previous;
      return false;
   }

   public Map<String, BoneTransform> hold(BoneTransform arm, BoneTransform fore) {
      this.result.put(this.armKey, arm.copy());
      this.result.put(this.foreKey, fore.copy());
      this.sample(arm, fore);
      return this.result;
   }

   Map<String, BoneTransform> current() {
      return this.result;
   }

   void resetMotion() {
      this.angularVelocity.zero();
      this.bendVelocity.zero();
   }

   Map<String, BoneTransform> inertial(BoneTransform previousArm, BoneTransform previousFore, float dt) {
      if (dt > 0.0F && Float.isFinite(dt)) {
         BoneTransform a = this.result.get(this.armKey);
         BoneTransform f = this.result.get(this.foreKey);
         this.upper(a, f, this.motionGoal);
         this.upper(previousArm, previousFore, this.rotation);
         this.bend.set(previousFore.rx, previousFore.ry, previousFore.rz).mul((float) (Math.PI / 180.0));
         this.bendGoal.set(f.rx, f.ry, f.rz).mul((float) (Math.PI / 180.0));
         int steps = Math.max(1, (int)Math.ceil((double)(Math.min(2.0F, dt) * 12.0F)));
         float h = Math.min(2.0F, dt) / (float)steps;

         for (int i = 0; i < steps; i++) {
            this.angularDelta.set(this.rotation).conjugate();
            this.motionStep.set(this.motionGoal).mul(this.angularDelta).normalize();
            if (this.motionStep.w < 0.0F) {
               this.motionStep.mul(-1.0F);
            }

            this.motionError.set(this.motionStep.x, this.motionStep.y, this.motionStep.z);
            float sine = this.motionError.length();
            if (sine > 1.0E-8F) {
               this.motionError.mul(2.0F * (float)Math.atan2((double)sine, (double)this.motionStep.w) / sine);
            } else {
               this.motionError.zero();
            }

            this.velocity(this.angularVelocity, this.motionError, h);
            float speed = this.angularVelocity.length();
            if (speed > 1.0E-8F) {
               this.motionStep.rotationAxis(speed * h, this.angularVelocity.x / speed, this.angularVelocity.y / speed, this.angularVelocity.z / speed);
               this.rotation.premul(this.motionStep).normalize();
            }

            this.velocity(this.bendVelocity, this.motionError.set(this.bendGoal).sub(this.bend), h);
            this.bend.fma(h, this.bendVelocity);
         }

         euler(this.rotation, this.angles);
         BoneTransform fore = f.copy();
         fore.rx = this.bend.x / (float) (Math.PI / 180.0);
         fore.ry = this.bend.y / (float) (Math.PI / 180.0);
         fore.rz = this.bend.z / (float) (Math.PI / 180.0);
         BoneTransform arm = a.copy();
         arm.rx = this.angles.x / (float) (Math.PI / 180.0);
         arm.ry = (float)this.side * this.angles.y / (float) (Math.PI / 180.0);
         arm.rz = (float)this.side * this.angles.z / (float) (Math.PI / 180.0);
         return this.hold(arm, fore);
      } else {
         return this.hold(previousArm, previousFore);
      }
   }

   private void velocity(Vector3f velocity, Vector3fc error, float dt) {
      this.acceleration.set(error).mul(0.1225F).fma(-0.7F, velocity);
      if (this.acceleration.lengthSquared() > 5.9705006E-4F) {
         this.acceleration.normalize(0.02443461F);
      }

      velocity.fma(dt, this.acceleration);
      if (velocity.lengthSquared() > 0.010966227F) {
         velocity.normalize(0.10471976F);
      }
   }

   public Map<String, BoneTransform> release(BoneTransform previousArm, BoneTransform previousFore, float amount) {
      this.upper(previousArm, previousFore, this.rotation);
      this.upper(this.originalArm, this.originalFore, this.difference);
      this.rotation.slerp(this.difference, amount);
      euler(this.rotation, this.angles);
      BoneTransform fore = BoneTransform.lerp(previousFore, this.originalFore, amount);
      BoneTransform arm = this.originalArm.copy();
      arm.rx = this.angles.x / (float) (Math.PI / 180.0);
      arm.ry = (float)this.side * this.angles.y / (float) (Math.PI / 180.0);
      arm.rz = (float)this.side * this.angles.z / (float) (Math.PI / 180.0);
      return this.hold(arm, fore);
   }

   public Map<String, BoneTransform> smooth(BoneTransform previousArm, BoneTransform previousFore, float dt) {
      if (!(dt > 0.0F)) {
         return this.hold(previousArm, previousFore);
      } else {
         float angle = this.turn(previousArm, previousFore);
         float alpha = Math.min(-((float)Math.expm1((double)(-dt) * 0.65)), 10.0F * dt / Math.max(1.0E-5F, angle));
         return this.blend(previousArm, previousFore, alpha);
      }
   }

   public float turn(BoneTransform previousArm, BoneTransform previousFore) {
      BoneTransform a = this.result.get(this.armKey);
      BoneTransform f = this.result.get(this.foreKey);
      this.upper(previousArm, previousFore, this.interpolationStart);
      this.upper(a, f, this.difference);
      this.angularDelta.set(this.interpolationStart).conjugate().mul(this.difference).normalize();
      float sine = (float)Math.sqrt(
         (double)(this.angularDelta.x * this.angularDelta.x + this.angularDelta.y * this.angularDelta.y + this.angularDelta.z * this.angularDelta.z)
      );
      float upperTurn = 2.0F * (float)Math.atan2((double)sine, (double)Math.abs(this.angularDelta.w)) / (float) (Math.PI / 180.0);
      return Math.max(upperTurn, Math.max(Math.abs(f.rx - previousFore.rx), Math.max(Math.abs(f.ry - previousFore.ry), Math.abs(f.rz - previousFore.rz))));
   }

   public Map<String, BoneTransform> blend(BoneTransform previousArm, BoneTransform previousFore, float amount) {
      if (!(amount > 0.0F)) {
         return this.hold(previousArm, previousFore);
      } else {
         amount = Math.min(1.0F, amount);
         BoneTransform a = this.result.get(this.armKey);
         BoneTransform f = this.result.get(this.foreKey);
         this.upper(previousArm, previousFore, this.interpolationStart);
         this.upper(a, f, this.difference);
         this.rotation.set(this.interpolationStart).slerp(this.difference, amount);
         euler(this.rotation, this.angles);
         BoneTransform fore = BoneTransform.lerp(previousFore, f, amount);
         BoneTransform arm = a.copy();
         arm.rx = this.angles.x / (float) (Math.PI / 180.0);
         arm.ry = (float)this.side * this.angles.y / (float) (Math.PI / 180.0);
         arm.rz = (float)this.side * this.angles.z / (float) (Math.PI / 180.0);
         return this.hold(arm, fore);
      }
   }

   public float skinGap(Vector3fc point) {
      this.inverse.transformPosition(this.scratch.set(point));
      this.rotation.transformInverse(this.scratch);
      this.scratch.y = this.scratch.y - 4.0F * this.length;
      this.lower.transformInverse(this.scratch);
      float x = Math.max(0.0F, Math.abs(this.scratch.x - this.center) - (this.width * 0.5F + 0.25F * this.thickness));
      float y = Math.max(0.0F, Math.max(3.5F * this.length - this.scratch.y, this.scratch.y - 6.25F * this.length));
      float z = Math.max(0.0F, Math.abs(this.scratch.z) - 2.25F * this.thickness);
      return (float)Math.sqrt((double)(x * x + y * y + z * z)) * 0.05859375F;
   }

   public float contactGap(Vector3fc point, Vector3fc normal) {
      float plane = (this.palm.x - point.x()) * normal.x()
         + (this.palm.y - point.y()) * normal.y()
         + (this.palm.z - point.z()) * normal.z()
         - this.clearance(normal)
         + 0.001F;
      return Math.max(0.0F, Math.max(plane, this.skinGap(point) - 0.025F));
   }

   public Map<String, BoneTransform> solve(Vector3fc worldTarget, float limit, float blend) {
      this.inverse.transformPosition(this.target.set(worldTarget));
      if (this.stroking) {
         this.constrainStrokeTarget();
      }

      this.reach.solve(this.target, this.hint, 4.0F * this.length, 6.0F * this.length, this.center);
      this.rotation.set(this.reach.upper);
      float turn = 2.0F * (float)Math.acos((double)Math.min(1.0F, Math.abs(this.rotation.dot(this.authored))));
      if (turn > limit * (float) (Math.PI / 180.0)) {
         this.rotation.set(this.authored).slerp(this.reach.upper, limit * (float) (Math.PI / 180.0) / turn);
      }

      euler(this.rotation, this.angles);
      BoneTransform arm = this.originalArm.copy();
      BoneTransform fore = this.originalFore.copy();
      arm.rx = this.angles.x / (float) (Math.PI / 180.0);
      arm.ry = (float)this.side * this.angles.y / (float) (Math.PI / 180.0);
      arm.rz = (float)this.side * this.angles.z / (float) (Math.PI / 180.0);
      fore.rx = Math.max(-155.0F, Math.max(this.originalFore.rx - limit, Math.min(this.originalFore.rx + limit, this.reach.bend / (float) (Math.PI / 180.0))));
      fore.ry = fore.rz = 0.0F;
      (this.side == 1 ? NpcBone.RIGHT_ARM : NpcBone.LEFT_ARM).clamp(arm);
      (this.side == 1 ? NpcBone.RIGHT_FOREARM : NpcBone.LEFT_FOREARM).clamp(fore);
      this.upper(arm, fore, this.difference);
      this.rotation.set(this.authored).slerp(this.difference, blend);
      euler(this.rotation, this.angles);
      fore = BoneTransform.lerp(this.originalFore, fore, blend);
      arm.rx = this.angles.x / (float) (Math.PI / 180.0);
      arm.ry = (float)this.side * this.angles.y / (float) (Math.PI / 180.0);
      arm.rz = (float)this.side * this.angles.z / (float) (Math.PI / 180.0);
      this.result.put(this.armKey, arm);
      this.result.put(this.foreKey, fore);
      this.sample(arm, fore);
      return this.result;
   }

   private void constrainStrokeTarget() {
      this.torsoInverse.transform(this.target);
      float requested = this.target.length();
      if (!Float.isFinite(requested) || requested < 1.0E-5F) {
         this.target.set(0.0F, 0.0F, -1.0F);
         requested = 1.0F;
      }

      this.target.z = Math.min(this.target.z, -0.15F * requested);
      this.target.y = Math.max(this.target.y, -0.35F * requested);
      float first = 4.0F * this.length;
      float second = 6.0F * this.length;
      float near = (float)Math.sqrt(
         (double)(first * first + second * second)
            + (double)(2.0F * first * second) * Math.cos((double)((float)(this.paired ? 150 : 140) * (float) (Math.PI / 180.0)))
            + (double)(this.center * this.center)
      );
      float far = (float)Math.sqrt(
         (double)(first * first + second * second) + (double)(2.0F * first * second) * Math.cos((float) (Math.PI / 4)) + (double)(this.center * this.center)
      );
      this.target.normalize().mul(Math.max(near, Math.min(far, requested)));
      this.torso.transform(this.target);
      this.hint.set(this.naturalPole);
      if (this.swivel != 0.0F) {
         this.scratch.set(this.target).normalize();
         this.swivelRotation.rotationAxis(this.swivel * (float) (Math.PI / 180.0), this.scratch).transform(this.hint);
      }
   }

   public void sample(BoneTransform arm, BoneTransform fore) {
      this.upper(arm, fore, this.rotation);
      this.lower
         .rotationZYX(
            (float)this.side * fore.rz * (float) (Math.PI / 180.0), (float)this.side * fore.ry * (float) (Math.PI / 180.0), fore.rx * (float) (Math.PI / 180.0)
         );
      this.rotation.transform(this.elbow.set(0.0F, 4.0F * this.length, 0.0F));
      this.shoulderFrame.transformPosition(this.elbow);
      this.lower.transform(this.palm.set(this.center, 6.0F * this.length, 0.0F)).add(0.0F, 4.0F * this.length, 0.0F);
      this.rotation.transform(this.palm);
      this.shoulderFrame.transformPosition(this.palm);
   }

   public float clearance(Vector3fc normal) {
      float min = 0.0F;

      for (int x = 0; x < 2; x++) {
         for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
               this.scratch
                  .set(
                     (float)(x == 0 ? -1 : 1) * (this.width * 0.5F + 0.25F * this.thickness),
                     (y == 0 ? -2.5F : 0.25F) * this.length,
                     (z == 0 ? -2.25F : 2.25F) * this.thickness
                  );
               this.lower.transform(this.scratch);
               this.rotation.transform(this.scratch);
               this.shoulderFrame.transformDirection(this.scratch);
               min = Math.min(min, this.scratch.dot(normal));
            }
         }
      }

      return -min + 0.001F;
   }

   public boolean clear(SceneTouchSurface surface) {
      if (surface.clear(this.shoulder, this.elbow, 0.01F) && surface.clear(this.elbow, this.palm, 0.09F)) {
         for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
               float a = this.center + (float)(x == 0 ? -1 : 1) * (this.width * 0.5F + 0.25F * this.thickness);
               float b = (z == 0 ? -2.25F : 2.25F) * this.thickness;
               this.rotation.transform(this.edgeStart.set(a, -2.0F * this.length, b));
               this.shoulderFrame.transformPosition(this.edgeStart);
               this.rotation.transform(this.edgeEnd.set(a, 4.0F * this.length, b));
               this.shoulderFrame.transformPosition(this.edgeEnd);
               if (!surface.clear(this.edgeStart, this.edgeEnd, 0.01F)) {
                  return false;
               }

               this.lower.transform(this.edgeStart.set(a, 0.0F, b)).add(0.0F, 4.0F * this.length, 0.0F);
               this.rotation.transform(this.edgeStart);
               this.shoulderFrame.transformPosition(this.edgeStart);
               this.lower.transform(this.edgeEnd.set(a, 6.0F * this.length, b)).add(0.0F, 4.0F * this.length, 0.0F);
               this.rotation.transform(this.edgeEnd);
               this.shoulderFrame.transformPosition(this.edgeEnd);
               if (!surface.clear(this.edgeStart, this.edgeEnd, 0.09F)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static void euler(Quaternionfc q, Vector3f out) {
      Matrix3f m = new Matrix3f().set(q);
      float cy = (float)Math.hypot((double)m.m00(), (double)m.m01());
      out.set(
         (double)cy > 1.0E-6 ? (float)Math.atan2((double)m.m12(), (double)m.m22()) : 0.0F,
         (float)Math.atan2((double)(-m.m02()), (double)cy),
         (double)cy > 1.0E-6 ? (float)Math.atan2((double)m.m01(), (double)m.m00()) : (float)Math.atan2((double)(-m.m10()), (double)m.m11())
      );
   }
}
