package pro.webhm.playerinteractions.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import pro.webhm.playerinteractions.body.SurfaceDent;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class SceneSqueezeRuntime {
   private final SceneTouchArm[] arms = new SceneTouchArm[]{new SceneTouchArm(), new SceneTouchArm(true)};
   private final SceneArmPair armPair = new SceneArmPair();
   private final BoneTransform[] previousArm = new BoneTransform[]{new BoneTransform(), new BoneTransform()};
   private final BoneTransform[] previousFore = new BoneTransform[]{new BoneTransform(), new BoneTransform()};
   private final SurfaceDent[] patches = new SurfaceDent[]{new SurfaceDent(), new SurfaceDent()};
   private final SceneSqueezeTargets targets = new SceneSqueezeTargets();
   private final SceneStrokeResponse response = new SceneStrokeResponse();
   private final Matrix4f frame = new Matrix4f();
   private final Vector3f anchor = new Vector3f();
   private final Vector3f contact = new Vector3f();
   private final Vector3f normal = new Vector3f();
   private final Vector3f first = new Vector3f();
   private final Vector3f second = new Vector3f();
   private boolean started;
   private boolean swapped;
   private float lastWeight;
   private final Vector3f[] contacts = new Vector3f[]{new Vector3f(), new Vector3f()};
   private final Vector3f[] normals = new Vector3f[]{new Vector3f(), new Vector3f()};

   public SceneSqueezeRuntime() {
      for (SceneTouchArm arm : this.arms) {
         arm.paired();
      }
   }

   private static String armKey(int i) {
      return i == 0 ? "right_arm" : "left_arm";
   }

   private static String foreKey(int i) {
      return i == 0 ? "right_forearm" : "left_forearm";
   }

   public Map<String, BoneTransform> update(
      SceneTouchRuntime host, ScenePetClient.State state, float partial, float dt, ScenePetClient.TouchActor actor, AbstractClientPlayer npc
   ) {
      SceneTouchSurface surface = host.surface;
      SurfaceDent stroke = host.stroke;
      surface.sample(npc, partial, stroke);
      host.origin = surface.origin;
      host.bone = state.point.bone;
      surface.anchor(state.point, this.anchor);
      surface.frame(state.point.bone.equals("body") ? "body" : "chest", this.frame);
      Map<String, BoneTransform> result = this.solve(host, state, dt, actor, this.frame, this.anchor);
      BodyData data = ScenePetClient.body(npc);
      host.reaction.update(host, state, dt, data.breast, data.breast != null && data.breast.enabled && !data.child);
      return result;
   }

   Map<String, BoneTransform> solve(
      SceneTouchRuntime host, ScenePetClient.State state, float dt, ScenePetClient.TouchActor actor, Matrix4fc frame, Vector3fc anchor
   ) {
      SceneTouchSurface surface = host.surface;
      SurfaceDent stroke = host.stroke;
      Map<String, BoneTransform> authored = actor.pose();
      HashMap<String, BoneTransform> pose = new HashMap<>(authored);
      boolean paired = this.targets.pick(surface, frame, anchor, state.x, -state.y, state.point.area);
      if (paired) {
         stroke.squeeze.frame(frame, this.targets.middle, this.targets.halfWidth, state.point.radius / 16.0F);
         stroke.squeeze.brushRadius = state.point.radius / 16.0F;

         for (int i = 0; i < 2; i++) {
            stroke.squeeze.contact[i].set(this.targets.hits[i].point);
            stroke.squeeze.contactNormal[i].set(this.targets.hits[i].normal);
         }
      }

      stroke.squeeze.press = Math.max(0.0F, Math.min(1.0F, state.pressureAmount));
      float requested = state.point.depth / 16.0F * state.weight * state.pressureFade;
      Vec3 relative = actor.position().subtract(host.origin);

      for (int i = 0; i < 2; i++) {
         if (!this.started) {
            this.previousArm[i] = authored.getOrDefault(armKey(i), new BoneTransform()).copy();
            this.previousFore[i] = authored.getOrDefault(foreKey(i), new BoneTransform()).copy();
         }

         this.arms[i].begin(authored, (float)relative.x, (float)relative.y, (float)relative.z, actor.yaw(), actor.slim(), state.weight);
         this.arms[i].seed(this.previousArm[i], this.previousFore[i]);
         this.patches[i].center.set(this.targets.hits[i].point);
         this.patches[i].normal.set(this.targets.hits[i].normal);
         this.patches[i].radius = state.point.radius / 16.0F;
         this.patches[i].depth = Math.max(stroke.depth, requested);
      }

      surface.contactPatches(this.targets.valid[0] ? this.patches[0] : null, this.targets.valid[1] ? this.patches[1] : null);
      surface.world.transformPosition(this.targets.hits[0].point, this.first);
      surface.world.transformPosition(this.targets.hits[1].point, this.second);
      if (!this.started) {
         this.swapped = this.arms[0].shoulder.distanceSquared(this.second) + this.arms[1].shoulder.distanceSquared(this.first)
            < this.arms[0].shoulder.distanceSquared(this.first) + this.arms[1].shoulder.distanceSquared(this.second);
      }

      this.started = true;
      float gap = 0.0F;
      boolean both = paired;

      for (int i = 0; i < 2; i++) {
         int j = this.swapped ? 1 - i : i;
         SurfaceSnapshot.Hit hit = this.targets.hits[j];
         stroke.displace(hit.point.x, hit.point.y, hit.point.z, this.contact);
         surface.world.transformPosition(this.contact);
         surface.world.transformDirection(this.normal.set(hit.normal)).normalize();
         this.contacts[i].set(this.contact);
         this.normals[i].set(this.normal);
      }

      this.armPair.solve(surface, this.arms, this.contacts, this.normals, paired, state.point.reachLimit, state.weight, this.previousArm, this.previousFore);

      for (int i = 0; i < 2; i++) {
         this.arms[i].inertial(this.previousArm[i], this.previousFore[i], dt);
      }

      for (int i = 0; i < 2; i++) {
         SceneTouchArm arm = this.arms[i];
         Map<String, BoneTransform> result = arm.current();
         gap = Math.max(gap, arm.contactGap(this.contacts[i], this.normals[i]));
         both &= arm.clear(surface);
         if (state.weight < this.lastWeight && this.lastWeight > 0.0F) {
            result = arm.release(this.previousArm[i], this.previousFore[i], 1.0F - state.weight / this.lastWeight);
         }

         this.previousArm[i] = result.get(armKey(i)).copy();
         this.previousFore[i] = result.get(foreKey(i)).copy();
         pose.put(armKey(i), this.previousArm[i]);
         pose.put(foreKey(i), this.previousFore[i]);
      }

      stroke.depth = this.response.pressure(dt, requested, gap, both);
      if (state.weight <= 0.0F) {
         stroke.depth = this.response.depth = 0.0F;
         this.started = false;
         this.armPair.reset();

         for (SceneTouchArm arm : this.arms) {
            arm.resetMotion();
         }

         stroke.squeeze.active = false;
         pose.clear();
         pose.putAll(authored);
      }

      this.lastWeight = state.weight;
      host.marker.set(this.first).add(this.second).mul(0.5F);
      host.markerNormal.set(this.normal);
      host.status = both && gap < 0.025F
         ? (state.pressureAmount > 0.2F ? InteractionClient.text("touch.squeeze_both").getString() : InteractionClient.text("touch.stroke_both").getString())
         : (paired ? InteractionClient.text("touch.approach_both").getString() : InteractionClient.text("touch.choose_torso").getString());
      return pose;
   }
}
