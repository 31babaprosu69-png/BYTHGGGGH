package pro.webhm.playerinteractions.client;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.body.SurfaceDent;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class SceneTouchRuntime {
   public final SurfaceDent stroke = new SurfaceDent();
   public final SceneTouchSurface surface = new SceneTouchSurface();
   public final SceneStrokeResponse response = new SceneStrokeResponse();
   public final SceneContactReaction reaction = new SceneContactReaction();
   public String bone = "body";
   public String status = InteractionClient.text("touch.approach").getString();
   public final Vector3f marker = new Vector3f();
   public final Vector3f markerNormal = new Vector3f(0.0F, 1.0F, 0.0F);
   private final SceneTouchArm arm = new SceneTouchArm();
   private final SceneSqueezeRuntime squeeze = new SceneSqueezeRuntime();
   private final SceneStrokePointer pointer = new SceneStrokePointer();
   private final Vector3f anchor = new Vector3f();
   private final Vector3f camera = new Vector3f();
   private final Vector3f aim = new Vector3f();
   private final Vector3f right = new Vector3f();
   private final Vector3f up = new Vector3f();
   private final Vector3f ray = new Vector3f();
   private final Vector3f target = new Vector3f();
   private final Vector3f normal = new Vector3f();
   private final Vector3f goal = new Vector3f();
   private final Vector3f lastPoint = new Vector3f();
   private final Vector3f lastNormal = new Vector3f();
   private final Vector3f filtered = new Vector3f();
   private double sampled = Double.NaN;
   private boolean validLast;
   private boolean hasPose;
   private float lastWeight;
   private BoneTransform lastArm = new BoneTransform();
   private BoneTransform lastFore = new BoneTransform();
   private Map<String, BoneTransform> pose = Map.of();
   public Vec3 origin = Vec3.ZERO;

   public void invalidate() {
      this.sampled = Double.NaN;
   }

   public Map<String, BoneTransform> update(ScenePetClient.State state, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return Map.of();
      } else {
         double now = (double)mc.level.getGameTime() + (double)partial;
         if (now <= this.sampled) {
            return this.pose;
         } else {
            float dt = Double.isNaN(this.sampled) ? 0.0F : (float)Math.min(2.0, now - this.sampled);
            this.sampled = now;
            ScenePetClient.TouchActor actor = ScenePetClient.touchActor(state.owner, partial);
            if (actor == null) {
               this.stroke.depth = 0.0F;
               this.response.shift.zero();
               this.reaction.clear();
               return this.pose = Map.of();
            } else {
               Map<String, BoneTransform> authored = actor.pose();
               this.pose = authored;
               if (mc.level.getEntity(state.npc) instanceof AbstractClientPlayer npc) {
                  if (state.point.twoHanded()) {
                     return this.pose = this.squeeze.update(this, state, partial, dt, actor, npc);
                  } else {
                     this.reaction.clear();
                     this.stroke.squeeze.separateWeight = 0.0F;
                     this.stroke.squeeze.active = false;
                     if (!this.hasPose) {
                        this.lastArm = authored.getOrDefault("right_arm", new BoneTransform()).copy();
                        this.lastFore = authored.getOrDefault("right_forearm", new BoneTransform()).copy();
                        this.hasPose = true;
                     }

                     Quaternionf view = SceneViewRotation.orientation(authored, actor.yaw());
                     view.transform(this.right.set(1.0F, 0.0F, 0.0F));
                     view.transform(this.up.set(0.0F, 1.0F, 0.0F));
                     this.response.follow(dt, this.right, this.up, state.x, state.y, state.weight * state.pressureFade);
                     this.bone = state.point.bone;
                     this.surface.sample(npc, partial, this.stroke);
                     this.origin = this.surface.origin.add((double)this.response.shift.x, (double)this.response.shift.y, (double)this.response.shift.z);
                     this.surface.anchor(state.point, this.anchor);
                     this.surface.world.transformPosition(this.anchor);
                     Vec3 relative = actor.position().subtract(this.origin);
                     SceneViewRotation.eye(authored, actor.yaw(), this.camera).add((float)relative.x, (float)relative.y, (float)relative.z);
                     float area = state.point.area / 32.0F * Math.max(0.05F, AvatarRig.sceneScale());
                     this.aim.set(this.anchor).fma(state.x * area, this.right).fma(-state.y * area, this.up);
                     this.surface.inverse.transformPosition(this.camera);
                     this.surface.inverse.transformPosition(this.aim);
                     this.ray.set(this.aim).sub(this.camera);
                     boolean contact = this.pointer.pick(this.surface.mesh, this.camera, this.aim);
                     float requested = state.point.depth / 16.0F * (0.7F + 0.3F * state.pressureAmount) * state.weight * state.pressureFade;
                     boolean accepted = false;
                     this.arm.begin(authored, (float)relative.x, (float)relative.y, (float)relative.z, actor.yaw(), actor.slim(), state.weight);
                     if (contact) {
                        this.arm.begin(authored, (float)relative.x, (float)relative.y, (float)relative.z, actor.yaw(), actor.slim(), state.weight);
                        this.arm.seed(this.lastArm, this.lastFore);
                        this.stroke.center.set(this.pointer.point);
                        this.stroke.normal.set(this.pointer.normal);
                        this.stroke.radius = state.point.radius / 16.0F;
                        this.stroke.depth = this.response.depth;
                        this.surface.contactPatch(this.stroke);
                        this.stroke.displace(this.stroke.center.x, this.stroke.center.y, this.stroke.center.z, this.target);
                        this.surface.world.transformPosition(this.target);
                        this.surface.world.transformDirection(this.normal.set(this.stroke.normal)).normalize();
                        boolean clear = this.arm.fit(this.surface, this.target, this.normal, state.point.reachLimit, this.goal);
                        if (clear) {
                           this.arm.solve(this.goal, state.point.reachLimit, state.weight);
                           this.pose = this.arm.smooth(this.lastArm, this.lastFore, dt);
                           float gap = this.arm.palm.distance(this.goal);
                           this.stroke.depth = this.response.pressure(dt, requested, gap, this.arm.clear(this.surface));
                           this.status = gap < 0.025F
                              ? InteractionClient.text("touch.stroke").getString()
                              : InteractionClient.text("touch.approach").getString();
                           this.validLast = true;
                           this.lastPoint.set(this.stroke.center);
                           this.lastNormal.set(this.stroke.normal);
                           this.marker.set(this.target);
                           this.markerNormal.set(this.normal);
                           accepted = true;
                        }
                     }

                     if (!accepted) {
                        this.arm.begin(authored, (float)relative.x, (float)relative.y, (float)relative.z, actor.yaw(), actor.slim(), state.weight);
                        this.arm.seed(this.lastArm, this.lastFore);
                        if (!contact) {
                           this.surface.contactPatch(null);
                           this.surface.world.transformPosition(this.target.set(this.pointer.point));
                           this.surface.world.transformDirection(this.normal.set(this.pointer.normal)).normalize();
                           this.goal.set(this.target).fma(this.arm.clearance(this.normal), this.normal);
                        }

                        boolean moved = this.arm.followPointer(this.surface, this.goal, state.point.reachLimit, this.lastArm, this.lastFore);
                        this.pose = this.arm.smooth(this.lastArm, this.lastFore, dt);
                        this.stroke.depth = this.response.pressure(dt, 0.0F, 1.0F, false);
                        if (this.validLast) {
                           this.stroke.center.set(this.lastPoint);
                           this.stroke.normal.set(this.lastNormal);
                        }

                        this.status = moved
                           ? (contact ? InteractionClient.text("touch.reach").getString() : InteractionClient.text("touch.aim").getString())
                           : InteractionClient.text("touch.limit").getString();
                        this.marker.set(this.target);
                        this.markerNormal.set(this.normal);
                     }

                     if (state.weight < this.lastWeight && this.lastWeight > 0.0F) {
                        this.pose = this.arm.release(this.lastArm, this.lastFore, 1.0F - state.weight / this.lastWeight);
                     }

                     if (state.weight <= 0.0F) {
                        this.stroke.depth = 0.0F;
                        this.response.depth = 0.0F;
                        this.pose = authored;
                     }

                     this.lastWeight = state.weight;
                     this.lastArm = this.pose.getOrDefault("right_arm", this.lastArm).copy();
                     this.lastFore = this.pose.getOrDefault("right_forearm", this.lastFore).copy();
                     return this.pose;
                  }
               } else {
                  this.stroke.depth = 0.0F;
                  this.response.shift.zero();
                  this.reaction.clear();
                  return this.pose;
               }
            }
         }
      }
   }

   public void move(ScenePetClient.State s, double dx, double dy) {
      if (!s.preview) {
         s.cursorX = Mth.clamp(s.cursorX + (float)dx, -1.0F, 1.0F);
         s.cursorY = Mth.clamp(s.cursorY + (float)dy, -1.0F, 1.0F);
      } else {
         Quaternionf view = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
         Vector3f r = view.transform(new Vector3f(1.0F, 0.0F, 0.0F));
         Vector3f u = view.transform(new Vector3f(0.0F, 1.0F, 0.0F));
         float x = (float)(dx * (double)r.dot(this.right) - dy * (double)u.dot(this.right));
         float y = (float)(dy * (double)u.dot(this.up) - dx * (double)r.dot(this.up));
         s.cursorX = Mth.clamp(s.cursorX + x, -1.0F, 1.0F);
         s.cursorY = Mth.clamp(s.cursorY + y, -1.0F, 1.0F);
      }
   }
}
